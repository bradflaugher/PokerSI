package com.bradflaugher.pokersi.run

import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.HouseBot
import com.bradflaugher.pokersi.engine.playOut
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RunStateTest {
    private fun outcome(chipsWon: Int = 0, net: Int = 0, showdown: Boolean = true, category: HandCategory? = null) =
        HandOutcome(chipsWon = chipsWon, net = net, showdown = showdown, category = category, potSize = chipsWon)

    /** Clears the current table in one hand by handing the player the target. */
    private fun RunState.clear(): RunState {
        val stacks = progress.chips.toMutableList().also { it[0] = table.chipTarget }
        val scoring = applyHandResult(outcome(chipsWon = 100, net = 100), stacks)
        assertEquals(TableStatus.CLEARED, scoring.status)
        return scoring.state
    }

    @Test
    fun seededAndDaily() {
        assertEquals(RunState.new(42), RunState.new(42))
        assertNotEquals(RunState.new(42).table, RunState.new(43).table)
        val day = LocalDate.of(2026, 10, 10)
        assertEquals(RunState.dailySeed(day), RunState.dailySeed(LocalDate.of(2026, 10, 10)))
        assertNotEquals(RunState.dailySeed(day), RunState.dailySeed(day.plusDays(1)))
        assertTrue(RunState.daily(day).daily)
        val first = RunState.new(7).table
        assertEquals(GameVariant.TEXAS_HOLDEM, first.variant)
        assertEquals(Risk.LOW, first.risk)
        assertEquals(RunState.STARTING_CHIPS, first.playerChips)
        assertEquals("You", first.seatNames[0])
    }

    @Test
    fun serializationRoundTrip() {
        val start = RunState.new(1)
        val state = start.applyHandResult(outcome(chipsWon = 100, net = 50), start.progress.chips).state
        assertEquals(state, RunState.fromJson(state.toJson()))
        val choosing = start.clear()
        assertEquals(choosing, RunState.fromJson(choosing.toJson()))
    }

    @Test
    fun oldJokerSavesAreRejectedNotCrashedOn() {
        // A v1 save (the Joker shop era): different fields, no version.
        val v1 = """{"seed":5,"ante":1,"stage":0,"coins":4,"jokers":["flush_fund"],"phase":"SHOP",
            "table":{"ante":1,"stage":0,"name":"Small Table","variant":"TEXAS_HOLDEM","opponents":[],"playerChips":1000,
            "smallBlind":10,"bigBlind":20,"tableAnte":0,"handsPerLevel":8,"blindGrowth":1.5,"chipTarget":1600,"handLimit":16,
            "reward":3,"seed":1},"progress":{"chips":[1000],"handsPlayed":0,"button":0,"mulliganUsed":false},"shop":["x"]}"""
        val thrown = runCatching { RunState.fromJson(v1) }.exceptionOrNull()
        assertTrue("old saves are refused: $thrown", thrown is Exception)
        // Even a well-formed state without the version is refused.
        val unversioned = RunState.new(5).copy(version = 0).toJson()
        assertTrue(runCatching { RunState.fromJson(unversioned) }.isFailure)
    }

    @Test
    fun oldBossTourSavesAreRejectedNotCrashedOn() {
        // A v2 save (the boss-table era): a boss table, a Short Stack reserve, three offers.
        val bossTable = """{"level":2,"risk":"BOSS","name":"The Wall","variant":"TEXAS_HOLDEM","opponents":[{"name":"Lucky Lou","style":"Rock","chips":1200}],
            "playerChips":1000,"smallBlind":15,"bigBlind":30,"tableAnte":0,"handsPerLevel":8,"blindGrowth":1.5,"chipTarget":1700,
            "handLimit":20,"reward":600,"bossId":"the_wall","seed":9}"""
        val v2 = """{"seed":5,"level":2,"phase":"CHOOSE","table":$bossTable,"progress":{"chips":[1000,1200]},"reserve":250,
            "bankroll":1250,"offers":[$bossTable],"version":2}"""
        val thrown = runCatching { RunState.fromJson(v2) }.exceptionOrNull()
        assertTrue("boss-era saves are refused: $thrown", thrown is Exception)
        // The same save stamped with today's version still fails: there is no BOSS risk any more.
        assertTrue(runCatching { RunState.fromJson(v2.replace("\"version\":2", "\"version\":${RunState.SAVE_VERSION}")) }.isFailure)
    }

    @Test
    fun bustingOutEndsTheRun() {
        val state = RunState.new(3)
        val stacks = state.progress.chips.toMutableList().also { it[0] = 0 }
        val scoring = state.applyHandResult(outcome(net = -1000), stacks)
        assertEquals(TableStatus.FAILED, scoring.status)
        assertEquals(RunPhase.GAME_OVER, scoring.state.phase)
        assertEquals(0, scoring.state.playerChips)
    }

    @Test
    fun runningOutOfHandsFails() {
        var state = RunState.new(4)
        repeat(state.table.handLimit - 1) {
            state = state.applyHandResult(outcome(), state.progress.chips).state
            assertEquals(RunPhase.TABLE, state.phase)
        }
        assertEquals(1, state.handsLeft)
        assertEquals(RunPhase.GAME_OVER, state.applyHandResult(outcome(), state.progress.chips).state.phase)
    }

    @Test
    fun forfeitingAHandCostsWhatWasPutIn() {
        val state = RunState.new(8)
        val stacks = state.progress.chips.toMutableList().also { it[0] -= 30; it[1] += 30 }
        val next = state.applyHandResult(HandOutcome.forfeit(30, potSize = 45), stacks).state
        assertEquals(RunState.STARTING_CHIPS - 30, next.progress.chips[0])
        assertEquals(1, next.progress.handsPlayed)
        assertEquals(0, next.stats.handsWon)
    }

    @Test
    fun clearingPaysAndOffersTablesToChooseFrom() {
        val state = RunState.new(5)
        val cleared = state.clear()
        assertEquals(RunPhase.CHOOSE, cleared.phase)
        val reward = cleared.lastReward!!
        assertEquals(state.table.reward, reward.bonus)
        assertEquals((state.table.handLimit - 1) * state.table.bigBlind, reward.handsLeftBonus)
        // Chips carry over, plus the bonus.
        assertEquals(state.table.chipTarget + reward.total, cleared.bankroll)
        assertEquals(cleared.bankroll, cleared.playerChips)
        assertEquals(1, cleared.stats.tablesCleared)

        // Two offers: low and high stakes. Bigger risk, bigger reward.
        assertEquals(listOf(Risk.LOW, Risk.HIGH), cleared.offers.map { it.risk })
        assertTrue(cleared.offers.all { it.level == 2 })
        val (low, high) = cleared.offers
        assertTrue(low.reward < high.reward)
        assertTrue(low.bigBlind <= high.bigBlind)

        assertNull(state.choose(0)) // only between tables
        assertNull(cleared.choose(2))
        val next = cleared.choose(1)!!
        assertEquals(RunPhase.TABLE, next.phase)
        assertEquals(2, next.level)
        assertEquals(cleared.offers[1], next.table)
        assertEquals(0, next.progress.handsPlayed)
        // The whole bankroll sits down: nothing is held back.
        assertEquals(cleared.bankroll, next.progress.chips[0])
        assertEquals(cleared.bankroll, next.playerChips)
        assertTrue(next.offers.isEmpty())
    }

    @Test
    fun offersArePlainTablesWithNoRuleTwists() {
        for (seed in 1L..40L) {
            var state = RunState.new(seed)
            repeat(12) {
                val cleared = state.clear()
                assertEquals(2, cleared.offers.size)
                for (offer in cleared.offers) {
                    // Every table plays by the standard rules: the player's whole bankroll,
                    // no ante, the usual blind schedule and hand limit, one game.
                    assertEquals(cleared.bankroll, offer.playerChips)
                    assertEquals(0, offer.schedule.start.ante)
                    assertEquals(8, offer.handsPerLevel)
                    assertEquals(1.5, offer.blindGrowth, 0.0)
                    assertEquals(12 + 4 * offer.opponents.size, offer.handLimit)
                    assertTrue(offer.name == "Low Stakes" || offer.name == "High Stakes")
                }
                // No trace of the old boss tables in the save either.
                assertFalse(cleared.toJson().contains("boss", ignoreCase = true))
                assertFalse(cleared.toJson().contains("reserve"))
                state = cleared.choose(it % 2)!!
            }
        }
    }

    @Test
    fun tourProgressesAndResumesFromASave() {
        var state = RunState.new(77)
        for (stop in 1..4) {
            assertEquals(stop, state.level)
            val midTable = state.applyHandResult(outcome(), state.progress.chips).state
            assertEquals(midTable, RunState.fromJson(midTable.toJson()))
            val cleared = RunState.fromJson(midTable.clear().toJson())
            assertEquals(RunPhase.CHOOSE, cleared.phase)
            state = RunState.fromJson(cleared.choose(stop % 2)!!.toJson())
            assertEquals(cleared.bankroll, state.playerChips)
        }
        assertEquals(5, state.level)
        assertEquals(4, state.stats.tablesCleared)
    }

    @Test
    fun winningTheFinalStopIsAVictoryAndTheTourGoesOn() {
        var state = RunState.new(11)
        while (state.level < TableSpec.FINAL_LEVEL) state = state.clear().choose(0)!!
        assertTrue(!state.victory)
        val won = state.clear()
        assertTrue(won.victory)
        val endless = won.choose(1)!!
        assertEquals(TableSpec.FINAL_LEVEL + 1, endless.level)
        assertTrue(endless.victory)
    }

    @Test
    fun tablesScaleAndStayWinnable() {
        for (seed in 1L..30L) for (level in 1..14) for (risk in Risk.entries) for (bankroll in listOf(100, 1000, 9000)) {
            val t = TableSpec.generate(seed, level, risk, bankroll)
            assertTrue(t.opponents.size in 1..5)
            assertTrue("target reachable", t.chipTarget <= t.startingChips.sum())
            assertTrue(t.chipTarget > t.playerChips)
            assertEquals(bankroll, t.playerChips)
            assertTrue(t.handLimit > 0)
            assertTrue(t.bigBlind >= 2 * t.smallBlind - 1)
        }
        assertTrue(TableSpec.generate(1, 8, Risk.LOW, 1000).bigBlind > TableSpec.generate(1, 1, Risk.LOW, 1000).bigBlind)
        assertTrue(TableSpec.generate(1, 12, Risk.LOW, 1000).opponents[0].chips > TableSpec.generate(1, 10, Risk.LOW, 1000).opponents[0].chips)
    }


    @Test
    fun simulatedTourWithHouseBots() = runTest {
        var state = RunState.new(2026)
        var tables = 0
        while (state.phase != RunPhase.GAME_OVER && tables < 6) {
            val match = state.newMatch()
            val bots = listOf(HouseBot(BotStyle.SHARK, seed = 1, iterations = 40)) +
                state.table.opponents.mapIndexed { i, o -> HouseBot(o.botStyle, seed = i + 2L, iterations = 40) }
            while (state.phase == RunPhase.TABLE) {
                val hand = state.startNextHand(match)
                assertEquals(state.table.variant, hand.variant)
                hand.playOut(bots)
                match.settle()
                state = state.applyHandResult(HandOutcome.from(hand), match.chips, hand.button).state
                if (state.phase == RunPhase.TABLE) assertEquals(state.progress.chips, match.chips)
                // Save and resume mid-table.
                state = RunState.fromJson(state.toJson())
            }
            if (state.phase == RunPhase.CHOOSE) {
                state = state.choose(tables % 2)!!
                tables++
            }
        }
        assertTrue(state.stats.handsPlayed > 0)
    }
}
