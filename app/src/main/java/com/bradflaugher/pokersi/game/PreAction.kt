package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BettingStructure
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.LegalActions

/**
 * A move picked while an opponent is still thinking (online poker's "advance
 * action" boxes): played the moment the turn comes round, unless the betting
 * changed in a way that makes it no longer what the player chose.
 *
 * The rules follow the big poker clients:
 * - [CheckFold] never folds for free: it checks if it can, else folds.
 * - [Check] and [Call] are for the bet as it stands; a bet or raise before
 *   your turn cancels them ([stillValid]) and you decide yourself.
 * - [CallAny] and [AllIn] stand whatever happens before your turn.
 * - Every choice lasts one betting round: a new street (or hand) clears it.
 */
sealed interface PreAction {
    /** Check if it's free, fold to a bet. */
    data object CheckFold : PreAction

    /** Check, only if nobody bets first. */
    data object Check : PreAction

    /** Call exactly [amount] (what you faced when you picked it). */
    data class Call(val amount: Int) : PreAction

    /** Call whatever it costs (or check if it's free). */
    data object CallAny : PreAction

    /** As much as the game allows: all in (no limit), a pot-size raise (pot limit), a raise (fixed limit). */
    data object AllIn : PreAction

    /** Whether this still means what the player picked, now that calling costs [toCall]. */
    fun stillValid(toCall: Int): Boolean = when (this) {
        Check -> toCall == 0
        is Call -> toCall == amount
        CheckFold, CallAny, AllIn -> true
    }

    /**
     * The decision this plays now that it is the player's turn with [legal],
     * or null when it no longer applies (the player decides).
     * [allInTotal] is the player's bet this round plus their stack.
     */
    fun decide(legal: LegalActions, allInTotal: Int): Decision? {
        if (legal.discard != null) return null
        return when (this) {
            CheckFold -> if (legal.canCheck) Decision.Check else Decision.Fold
            Check -> if (legal.canCheck) Decision.Check else null
            is Call -> if (!legal.canCheck && legal.toCall == amount) Decision.Call else null
            CallAny -> if (legal.canCheck) Decision.Check else Decision.Call
            AllIn -> {
                val max = legal.maxRaiseTo
                when {
                    max != null && max >= allInTotal -> Decision.AllIn
                    max != null -> Decision.RaiseTo(max)
                    // No raise left (capped, or not reopened): calling is the most there is.
                    legal.canCheck -> Decision.Check
                    else -> Decision.Call
                }
            }
        }
    }

    /** What the toggle says while waiting, facing [toCall] (of a [stack]) in a [structure] game. */
    fun label(toCall: Int, stack: Int, structure: BettingStructure): String = when (this) {
        CheckFold -> if (toCall == 0) "CHECK/\nFOLD" else "FOLD"
        Check -> "CHECK"
        is Call -> if (amount >= stack) "CALL\nALL IN" else "CALL\n${formatChips(amount)}"
        CallAny -> "CALL\nANY"
        AllIn -> allInLabel(structure)
    }

    /** Read aloud by TalkBack and shown once it plays ("Pre-action: call any"). */
    fun spoken(toCall: Int, structure: BettingStructure): String = when (this) {
        CheckFold -> if (toCall == 0) "check or fold" else "fold"
        Check -> "check"
        is Call -> "call ${formatChips(amount)}"
        CallAny -> "call any"
        AllIn -> allInLabel(structure).replace("\n", " ").lowercase()
    }

    companion object {
        fun allInLabel(structure: BettingStructure): String = when (structure) {
            BettingStructure.NO_LIMIT -> "ALL IN"
            BettingStructure.POT_LIMIT -> "RAISE\nPOT"
            BettingStructure.FIXED_LIMIT -> "RAISE"
        }

        /**
         * The toggles to offer while waiting, facing [toCall] (of a [stack]),
         * left to right: fold-ish first (it stands apart behind a moat), then
         * the matching check or call, call any, and all in. A call that would
         * already put the player all in has no separate ALL IN.
         */
        fun options(toCall: Int, stack: Int): List<PreAction> = buildList {
            add(CheckFold)
            if (toCall == 0) add(Check) else add(Call(toCall))
            if (toCall < stack) {
                add(CallAny)
                add(AllIn)
            }
        }
    }
}

/**
 * The advance-action toggles while the player waits: what calling costs right
 * now, their stack, and what (if anything) is picked.
 */
data class PreActionUi(
    val toCall: Int,
    val stack: Int,
    val selected: PreAction?,
) {
    val options: List<PreAction> get() = PreAction.options(toCall, stack)
}
