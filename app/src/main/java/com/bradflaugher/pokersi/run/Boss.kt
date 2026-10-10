package com.bradflaugher.pokersi.run

import com.bradflaugher.pokersi.engine.ActionKind
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.HandEvent
import kotlinx.serialization.Serializable

/**
 * The twist a boss table plays with. Every field is something the engine or
 * the run can apply: table setup ([TableSpec]), blind schedule, which variant
 * each hand is, an opponent decision filter ([BossTwists]) or UI flags.
 */
@Serializable
data class BossRules(
    /** Opponents never fold to a bet of at most this fraction of the pot. */
    val opponentsCallUpToPotFraction: Double? = null,
    /** Blinds go up every this many hands (instead of the usual schedule)... */
    val blindsUpEvery: Int? = null,
    /** ...multiplied by this much. */
    val blindsGrowth: Double = 2.0,
    /** Opponents say nothing (the UI drops their speech bubbles). */
    val silentOpponents: Boolean = false,
    /** The UI hides opponents' chip counts. */
    val hideChipCounts: Boolean = false,
    /** Multiplies the player's starting chips. */
    val playerChipsMultiplier: Double = 1.0,
    /** This seat repeats the player's last betting action. */
    val mirrorSeat: Int? = null,
    /** Everyone antes this fraction of the big blind every hand. */
    val anteFraction: Double = 0.0,
    /** The game changes every hand. */
    val rotateVariants: Boolean = false,
    /** The table plays only this game. */
    val forcedVariant: GameVariant? = null,
    /** Multiplies the table's hand limit. */
    val handLimitMultiplier: Double = 1.0,
)

data class Boss(val id: String, val name: String, val description: String, val rules: BossRules)

object Bosses {
    val ALL: List<Boss> = listOf(
        Boss("the_wall", "The Wall", "Never folds to a bet under half the pot. Bluffing is futile.",
            BossRules(opponentsCallUpToPotFraction = 0.5)),
        Boss("blind_fury", "Blind Fury", "Blinds double every 3 hands. Tick tock.",
            BossRules(blindsUpEvery = 3, blindsGrowth = 2.0)),
        Boss("the_mime", "The Mime", "No table talk. Chip counts hidden. Spooky.",
            BossRules(silentOpponents = true, hideChipCounts = true)),
        Boss("short_stack", "Short Stack", "You sit down with half your chips. The rest waits at the cashier.",
            BossRules(playerChipsMultiplier = 0.5)),
        Boss("the_mirror", "The Mirror", "Copies your last move. Check, it checks. Raise, it raises.",
            BossRules(mirrorSeat = 1)),
        Boss("the_tax_man", "The Tax Man", "Everyone antes a quarter of the big blind. Every hand.",
            BossRules(anteFraction = 0.25)),
        Boss("the_shapeshifter", "The Shapeshifter", "A different game every hand.",
            BossRules(rotateVariants = true)),
        Boss("four_card_monty", "Four-Card Monty", "Pot-Limit Omaha only. Use exactly two!",
            BossRules(forcedVariant = GameVariant.OMAHA)),
        Boss("the_clock", "The Clock", "Hit the target in 60% of the usual hands.",
            BossRules(handLimitMultiplier = 0.6)),
    )

    private val BY_ID = ALL.associateBy { it.id }

    fun byId(id: String): Boss? = BY_ID[id]
}

/** Boss rules that act on decisions, applied by the game layer between an agent and the hand. */
object BossTwists {
    /**
     * Adjusts an opponent's [decision] for [boss]'s rules: The Wall turns cheap folds
     * into calls, The Mirror repeats [playerLastAction]. The result is still coerced
     * by the hand, so it never needs to be legal here.
     */
    fun adjustOpponentDecision(boss: Boss?, context: DecisionContext, decision: Decision, playerLastAction: ActionKind?): Decision {
        val rules = boss?.rules ?: return decision
        val legal = context.legal
        if (legal.discard != null) return decision
        if (rules.mirrorSeat == context.seat && playerLastAction != null) {
            return when (playerLastAction) {
                ActionKind.FOLD -> Decision.Fold
                ActionKind.CHECK -> Decision.Check
                ActionKind.CALL -> Decision.Call
                ActionKind.BET, ActionKind.RAISE -> legal.minRaiseTo?.let { Decision.RaiseTo(it) } ?: Decision.Call
            }
        }
        val fraction = rules.opponentsCallUpToPotFraction
        if (fraction != null && legal.coerce(decision) == Decision.Fold && legal.toCall <= context.pot * fraction) {
            return Decision.Call
        }
        return decision
    }

    /** The last betting action [seat] took in [events] (this hand), or null. */
    fun lastActionOf(events: List<HandEvent>, seat: Int): ActionKind? =
        events.lastOrNull { it is HandEvent.Action && it.seat == seat }?.let { (it as HandEvent.Action).kind }
}
