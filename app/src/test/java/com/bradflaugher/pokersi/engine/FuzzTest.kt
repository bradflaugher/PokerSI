package com.bradflaugher.pokersi.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Thousands of seeded hands with random (often nonsense) decisions: chips are conserved and every hand ends. */
class FuzzTest {
    private fun randomDecision(rng: Random, legal: LegalActions): Decision = when (rng.nextInt(10)) {
        0 -> Decision.Fold
        1 -> Decision.Check
        2, 3 -> Decision.Call
        4 -> Decision.AllIn
        5 -> Decision.RaiseTo(rng.nextInt(-10, 2000))
        6 -> Decision.RaiseTo(legal.minRaiseTo ?: 0)
        7 -> Decision.Discard(List(rng.nextInt(0, 6)) { rng.nextInt(-1, 7) }.toSet())
        else -> if (legal.discard != null) Decision.Discard(setOf(rng.nextInt(0, 3))) else if (legal.canCheck) Decision.Check else Decision.Call
    }

    @Test
    fun randomAgentsConserveChipsAndTerminate() {
        val rng = Random(20261010)
        var hands = 0
        for (variant in GameVariant.entries) {
            repeat(800) { round ->
                val n = rng.nextInt(2, 7)
                val chips = List(n) { if (rng.nextInt(8) == 0) 0 else rng.nextInt(1, 400) }.toMutableList()
                if (chips.count { it > 0 } < 2) { chips[0] = 100; chips[1] = 3 }
                val bb = listOf(2, 10, 20, 50).random(rng)
                val stakes = Stakes.forVariant(variant, bb / 2, bb, ante = if (rng.nextBoolean()) 0 else bb / 5)
                val hand = Hand(
                    variant, chips.mapIndexed { i, c -> SeatSetup("S$i", c) },
                    button = rng.nextInt(n), stakes = stakes, seed = rng.nextLong(), handNumber = round + 1,
                )
                val total = chips.sum()
                var steps = 0
                while (true) {
                    val seat = hand.toAct ?: break
                    val ctx = hand.context(seat)
                    assertEquals(seat, ctx.seat)
                    // An agent never sees another seat's face-down cards.
                    for (v in ctx.seats) if (v.seat != seat) assertTrue(v.cards.none { c -> c != null && c in hand.holeCards(v.seat) && !isUpCard(hand, v.seat, c) })
                    hand.apply(seat, randomDecision(rng, ctx.legal), if (rng.nextInt(20) == 0) "hi" else null)
                    if (!hand.isFinished) {
                        assertEquals("chips in play during $variant", total, hand.snapshot().seats.sumOf { it.chips + it.committed })
                    }
                    assertTrue("hand runs away", ++steps < 500)
                }
                val result = hand.result
                assertNotNull(result)
                assertEquals("$variant conserves chips", total, result!!.finalChips.sum())
                assertTrue(result.finalChips.all { it >= 0 })
                assertEquals(result.pots.sumOf { it.amount }, result.pots.sumOf { it.shares.values.sum() })
                hands++
            }
        }
        assertTrue(hands >= 4800)
    }

    private fun isUpCard(hand: Hand, seat: Int, card: Card): Boolean =
        hand.snapshot().seats[seat].faceUp[hand.holeCards(seat).indexOf(card)]

    @Test
    fun houseBotMatchesRunToTheEnd() = runTest {
        for ((k, variant) in GameVariant.entries.withIndex()) {
            val n = 2 + k % 5
            val match = Match(
                names = List(n) { "Bot$it" },
                startingChips = List(n) { 300 },
                variant = variant,
                schedule = BlindSchedule(BlindLevel(5, 10), handsPerLevel = 5, growth = 2.0),
                seed = 99L + k,
            )
            val bots = List(n) { HouseBot(BotStyle.ALL[(it + k) % BotStyle.ALL.size], seed = it.toLong(), iterations = 40) }
            var hands = 0
            while (!match.isOver && hands < 200) {
                val hand = match.startNextHand()
                hand.playOut(bots)
                match.settle()
                assertEquals(300 * n, match.chips.sum())
                hands++
            }
            assertTrue("$variant match ended (after $hands hands)", match.isOver)
        }
    }
}
