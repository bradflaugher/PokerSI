package com.bradflaugher.pokersi.run

import com.bradflaugher.pokersi.engine.ActionKind
import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.Hand
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.HouseBot
import com.bradflaugher.pokersi.engine.LegalActions
import com.bradflaugher.pokersi.engine.SeatSetup
import com.bradflaugher.pokersi.engine.Stakes
import com.bradflaugher.pokersi.engine.playOut
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        assertNull(first.boss)
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

        // Three offers: low, high, boss. Bigger risk, bigger reward.
        assertEquals(Risk.entries, cleared.offers.map { it.risk })
        assertTrue(cleared.offers.all { it.level == 2 })
        assertTrue(cleared.offers[0].reward < cleared.offers[1].reward)
        assertTrue(cleared.offers[1].reward < cleared.offers[2].reward)
        assertTrue(cleared.offers[2].isBoss)
        assertTrue(cleared.offers.take(2).none { it.isBoss })

        assertNull(state.choose(0)) // only between tables
        assertNull(cleared.choose(3))
        val next = cleared.choose(1)!!
        assertEquals(RunPhase.TABLE, next.phase)
        assertEquals(2, next.level)
        assertEquals(cleared.offers[1], next.table)
        assertEquals(0, next.progress.handsPlayed)
        assertEquals(cleared.bankroll, next.progress.chips[0] + next.reserve)
        assertTrue(next.offers.isEmpty())
    }

    @Test
    fun shortStackKeepsHalfInReserve() {
        var spec: TableSpec
        var seed = 1L
        do {
            spec = TableSpec.generate(seed++, 3, Risk.BOSS, 2000)
        } while (spec.bossId != "short_stack")
        assertEquals(1000, spec.playerChips)
        val state = RunState.new(1).let { it.copy(phase = RunPhase.CHOOSE, offers = listOf(spec), bankroll = 2000) }.choose(0)!!
        assertEquals(1000, state.reserve)
        assertEquals(2000, state.playerChips)
        // Clearing gives the reserve back.
        val cleared = state.clear()
        assertEquals(spec.chipTarget + 1000 + cleared.lastReward!!.total, cleared.bankroll)
    }

    @Test
    fun winningTheFinalStopIsAVictoryAndTheTourGoesOn() {
        var state = RunState.new(11)
        while (state.level < TableSpec.FINAL_LEVEL) state = state.clear().choose(0)!!
        assertTrue(!state.victory)
        val won = state.clear()
        assertTrue(won.victory)
        val endless = won.choose(2)!!
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
            assertTrue(t.playerChips in 1..bankroll)
            assertTrue(t.handLimit > 0)
            assertEquals(risk == Risk.BOSS, t.isBoss)
            assertTrue(t.bigBlind >= 2 * t.smallBlind - 1)
        }
        assertTrue(TableSpec.generate(1, 8, Risk.LOW, 1000).bigBlind > TableSpec.generate(1, 1, Risk.LOW, 1000).bigBlind)
        assertTrue(TableSpec.generate(1, 12, Risk.LOW, 1000).opponents[0].chips > TableSpec.generate(1, 10, Risk.LOW, 1000).opponents[0].chips)
    }

    @Test
    fun bossCatalogIsWellFormed() {
        assertTrue(Bosses.ALL.size in 6..10)
        assertEquals(Bosses.ALL.size, Bosses.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun bossTwistsAdjustOpponentDecisions() {
        val hand = Hand(GameVariant.TEXAS_HOLDEM, listOf(SeatSetup("You", 500), SeatSetup("Wall", 500)), 0, Stakes(5, 10), 1)
        val ctx = hand.context(0).copy(seat = 1, pot = 100, legal = LegalActions(true, false, 40, 80, 500))
        val wall = Bosses.byId("the_wall")
        assertEquals(Decision.Call, BossTwists.adjustOpponentDecision(wall, ctx, Decision.Fold, null))
        val bigBet = ctx.copy(legal = ctx.legal.copy(toCall = 80))
        assertEquals(Decision.Fold, BossTwists.adjustOpponentDecision(wall, bigBet, Decision.Fold, null))
        val mirror = Bosses.byId("the_mirror")
        assertEquals(Decision.RaiseTo(80), BossTwists.adjustOpponentDecision(mirror, ctx, Decision.Fold, ActionKind.RAISE))
        assertEquals(Decision.Call, BossTwists.adjustOpponentDecision(mirror, ctx, Decision.Fold, ActionKind.CALL))
        hand.apply(0, Decision.Call)
        assertEquals(ActionKind.CALL, BossTwists.lastActionOf(hand.events, 0))
        assertEquals(Decision.Fold, BossTwists.adjustOpponentDecision(null, ctx, Decision.Fold, ActionKind.CALL))
    }

    @Test
    fun shapeshifterRotatesGames() {
        val t = TableSpec.generate(1, 1, Risk.BOSS, 1000).copy(bossId = "the_shapeshifter")
        assertEquals(GameVariant.entries.size, (1..GameVariant.entries.size).map(t::variantForHand).toSet().size)
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
                hand.playOut(bots)
                match.settle()
                state = state.applyHandResult(HandOutcome.from(hand), match.chips, hand.button).state
                if (state.phase == RunPhase.TABLE) assertEquals(state.progress.chips, match.chips)
                // Save and resume mid-table.
                state = RunState.fromJson(state.toJson())
            }
            if (state.phase == RunPhase.CHOOSE) {
                state = state.choose(tables % 3)!!
                tables++
            }
        }
        assertTrue(state.stats.handsPlayed > 0)
    }
}
