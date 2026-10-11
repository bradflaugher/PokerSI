package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.HandEvaluator
import com.bradflaugher.pokersi.run.RunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttractLinesTest {
    @Test
    fun firstLaunchIsWelcoming() {
        val lines = AttractLines.lines(TitleFacts())
        assertEquals("WELCOME, PLAYER ONE", lines.first())
        assertTrue("TODAY'S DAILY IS OPEN" in lines)
        assertTrue("HOUSE BOT ON DUTY" in lines)
        assertTrue("FREE PLAY · NO COINS NEEDED" in lines)
        assertFalse(lines.any { it.startsWith("CREDITS") || it.startsWith("BIGGEST") || it.startsWith("BEST HAND") })
        assertTrue(lines.size in 3..AttractLines.MAX)
    }

    @Test
    fun aSavedTourLeadsWithItsCredits() {
        val tour = RunState.new(5L)
        val best = BestHand.of(HandEvaluator.evaluate(Card.parseList("As Ah Ad Ks Kh")), GameVariant.TEXAS_HOLDEM)
        val stats = LifetimeStats(handsPlayed = 40, handsWon = 12, biggestPot = 3200, bestHand = best)
        val lines = AttractLines.lines(TitleFacts(tour, DailyResult(played = true, finished = true, bestTables = 4), stats, "Qwen 3 1.7B"))
        assertEquals("CREDITS ${formatChips(tour.playerChips)} · TOUR STOP 1", lines.first())
        assertTrue("DAILY TOUR: BEST 4 TABLES" in lines)
        assertTrue("QWEN 3 1.7B IS AT THE TABLE" in lines)
        assertTrue("BIGGEST POT 3,200" in lines)
        assertTrue("BEST HAND: FULL HOUSE" in lines)
        assertFalse(lines.any { it.startsWith("WELCOME") })
        assertTrue(lines.size <= AttractLines.MAX)
    }
}
