package com.bradflaugher.pokersi.engine

import kotlin.random.Random

/**
 * A playing personality for [HouseBot], each knob 0..1. [tightness] is how much
 * edge it wants before putting chips in, [aggression] how often and how big it
 * bets its good hands, [bluffing] how often it bets with nothing, [chattiness]
 * how often it says something.
 */
data class BotStyle(
    val name: String,
    val tightness: Double,
    val aggression: Double,
    val bluffing: Double,
    val chattiness: Double = 0.5,
) {
    companion object {
        val NIT = BotStyle("Nit", tightness = 0.9, aggression = 0.25, bluffing = 0.02, chattiness = 0.25)
        val ROCK = BotStyle("Rock", tightness = 0.75, aggression = 0.45, bluffing = 0.05, chattiness = 0.3)
        val SHARK = BotStyle("Shark", tightness = 0.6, aggression = 0.7, bluffing = 0.12, chattiness = 0.4)
        val BALANCED = BotStyle("Regular", tightness = 0.5, aggression = 0.5, bluffing = 0.1, chattiness = 0.5)
        val LOOSE_CANNON = BotStyle("Loose Cannon", tightness = 0.25, aggression = 0.75, bluffing = 0.25, chattiness = 0.7)
        val MANIAC = BotStyle("Maniac", tightness = 0.1, aggression = 0.95, bluffing = 0.4, chattiness = 0.9)
        val CALLING_STATION = BotStyle("Calling Station", tightness = 0.1, aggression = 0.1, bluffing = 0.03, chattiness = 0.6)

        val ALL: List<BotStyle> = listOf(NIT, ROCK, SHARK, BALANCED, LOOSE_CANNON, MANIAC, CALLING_STATION)
    }
}

/**
 * The built-in opponent: Monte Carlo equity against the live hands, pot odds,
 * position and a [BotStyle]. Plays every variant, including draws and
 * Pineapple's discard. Deterministic for a given [seed] and sequence of contexts.
 */
class HouseBot(
    val style: BotStyle = BotStyle.BALANCED,
    seed: Long = 0L,
    private val iterations: Int = 250,
    private val talk: Boolean = true,
) : PlayerAgent {
    private val random = Random(seed)

    /** Equity behind the last betting decision (the "Tell Detector" reads it), or null. */
    var lastEquity: Double? = null
        private set

    override suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit): AgentReply =
        decideNow(context).also { reply -> reply.say?.let(onChatter) }

    /** The same decision, without suspending. */
    fun decideNow(context: DecisionContext): AgentReply {
        val discard = context.legal.discard
        return if (discard != null) discardReply(context, discard) else bettingReply(context)
    }

    // ---------------------------------------------------------------- betting

    private fun bettingReply(ctx: DecisionContext): AgentReply {
        val legal = ctx.legal
        val opponents = ctx.seats.filter { it.seat != ctx.seat && !it.folded }
        val rawEquity = Equity.estimate(
            hole = ctx.holeCards,
            board = ctx.board,
            opponents = opponents.size,
            variant = ctx.variant,
            iterations = iterations,
            rng = random,
            opponentUpCards = if (ctx.variant.isStud) opponents.map { o -> o.cards.filterNotNull() } else emptyList(),
        )
        lastEquity = rawEquity
        val fair = 1.0 / (opponents.size + 1)
        val potOdds = if (legal.toCall == 0) 0.0 else legal.toCall.toDouble() / (ctx.pot + legal.toCall)
        val inPosition = isLastToAct(ctx)
        // Big bets come from better-than-random hands: discount our equity against them.
        val pressure = (legal.toCall.toDouble() / (ctx.pot - legal.toCall).coerceAtLeast(1)).coerceIn(0.0, 1.0)
        val equity = rawEquity * (1.0 - 0.3 * pressure * (0.5 + style.tightness))

        // How far above an even share the hand must be to bet for value (more when re-raising).
        val raiseBar = fair * (1.25 + 0.8 * style.tightness - 0.6 * style.aggression - (if (inPosition) 0.1 else 0.0)) *
            (1.0 + 0.6 * pressure)
        // Extra edge wanted over the pot odds before calling (negative: calls light).
        val callMargin = (style.tightness - 0.35) * 0.2 - if (inPosition) 0.02 else 0.0
        val canRaise = legal.minRaiseTo != null

        val wantsValue = canRaise && equity >= raiseBar.coerceAtLeast(fair * 0.9) && random.nextDouble() < 0.45 + 0.55 * style.aggression
        val wantsBluff = canRaise && !wantsValue && equity < fair &&
            random.nextDouble() < style.bluffing * (if (legal.canCheck) 1.0 else 0.4) * (if (inPosition) 1.3 else 1.0)

        val decision: Decision
        val moment: TalkMoment
        when {
            wantsValue || wantsBluff -> {
                decision = raise(ctx, equity, bluff = wantsBluff)
                val total = (decision as Decision.RaiseTo).total
                val me = ctx.seats.firstOrNull { it.seat == ctx.seat }
                val allIn = me != null && total >= me.betThisRound + me.chips
                moment = when {
                    allIn -> TalkMoment.ALL_IN
                    wantsBluff -> TalkMoment.BLUFF
                    equity > 0.8 -> TalkMoment.MONSTER
                    legal.canCheck -> TalkMoment.BET
                    else -> TalkMoment.RAISE
                }
            }
            legal.canCheck -> {
                decision = Decision.Check
                moment = TalkMoment.CHECK
            }
            equity >= potOdds + callMargin || (style.tightness < 0.2 && legal.toCall <= ctx.bigBlind) -> {
                decision = Decision.Call
                moment = TalkMoment.CALL
            }
            else -> {
                decision = Decision.Fold
                moment = TalkMoment.FOLD
            }
        }
        return AgentReply(legal.coerce(decision), chatter(moment))
    }

    private fun raise(ctx: DecisionContext, equity: Double, bluff: Boolean): Decision {
        val legal = ctx.legal
        val min = legal.minRaiseTo!!
        val max = legal.maxRaiseTo!!
        if (ctx.variant.structure == BettingStructure.FIXED_LIMIT) return Decision.RaiseTo(min)
        val myBet = ctx.seats.firstOrNull { it.seat == ctx.seat }?.betThisRound ?: 0
        val potAfterCall = ctx.pot + legal.toCall
        var fraction = 0.45 + 0.5 * style.aggression + random.nextDouble(-0.15, 0.15)
        if (!bluff && equity > 0.75) fraction += 0.4
        val target = myBet + legal.toCall + (potAfterCall * fraction).toInt()
        val shove = !bluff && equity > 0.85 && random.nextDouble() < style.aggression * 0.6
        val total = if (shove) max else target
        return Decision.RaiseTo(roundChips(total.coerceIn(min, max), ctx.bigBlind, min, max))
    }

    /** Rounds a bet to a tidy multiple of the small unit, staying within [min]..[max]. */
    private fun roundChips(total: Int, bigBlind: Int, min: Int, max: Int): Int {
        if (total == max) return max
        val unit = maxOf(1, bigBlind / 2)
        return ((total + unit / 2) / unit * unit).coerceIn(min, max)
    }

    /** Whether everyone still to act this round is behind us (we act last postflop). */
    private fun isLastToAct(ctx: DecisionContext): Boolean {
        val live = ctx.seats.filter { !it.folded && !it.allIn }.map { it.seat }
        val dealer = ctx.seats.firstOrNull { it.isDealer }?.seat ?: return false
        if (ctx.variant.isStud) return false
        // The live seat closest to the button (counting backwards from it) acts last after the flop.
        val n = (ctx.seats.maxOfOrNull { it.seat } ?: 0) + 1
        for (k in 0 until n) {
            val s = (dealer - k + n) % n
            if (s in live) return s == ctx.seat
        }
        return false
    }

    // ---------------------------------------------------------------- discards

    private fun discardReply(ctx: DecisionContext, rule: DiscardRule): AgentReply {
        val indices = if (ctx.variant.isDraw) drawDiscards(ctx.holeCards) else pineappleDiscard(ctx)
        val coerced = ctx.legal.coerce(Decision.Discard(indices.take(rule.max).toSet()))
        val moment = when {
            !ctx.variant.isDraw -> TalkMoment.DISCARD
            (coerced as Decision.Discard).indices.isEmpty() -> TalkMoment.STAND_PAT
            else -> TalkMoment.DRAW
        }
        return AgentReply(coerced, chatter(moment))
    }

    /** Pineapple: throw away the card whose loss hurts the hand least. */
    private fun pineappleDiscard(ctx: DecisionContext): Set<Int> {
        val hole = ctx.holeCards
        if (hole.size <= 2) return setOf(0)
        val opponents = ctx.seats.count { it.seat != ctx.seat && !it.folded }.coerceAtLeast(1)
        val best = hole.indices.maxBy { i ->
            Equity.estimate(
                hole = hole.filterIndexed { k, _ -> k != i },
                board = ctx.board,
                opponents = opponents,
                variant = GameVariant.TEXAS_HOLDEM,
                iterations = iterations / 2,
                rng = random,
                dead = listOf(hole[i]),
            )
        }
        return setOf(best)
    }

    companion object {
        /**
         * Five-card draw: which cards to throw (at most three). Stands pat on a straight
         * or better, keeps pairs, trips and two pair, draws to four-flushes and
         * open-ended straights, otherwise keeps the two highest cards.
         */
        fun drawDiscards(hand: List<Card>): Set<Int> {
            if (hand.size != 5) return emptySet()
            val value = HandEvaluator.evaluate(hand)
            val byRank = hand.indices.groupBy { hand[it].rank }
            fun allBut(keep: Collection<Int>) = hand.indices.filter { it !in keep }.toSet()
            when (value.category) {
                HandCategory.STRAIGHT, HandCategory.FLUSH, HandCategory.FULL_HOUSE,
                HandCategory.FOUR_OF_A_KIND, HandCategory.STRAIGHT_FLUSH, HandCategory.ROYAL_FLUSH -> return emptySet()
                HandCategory.THREE_OF_A_KIND, HandCategory.TWO_PAIR ->
                    return allBut(byRank.values.filter { it.size >= 2 }.flatten())
                else -> Unit
            }
            val flushDraw = hand.indices.groupBy { hand[it].suit }.values.firstOrNull { it.size == 4 }
            val pair = byRank.values.firstOrNull { it.size == 2 }
            val bigPair = pair != null && hand[pair[0]].rank >= Rank.JACK
            if (flushDraw != null && !bigPair) return allBut(flushDraw)
            if (pair != null) return allBut(pair)
            openEndedDraw(hand)?.let { return allBut(it) }
            val highTwo = hand.indices.sortedByDescending { hand[it].rank }.take(2)
            return allBut(highTwo)
        }

        /** Indices of four consecutive ranks open at both ends, if any. */
        private fun openEndedDraw(hand: List<Card>): List<Int>? {
            val values = hand.map { it.rank.value }
            for (low in 2..10) {
                val run = (low until low + 4).toList()
                if (run.all { it in values }) return run.map { v -> values.indexOf(v) }
            }
            return null
        }
    }

    private fun chatter(moment: TalkMoment): String? =
        if (talk && random.nextDouble() < style.chattiness * 0.6) BotLines.pick(moment, random) else null
}
