package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Rank
import kotlinx.serialization.Serializable

/** How often the player was dealt a Hold'em starting hand, and how often they played it. */
@Serializable
data class HandTally(val dealt: Int = 0, val played: Int = 0) {
    /** Played as a whole percentage of dealt, or null when never dealt. */
    val playRate: Int? get() = if (dealt == 0) null else (played * 100 + dealt / 2) / dealt
}

/**
 * Hold'em starting hands as the 169 classes of the classic 13x13 chart:
 * pairs on the diagonal ("77"), suited hands above it ("AKs"), offsuit
 * below ("AKo"). Rows and columns run from aces down to deuces.
 */
object StartingHands {
    /** The chart's ranks, top (or left) to bottom (or right). */
    val ranks: List<Rank> = Rank.entries.sortedByDescending { it.value }

    /** The class of two hole cards ("AKs", "77", "T9o"), or null when there aren't exactly two. */
    fun classOf(cards: List<Card>): String? {
        if (cards.size != 2) return null
        val (hi, lo) = cards.sortedByDescending { it.rank.value }
        return when {
            hi.rank == lo.rank -> "${hi.rank.letter}${lo.rank.letter}"
            hi.suit == lo.suit -> "${hi.rank.letter}${lo.rank.letter}s"
            else -> "${hi.rank.letter}${lo.rank.letter}o"
        }
    }

    /** The class at [row], [col] of the chart. */
    fun at(row: Int, col: Int): String {
        val r = ranks[row].letter
        val c = ranks[col].letter
        return when {
            row == col -> "$r$c"
            row < col -> "$r${c}s"
            else -> "$c${r}o"
        }
    }

    /** Every class, row by row. */
    val all: List<String> = (0 until 13).flatMap { row -> (0 until 13).map { col -> at(row, col) } }
}
