package com.bradflaugher.pokersi.ui.screens

import com.bradflaugher.pokersi.game.HandTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandChartTest {
    @Test
    fun talkBackHearsTheTotalsTheFavouritesAndThePick() {
        val hands = mapOf("AA" to HandTally(4, 4), "AKs" to HandTally(5, 4), "72o" to HandTally(11, 0))
        val text = chartDescription(hands, picked = "AKs")
        assertTrue(text, text.contains("you played 40 percent of 20 hands"))
        assertTrue(text, text.contains("Played most: Pair of Aces 100 percent, Ace King suited 80 percent."))
        assertTrue(text, text.contains("Selected: Ace King suited, dealt 5, played 4."))
        assertTrue(chartDescription(hands, "T9o").contains("Ten Nine offsuit, not dealt yet"))
        assertEquals("Hold'em starting-hand chart: no hands yet.", chartDescription(emptyMap(), null))
    }
}
