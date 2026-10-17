package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.TableTalk
import kotlin.random.Random

/**
 * What a character should talk about this turn, and the memory the prompt
 * shows so it doesn't repeat itself: a short [hint] picked from the situation
 * (react to the last action, answer whoever spoke, tease someone, the pot,
 * the board, something in character), the latest table talk by others and
 * the character's own last lines.
 *
 * Small models parrot whatever the prompt repeats, so the catchphrase is only
 * offered now and then ([catchphraseOk]); every other turn it counts as
 * already said.
 */
data class TalkPlan(
    val hint: String,
    /** The latest lines by the other characters, oldest first. */
    val others: List<TableTalk.Line> = emptyList(),
    /** This character's own latest lines, oldest first. */
    val mine: List<String> = emptyList(),
    val catchphraseOk: Boolean = false,
    /** Which kind of hint this is ("react", "tease"...): the next turn picks a different one. */
    val kind: String = "",
    /**
     * Whether the character talks this turn at all. Quiet turns leave the talk
     * out of the prompt and the reply ([PokerGrammar]): fewer tokens to read
     * and write, so the model decides faster.
     */
    val speak: Boolean = true,
) {
    companion object {
        const val OTHERS_SHOWN = 3
        const val MINE_SHOWN = 3

        /** Chance (by weight) that a turn's hint invites the catchphrase. */
        private const val CATCHPHRASE_WEIGHT = 0.6

        /** How often a character talks: when someone just spoke, when facing a bet or raise, otherwise. */
        internal const val SPEAK_ANSWER = 0.75
        internal const val SPEAK_PRESSURE = 0.6
        internal const val SPEAK_OTHERWISE = 0.45

        private val actionVerbs = listOf(
            "folds", "checks", "calls", "bets", "raises", "completes", "draws", "stands pat", "discards", "takes a mulligan",
        )

        private val inCharacter = listOf(
            "Brag about yourself, in character.",
            "Complain about your luck tonight, in character.",
            "Make a bold prediction about this hand.",
            "Tell a tiny poker joke in your voice.",
            "Mention something from your life story.",
            "Give the table some unasked-for advice, in character.",
            "Act worried, or act confident: keep them guessing.",
            "Say how you feel about the stakes, in character.",
        )

        /**
         * Plans the talk for [persona]'s turn in [context], from what [talk]
         * remembers; [alwaysSpeak] skips the quiet turns (tests, the harness).
         */
        fun make(
            context: DecisionContext,
            persona: Persona,
            talk: TableTalk,
            random: Random,
            lastKind: String? = null,
            alwaysSpeak: Boolean = false,
        ): TalkPlan {
            val me = persona.name
            val others = talk.recent(OTHERS_SHOWN * 3).filter { it.speaker != me }.takeLast(OTHERS_SHOWN)
            val mine = talk.saidBy(me, MINE_SHOWN)
            // (kind, weight, text): the text is only built for the pick.
            val options = mutableListOf<Triple<String, Double, () -> String>>()

            lastAction(context, me)?.let { line -> options += Triple("react", 3.0) { "React to this: $line." } }
            // Answer whoever spoke last, if it was someone else and recent.
            talk.recent(3).lastOrNull()?.takeIf { it.speaker != me }?.let { line ->
                options += Triple("answer", 3.0) { "Answer ${display(line.speaker)}, who said \"${line.text}\"." }
            }
            val rivals = context.seats.filter { it.seat != context.seat }
            if (rivals.isNotEmpty()) {
                options += Triple("tease", 2.0) {
                    val live = rivals.filter { !it.folded }.ifEmpty { rivals }
                    val s = live[random.nextInt(live.size)]
                    val who = display(s.name)
                    when {
                        s.folded -> "Tease $who for folding."
                        s.allIn -> "Tease $who for going all in."
                        s.betThisRound > 0 -> "Tease $who about that bet."
                        else -> listOf("Tease $who.", "Tease $who about their chip stack.", "Ask $who a cheeky question.").random(random)
                    }
                }
            }
            if (context.pot >= context.bigBlind * 10) options += Triple("pot", 2.0) { "The pot is big (${context.pot} chips): say how that feels." }
            if (context.board.isNotEmpty()) options += Triple("board", 1.0) { "Say something about the board (${context.board.joinToString(" ")})." }
            for (line in inCharacter) options += Triple(line, 2.0 / inCharacter.size) { line }
            val catchphraseFree = persona.catchphrase.isNotBlank() && !talk.saidRecently(persona.catchphrase)
            var catchphraseOk = false
            if (catchphraseFree) {
                options += Triple("catchphrase", CATCHPHRASE_WEIGHT) {
                    catchphraseOk = true
                    "You may use your catchphrase: \"${persona.catchphrase}\"."
                }
            }

            // Never the same kind of hint twice in a row.
            val pool = options.filter { it.first != lastKind }.ifEmpty { options }
            var roll = random.nextDouble() * pool.sumOf { it.second }
            val pick = pool.firstOrNull { (_, weight, _) -> roll -= weight; roll < 0 } ?: pool.last()
            val hint = pick.third()
            // Talk on some turns, not all: more likely when spoken to or under pressure.
            // Spoken to: someone else talked in this hand since this character last acted
            // (from the hand's own history, so the boost is used up by the next decision).
            val history = context.history
            val myLast = history.indexOfLast { it.startsWith("$me ") && !it.contains(" says \"") }
            val spokenTo = history.drop(myLast + 1).any { it.contains(" says \"") && !it.startsWith("$me ") }
            // Facing a real bet: more than the big blind to call, or any bet once the board is out.
            val toCall = context.legal.toCall
            val pressed = toCall > context.bigBlind || (toCall > 0 && context.board.isNotEmpty())
            val chance = when {
                spokenTo -> SPEAK_ANSWER
                pressed -> SPEAK_PRESSURE
                else -> SPEAK_OTHERWISE
            }
            val speak = alwaysSpeak || random.nextDouble() < chance
            return TalkPlan(hint, others, mine, catchphraseOk && speak, pick.first, speak)
        }

        /** The latest betting or drawing line by someone else in this hand ("Mo raises to 60"). */
        internal fun lastAction(context: DecisionContext, me: String): String? {
            val names = context.seats.map { it.name }.filter { it != me }
            return context.history.asReversed().firstOrNull { line ->
                names.any { name -> line.startsWith("$name ") && actionVerbs.any { line.startsWith("$name $it") } }
            }
        }

        /** The human seat is called "You", which reads badly in an instruction. */
        private fun display(name: String): String = if (name == "You") "the human player" else name
    }
}
