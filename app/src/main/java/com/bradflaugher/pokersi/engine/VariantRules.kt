package com.bradflaugher.pokersi.engine

/** Rule facts the engine derives from a [GameVariant]. */

/** Plays with the 36-card deck and short-deck ranking (flush beats a full house, A-6-7-8-9 straight). */
val GameVariant.usesShortDeck: Boolean get() = this == GameVariant.SHORT_DECK

/** Seven-card stud: antes and a bring-in instead of blinds, no community board. */
val GameVariant.isStud: Boolean get() = this == GameVariant.SEVEN_CARD_STUD

/** Five-card draw: no board, one draw between two betting rounds. */
val GameVariant.isDraw: Boolean get() = this == GameVariant.FIVE_CARD_DRAW

/** Flop games: Hold'em, Omaha, Short Deck, Crazy Pineapple. */
val GameVariant.hasBoard: Boolean get() = !isStud && !isDraw

/** Omaha's rule: exactly two hole cards and exactly three board cards. */
val GameVariant.mustUseTwoHoleCards: Boolean get() = this == GameVariant.OMAHA

/** Every variant but stud posts a small and big blind. */
val GameVariant.usesBlinds: Boolean get() = !isStud

/** The names of the betting/drawing steps of a hand, in order. */
val GameVariant.streetNames: List<String>
    get() = when {
        isStud -> listOf("3rd street", "4th street", "5th street", "6th street", "7th street")
        isDraw -> listOf("Pre-draw", "Draw", "Post-draw")
        this == GameVariant.CRAZY_PINEAPPLE -> listOf("Pre-flop", "Flop", "Discard", "Turn", "River")
        else -> listOf("Pre-flop", "Flop", "Turn", "River")
    }

/**
 * Where this category ranks: its order, except that with the short deck
 * ([shortDeck]) a flush beats a full house.
 */
fun HandCategory.strength(shortDeck: Boolean): Int = when {
    !shortDeck -> ordinal
    this == HandCategory.FLUSH -> HandCategory.FULL_HOUSE.ordinal
    this == HandCategory.FULL_HOUSE -> HandCategory.FLUSH.ordinal
    else -> ordinal
}
