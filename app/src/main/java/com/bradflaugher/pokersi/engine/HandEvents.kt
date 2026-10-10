package com.bradflaugher.pokersi.engine

/** A player sitting down for a hand. A seat with 0 chips sits the hand out. */
data class SeatSetup(val name: String, val chips: Int)

/**
 * Forced bets and bet sizes for one hand. Blind games use [smallBlind] and
 * [bigBlind] (also the minimum bet in no-limit and pot-limit); fixed-limit
 * games bet [smallBet] on the early streets and [bigBet] on the later ones;
 * stud posts [ante] and a [bringIn] instead of blinds.
 */
data class Stakes(
    val smallBlind: Int,
    val bigBlind: Int,
    val ante: Int = 0,
    val bringIn: Int = 0,
    val smallBet: Int = bigBlind,
    val bigBet: Int = bigBlind * 2,
) {
    companion object {
        /**
         * Sensible stakes for [variant] at a blind level. Stud turns the big blind
         * into its small bet, with an ante of a tenth and a bring-in of a quarter.
         */
        fun forVariant(variant: GameVariant, smallBlind: Int, bigBlind: Int, ante: Int = 0): Stakes =
            if (variant.isStud) {
                Stakes(
                    smallBlind = 0,
                    bigBlind = bigBlind,
                    ante = if (ante > 0) ante else maxOf(1, bigBlind / 10),
                    bringIn = maxOf(1, (bigBlind + 3) / 4),
                    smallBet = bigBlind,
                    bigBet = bigBlind * 2,
                )
            } else {
                Stakes(smallBlind, bigBlind, ante, smallBet = bigBlind, bigBet = bigBlind * 2)
            }
    }
}

enum class ForcedBet(val label: String, val short: String) {
    ANTE("antes", "Ante"),
    SMALL_BLIND("posts the small blind", "Small blind"),
    BIG_BLIND("posts the big blind", "Big blind"),
    BRING_IN("brings it in for", "Bring-in"),
}

enum class ActionKind { FOLD, CHECK, CALL, BET, RAISE }

/**
 * Everything that happens in a hand, in order, for the UI to animate. [text]
 * is the public history line ("Mo raises to 60"), or null for events that
 * would only clutter a prompt. No event reveals a face-down card.
 */
sealed interface HandEvent {
    val text: String?

    data class HandStarted(val handNumber: Int, val variant: GameVariant, val button: Int, override val text: String) : HandEvent

    data class Forced(val seat: Int, val kind: ForcedBet, val amount: Int, override val text: String) : HandEvent

    /** Cards dealt to [seat]; null entries are face down (the seat's owner sees them in a snapshot). */
    data class Dealt(val seat: Int, val cards: List<Card?>, override val text: String? = null) : HandEvent

    /** A new street begins ("Flop", "4th street", "Draw"). */
    data class StreetStarted(val street: String, override val text: String?) : HandEvent

    /** Community cards turned over: [cards] are new, [board] is the whole board now. */
    data class StreetDealt(val street: String, val cards: List<Card>, val board: List<Card>, override val text: String) : HandEvent

    /** A betting action. [amount] is the chips added now, [total] the seat's bet this round afterwards. */
    data class Action(
        val seat: Int,
        val kind: ActionKind,
        val amount: Int,
        val total: Int,
        val allIn: Boolean,
        override val text: String,
    ) : HandEvent

    /** Table talk from the seat's agent. */
    data class Chat(val seat: Int, val line: String, override val text: String) : HandEvent

    /** A seat threw away [count] cards (and in draw games got as many new ones). */
    data class Discarded(val seat: Int, val count: Int, override val text: String) : HandEvent

    /** A seat re-dealt its starting cards (the Run mode's Mulligan Joker). */
    data class Redealt(val seat: Int, override val text: String) : HandEvent

    /** The part of a bet nobody called goes back to its owner. */
    data class UncalledReturned(val seat: Int, val amount: Int, override val text: String) : HandEvent

    data class ShowdownReveal(val seat: Int, val cards: List<Card>, val handValue: HandValue, override val text: String) : HandEvent {
        val description: String get() = handValue.description
    }

    /** [amount] of pot [potIndex] (0 = main pot) goes to [seat]; [handDescription] is null when everyone else folded. */
    data class PotAwarded(val seat: Int, val amount: Int, val potIndex: Int, val handDescription: String?, override val text: String) : HandEvent

    data class HandFinished(override val text: String? = null) : HandEvent
}

/** One pot's outcome: [shares] maps each winning seat to its chips (odd chips included). */
data class PotResult(val index: Int, val amount: Int, val eligible: List<Int>, val shares: Map<Int, Int>, val handDescription: String?) {
    val winners: List<Int> get() = shares.keys.toList()
}

/** How a finished hand came out. Lists are indexed by seat. */
data class HandResult(
    val startingChips: List<Int>,
    val finalChips: List<Int>,
    val pots: List<PotResult>,
    /** True when two or more hands were shown down. */
    val showdown: Boolean,
    /** Each shown-down seat's hand. */
    val handValues: Map<Int, HandValue>,
    /** Seats that were all-in at some point in the hand. */
    val allInSeats: Set<Int>,
) {
    /** Chips won (positive) or lost (negative) by each seat. */
    val net: List<Int> get() = finalChips.indices.map { finalChips[it] - startingChips[it] }

    /** Total chips each seat collected from pots. */
    val collected: Map<Int, Int> get() = pots.flatMap { it.shares.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }

    /** Seats that won at least part of a pot. */
    val winners: Set<Int> get() = collected.keys
}

/** One seat as the UI should draw it right now. */
data class SeatSnapshot(
    val seat: Int,
    val name: String,
    val chips: Int,
    val bet: Int,
    /** Chips this seat has put into the pot this hand (bets included). */
    val committed: Int,
    val folded: Boolean,
    val allIn: Boolean,
    /** Not dealt in (no chips). */
    val sittingOut: Boolean,
    val isDealer: Boolean,
    val isSmallBlind: Boolean,
    val isBigBlind: Boolean,
    val isToAct: Boolean,
    /** Null entries are face down to the viewer. */
    val cards: List<Card?>,
    /** Stud: which of [cards] are face up to everyone. */
    val faceUp: List<Boolean>,
    val lastAction: String?,
    /** The hand shown at showdown. */
    val shownHand: String?,
    val isWinner: Boolean,
)

/** The table as one viewer may see it. */
data class TableSnapshot(
    val variant: GameVariant,
    val handNumber: Int,
    val street: String,
    val board: List<Card>,
    /** Every chip committed this hand, current bets included. */
    val pot: Int,
    /** Main pot and side pots as they stand (sums to [pot]). */
    val pots: List<Int>,
    val currentBet: Int,
    val seats: List<SeatSnapshot>,
    val toAct: Int?,
    val finished: Boolean,
    val viewer: Int?,
)
