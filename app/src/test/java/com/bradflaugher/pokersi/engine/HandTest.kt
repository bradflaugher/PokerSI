package com.bradflaugher.pokersi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandTest {
    private val nl = Stakes(smallBlind = 1, bigBlind = 2)

    /** A Hold'em deck: hole cards in dealing order (left of the button first), burns, then the board. */
    private fun holdemDeck(holes: List<String>, board: String): Deck {
        val hole = holes.flatMap { Card.parseList(it) }
        val b = Card.parseList(board)
        val fillers = Deck.fullDeck().filter { it !in hole && it !in b }
        return Deck.stacked(hole + fillers[0] + b.take(3) + fillers[1] + b[3] + fillers[2] + b[4])
    }

    private fun seats(vararg chips: Int) = chips.mapIndexed { i, c -> SeatSetup("P$i", c) }

    private fun Hand.act(seat: Int, d: Decision) = apply(seat, d)

    @Test
    fun headsUpButtonPostsSmallBlindActsFirstPreflopLastAfter() {
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(100, 100), button = 0, stakes = nl, seed = 1)
        assertEquals(0, hand.smallBlindSeat)
        assertEquals(1, hand.bigBlindSeat)
        assertEquals(0, hand.toAct)
        assertEquals(1, hand.context(0).legal.toCall)
        hand.act(0, Decision.Call)
        assertEquals(1, hand.toAct)
        assertTrue(hand.context(1).legal.canCheck)
        hand.act(1, Decision.Check)
        assertEquals("Flop", hand.street)
        assertEquals(1, hand.toAct)
        hand.act(1, Decision.Check)
        assertEquals(0, hand.toAct)
    }

    @Test
    fun threeHandedBlindsAndFirstToAct() {
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(100, 100, 100), button = 0, stakes = nl, seed = 1)
        assertEquals(1, hand.smallBlindSeat)
        assertEquals(2, hand.bigBlindSeat)
        assertEquals(0, hand.toAct)
        hand.act(0, Decision.Call)
        hand.act(1, Decision.Call)
        hand.act(2, Decision.Check)
        assertEquals("Flop", hand.street)
        assertEquals(1, hand.toAct)
    }

    @Test
    fun minRaiseFollowsLastFullRaise() {
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(500, 500, 500), button = 0, stakes = Stakes(5, 10), seed = 2)
        assertEquals(20, hand.context(0).legal.minRaiseTo)
        hand.act(0, Decision.RaiseTo(35)) // a raise of 25
        assertEquals(60, hand.context(1).legal.minRaiseTo)
        assertEquals(500, hand.context(1).legal.maxRaiseTo) // the small blind's whole stack
        // Asking for less than the minimum is clamped up.
        hand.act(1, Decision.RaiseTo(40))
        assertEquals(60, (hand.events.last() as HandEvent.Action).total)
    }

    @Test
    fun allInShortRaiseDoesNotReopenBetting() {
        // Seat 0 bets 100 on the flop, seat 1 shoves 150 (a short raise), seat 2 calls; seat 0 may only call.
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(1000, 160, 1000), button = 2, stakes = Stakes(5, 10), seed = 3)
        // Pre-flop: button 2 acts first (left of BB = seat 1 is seat 2).
        assertEquals(2, hand.toAct)
        hand.act(2, Decision.Call)
        hand.act(0, Decision.Call)
        hand.act(1, Decision.Check)
        assertEquals("Flop", hand.street)
        assertEquals(0, hand.toAct)
        hand.act(0, Decision.RaiseTo(100))
        assertEquals(1, hand.toAct)
        hand.act(1, Decision.AllIn)
        val shove = hand.events.last() as HandEvent.Action
        assertEquals(150, shove.total)
        assertTrue(shove.allIn)
        // Seat 2 has not acted on this round: it may re-raise.
        assertEquals(2, hand.toAct)
        assertNotNull(hand.context(2).legal.minRaiseTo)
        assertEquals(250, hand.context(2).legal.minRaiseTo) // 150 + the last full raise of 100
        hand.act(2, Decision.Call)
        assertEquals(0, hand.toAct)
        val legal = hand.context(0).legal
        assertNull("short all-in raise must not reopen", legal.minRaiseTo)
        assertEquals(50, legal.toCall)
        // A raise request becomes a call.
        hand.act(0, Decision.RaiseTo(900))
        assertEquals(ActionKind.CALL, hand.events.filterIsInstance<HandEvent.Action>().last().kind)
        assertEquals("Turn", hand.street)
    }

    @Test
    fun potLimitMaximum() {
        val stakes = Stakes(1, 2)
        val hand = Hand(GameVariant.OMAHA, seats(1000, 1000, 1000, 1000), button = 0, stakes = stakes, seed = 4)
        // UTG (seat 3): pot 3, call 2 -> max raise to 2 + 5 = 7.
        assertEquals(3, hand.toAct)
        assertEquals(7, hand.context(3).legal.maxRaiseTo)
        hand.act(3, Decision.RaiseTo(1000))
        assertEquals(7, (hand.events.last() as HandEvent.Action).total)
        // Button: pot 10, call 7 -> 17, raise to 7 + 17 = 24.
        assertEquals(24, hand.context(0).legal.maxRaiseTo)
        assertEquals(12, hand.context(0).legal.minRaiseTo)
        hand.act(0, Decision.Call)
        // Small blind: pot 17, to call 6 -> 23, raise to 7 + 23 = 30.
        assertEquals(30, hand.context(1).legal.maxRaiseTo)
    }

    @Test
    fun fixedLimitCapsAtFourBets() {
        val stakes = Stakes(smallBlind = 5, bigBlind = 10, smallBet = 10, bigBet = 20)
        val hand = Hand(GameVariant.FIVE_CARD_DRAW, seats(1000, 1000, 1000), button = 0, stakes = stakes, seed = 5)
        assertEquals(0, hand.toAct)
        var legal = hand.context(0).legal
        assertEquals(20, legal.minRaiseTo)
        assertEquals(20, legal.maxRaiseTo)
        hand.act(0, Decision.RaiseTo(999)) // 2nd bet
        assertEquals(20, (hand.events.last() as HandEvent.Action).total)
        hand.act(1, Decision.RaiseTo(30)) // 3rd bet
        hand.act(2, Decision.AllIn) // 4th bet: AllIn clamps to the fixed raise
        assertEquals(40, (hand.events.last() as HandEvent.Action).total)
        legal = hand.context(0).legal
        assertNull("capped", legal.minRaiseTo)
        assertEquals(20, legal.toCall)
        hand.act(0, Decision.Call)
        hand.act(1, Decision.Call)
        assertEquals("Draw", hand.street)
    }

    @Test
    fun studBringInIsLowestUpCardWithSuitTiebreak() {
        val stakes = Stakes.forVariant(GameVariant.SEVEN_CARD_STUD, 0, 20)
        assertEquals(2, stakes.ante)
        assertEquals(5, stakes.bringIn)
        // Deal order from left of button (button 0): seats 1, 2, 0 get two down each, then one up each.
        val down = Card.parseList("Ac Ad Kc Kd Qc Qd")
        val up = Card.parseList("3s 3c 9h") // seat 1: 3s, seat 2: 3c, seat 0: 9h
        val hand = Hand(
            GameVariant.SEVEN_CARD_STUD, seats(500, 500, 500), button = 0, stakes = stakes, seed = 6,
            deck = Deck.stacked(down + up),
        )
        assertEquals(2, hand.bringInSeat)
        assertEquals(5, hand.events.filterIsInstance<HandEvent.Forced>().single { it.kind == ForcedBet.BRING_IN }.amount)
        assertEquals(6, hand.pot - 5) // three antes of 2 plus the bring-in
        assertEquals(0, hand.toAct) // left of the bring-in
        val legal = hand.context(0).legal
        assertEquals(5, legal.toCall)
        assertEquals(20, legal.minRaiseTo) // complete to the small bet
        // Others see seat 2's up card but not its down cards.
        val view = hand.context(0).seats.single { it.seat == 2 }
        assertEquals(listOf(null, null, Card.parse("3c")), view.cards)
        hand.act(0, Decision.RaiseTo(20))
        assertTrue(hand.events.last().text!!.contains("completes to 20"))
        hand.act(1, Decision.Call)
        // The bring-in may now act again.
        assertEquals(2, hand.toAct)
        hand.act(2, Decision.Call)
        assertEquals("4th street", hand.street)
    }

    @Test
    fun drawFlowReplacesDiscards() {
        val hand = Hand(GameVariant.FIVE_CARD_DRAW, seats(200, 200), button = 0, stakes = Stakes(5, 10), seed = 7)
        hand.act(0, Decision.Call)
        hand.act(1, Decision.Check)
        assertEquals("Draw", hand.street)
        val seat = hand.toAct!!
        assertEquals(1, seat) // left of the button draws first
        assertEquals(DiscardRule(0, 3), hand.context(seat).legal.discard)
        val before = hand.holeCards(seat)
        hand.act(seat, Decision.Discard(setOf(0, 1)))
        val after = hand.holeCards(seat)
        assertEquals(5, after.size)
        assertEquals(before.drop(2), after.take(3))
        assertTrue(after.drop(3).none { it in before })
        // Asking for four is trimmed to three.
        hand.act(0, Decision.Discard(setOf(0, 1, 2, 3)))
        assertEquals(3, (hand.events.last { it is HandEvent.Discarded } as HandEvent.Discarded).count)
        assertEquals("Post-draw", hand.street)
        assertEquals(1, hand.toAct)
        hand.act(1, Decision.Check)
        hand.act(0, Decision.Check)
        assertTrue(hand.isFinished)
        assertTrue(hand.result!!.showdown)
    }

    @Test
    fun pineappleDiscardsOneAfterFlopBetting() {
        val hand = Hand(GameVariant.CRAZY_PINEAPPLE, seats(200, 200, 200), button = 0, stakes = Stakes(1, 2), seed = 8)
        assertEquals(3, hand.holeCards(0).size)
        hand.act(0, Decision.Call)
        hand.act(1, Decision.Call)
        hand.act(2, Decision.Check)
        assertEquals("Flop", hand.street)
        repeat(3) { hand.act(hand.toAct!!, Decision.Check) }
        assertEquals("Discard", hand.street)
        assertEquals(DiscardRule(1, 1), hand.context(hand.toAct!!).legal.discard)
        // A check on a discard step becomes a discard of exactly one card.
        hand.act(hand.toAct!!, Decision.Check)
        hand.act(hand.toAct!!, Decision.Discard(setOf(0, 1, 2)))
        hand.act(hand.toAct!!, Decision.Discard(emptySet()))
        assertEquals("Turn", hand.street)
        for (s in 0..2) assertEquals(2, hand.holeCards(s).size)
    }

    @Test
    fun sidePotsWithThreeAllInsOfDifferentSizes() {
        // Button 0; deal order 1, 2, 3, 0. Seat 1 (50 chips) has the best hand, then seat 2 (100), then seat 3 (200), seat 0 worst.
        val deck = holdemDeck(listOf("Ac Ad", "Kc Kd", "Qc Qd", "2h 7s"), "As Ks Qs 3h 4d")
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(400, 50, 100, 200), button = 0, stakes = Stakes(5, 10), seed = 9, deck = deck)
        assertEquals(3, hand.toAct)
        hand.act(3, Decision.AllIn)
        hand.act(0, Decision.AllIn)
        hand.act(1, Decision.AllIn)
        hand.act(2, Decision.AllIn)
        assertTrue(hand.isFinished)
        val r = hand.result!!
        // Main pot: 4 x 50 = 200 (seat 1: trips aces). Side 1: 3 x 50 = 150 (seat 2: trips kings).
        // Side 2: 2 x 100 = 200 (seat 3: trips queens). Seat 0's extra 200 is returned uncalled.
        assertEquals(listOf(200, 150, 200), r.pots.map { it.amount })
        assertEquals(listOf(1, 2, 3), r.pots.map { it.winners.single() })
        assertEquals(listOf(200, 200, 150, 200), r.finalChips)
        assertEquals(750, r.finalChips.sum())
        assertTrue(hand.events.any { it is HandEvent.UncalledReturned && it.seat == 0 && it.amount == 200 })
        // The board ran out.
        assertEquals(3, hand.events.count { it is HandEvent.StreetDealt })
        assertEquals(setOf(0, 1, 2, 3), r.handValues.keys)
    }

    @Test
    fun splitPotOddChipGoesLeftOfButton() {
        // Seats 1 and 2 (dealt in that order after the button) tie with the board straight.
        val deck = holdemDeck(listOf("2c 3d", "2d 3c", "4h 4s"), "Ts Js Qd Kh Ac")
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(100, 100, 100), button = 0, stakes = Stakes(5, 10), seed = 10, deck = deck)
        hand.act(0, Decision.Fold)
        hand.act(1, Decision.Call)
        hand.act(2, Decision.Check)
        repeat(6) { hand.act(hand.toAct!!, Decision.Check) }
        val r = hand.result!!
        assertEquals(20, r.pots.single().amount)
        assertEquals(mapOf(1 to 10, 2 to 10), r.pots.single().shares)

        // Odd pot: three antes make it 23; seat 1 (first left of the button) gets the odd chip.
        val deck2 = holdemDeck(listOf("2c 3d", "2d 3c", "4h 4s"), "Ts Js Qd Kh Ac")
        val h2 = Hand(GameVariant.TEXAS_HOLDEM, seats(100, 100, 100), button = 0, stakes = Stakes(5, 10, ante = 1), seed = 10, deck = deck2)
        h2.act(0, Decision.Fold)
        h2.act(1, Decision.Call)
        h2.act(2, Decision.Check)
        repeat(6) { h2.act(h2.toAct!!, Decision.Check) }
        val r2 = h2.result!!
        assertEquals(23, r2.pots.single().amount)
        assertEquals(mapOf(1 to 12, 2 to 11), r2.pots.single().shares)
        assertEquals(listOf(99, 101, 100), r2.finalChips)
    }

    @Test
    fun everyoneFoldsNoShowdown() {
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(100, 100, 100), button = 0, stakes = Stakes(5, 10), seed = 11)
        hand.act(0, Decision.RaiseTo(30))
        hand.act(1, Decision.Fold)
        hand.act(2, Decision.Fold)
        assertTrue(hand.isFinished)
        assertNull(hand.toAct)
        val r = hand.result!!
        assertFalse(r.showdown)
        assertTrue(hand.events.none { it is HandEvent.ShowdownReveal })
        assertEquals(listOf(115, 95, 90), r.finalChips)
        // Nobody else's cards are shown.
        val snap = hand.snapshot(viewer = 1)
        assertTrue(snap.seats[0].cards.all { it == null })
        assertTrue(snap.seats[1].cards.all { it != null })
    }

    @Test
    fun wrongSeatThrowsButBadInputIsCoerced() {
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(100, 100, 100), button = 0, stakes = Stakes(5, 10), seed = 12)
        assertTrue(runCatching { hand.apply(1, Decision.Call) }.isFailure)
        hand.apply(0, Decision.Check) // can't check facing the big blind: becomes a call
        assertEquals(ActionKind.CALL, (hand.events.last() as HandEvent.Action).kind)
        hand.apply(1, Decision.Discard(setOf(1))) // nonsense on a betting step: folds
        assertEquals(ActionKind.FOLD, (hand.events.last() as HandEvent.Action).kind)
        hand.apply(2, Decision.RaiseTo(-50), say = "  Hi there ")
        assertTrue(hand.events.any { it is HandEvent.Chat && it.line == "Hi there" })
    }

    @Test
    fun contextHidesOpponentsCards() {
        val hand = Hand(GameVariant.OMAHA, seats(100, 100, 100), button = 0, stakes = Stakes(1, 2), seed = 13)
        val ctx = hand.context(0)
        assertEquals(4, ctx.holeCards.size)
        for (s in ctx.seats) {
            if (s.seat == 0) assertTrue(s.cards.all { it != null }) else assertTrue(s.cards.all { it == null })
        }
        assertNotNull(ctx.madeHand)
        assertEquals("Pre-flop", ctx.street)
    }

    @Test
    fun mulliganRedealsBeforeActing() {
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(100, 100, 100), button = 0, stakes = Stakes(5, 10), seed = 14)
        val before = hand.holeCards(0)
        assertTrue(hand.redealHoleCards(0))
        val after = hand.holeCards(0)
        assertEquals(2, after.size)
        assertTrue(after.none { it in before })
        hand.apply(0, Decision.Call)
        assertFalse(hand.redealHoleCards(0))
    }

    @Test
    fun sittingOutSeatsAreSkipped() {
        val hand = Hand(GameVariant.TEXAS_HOLDEM, seats(100, 0, 100, 100), button = 1, stakes = Stakes(5, 10), seed = 15)
        assertEquals(2, hand.button)
        assertEquals(3, hand.smallBlindSeat)
        assertEquals(0, hand.bigBlindSeat)
        assertEquals(2, hand.toAct)
        assertTrue(hand.holeCards(1).isEmpty())
        assertTrue(hand.snapshot().seats[1].sittingOut)
    }
}
