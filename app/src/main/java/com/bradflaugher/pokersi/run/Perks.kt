package com.bradflaugher.pokersi.run

import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Equity
import com.bradflaugher.pokersi.engine.Hand
import com.bradflaugher.pokersi.engine.Match
import kotlin.random.Random

/** Helpers for the Jokers the game layer applies itself (X-Ray Specs, Tell Detector). */
object Perks {
    /** A face-down card [viewer] may peek at with X-Ray Specs: (seat, card), the same for the whole hand. */
    fun xRayPeek(hand: Hand, viewer: Int = 0): Pair<Int, Card>? {
        val snapshot = hand.snapshot(viewer)
        val hidden = snapshot.seats
            .filter { it.seat != viewer && !it.folded && !it.sittingOut }
            .flatMap { s -> s.cards.indices.filter { s.cards[it] == null }.map { s.seat to it } }
        if (hidden.isEmpty()) return null
        val (seat, index) = hidden[Random(Match.mix(hand.handNumber.toLong(), hand.holeCards(viewer).hashCode())).nextInt(hidden.size)]
        return seat to hand.holeCards(seat)[index]
    }

    /**
     * How good [seat] feels about its hand, 0..1 (its equity against the live hands, from
     * what that seat can see): the Tell Detector's meter. Works for any agent.
     */
    fun confidence(hand: Hand, seat: Int, iterations: Int = 200, random: Random = Random(seat.toLong())): Double {
        val ctx = hand.context(seat)
        val opponents = ctx.seats.filter { it.seat != seat && !it.folded }
        return Equity.estimate(
            hole = ctx.holeCards,
            board = ctx.board,
            opponents = opponents.size,
            variant = ctx.variant,
            iterations = iterations,
            rng = random,
            opponentUpCards = opponents.map { o -> o.cards.filterNotNull() },
        )
    }
}
