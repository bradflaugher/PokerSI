package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.HouseBot
import com.bradflaugher.pokersi.engine.PlayerAgent
import com.bradflaugher.pokersi.run.RunPhase
import com.bradflaugher.pokersi.run.RunState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameControllerTest {
    private fun TestScope.controller(autopilot: PlayerAgent? = null, saver: RunSaver? = null) = GameController(
        scope = backgroundScope,
        pacing = Pacing.Instant,
        speed = MutableStateFlow(GameSpeed.NORMAL),
        compute = Dispatchers.Unconfined,
        runSaver = saver,
        autopilot = autopilot,
        houseBotIterations = 40,
    )

    private fun config(variant: GameVariant = GameVariant.TEXAS_HOLDEM, opponents: Int = 2, seed: Long = 7L) =
        QuickGameConfig(variant, OpponentGenerator.table(seed, opponents), startingStack = 1000, seed = seed)

    @Test
    fun humanInputDrivesTheHand() = runTest {
        val c = controller()
        c.startQuick(config(opponents = 1, seed = 3L))
        var guard = 0
        // Play a few hands by always checking or calling.
        while (c.table.value.handNumber < 3 && guard++ < 500) {
            runCurrent()
            val state = c.table.value
            val turn = state.humanTurn
            when {
                turn != null && turn.isDiscard -> c.confirmDiscard()
                turn != null -> {
                    assertEquals(0, state.seats.first { it.isToAct }.seat)
                    assertNotNull(state.human)
                    c.act(if (turn.legal.canCheck) Decision.Check else Decision.Call)
                }
                state.phase == TablePhase.HandOver -> {
                    assertNotNull("a finished hand has a banner", state.banner)
                    c.next()
                }
                state.phase == TablePhase.Busted -> c.next()
            }
        }
        assertTrue("reached hand 3", c.table.value.handNumber >= 3)
        c.stop()
    }

    @Test
    fun actIsIgnoredWhenItIsNotTheHumansTurn() = runTest {
        val c = controller()
        c.startQuick(config(opponents = 1, seed = 11L))
        runCurrent()
        val before = c.table.value
        if (before.humanTurn == null) {
            c.act(Decision.Fold)
            runCurrent()
            assertFalse(c.table.value.seats[0].folded)
        }
        c.stop()
    }

    @Test
    fun drawGameHoldsAndDraws() = runTest {
        val c = controller()
        c.startQuick(config(variant = GameVariant.FIVE_CARD_DRAW, opponents = 1, seed = 5L))
        var sawDraw = false
        var guard = 0
        while (!sawDraw && guard++ < 400) {
            runCurrent()
            val state = c.table.value
            val turn = state.humanTurn
            when {
                turn != null && turn.isDiscard -> {
                    // Hold the first two cards only: draw three.
                    state.selected.forEach { c.toggleSelect(it) }
                    c.toggleSelect(0)
                    c.toggleSelect(1)
                    assertEquals(setOf(0, 1), c.table.value.selected)
                    c.confirmDiscard()
                    runCurrent()
                    assertEquals(5, c.table.value.human!!.cards.size)
                    sawDraw = true
                }
                turn != null -> c.act(if (turn.legal.canCheck) Decision.Check else Decision.Call)
                state.phase == TablePhase.HandOver || state.phase == TablePhase.Busted -> c.next()
            }
        }
        assertTrue(sawDraw)
        c.stop()
    }

    @Test
    fun quickGameWithHouseBotsRunsToTheEnd() = runTest {
        val c = controller(autopilot = HouseBot(BotStyle.MANIAC, seed = 1, iterations = 40, talk = false))
        c.startQuick(config(opponents = 3, seed = 21L))
        runCurrent()
        val end = c.table.value
        assertTrue("game over: ${end.phase}", end.phase == TablePhase.TableWon || end.phase == TablePhase.Busted)
        assertFalse(c.isRunning)
        val withChips = end.seats.count { it.chips > 0 }
        if (end.phase == TablePhase.TableWon) assertEquals(1, withChips)
        assertEquals(4 * 1000, end.seats.sumOf { it.chips })
    }

    @Test
    fun everyVariantPlaysAHand() = runTest {
        GameVariant.entries.forEach { variant ->
            val c = controller(autopilot = HouseBot(BotStyle.BALANCED, seed = 2, iterations = 30, talk = false))
            c.startQuick(config(variant = variant, opponents = 2, seed = 99L))
            runCurrent()
            assertTrue("$variant dealt hands", c.table.value.handNumber > 1)
        }
    }

    @Test
    fun runTableLeadsToTheShopAndBack() = runTest {
        val saver = RunSaver.InMemory()
        // A shark on autopilot until a table is cleared (or the run ends).
        var seed = 1L
        var cleared = false
        while (!cleared && seed < 30) {
            val c = controller(autopilot = HouseBot(BotStyle.SHARK, seed = seed, iterations = 60, talk = false), saver = saver)
            c.startRun(RunState.new(seed))
            assertEquals(RunScreen.Intro, c.run.value!!.screen)
            assertNotNull(saver.saved)
            c.beginTable()
            runCurrent()
            val ui = c.run.value!!
            if (ui.screen == RunScreen.Cleared) {
                cleared = true
                assertEquals(RunPhase.SHOP, ui.state.phase)
                assertNotNull(ui.reward)
                c.toShop()
                assertEquals(RunScreen.Shop, c.run.value!!.screen)
                assertEquals(3, c.run.value!!.state.shop.size)
                val coins = c.run.value!!.state.coins
                if (c.reroll()) assertTrue(c.run.value!!.state.coins < coins)
                c.leaveShop()
                val next = c.run.value!!
                assertEquals(RunScreen.Intro, next.screen)
                assertEquals(RunPhase.TABLE, next.state.phase)
                assertEquals(1, next.state.stage)
                assertEquals(next.state, saver.saved)
            } else {
                assertEquals(RunScreen.GameOver, ui.screen)
                assertNull("a lost run is not kept", saver.saved)
            }
            seed++
        }
        assertTrue("some seed clears the first table", cleared)
    }

    @Test
    fun suggestedHoldsKeepPairs() {
        val cards = com.bradflaugher.pokersi.engine.Card.parseList("Ks 7d Kh 2c 9s")
        assertEquals(setOf(0, 2), GameController.suggestHolds(cards))
        val nothing = com.bradflaugher.pokersi.engine.Card.parseList("As 7d Kh 2c 9s")
        assertEquals(setOf(0, 2), GameController.suggestHolds(nothing))
    }

    @Test
    fun generatorIsDeterministicAndDistinct() {
        val a = OpponentGenerator.table(42L, 5)
        assertEquals(a, OpponentGenerator.table(42L, 5))
        assertEquals(5, a.map { it.name }.toSet().size)
    }
}
