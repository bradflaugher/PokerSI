package com.bradflaugher.pokersi.ui.screens

import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.HandEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How to Play's tap-a-ranking examples must really be the hands they claim to be. */
class HandExamplesTest {
    @Test
    fun everyRankingIsListedBestFirst() {
        assertEquals(HandCategory.entries.sortedByDescending { if (it == HandCategory.ROYAL_FLUSH) 99 else it.ordinal }.map { it.label.uppercase() }, HandExamples.map { it.name })
    }

    @Test
    fun eachExampleIsItsHand() {
        HandExamples.forEach { ex ->
            val cards = Card.parseList(ex.cards)
            assertEquals(ex.cards, 5, cards.size)
            assertEquals(ex.name, HandEvaluator.evaluate(cards).category.label.uppercase())
            assertTrue("${ex.name}: making cards ${ex.making}", ex.making in 1..5)
        }
    }
}
