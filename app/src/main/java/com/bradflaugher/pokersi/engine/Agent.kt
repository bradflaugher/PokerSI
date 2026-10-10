package com.bradflaugher.pokersi.engine

/**
 * The contract between the poker engine and whoever makes decisions for a
 * seat: the human (through the UI), the local LLM (`llm/LlmAgent`) or the
 * built-in House Bot (`engine/HouseBot`).
 *
 * The engine builds a [DecisionContext] holding only what that seat may
 * see, the agent answers with an [AgentReply], and the engine clamps the
 * reply to something legal (see [LegalActions.coerce]) so a confused agent
 * can never break a hand.
 */
sealed interface Decision {
    data object Fold : Decision
    data object Check : Decision
    data object Call : Decision

    /** Bet or raise so this seat's total chips in this betting round become [total]. */
    data class RaiseTo(val total: Int) : Decision

    data object AllIn : Decision

    /** Draw games (and Crazy Pineapple's discard): indices into the seat's hole cards. */
    data class Discard(val indices: Set<Int>) : Decision
}

/** A decision plus optional table talk, shown in the seat's speech bubble. */
data class AgentReply(val decision: Decision, val say: String? = null)

/** How many cards may (or must) be thrown away at a draw / discard step. */
data class DiscardRule(val min: Int, val max: Int)

/**
 * What the acting seat may do right now. Exactly one of two shapes:
 * a betting decision ([discard] == null) or a discard decision ([discard] != null).
 */
data class LegalActions(
    val canFold: Boolean,
    val canCheck: Boolean,
    /** Chips needed to call (0 when checking is possible). Already capped at the seat's stack. */
    val toCall: Int,
    /** Smallest legal [Decision.RaiseTo] total, or null when raising is impossible. */
    val minRaiseTo: Int?,
    /** Largest legal [Decision.RaiseTo] total (all-in or the pot/fixed limit), or null. */
    val maxRaiseTo: Int?,
    val discard: DiscardRule? = null,
) {
    /** Turns any reply into a legal decision, preferring the closest sensible action. */
    fun coerce(decision: Decision): Decision {
        if (discard != null) {
            val d = decision as? Decision.Discard ?: return Decision.Discard(emptySet<Int>().takeIf { discard.min == 0 } ?: (0 until discard.min).toSet())
            var idx = d.indices.filter { it >= 0 }.toSortedSet()
            if (idx.size > discard.max) idx = idx.take(discard.max).toSortedSet()
            var next = 0
            while (idx.size < discard.min) { if (next !in idx) idx.add(next); next++ }
            return Decision.Discard(idx)
        }
        return when (decision) {
            is Decision.Discard -> if (canCheck) Decision.Check else Decision.Fold
            Decision.Fold -> if (canCheck) Decision.Check else Decision.Fold
            Decision.Check -> if (canCheck) Decision.Check else if (toCall > 0) Decision.Call else Decision.Fold
            Decision.Call -> if (canCheck) Decision.Check else Decision.Call
            Decision.AllIn -> when {
                maxRaiseTo != null -> Decision.RaiseTo(maxRaiseTo)
                canCheck -> Decision.Check
                else -> Decision.Call
            }
            is Decision.RaiseTo -> when {
                minRaiseTo == null || maxRaiseTo == null -> if (canCheck) Decision.Check else Decision.Call
                else -> Decision.RaiseTo(decision.total.coerceIn(minRaiseTo, maxRaiseTo))
            }
        }
    }
}

/** One other seat as the acting seat sees it. */
data class SeatView(
    val seat: Int,
    val name: String,
    val chips: Int,
    /** Chips this seat has put in during the current betting round. */
    val betThisRound: Int,
    val folded: Boolean,
    val allIn: Boolean,
    val isDealer: Boolean,
    /** Cards in front of the seat: null entries are face down (stud shows some up). */
    val cards: List<Card?>,
)

/** Everything an agent may know when it is asked to act. */
data class DecisionContext(
    val variant: GameVariant,
    val seat: Int,
    val handNumber: Int,
    /** "Pre-flop", "Flop", "Turn", "Draw", "4th street"... */
    val street: String,
    val holeCards: List<Card>,
    val board: List<Card>,
    val pot: Int,
    val bigBlind: Int,
    val seats: List<SeatView>,
    /** This hand so far, one line per event: "Mo raises to 60", "Flop: Ah 7d 2c". */
    val history: List<String>,
    val legal: LegalActions,
    /** The seat's best hand right now in words ("Pair of Kings"), or null before there is one. */
    val madeHand: String?,
)

/** Anything that can sit at the table. */
interface PlayerAgent {
    /**
     * Decide for the seat in [context]. [onChatter] may be called any number
     * of times with the table talk so far (for a streaming speech bubble).
     */
    suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit = {}): AgentReply
}
