package com.bradflaugher.pokersi.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HouseBotTest {
    private fun discards(s: String) = HouseBot.drawDiscards(Card.parseList(s))

    @Test
    fun drawKeepsTheRightCards() {
        assertEquals(emptySet<Int>(), discards("2c 3d 4h 5s 6c")) // straight: pat
        assertEquals(emptySet<Int>(), discards("2c 9c 4c Jc Kc")) // flush: pat
        assertEquals(setOf(3, 4), discards("9c 9d 9h 2s Kc")) // trips: draw two
        assertEquals(setOf(4), discards("9c 9d Kh Ks 2c")) // two pair: draw one
        assertEquals(setOf(2, 3, 4), discards("Qc Qd 2h 7s 9c")) // pair: draw three
        assertEquals(setOf(4), discards("2h 7h 9h Kh Qs")) // four-flush: draw one
        assertEquals(setOf(0), discards("Kc 5d 6h 7s 8c")) // open-ended: draw one
        assertEquals(setOf(1, 2, 4), discards("Ac 3d 7h Ks 9c")) // nothing: keep A-K
    }

    @Test
    fun pineappleDiscardKeepsTheMadeHand() {
        val ctx = Hand(
            GameVariant.CRAZY_PINEAPPLE, listOf(SeatSetup("A", 100), SeatSetup("B", 100)), 0, Stakes(1, 2), 1,
        ).context(0).copy(
            holeCards = Card.parseList("Ah Kh 2c"),
            board = Card.parseList("Qh Jh 7d"),
            legal = LegalActions(false, false, 0, null, null, DiscardRule(1, 1)),
        )
        val reply = HouseBot(seed = 3).decideNow(ctx)
        assertEquals(Decision.Discard(setOf(2)), reply.decision)
    }

    @Test
    fun nitFoldsTrashToABigBetAndStationCalls() {
        val base = Hand(
            GameVariant.TEXAS_HOLDEM, listOf(SeatSetup("A", 1000), SeatSetup("B", 1000)), 0, Stakes(5, 10), 1,
        ).context(0)
        val ctx = base.copy(
            holeCards = Card.parseList("7c 2d"),
            board = Card.parseList("As Kd Qh"),
            pot = 100,
            legal = LegalActions(true, false, 80, 160, 990),
        )
        repeat(5) { assertEquals(Decision.Fold, HouseBot(BotStyle.NIT, seed = it.toLong()).decideNow(ctx).decision) }
        val nuts = ctx.copy(holeCards = Card.parseList("Jc Td"))
        repeat(5) { assertTrue(HouseBot(BotStyle.CALLING_STATION, seed = it.toLong()).decideNow(nuts).decision != Decision.Fold) }
    }

    @Test
    fun beatsRandomPlayers() = runTest {
        val rng = Random(5)
        val random = object : PlayerAgent {
            override suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit): AgentReply {
                val l = context.legal
                val d = when {
                    l.discard != null -> Decision.Discard(setOf(rng.nextInt(3)))
                    rng.nextInt(3) == 0 -> Decision.Fold
                    rng.nextInt(4) == 0 -> Decision.RaiseTo(l.minRaiseTo ?: 0)
                    else -> Decision.Call
                }
                return AgentReply(d)
            }
        }
        var botNet = 0
        for (variant in GameVariant.entries) {
            val bot = HouseBot(BotStyle.SHARK, seed = 1, iterations = 80)
            repeat(60) { i ->
                val hand = Hand(variant, listOf(SeatSetup("Bot", 500), SeatSetup("Rnd", 500)), i % 2, Stakes.forVariant(variant, 5, 10), seed = i.toLong())
                hand.playOut(listOf(bot, random))
                botNet += hand.result!!.net[0]
            }
        }
        assertTrue("House Bot should beat random play, net $botNet", botNet > 0)
    }
}
