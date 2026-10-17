package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.ui.screens.ScreenshotScenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachMarksTest {
    private val betting = ScreenshotScenes.holdemMidHand()
    private val drawing = ScreenshotScenes.drawWithHolds()
    private val showdown = ScreenshotScenes.showdownWin()

    @Test
    fun yourTurnExplainsTheButtons() {
        assertEquals(listOf(Coach.YourTurn), CoachMarks.forTable(betting, raising = false))
    }

    @Test
    fun theRaisePanelComesFirstWhileOpen() {
        assertEquals(listOf(Coach.Raise, Coach.YourTurn), CoachMarks.forTable(betting, raising = true))
        // Once YOUR MOVE has been seen, opening the raise panel brings its own hint.
        assertEquals(Coach.Raise, CoachMarks.next(CoachMarks.forTable(betting, raising = true), setOf(Coach.YourTurn)))
    }

    @Test
    fun drawGamesHoldAndPineappleTosses() {
        assertEquals(listOf(Coach.Hold), CoachMarks.forTable(drawing, raising = false))
        val pineapple = drawing.copy(variant = GameVariant.CRAZY_PINEAPPLE)
        assertEquals(listOf(Coach.Toss), CoachMarks.forTable(pineapple, raising = false))
    }

    @Test
    fun theEndOfAHandExplainsTheResult() {
        assertEquals(listOf(Coach.HandOver), CoachMarks.forTable(showdown, raising = false))
        // Busted and cleared tables have their own banners and buttons.
        assertTrue(CoachMarks.forTable(showdown.copy(phase = TablePhase.Busted), raising = false).isEmpty())
    }

    @Test
    fun pauseWaitsForAQuietMomentAfterTheFirstHand() {
        val waiting = betting.copy(humanTurn = null)
        assertEquals(listOf(Coach.Pause), CoachMarks.forTable(waiting, raising = false))
        assertTrue(CoachMarks.forTable(waiting.copy(handNumber = 1), raising = false).isEmpty())
        assertTrue(CoachMarks.forTable(TableUiState(), raising = false).isEmpty())
    }

    @Test
    fun tourChoiceOnlyOnTheChooseScreen() {
        assertEquals(listOf(Coach.ChooseTable), CoachMarks.forRun(RunScreen.Choose))
        RunScreen.entries.filter { it != RunScreen.Choose }.forEach { assertTrue(CoachMarks.forRun(it).isEmpty()) }
    }

    @Test
    fun eachHintShowsOnce() {
        val wanted = CoachMarks.forTable(betting, raising = false)
        assertEquals(Coach.YourTurn, CoachMarks.next(wanted, emptySet()))
        assertNull(CoachMarks.next(wanted, setOf(Coach.YourTurn)))
        assertNull(CoachMarks.next(emptyList(), emptySet()))
    }

    @Test
    fun aHintGoesWhenItsSituationDoes() {
        assertTrue(CoachMarks.keeps(Coach.YourTurn, CoachMarks.forTable(betting, raising = false)))
        assertFalse(CoachMarks.keeps(Coach.YourTurn, CoachMarks.forTable(betting.copy(humanTurn = null), raising = false)))
        assertFalse(CoachMarks.keeps(Coach.Raise, CoachMarks.forTable(betting, raising = false)))
    }

    @Test
    fun hintsAreShortAndNeverSayInsertCoin() {
        Coach.entries.forEach {
            assertTrue("${it.name} is too long for a hint", it.text.length <= 120)
            assertFalse(it.text.lowercase().contains("coin"))
        }
    }
}
