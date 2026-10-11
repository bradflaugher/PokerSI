package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StartingHandsTest {
    @Test
    fun classesOfTwoCards() {
        assertEquals("AKs", StartingHands.classOf(Card.parseList("Ks As")))
        assertEquals("AKo", StartingHands.classOf(Card.parseList("Ad Kc")))
        assertEquals("77", StartingHands.classOf(Card.parseList("7h 7c")))
        assertEquals("T9o", StartingHands.classOf(Card.parseList("9s Th")))
        assertNull(StartingHands.classOf(Card.parseList("Ad Kc Qs")))
    }

    @Test
    fun theChartHasEveryClassOncePairsOnTheDiagonalSuitedAbove() {
        assertEquals(169, StartingHands.all.toSet().size)
        assertEquals("AA", StartingHands.at(0, 0))
        assertEquals("22", StartingHands.at(12, 12))
        assertEquals("AKs", StartingHands.at(0, 1))
        assertEquals("AKo", StartingHands.at(1, 0))
        assertEquals("32s", StartingHands.at(11, 12))
        assertEquals("32o", StartingHands.at(12, 11))
    }
}
