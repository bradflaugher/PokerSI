package com.bradflaugher.pokersi.run

import com.bradflaugher.pokersi.engine.ActionKind
import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.Card
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RunStateTest {
    private fun outcome(
        chipsWon: Int = 0,
        net: Int = 0,
        showdown: Boolean = true,
        category: HandCategory? = null,
        five: String = "",
        wentAllIn: Boolean = false,
        wasBigBlind: Boolean = false,
        busted: Int = 0,
    ) = HandOutcome(
        chipsWon = chipsWon, net = net, showdown = showdown, category = category,
        bestFive = if (five.isEmpty()) emptyList() else Card.parseList(five),
        potSize = chipsWon, bigBlind = 20, wasBigBlind = wasBigBlind, wentAllIn = wentAllIn, opponentsBusted = busted,
    )

    private fun runWith(vararg jokers: String): RunState = RunState.new(1).copy(jokers = jokers.toList())

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
        assertEquals("You", first.seatNames[0])
    }

    @Test
    fun serializationRoundTrip() {
        val start = runWith("flush_fund", "xray_specs")
        val state = start.applyHandResult(outcome(chipsWon = 100, net = 50), start.progress.chips).state
        val json = state.toJson()
        assertEquals(state, RunState.fromJson(json))
    }

    @Test
    fun jokersPayAndDoubleDown() {
        val state = runWith("flush_fund", "lucky_sevens", "rake_back", "double_down")
        val stacks = state.progress.chips.toMutableList().also { it[0] += 400 }
        val scoring = state.applyHandResult(
            outcome(chipsWon = 600, net = 400, category = HandCategory.FLUSH, five = "Ah 7h 9h 4h 2h", wentAllIn = true),
            stacks,
        )
        val p = scoring.payout
        // Flush Fund 6 + Lucky Sevens 7 = 13, doubled = 26; Rake Back 10% of 600 = 60 chips.
        assertEquals(26, p.coins)
        assertEquals(60, p.chips)
        assertEquals(listOf("flush_fund", "lucky_sevens", "rake_back", "double_down"), p.triggers.map { it.jokerId })
        assertEquals(state.coins + 26, scoring.state.coins)
        assertEquals(stacks[0] + 60, scoring.state.progress.chips[0])
        assertEquals(1, scoring.state.progress.handsPlayed)
    }

    @Test
    fun insuranceRefundsLostAllIn() {
        val state = runWith("insurance")
        val stacks = state.progress.chips.toMutableList().also { it[0] = 0 }
        val scoring = state.applyHandResult(outcome(net = -state.progress.chips[0], wentAllIn = true), stacks)
        assertEquals(state.progress.chips[0] / 4, scoring.payout.chips)
        // The refund keeps the player alive.
        assertEquals(TableStatus.IN_PROGRESS, scoring.status)
        assertEquals(RunPhase.TABLE, scoring.state.phase)
    }

    @Test
    fun bustingOutEndsTheRun() {
        val state = RunState.new(3)
        val stacks = state.progress.chips.toMutableList().also { it[0] = 0 }
        val scoring = state.applyHandResult(outcome(net = -1000), stacks)
        assertEquals(TableStatus.FAILED, scoring.status)
        assertEquals(RunPhase.GAME_OVER, scoring.state.phase)
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
    fun clearingOpensTheShopAndClimbsTheLadder() {
        var state = RunState.new(5).copy(coins = 20)
        val target = state.table.chipTarget
        val stacks = state.progress.chips.toMutableList().also { it[0] = target }
        val scoring = state.applyHandResult(outcome(chipsWon = 500, net = 500), stacks)
        assertEquals(TableStatus.CLEARED, scoring.status)
        state = scoring.state
        assertEquals(RunPhase.SHOP, state.phase)
        val reward = state.lastReward!!
        assertEquals(state.table.reward, reward.base)
        assertEquals(4, reward.interest) // 20 coins -> 4 interest
        assertEquals(20 + reward.total, state.coins)
        assertEquals(3, state.shop.size)
        assertEquals(3, state.shop.toSet().size)

        val pick = state.shop.first()
        val joker = Jokers.byId(pick)!!
        val bought = state.buy(pick)!!
        assertEquals(state.coins - joker.price, bought.coins)
        assertTrue(pick in bought.jokers)
        assertFalse(pick in bought.shop)
        assertNull(bought.buy(pick))
        val sold = bought.sell(pick)!!
        assertEquals(bought.coins + joker.price / 2, sold.coins)

        val rerolled = state.reroll()!!
        assertEquals(state.coins - 2, rerolled.coins)
        assertEquals(3, rerolled.rerollCost)
        assertNotEquals(state.shop, rerolled.shop)

        var next = bought.leaveShop()
        assertEquals(RunPhase.TABLE, next.phase)
        assertEquals(1, next.ante)
        assertEquals(1, next.stage)
        assertEquals(0, next.progress.handsPlayed)
        assertEquals(next.table.startingChips, next.progress.chips)
        // Clear the big table and the boss: on to ante 2.
        repeat(2) {
            val s = next.progress.chips.toMutableList().also { it[0] = next.table.chipTarget }
            next = next.applyHandResult(outcome(), s).state.leaveShop()
        }
        assertEquals(2, next.ante)
        assertEquals(0, next.stage)
        assertEquals(3, next.stats.tablesCleared)
    }

    @Test
    fun shopRespectsMoneyAndSlots() {
        val state = RunState.new(6).copy(phase = RunPhase.SHOP, shop = listOf("high_roller"), coins = 3)
        assertNull(state.buy("high_roller"))
        val full = state.copy(coins = 100, jokers = List(RunState.MAX_JOKERS) { Jokers.ALL[it].id })
        assertNull(full.buy("high_roller"))
        val coupons = state.copy(coins = 100, jokers = listOf("coupon_book"))
        assertEquals(6, coupons.price(Jokers.byId("high_roller")!!)) // 9 * 75%
    }

    @Test
    fun tablesScaleAndStayWinnable() {
        for (seed in 1L..30L) for (ante in 1..12) for (stage in 0..2) {
            val t = TableSpec.generate(seed, ante, stage)
            assertTrue(t.opponents.size in 1..5)
            assertTrue("target reachable", t.chipTarget <= t.startingChips.sum())
            assertTrue(t.chipTarget > t.playerChips)
            assertTrue(t.handLimit > 0)
            assertEquals(stage == 2, t.isBoss)
            assertTrue(t.bigBlind >= 2 * t.smallBlind - 1)
        }
        assertTrue(TableSpec.generate(1, 8, 0).bigBlind > TableSpec.generate(1, 1, 0).bigBlind)
        assertTrue(TableSpec.generate(1, 10, 0).opponents[0].chips > TableSpec.generate(1, 8, 0).opponents[0].chips)
        // Deep Pockets and Overtime shape the table.
        val plain = TableSpec.generate(9, 2, 1)
        val perked = TableSpec.generate(9, 2, 1, listOf("deep_pockets", "overtime"))
        assertEquals(plain.handLimit + 5, perked.handLimit)
        assertTrue(perked.playerChips > plain.playerChips)
    }

    @Test
    fun catalogsAreWellFormed() {
        assertTrue(Jokers.ALL.size in 15..25)
        assertEquals(Jokers.ALL.size, Jokers.ALL.map { it.id }.toSet().size)
        assertTrue(Bosses.ALL.size in 6..10)
        assertEquals(Bosses.ALL.size, Bosses.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun killjoyDisablesJokers() {
        val killjoy = Bosses.byId("the_killjoy")!!
        val state = runWith("flush_fund").let { it.copy(table = it.table.copy(bossId = killjoy.id)) }
        assertTrue(state.activeJokers.isEmpty())
        assertEquals(0, state.applyHandResult(outcome(chipsWon = 100, category = HandCategory.FLUSH), state.progress.chips).payout.coins)
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
        val t = TableSpec.generate(1, 1, 2).copy(bossId = "the_shapeshifter")
        assertEquals(GameVariant.entries.size, (1..GameVariant.entries.size).map(t::variantForHand).toSet().size)
    }

    @Test
    fun simulatedRunWithHouseBots() = runTest {
        var state = RunState.new(2026)
        var tables = 0
        while (state.phase != RunPhase.GAME_OVER && tables < 6) {
            val match = state.newMatch()
            val bots = listOf(HouseBot(BotStyle.SHARK, seed = 1, iterations = 40)) +
                state.table.opponents.mapIndexed { i, o -> HouseBot(o.botStyle, seed = i + 2L, iterations = 40) }
            while (state.phase == RunPhase.TABLE) {
                val hand = state.startNextHand(match)
                if (state.canMulligan && hand.redealHoleCards(0)) state = state.useMulligan()
                assertNotNull(Perks.xRayPeek(hand))
                hand.playOut(bots)
                match.settle()
                val scoring = state.applyHandResult(HandOutcome.from(hand), match.chips, hand.button)
                state = scoring.state
                scoring.state.progress.chips.forEachIndexed { seat, c -> match.adjustChips(seat, c - match.chips[seat]) }
                assertEquals(state.progress.chips, match.chips)
                // Save and resume mid-table.
                state = RunState.fromJson(state.toJson())
            }
            if (state.phase == RunPhase.SHOP) {
                state = (state.shop.firstNotNullOfOrNull { state.buy(it) } ?: state).leaveShop()
                tables++
            }
        }
        assertTrue(state.stats.handsPlayed > 0)
    }
}
