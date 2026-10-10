package com.bradflaugher.pokersi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HandEvaluatorTest {
    private fun eval(s: String, short: Boolean = false) = HandEvaluator.evaluate(Card.parseList(s), short)

    private fun assertBeats(winner: String, loser: String, short: Boolean = false) {
        val w = eval(winner, short)
        val l = eval(loser, short)
        assertTrue("$winner (${w.description}) should beat $loser (${l.description})", w > l)
    }

    @Test
    fun categoriesAndDescriptions() {
        assertEquals("Royal Flush", eval("As Ks Qs Js Ts 2d 3c").description)
        assertEquals(HandCategory.ROYAL_FLUSH, eval("As Ks Qs Js Ts").category)
        assertEquals("Straight Flush, Nine high", eval("9h 8h 7h 6h 5h Ah Kh").description)
        assertEquals("Four of a Kind, Jacks", eval("Jc Jd Jh Js 2c").description)
        assertEquals("Full House, Queens full of Fours", eval("Qc Qd Qh 4s 4c 9d 2h").description)
        assertEquals("Flush, Ace high", eval("Ad 9d 7d 4d 2d Kc Qh").description)
        assertEquals("Straight, Nine high", eval("5c 6d 7h 8s 9c 2d 2h").description)
        assertEquals("Three of a Kind, Sevens", eval("7c 7d 7h Ks 2c").description)
        assertEquals("Two Pair, Kings and Sevens", eval("Kc Kd 7h 7s 2c 3d 9h").description)
        assertEquals("Pair of Nines", eval("9c 9d Ah Ks 2c").description)
        assertEquals("Ace high", eval("Ac 9d 7h 4s 2c").description)
    }

    @Test
    fun wheelAndSteelWheel() {
        val wheel = eval("Ac 2d 3h 4s 5c Kd Qh")
        assertEquals(HandCategory.STRAIGHT, wheel.category)
        assertEquals("Straight, Five high", wheel.description)
        assertEquals(Card.parseList("5c 4s 3h 2d Ac"), wheel.cards)
        assertBeats("2c 3d 4h 5s 6c", "Ac 2d 3h 4s 5c")
        val steel = eval("Ah 2h 3h 4h 5h")
        assertEquals(HandCategory.STRAIGHT_FLUSH, steel.category)
        assertEquals("Straight Flush, Five high", steel.description)
        assertBeats("Ah 2h 3h 4h 5h", "Kc Kd Kh Ks Ac")
        // No wraparound straight.
        assertEquals(HandCategory.HIGH_CARD, eval("Qc Kd Ah 2s 3c").category)
    }

    @Test
    fun bestFiveOfSevenPrefersHigherStraightAndFlushOverStraight() {
        assertEquals("Straight, Ten high", eval("6c 7d 8h 9s Tc 5d 4h").description)
        assertEquals(HandCategory.FLUSH, eval("6c 7d 8h 9s Tc 2c 3c 4c").category)
        assertEquals("Full House, Aces full of Kings", eval("Ac Ad Ah Kc Kd Ks 2c").description)
    }

    @Test
    fun kickersDecide() {
        assertBeats("Ac Ad Kh 7s 2c", "As Ah Qh 7d 2d")
        assertBeats("Ac Ad Kh 8s 2c", "As Ah Kd 7d 2d")
        assertBeats("Kc Kd 7h 7s Ac", "Ks Kh 7d 7c Qc")
        assertBeats("Ac Kc 9c 7c 3c", "Ad Kd 9d 7d 2d")
        val a = eval("Ac Ad Kh 7s 2c 3d 4h")
        val b = eval("As Ah Kd 7d 2d 3c 4s")
        assertEquals(0, a.compareTo(b))
        // The board plays: both have the same best five.
        val board = "Ac Kd Qh Js Tc"
        assertEquals(0, eval("$board 2c 3d").compareTo(eval("$board 4h 5s")))
    }

    @Test
    fun omahaMustUseExactlyTwo() {
        // Four hearts on the board, one heart in hand: no flush in Omaha.
        val hole = Card.parseList("Ah Kc Kd 2s")
        val board = Card.parseList("Qh 9h 5h 3h 7c")
        val omaha = HandEvaluator.evaluateOmaha(hole, board)
        assertEquals(HandCategory.PAIR, omaha.category)
        assertEquals("Pair of Kings", omaha.description)
        assertEquals(HandCategory.FLUSH, HandEvaluator.evaluate(hole + board).category)
        // Quads on the board play as a full house: only three board cards count.
        val boardQuads = HandEvaluator.evaluateOmaha(Card.parseList("Ac Ad 2c 3c"), Card.parseList("7c 7d 7h 7s Kd"))
        assertEquals("Full House, Sevens full of Aces", boardQuads.description)
        // Two from hand, three from board makes a straight.
        val straight = HandEvaluator.evaluate(Card.parseList("8c 9d 2h 2s"), Card.parseList("Th Jc Qd 3c 4s"), GameVariant.OMAHA)
        assertEquals("Straight, Queen high", straight.description)
        assertEquals(5, straight.cards.size)
        assertEquals(2, straight.cards.count { it in Card.parseList("8c 9d 2h 2s") })
    }

    @Test
    fun shortDeckFlushBeatsFullHouseAndSixHighWheel() {
        val flush = "Ah 9h 7h 6h Th"
        val boat = "Kc Kd Kh 6s 6c"
        assertBeats(flush, boat, short = true)
        assertBeats(boat, flush, short = false)
        val wheel = eval("Ac 6d 7h 8s 9c", short = true)
        assertEquals(HandCategory.STRAIGHT, wheel.category)
        assertEquals("Straight, Nine high", wheel.description)
        assertBeats("6c 7d 8h 9s Tc", "Ac 6d 7h 8s 9c", short = true)
        assertBeats("Ac 6d 7h 8s 9c", "Ac Ad Ah Ks Qc", short = true)
        // Seven cards: a flush and a full house both present, short deck takes the flush.
        val both = eval("Kh Kd Ks 9h 9c 7h 6h", short = true)
        assertEquals(HandCategory.FULL_HOUSE, both.category)
        val both2 = eval("Kh Kd Ks 9h 9c 7h 6h Th", short = true)
        assertEquals(HandCategory.FLUSH, both2.category)
    }

    @Test
    fun partialDescriptions() {
        val h = GameVariant.TEXAS_HOLDEM
        assertEquals("Pocket Kings", HandEvaluator.describe(Card.parseList("Kc Kd"), emptyList(), h))
        assertEquals("Ace-King suited", HandEvaluator.describe(Card.parseList("Kc Ac"), emptyList(), h))
        assertEquals("Seven-Two offsuit", HandEvaluator.describe(Card.parseList("2c 7d"), emptyList(), h))
        assertEquals("Pair of Nines", HandEvaluator.describe(Card.parseList("9c 9d Ah"), emptyList(), GameVariant.SEVEN_CARD_STUD, moreCardsToCome = false))
        assertEquals("Ace high, three to a flush", HandEvaluator.describe(Card.parseList("Ac 9c 4c"), emptyList(), GameVariant.SEVEN_CARD_STUD))
        assertEquals(
            "Pair of Sevens, flush draw",
            HandEvaluator.describe(Card.parseList("7h 7c"), Card.parseList("Ah 2h 9h"), h),
        )
        assertEquals(
            "Queen high, open-ended straight draw",
            HandEvaluator.describe(Card.parseList("9c Td"), Card.parseList("Jh Qd 2s"), h),
        )
    }

    @Test
    fun evaluatorIsFast() {
        val rng = Random(7)
        val deck = Deck.fullDeck()
        val hands = List(20_000) { deck.shuffled(rng).take(7) }
        hands.forEach { HandEvaluator.evaluate(it) } // warm up
        val codes = hands.map { h -> IntArray(7) { h[it].code } }
        val start = System.nanoTime()
        var sink = 0
        repeat(10) { for (c in codes) sink += HandEvaluator.score(c, 7, false) }
        val perEval = (System.nanoTime() - start) / 200_000.0
        assertTrue("sink", sink != 42)
        assertTrue("7-card eval took ${perEval}ns", perEval < 5_000)

        val t0 = System.nanoTime()
        Equity.estimate(Card.parseList("Ah Kh"), emptyList(), 5, GameVariant.TEXAS_HOLDEM, 300, Random(1))
        Equity.estimate(Card.parseList("Ah Kh Qd Jd"), emptyList(), 5, GameVariant.OMAHA, 300, Random(1))
        val warmMs = (System.nanoTime() - t0) / 1e6
        val t1 = System.nanoTime()
        Equity.estimate(Card.parseList("Ah Kh"), emptyList(), 5, GameVariant.TEXAS_HOLDEM, 300, Random(2))
        val holdemMs = (System.nanoTime() - t1) / 1e6
        assertTrue("equity took ${holdemMs}ms (cold $warmMs)", holdemMs < 50)
    }

    @Test
    fun equityIsSensible() {
        val aces = Equity.estimate(Card.parseList("Ac Ad"), emptyList(), 1, GameVariant.TEXAS_HOLDEM, 3000, Random(3))
        assertTrue("AA vs random $aces", aces in 0.80..0.89)
        val nuts = Equity.estimate(Card.parseList("As Ks"), Card.parseList("Qs Js Ts 2c 3d"), 3, GameVariant.TEXAS_HOLDEM, 500, Random(3))
        assertEquals(1.0, nuts, 1e-9)
        val split = Equity.estimate(Card.parseList("2c 3d"), Card.parseList("As Ks Qs Js Ts"), 1, GameVariant.TEXAS_HOLDEM, 500, Random(3))
        assertEquals(0.5, split, 1e-9)
    }
}
