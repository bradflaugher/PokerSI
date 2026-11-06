package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BettingStructure
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.DiscardRule
import com.bradflaugher.pokersi.engine.LegalActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreActionTest {
    /** Free to check, raise up to 1000 total (all in). */
    private val free = LegalActions(canFold = false, canCheck = true, toCall = 0, minRaiseTo = 20, maxRaiseTo = 1000)

    /** Facing 60, raise up to 1000 total (all in). */
    private val facing = LegalActions(canFold = true, canCheck = false, toCall = 60, minRaiseTo = 140, maxRaiseTo = 1000)

    @Test
    fun checkFoldNeverFoldsForFree() {
        assertEquals(Decision.Check, PreAction.CheckFold.decide(free, 1000))
        assertEquals(Decision.Fold, PreAction.CheckFold.decide(facing, 1000))
    }

    @Test
    fun checkAndCallOnlyForTheBetTheyWerePickedFor() {
        assertEquals(Decision.Check, PreAction.Check.decide(free, 1000))
        assertNull(PreAction.Check.decide(facing, 1000))
        assertEquals(Decision.Call, PreAction.Call(60).decide(facing, 1000))
        assertNull(PreAction.Call(40).decide(facing, 1000))
        assertNull(PreAction.Call(60).decide(free, 1000))
        assertTrue(PreAction.Check.stillValid(0))
        assertFalse(PreAction.Check.stillValid(60))
        assertTrue(PreAction.Call(60).stillValid(60))
        assertFalse(PreAction.Call(60).stillValid(200))
        listOf(PreAction.CheckFold, PreAction.CallAny, PreAction.AllIn).forEach { assertTrue(it.stillValid(500)) }
        // A short stack's CALL ALL IN: the capped call stays 400, but a raise over the 600 bet still cancels it.
        val allInCall = PreAction.options(400, 400, topBet = 600)[1]
        assertEquals(PreAction.Call(400, 600), allInCall)
        assertTrue(allInCall.stillValid(400, 600))
        assertFalse(allInCall.stillValid(400, 1200))
    }

    @Test
    fun callAnyChecksOrCalls() {
        assertEquals(Decision.Check, PreAction.CallAny.decide(free, 1000))
        assertEquals(Decision.Call, PreAction.CallAny.decide(facing, 1000))
    }

    @Test
    fun allInIsTheMostTheGameAllows() {
        assertEquals(Decision.AllIn, PreAction.AllIn.decide(facing, 1000))
        // Pot or fixed limit: the biggest raise allowed.
        assertEquals(Decision.RaiseTo(300), PreAction.AllIn.decide(facing.copy(maxRaiseTo = 300), 1000))
        // No raise left: never quietly a call; the player decides.
        assertNull(PreAction.AllIn.decide(facing.copy(minRaiseTo = null, maxRaiseTo = null), 1000))
        assertNull(PreAction.AllIn.decide(free.copy(minRaiseTo = null, maxRaiseTo = null), 1000))

    }

    @Test
    fun nothingPlaysAtADiscard() {
        val draw = LegalActions(false, false, 0, null, null, discard = DiscardRule(0, 3))
        listOf(PreAction.CheckFold, PreAction.Check, PreAction.CallAny, PreAction.AllIn).forEach { assertNull(it.decide(draw, 1000)) }
    }

    @Test
    fun optionsFollowTheBet() {
        assertEquals(listOf(PreAction.CheckFold, PreAction.Check, PreAction.CallAny, PreAction.AllIn), PreAction.options(0, 1000))
        assertEquals(listOf(PreAction.CheckFold, PreAction.Call(60), PreAction.CallAny, PreAction.AllIn), PreAction.options(60, 1000))
        // A call that already puts you all in: no separate CALL ANY or ALL IN.
        assertEquals(listOf(PreAction.CheckFold, PreAction.Call(400)), PreAction.options(400, 400))
    }

    @Test
    fun labelsSayWhatTheyDo() {
        val nl = BettingStructure.NO_LIMIT
        assertEquals("CHECK/\nFOLD", PreAction.CheckFold.label(0, 1000, nl))
        assertEquals("FOLD", PreAction.CheckFold.label(60, 1000, nl))
        assertEquals("CALL\n1,200", PreAction.Call(1200).label(1200, 5000, nl))
        assertEquals("CALL\nALL IN", PreAction.Call(400).label(400, 400, nl))
        assertEquals("ALL IN", PreAction.AllIn.label(0, 1000, nl))
        assertEquals("RAISE\nPOT", PreAction.AllIn.label(0, 1000, BettingStructure.POT_LIMIT))
        assertEquals("RAISE", PreAction.AllIn.label(0, 1000, BettingStructure.FIXED_LIMIT))
        assertEquals("raise pot", PreAction.AllIn.spoken(0, BettingStructure.POT_LIMIT))
    }

    @Test
    fun potOddsCountOnlyWhatACallCanWin() {
        // You have 50 left after 10 in; the opponent shoves 100 on top of their 10. Calling 50 can win
        // their 10 + 50 matched (not the unmatched 50) plus your own 10.
        assertEquals(70, GameController.winnablePot(listOf(10, 110), me = 0, toCall = 50))
        // Covered: the whole pot.
        assertEquals(30, GameController.winnablePot(listOf(10, 20), me = 0, toCall = 10))
        val turn = HumanTurn(LegalActions(true, false, 50, null, null), pot = 120, bigBlind = 10, betThisRound = 0, stack = 50, winnable = 70)
        assertEquals(50f / 120f, turn.callShare, 1e-6f)
        assertEquals("41.7% rounds up", 42, turn.needPercent)
        assertEquals("exact stays", 25, turn.copy(winnable = 150).needPercent)
    }
}
