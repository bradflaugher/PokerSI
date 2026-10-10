package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.Rank
import kotlin.math.roundToInt

/** One chat message for the model's chat template. */
data class ChatMessage(val role: String, val content: String) {
    companion object {
        fun system(content: String) = ChatMessage("system", content)
        fun user(content: String) = ChatMessage("user", content)
    }
}

/**
 * Builds the self-contained prompt for one decision: a system message with
 * the persona and the output format, and a user message with only what the
 * seat can see, spelled out for a small model (hand strength in words, exact
 * numbers for every legal action). Kept to a few hundred tokens.
 */
object PokerPrompt {
    /** Most recent history lines kept; older ones rarely matter to the decision. */
    private const val HISTORY_LINES = 10

    fun build(context: DecisionContext, persona: Persona): List<ChatMessage> {
        val discard = context.legal.discard != null
        return listOf(
            ChatMessage.system(system(persona, discard)),
            ChatMessage.user(if (discard) discardSituation(context) else bettingSituation(context, persona)),
        )
    }

    private fun system(persona: Persona, discard: Boolean): String = buildString {
        appendLine("You are ${persona.name}, a character playing poker at a friendly game night. ${persona.bio}")
        appendLine("You talk like ${persona.voice}. Your catchphrase: \"${persona.catchphrase}\"")
        appendLine("Your playing style: ${persona.styleWords}.")
        appendLine()
        appendLine("Each turn you see the table and answer with one JSON object only:")
        if (discard) {
            appendLine("""{"discard": [<positions of the cards to throw away>], "say": "<table talk>"}""")
            appendLine("Keep the cards that make your hand (pairs, high cards) and throw away the rest.")
        } else {
            appendLine("""{"action": "<one legal action>", "amount": <chips>, "say": "<table talk>"}""")
        }
        append("\"say\" is one short, funny sentence in your voice, under 12 words. Family friendly. Never mention your cards.")
    }

    private fun bettingSituation(c: DecisionContext, persona: Persona): String = buildString {
        val strength = header(c)
        appendLine("Pot: ${c.pot}. ${stackLine(c)}")
        if (c.legal.toCall > 0) {
            val odds = (100.0 * c.legal.toCall / (c.pot + c.legal.toCall)).roundToInt()
            appendLine("To call: ${c.legal.toCall} (call ${c.legal.toCall} to win ${c.pot + c.legal.toCall}: you need to win $odds% of the time).")
        } else {
            appendLine("Nobody has bet: you can check for free.")
        }
        players(c)
        history(c)
        appendLine("Legal actions (pick exactly one):")
        for (option in PokerGrammar.actionMenu(c)) {
            val detail = when {
                option.range != null -> "amount = your total bet this round, ${option.range.first} to ${option.range.last}"
                option.word == "allin" -> "bet all your chips, amount ${option.amount}"
                else -> "amount ${option.amount}"
            }
            appendLine("- \"${option.word}\" ($detail)")
        }
        // Last, where a small model pays the most attention.
        appendLine("Your hand is ${strength.label}. ${advice(c, strength, persona)}")
        append("Your move, ${nameOf(c)}?")
    }

    private fun discardSituation(c: DecisionContext): String = buildString {
        header(c, numbered = true)
        val rule = c.legal.discard!!
        val count = if (rule.min == rule.max) "exactly ${rule.min}" else "${rule.min} to ${rule.max}"
        appendLine("Pot: ${c.pot}. ${stackLine(c)}")
        players(c)
        history(c)
        val replaced = c.variant == GameVariant.FIVE_CARD_DRAW
        appendLine("Now throw away $count of your cards" + if (replaced) " and get new ones." else ".")
        appendLine("List the positions (1 to ${c.holeCards.size}) of the cards to throw away.")
        appendLine("Advice: \"discard\": ${usualDiscard(c)}.")
        append("Your move, ${nameOf(c)}?")
    }

    private fun StringBuilder.header(c: DecisionContext, numbered: Boolean = false): Strength {
        appendLine("Game: ${c.variant.rulesForPrompt}")
        appendLine("Hand #${c.handNumber}, ${c.street}. Big blind ${c.bigBlind}.")
        appendLine("Cards: rank then suit (A K Q J T 9...2; s=spades h=hearts d=diamonds c=clubs).")
        val cards = if (numbered) c.holeCards.mapIndexed { i, card -> "${i + 1}=$card" } else c.holeCards.map { it.toString() }
        appendLine("Your cards: ${cards.joinToString(" ")}")
        if (c.board.isNotEmpty()) appendLine("Board: ${c.board.joinToString(" ")}")
        val (hand, strength) = strength(c)
        appendLine("Your hand: $hand (${strength.label})")
        return strength
    }

    private fun stackLine(c: DecisionContext): String {
        val me = c.seats.firstOrNull { it.seat == c.seat } ?: return ""
        val inPot = if (me.betThisRound > 0) " (${me.betThisRound} already bet this round)" else ""
        return "You have ${me.chips} chips$inPot."
    }

    private fun StringBuilder.players(c: DecisionContext) {
        val others = c.seats.filter { it.seat != c.seat }
        if (others.isEmpty()) return
        appendLine("Opponents:")
        for (s in others) {
            val state = when {
                s.folded -> "folded"
                s.allIn -> "ALL IN"
                else -> "${s.chips} chips" + if (s.betThisRound > 0) ", bet ${s.betThisRound} this round" else ""
            }
            val up = s.cards.filterNotNull()
            val shown = if (up.isNotEmpty() && !s.folded) ", showing ${up.joinToString(" ")}" else ""
            val dealer = if (s.isDealer) " (dealer)" else ""
            appendLine("- ${s.name}$dealer: $state$shown")
        }
    }

    private fun StringBuilder.history(c: DecisionContext) {
        if (c.history.isEmpty()) return
        appendLine("This hand so far:")
        for (line in c.history.takeLast(HISTORY_LINES)) appendLine("- $line")
    }

    private fun nameOf(c: DecisionContext): String =
        c.seats.firstOrNull { it.seat == c.seat }?.name ?: "player"

    /** A rough hand rating, because small models can't rate poker hands on their own. */
    internal enum class Strength(val label: String) {
        WEAK("weak"), MEDIUM("medium"), GOOD("good"), STRONG("strong"), VERY_STRONG("very strong")
    }

    /** The seat's hand in words plus its [Strength], e.g. "Pair of Aces" / STRONG. */
    internal fun strength(c: DecisionContext): Pair<String, Strength> {
        val made = c.madeHand
        val early = made == null || made.startsWith("High", ignoreCase = true) || made.contains("pair", ignoreCase = true)
        if (c.board.isEmpty() && c.holeCards.size <= 3 && early) {
            return (made ?: describeStart(c.holeCards)) to preflopStrength(c.holeCards)
        }
        if (made == null) return "nothing yet" to Strength.WEAK
        val m = made.lowercase()
        val strength = when {
            listOf("royal", "straight flush", "four of a kind", "quads", "full house", "flush", "straight").any { it in m } -> Strength.VERY_STRONG
            listOf("three of a kind", "trips", "set of", "two pair").any { it in m } -> Strength.STRONG
            "pair" in m -> if ((pairRank(m) ?: 0) >= Rank.JACK.value) Strength.GOOD else Strength.MEDIUM
            else -> Strength.WEAK
        }
        return made to strength
    }

    /**
     * What a sensible player would usually do, so a 1B model has a sane
     * default to follow or (in character) to ignore.
     */
    internal fun advice(c: DecisionContext, strength: Strength, persona: Persona): String {
        val facingBet = c.legal.toCall > 0
        val bigBet = facingBet && c.legal.toCall * 3 > c.pot
        val words = PokerGrammar.actionMenu(c).map { it.word }
        val passive = if (facingBet) "call" else "check"
        val aggressive = words.firstOrNull { it == "raise" } ?: words.firstOrNull { it == "allin" } ?: passive
        val usual = when (strength) {
            Strength.VERY_STRONG, Strength.STRONG -> aggressive
            Strength.GOOD -> if (facingBet) passive else aggressive
            Strength.MEDIUM -> if (bigBet) "fold" else passive
            Strength.WEAK -> if (facingBet) "fold" else "check"
        }.takeIf { it in words } ?: words.first()
        val bluff = persona.bluffiness > 0.6f && strength <= Strength.MEDIUM && aggressive != passive
        return "Advice: \"$usual\"." + if (bluff) " A bluffer like you might \"$aggressive\" instead." else ""
    }

    private fun pairRank(m: String): Int? =
        Rank.entries.firstOrNull { "pair of ${it.plural.lowercase()}" in m }?.value

    /**
     * 1-based positions a plain strategy throws away: keep cards that pair
     * another hole or board card, then the highest; throw the lowest of the
     * rest, as many as the rule allows (and at least as many as it demands).
     */
    internal fun usualDiscard(c: DecisionContext): List<Int> {
        val rule = c.legal.discard ?: return emptyList()
        val ranks = (c.holeCards + c.board).groupingBy { it.rank }.eachCount()
        val paired = c.holeCards.indices.filter { ranks.getValue(c.holeCards[it].rank) > 1 }
        val candidates = c.holeCards.indices.filter { it !in paired }.sortedBy { c.holeCards[it].rank.value }
        // With nothing paired, a draw keeps its best card or two.
        val keepHigh = if (paired.isEmpty()) candidates.filter { c.holeCards[it].rank.value >= Rank.KING.value }.takeLast(2) else emptyList()
        var out = candidates.filter { it !in keepHigh }.take(rule.max)
        if (out.size < rule.min) out = (out + c.holeCards.indices.sortedBy { c.holeCards[it].rank.value }.filter { it !in out }).take(rule.min)
        return out.map { it + 1 }.sorted()
    }

    /** "Ace-King suited", "pair of Sevens", "Queen-Nine-Four". */
    private fun describeStart(cards: List<Card>): String {
        val sorted = cards.sortedByDescending { it.rank.value }
        if (cards.size == 2 && sorted[0].rank == sorted[1].rank) return "pair of ${sorted[0].rank.plural}"
        val suited = cards.size == 2 && sorted[0].suit == sorted[1].suit
        return sorted.joinToString("-") { it.rank.label } + if (suited) " suited" else ""
    }

    /** A rough pre-flop rating from the best two cards. */
    private fun preflopStrength(cards: List<Card>): Strength {
        if (cards.size < 2) return Strength.WEAK
        var best = 0
        for (i in cards.indices) for (j in i + 1 until cards.size) {
            val a = maxOf(cards[i].rank.value, cards[j].rank.value)
            val b = minOf(cards[i].rank.value, cards[j].rank.value)
            var score = a + b
            if (a == b) score += 12 + a
            if (cards[i].suit == cards[j].suit) score += 2
            if (a - b in 1..2) score += 1
            best = maxOf(best, score)
        }
        return when {
            best >= 34 -> Strength.VERY_STRONG
            best >= 26 -> Strength.STRONG
            best >= 23 -> Strength.GOOD
            best >= 20 -> Strength.MEDIUM
            else -> Strength.WEAK
        }
    }
}
