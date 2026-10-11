package com.bradflaugher.pokersi.game

import android.content.SharedPreferences
import androidx.core.content.edit
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.Hand
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.HandEvaluator
import com.bradflaugher.pokersi.engine.HandValue
import com.bradflaugher.pokersi.engine.hasBoard
import com.bradflaugher.pokersi.engine.mustUseTwoHoleCards
import com.bradflaugher.pokersi.engine.strength
import com.bradflaugher.pokersi.engine.usesShortDeck
import com.bradflaugher.pokersi.run.RunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Told about every hand the human finishes at the table (see [GameController]). */
fun interface HandRecorder {
    fun handFinished(hand: Hand)
}

/** The best hand the player has ever held to the end of a hand. */
@Serializable
data class BestHand(
    val category: HandCategory,
    /** [HandValue.score], to break ties within a category. */
    val score: Int,
    /** The five cards, "As Ks Qs Js Ts". */
    val cards: String,
    /** "Full House, Kings full of Sevens". */
    val description: String,
    val variant: GameVariant,
) {
    val cardList: List<Card> get() = Card.parseList(cards)

    /**
     * Within one game the evaluator's score decides (it already knows a short-deck
     * flush beats a full house); across games, each hand ranks by its own game's order.
     */
    fun beats(other: BestHand?): Boolean {
        if (other == null) return true
        if (variant == other.variant) return score > other.score
        val mine = category.strength(variant.usesShortDeck)
        val theirs = other.category.strength(other.variant.usesShortDeck)
        return mine > theirs || (mine == theirs && score > other.score)
    }

    companion object {
        fun of(value: HandValue, variant: GameVariant) =
            BestHand(value.category, value.score, value.cards.joinToString(" "), value.description, variant)
    }
}

/** What one finished hand adds to the lifetime records (seat 0's view). */
data class HandRecord(
    val variant: GameVariant,
    val won: Boolean,
    /** Every pot of the hand together. */
    val potSize: Int,
    /** The player's five-card hand, when they stayed in with five cards to make one. */
    val best: BestHand?,
) {
    companion object {
        /** Reads [seat]'s record from a finished [hand]; null when the seat sat it out. */
        fun from(hand: Hand, seat: Int = 0): HandRecord? {
            val result = hand.result ?: return null
            val me = hand.snapshot(seat).seats[seat]
            if (me.sittingOut) return null
            val shown = result.handValues[seat] ?: if (me.folded) null else madeHand(hand, seat)
            return HandRecord(
                variant = hand.variant,
                won = (result.collected[seat] ?: 0) > 0,
                potSize = result.pots.sumOf { it.amount },
                best = shown?.let { BestHand.of(it, hand.variant) },
            )
        }

        /** The seat's best five, when the cards dealt so far make one (an uncalled bet ends hands early). */
        private fun madeHand(hand: Hand, seat: Int): HandValue? {
            val hole = hand.holeCards(seat)
            val board = hand.board
            val v = hand.variant
            val enough = if (v.mustUseTwoHoleCards) hole.size >= 2 && board.size >= 3 else hole.size + board.size >= 5
            if (!enough || (v.hasBoard && board.isEmpty())) return null
            return HandEvaluator.evaluate(hole, board, v)
        }
    }
}

/** Lifetime records for the STATS screen. Immutable; every update returns a new value. */
@Serializable
data class LifetimeStats(
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val biggestPot: Int = 0,
    val bestHand: BestHand? = null,
    /** Hands played per game. */
    val handsByGame: Map<GameVariant, Int> = emptyMap(),
    val toursStarted: Int = 0,
    /** Most tables cleared in one Poker Tour. */
    val bestTour: Int = 0,
    /** Most tables cleared in a daily tour. */
    val dailyBest: Int = 0,
) {
    /** Hands won as a whole percentage, or null before the first hand. */
    val winRate: Int? get() = if (handsPlayed == 0) null else (handsWon * 100 + handsPlayed / 2) / handsPlayed

    /** The most played game (ties go to the order of [GameVariant.entries]). */
    val favoriteGame: GameVariant? get() = handsByGame.filterValues { it > 0 }.entries
        .maxWithOrNull(compareBy<Map.Entry<GameVariant, Int>> { it.value }.thenByDescending { it.key.ordinal })?.key

    fun withHand(r: HandRecord): LifetimeStats = copy(
        handsPlayed = handsPlayed + 1,
        handsWon = handsWon + if (r.won) 1 else 0,
        biggestPot = if (r.won) maxOf(biggestPot, r.potSize) else biggestPot,
        bestHand = if (r.best != null && r.best.beats(bestHand)) r.best else bestHand,
        handsByGame = handsByGame + (r.variant to (handsByGame[r.variant] ?: 0) + 1),
    )

    fun withTourStarted(): LifetimeStats = copy(toursStarted = toursStarted + 1)

    /** Tour records from [run] as it stands. */
    fun withRun(run: RunState): LifetimeStats {
        val cleared = run.stats.tablesCleared
        return copy(
            bestTour = maxOf(bestTour, cleared),
            dailyBest = if (run.daily) maxOf(dailyBest, cleared) else dailyBest,
        )
    }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /** Null for anything unreadable. */
        fun fromJson(json: String): LifetimeStats? = try {
            JSON.decodeFromString(serializer(), json)
        } catch (_: Exception) {
            null
        }
    }
}

/** Keeps [LifetimeStats] in [prefs]; records hands for [GameController] and tours for the view model. */
class Stats(private val prefs: SharedPreferences) : HandRecorder {
    private val _stats = MutableStateFlow(prefs.getString(KEY, null)?.let(LifetimeStats::fromJson) ?: LifetimeStats())
    val stats: StateFlow<LifetimeStats> = _stats.asStateFlow()

    override fun handFinished(hand: Hand) {
        HandRecord.from(hand)?.let { r -> update { it.withHand(r) } }
    }

    fun tourStarted() = update { it.withTourStarted() }

    fun runChanged(run: RunState) = update { it.withRun(run) }

    fun reset() = update { LifetimeStats() }

    private fun update(f: (LifetimeStats) -> LifetimeStats) {
        val next = f(_stats.value)
        if (next == _stats.value) return
        _stats.value = next
        prefs.edit { putString(KEY, next.toJson()) }
    }

    private companion object {
        const val KEY = "stats_v1"
    }
}
