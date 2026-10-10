package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.AgentReply
import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.HouseBot
import com.bradflaugher.pokersi.engine.PlayerAgent
import com.bradflaugher.pokersi.engine.isDraw
import com.bradflaugher.pokersi.run.RunPhase
import com.bradflaugher.pokersi.run.RunState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameControllerTest {
    private fun TestScope.controller(
        autopilot: PlayerAgent? = null,
        saver: RunSaver? = null,
        brains: OpponentBrains = HouseOnlyBrains,
        replaceAfter: Int? = 1,
        pacing: Pacing = Pacing.Instant,
    ) = GameController(
        scope = backgroundScope,
        brains = brains,
        pacing = pacing,
        speed = MutableStateFlow(GameSpeed.NORMAL),
        compute = Dispatchers.Unconfined,
        runSaver = saver,
        autopilot = autopilot,
        houseBotIterations = 40,
        agentTimeoutMs = 5_000,
        replaceBustedAfterHands = replaceAfter,
    )

    private fun agentOf(block: suspend (DecisionContext, (String) -> Unit) -> AgentReply) = object : PlayerAgent {
        override suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit): AgentReply = block(context, onChatter)
    }

    /** Every opponent seat played by [agent]. */
    private fun brainsOf(agent: (seat: Int, fallback: PlayerAgent) -> PlayerAgent) = object : OpponentBrains {
        override val label = MutableStateFlow("TEST")
        override fun agentFor(seat: Int, opponent: OpponentProfile, fallback: PlayerAgent) = agent(seat, fallback)
    }

    /** Plays the human seat by checking or calling (holding the suggested cards, tossing the first) until [done]. */
    private fun TestScope.drive(c: GameController, steps: Int = 2000, done: () -> Boolean) {
        var guard = 0
        while (!done() && guard++ < steps) {
            runCurrent()
            val state = c.table.value
            val turn = state.humanTurn
            when {
                turn != null && turn.isDiscard -> {
                    if (!state.variant.isDraw && state.selected.isEmpty()) c.toggleSelect(0)
                    c.confirmDiscard()
                }
                turn != null -> c.act(if (turn.legal.canCheck) Decision.Check else Decision.Call)
                state.phase == TablePhase.HandOver || state.phase == TablePhase.Busted -> c.next()
            }
        }
    }

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
        val c = controller(autopilot = HouseBot(BotStyle.MANIAC, seed = 1, iterations = 40, talk = false), replaceAfter = null)
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
    fun runTableLeadsToTheTableChoiceAndOn() = runTest {
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
                assertEquals(RunPhase.CHOOSE, ui.state.phase)
                assertNotNull(ui.reward)
                c.toChoose()
                val choosing = c.run.value!!
                assertEquals(RunScreen.Choose, choosing.screen)
                assertEquals(3, choosing.state.offers.size)
                assertEquals(3, choosing.offerOpponents.size)
                assertEquals(choosing.state.offers[2].opponents.size, choosing.offerOpponents[2].size)
                assertTrue(c.chooseTable(2))
                val next = c.run.value!!
                assertEquals(RunScreen.Intro, next.screen)
                assertEquals(RunPhase.TABLE, next.state.phase)
                assertEquals(2, next.state.level)
                assertTrue(next.state.table.isBoss)
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

    // ---------------------------------------------------------------- misbehaving agents

    @Test
    fun hungAgentTimesOutToTheHouseBotAndIsCancelled() = runTest {
        var cancelled = 0
        val hung = agentOf { _, _ ->
            try {
                awaitCancellation()
            } finally {
                cancelled++
            }
        }
        val c = controller(brains = brainsOf { _, _ -> hung })
        c.startQuick(config(opponents = 2, seed = 4L))
        drive(c) { advanceTimeBy(1_000); c.table.value.handNumber >= 3 }
        assertTrue("the table kept dealing: hand ${c.table.value.handNumber}", c.table.value.handNumber >= 3)
        assertTrue("every timed-out decision was cancelled", cancelled > 0)
        c.stop()
    }

    @Test
    fun throwingAgentFallsBack() = runTest {
        val broken = agentOf { _, _ -> error("model on fire") }
        val c = controller(brains = brainsOf { _, _ -> broken })
        c.startQuick(config(opponents = 3, seed = 6L))
        drive(c) { c.table.value.handNumber >= 3 }
        assertTrue(c.table.value.handNumber >= 3)
        c.stop()
    }

    @Test
    fun garbageDecisionsAreCoercedInEveryVariant() = runTest {
        val garbage = listOf(Decision.RaiseTo(-5), Decision.RaiseTo(Int.MAX_VALUE), Decision.Discard(setOf(-1, 42)), Decision.Check, Decision.Fold, Decision.AllIn)
        GameVariant.entries.forEach { variant ->
            var n = 0
            val silly = agentOf { _, _ -> AgentReply(garbage[n++ % garbage.size], "x".repeat(500)) }
            val c = controller(brains = brainsOf { _, _ -> silly })
            c.startQuick(config(variant = variant, opponents = 2, seed = 12L))
            var longest = 0
            drive(c) {
                longest = maxOf(longest, (c.table.value.seats.maxOfOrNull { it.chatter?.length ?: 0 } ?: 0))
                c.table.value.handNumber >= 3 || c.table.value.phase == TablePhase.TableWon
            }
            assertTrue("$variant kept dealing: ${c.table.value.phase} #${c.table.value.handNumber}", c.table.value.handNumber >= 3 || c.table.value.phase == TablePhase.TableWon)
            assertTrue("$variant: a bubble holds at most ${GameController.SAY_MAX} chars, saw $longest", longest in 1..GameController.SAY_MAX)
            c.stop()
        }
    }

    @Test
    fun humanInputWorksInEveryVariant() = runTest {
        GameVariant.entries.forEach { variant ->
            val c = controller()
            c.startQuick(config(variant = variant, opponents = 2, seed = 31L))
            drive(c) { c.table.value.handNumber >= 4 }
            assertTrue("$variant: human-driven hands", c.table.value.handNumber >= 4)
            c.stop()
        }
    }

    @Test
    fun tableTalkFromAnotherThreadIsSafe() = runTest {
        val chatty = agentOf { ctx, onChatter ->
            // Like the LLM: table talk arrives on the inference thread.
            val t = Thread { onChatter("thinking about it...") }
            t.start()
            t.join()
            AgentReply(if (ctx.legal.canCheck) Decision.Check else Decision.Call, "done")
        }
        val c = controller(brains = brainsOf { _, _ -> chatty })
        c.startQuick(config(opponents = 2, seed = 8L))
        drive(c) { c.table.value.handNumber >= 3 }
        assertTrue(c.table.value.handNumber >= 3)
        c.stop()
    }

    // ---------------------------------------------------------------- pause, leave, cancel

    @Test
    fun pauseFreezesTheBots() = runTest {
        var decisions = 0
        val counting = agentOf { ctx, _ -> decisions++; AgentReply(if (ctx.legal.canCheck) Decision.Check else Decision.Call) }
        val c = controller(brains = brainsOf { _, _ -> counting })
        c.setPaused(PauseReason.Menu, true)
        c.startQuick(config(opponents = 3, seed = 9L))
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("no bot acts while paused", 0, decisions)
        assertEquals(TablePhase.Playing, c.table.value.phase)
        c.setPaused(PauseReason.Background, true)
        c.setPaused(PauseReason.Menu, false)
        runCurrent()
        assertEquals("still held by the other reason", 0, decisions)
        c.setPaused(PauseReason.Background, false)
        drive(c) { decisions > 0 }
        assertTrue(decisions > 0)
        c.stop()
    }

    @Test
    fun stoppingCancelsAThinkingOpponent() = runTest {
        var cancelled = false
        val slow = agentOf { _, _ ->
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val c = controller(brains = brainsOf { _, _ -> slow })
        c.startQuick(config(opponents = 2, seed = 10L))
        drive(c) { c.table.value.seats.any { it.thinking } }
        assertTrue(c.table.value.seats.any { it.thinking })
        c.stop()
        runCurrent()
        assertTrue("leaving the table cancels the decision in flight", cancelled)
        assertFalse(c.isRunning)
    }

    @Test
    fun leavingATourTableMidHandFoldsIt() = runTest {
        val saver = RunSaver.InMemory()
        val start = RunState.new(77)
        val c = controller(saver = saver)
        c.startRun(start)
        c.beginTable()
        drive(c) { c.table.value.humanTurn != null }
        val table = c.table.value
        assertNotNull(table.humanTurn)
        assertEquals(1, table.handNumber)
        // While the hand is in play the save already holds it as folded (process death can't dodge it).
        val committed = start.progress.chips[0] - table.human!!.chips
        val during = saver.saved!!
        assertEquals(1, during.progress.handsPlayed)
        assertEquals(start.progress.chips[0] - committed, during.progress.chips[0])

        c.stop()
        val saved = saver.saved!!
        assertEquals(1, saved.progress.handsPlayed)
        assertEquals(start.progress.chips[0] - committed, saved.progress.chips[0])
        assertEquals("chips are conserved", start.progress.chips.sum(), saved.progress.chips.sum())
        assertEquals(saved, c.run.value!!.state)

        // Resuming deals the next hand, not the same one again.
        c.startRun(saved)
        c.beginTable()
        runCurrent()
        assertEquals(2, c.table.value.handNumber)
        c.stop()
    }

    @Test
    fun leavingAfterFoldingKeepsTheTableConsistent() = runTest {
        val saver = RunSaver.InMemory()
        val start = RunState.new(78)
        // Slow run-outs so we can leave while the result is still animating.
        val c = controller(saver = saver, pacing = Pacing.Instant.copy(runOutMs = 10_000, actionMs = 10_000))
        c.startRun(start)
        c.beginTable()
        // Fold at once: the hand is decided, then we leave during the pause after our action.
        drive(c) { c.table.value.humanTurn != null }
        c.act(Decision.Fold)
        runCurrent()
        c.stop()
        val saved = saver.saved!!
        assertEquals(1, saved.progress.handsPlayed)
        assertEquals(start.progress.chips.sum(), saved.progress.chips.sum())
    }

    // ---------------------------------------------------------------- quick game newcomers

    @Test
    fun bustedOpponentsAreReplacedByNewcomers() = runTest {
        val shover = agentOf { ctx, _ -> AgentReply(if (ctx.legal.discard != null) Decision.Discard(emptySet()) else Decision.AllIn) }
        val folder = agentOf { ctx, _ -> AgentReply(if (ctx.legal.canCheck) Decision.Check else Decision.Fold) }
        val c = controller(brains = brainsOf { seat, _ -> if (seat == 1) shover else folder })
        val cfg = config(opponents = 2, seed = 13L)
        c.startQuick(cfg)
        val firstName = cfg.opponents[0].name
        var left = false
        drive(c, steps = 5000) {
            val log = c.table.value.log
            if (log.any { it == "$firstName has left the table" }) left = true
            left && log.any { it.endsWith("sits down") }
        }
        assertTrue("the busted opponent left", left)
        val seat1 = c.table.value.seats[1]
        assertNotEquals(firstName, seat1.name)
        assertEquals(seat1.name, seat1.profile!!.name)
        assertTrue("the newcomer has chips", seat1.chips > 0)
        assertTrue(c.isRunning)
        c.stop()
    }
}

class SecondPersonTest {
    @org.junit.Test
    fun youGetsSecondPersonVerbs() {
        org.junit.Assert.assertEquals("You call 105", secondPerson("You calls 105"))
        org.junit.Assert.assertEquals("You raise to 300 and are all-in", secondPerson("You raises to 300 and is all-in"))
        org.junit.Assert.assertEquals("Mo calls 105", secondPerson("Mo calls 105"))
        org.junit.Assert.assertEquals("Youngblood calls 5", secondPerson("Youngblood calls 5"))
    }
}
