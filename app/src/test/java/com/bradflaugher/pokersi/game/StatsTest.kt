package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.HandEvaluator
import com.bradflaugher.pokersi.run.RunState
import com.bradflaugher.pokersi.run.RunStats
import com.bradflaugher.pokersi.ui.screens.ScreenshotScenes
import com.bradflaugher.pokersi.ui.screens.ScreenshotScenes.script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsTest {
    private fun best(cards: String, variant: GameVariant = GameVariant.TEXAS_HOLDEM) =
        BestHand.of(HandEvaluator.evaluate(Card.parseList(cards)), variant)

    private fun record(won: Boolean, pot: Int = 100, variant: GameVariant = GameVariant.TEXAS_HOLDEM, best: BestHand? = null) =
        HandRecord(variant, won, pot, best)

    @Test
    fun emptyStatsHaveNoRates() {
        val s = LifetimeStats()
        assertNull(s.winRate)
        assertNull(s.favoriteGame)
        assertNull(s.bestHand)
    }

    @Test
    fun handsCountWinsPotsAndGames() {
        val s = LifetimeStats()
            .withHand(record(won = true, pot = 300))
            .withHand(record(won = false, pot = 900))
            .withHand(record(won = true, pot = 120, variant = GameVariant.OMAHA))
        assertEquals(3, s.handsPlayed)
        assertEquals(2, s.handsWon)
        assertEquals(67, s.winRate)
        assertEquals("a lost pot is not a biggest pot won", 300, s.biggestPot)
        assertEquals(GameVariant.TEXAS_HOLDEM, s.favoriteGame)
    }

    @Test
    fun favoriteGameTiesGoToTheFirstGame() {
        val s = LifetimeStats(handsByGame = mapOf(GameVariant.SHORT_DECK to 4, GameVariant.OMAHA to 4))
        assertEquals(GameVariant.OMAHA, s.favoriteGame)
    }

    @Test
    fun bestHandOnlyImproves() {
        val trips = best("7s 7h 7d Kc 2s")
        val flush = best("2h 9h Jh 4h Kh")
        val betterTrips = best("9s 9h 9d Kc 2s")
        var s = LifetimeStats().withHand(record(false, best = trips))
        assertEquals(HandCategory.THREE_OF_A_KIND, s.bestHand?.category)
        s = s.withHand(record(true, best = flush)).withHand(record(true, best = betterTrips)).withHand(record(true))
        assertEquals(HandCategory.FLUSH, s.bestHand?.category)
        assertEquals(5, s.bestHand!!.cardList.size)
        assertTrue(betterTrips.beats(trips))
        assertFalse(trips.beats(betterTrips))
    }

    @Test
    fun toursAndDailies() {
        val day = LocalDate.of(2026, 10, 10)
        val daily = RunState.daily(day).let { it.copy(stats = it.stats.copy(tablesCleared = 3)) }
        val plain = RunState.new(5L).copy(stats = RunStats(tablesCleared = 5))
        val s = LifetimeStats().withTourStarted().withTourStarted().withRun(daily).withRun(plain).withRun(daily)
        assertEquals(2, s.toursStarted)
        assertEquals(5, s.bestTour)
        assertEquals(3, s.dailyBest)
    }

    @Test
    fun jsonRoundTripsAndBadJsonIsNull() {
        val s = LifetimeStats(
            handsPlayed = 9,
            handsWon = 4,
            bestHand = best("As Ks Qs Js Ts"),
            handsByGame = mapOf(GameVariant.FIVE_CARD_DRAW to 9),
        )
        assertEquals(s, LifetimeStats.fromJson(s.toJson()))
        assertNull(LifetimeStats.fromJson("{not json"))
    }

    @Test
    fun recordFromAShowdownHand() {
        val hand = ScreenshotScenes.stackedHand(
            GameVariant.TEXAS_HOLDEM, listOf("You", "Bob"), listOf(1000, 1000), button = 1,
            holes = mapOf(0 to "7s Ks", 1 to "Ad Kc"), board = "7h 7c Kd 2s 9h",
        )
        while (!hand.isFinished) {
            val seat = hand.toAct!!
            hand.apply(seat, if (hand.legalActions(seat).canCheck) Decision.Check else Decision.Call)
        }
        val r = HandRecord.from(hand)!!
        assertTrue(r.won)
        assertEquals(HandCategory.FULL_HOUSE, r.best?.category)
        assertEquals(40, r.potSize)
        assertEquals("K7s", r.startingHand)
        assertFalse("checking the big blind isn't choosing to play", r.playedPreflop)
    }

    @Test
    fun callingBeforeTheFlopCountsAsPlayingTheHand() {
        val hand = ScreenshotScenes.stackedHand(
            GameVariant.TEXAS_HOLDEM, listOf("You", "Bob"), listOf(1000, 1000), button = 0,
            holes = mapOf(0 to "Qh Jd", 1 to "Ad Kc"), board = "2c 5d 9h Ts 3s",
        )
        while (!hand.isFinished) {
            val seat = hand.toAct!!
            hand.apply(seat, if (hand.legalActions(seat).canCheck) Decision.Check else Decision.Call)
        }
        val r = HandRecord.from(hand)!!
        assertEquals("QJo", r.startingHand)
        assertTrue(r.playedPreflop)
    }

    @Test
    fun onlyTexasHoldemFillsTheChart() {
        val hand = ScreenshotScenes.stackedHand(
            GameVariant.OMAHA, listOf("You", "Bob"), listOf(1000, 1000), button = 0,
            holes = mapOf(0 to "Qh Jd Ts 9c", 1 to "Ad Kc 2s 3h"),
        ).script(Decision.Fold to null)
        assertNull(HandRecord.from(hand)!!.startingHand)
    }

    @Test
    fun theChartTalliesDealtAndPlayed() {
        val s = LifetimeStats()
            .withHand(HandRecord(GameVariant.TEXAS_HOLDEM, true, 100, null, "AKs", playedPreflop = true))
            .withHand(HandRecord(GameVariant.TEXAS_HOLDEM, false, 100, null, "AKs", playedPreflop = false))
            .withHand(HandRecord(GameVariant.TEXAS_HOLDEM, false, 100, null, "72o", playedPreflop = false))
            .withHand(record(won = true, variant = GameVariant.OMAHA))
        assertEquals(HandTally(2, 1), s.holdemHands["AKs"])
        assertEquals(50, s.holdemHands["AKs"]!!.playRate)
        assertEquals(HandTally(1, 0), s.holdemHands["72o"])
        assertEquals(2, s.holdemHands.size)
        // And it survives a save.
        assertEquals(s, LifetimeStats.fromJson(s.toJson()))
        // Older saves (no chart yet) still load.
        assertEquals(emptyMap<String, HandTally>(), LifetimeStats.fromJson("""{"handsPlayed": 3}""")!!.holdemHands)
    }

    @Test
    fun foldedHandsHaveNoBestHand() {
        val hand = ScreenshotScenes.stackedHand(
            GameVariant.TEXAS_HOLDEM, listOf("You", "Bob"), listOf(1000, 1000), button = 0,
            holes = mapOf(0 to "7s 2d", 1 to "Ad Kc"),
        ).script(Decision.Fold to null)
        val r = HandRecord.from(hand)!!
        assertFalse(r.won)
        assertNull(r.best)
    }
}

class ShortDeckBestHandTest {
    @org.junit.Test
    fun shortDeckFlushBeatsShortDeckFullHouse() {
        val v = com.bradflaugher.pokersi.engine.GameVariant.SHORT_DECK
        val fullHouse = com.bradflaugher.pokersi.engine.HandEvaluator.evaluate(
            com.bradflaugher.pokersi.engine.Card.parseList("Kc Kd Kh 7s 7c"), shortDeck = true,
        )
        val flush = com.bradflaugher.pokersi.engine.HandEvaluator.evaluate(
            com.bradflaugher.pokersi.engine.Card.parseList("Ah Jh 9h 8h 6h"), shortDeck = true,
        )
        org.junit.Assert.assertTrue(BestHand.of(flush, v).beats(BestHand.of(fullHouse, v)))
        org.junit.Assert.assertFalse(BestHand.of(fullHouse, v).beats(BestHand.of(flush, v)))
    }
}
