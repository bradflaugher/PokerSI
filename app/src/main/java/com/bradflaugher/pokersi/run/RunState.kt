package com.bradflaugher.pokersi.run

import com.bradflaugher.pokersi.engine.Hand
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.strength
import com.bradflaugher.pokersi.engine.usesShortDeck
import com.bradflaugher.pokersi.engine.Match
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
enum class RunPhase {
    /** Playing (or about to sit down at) [RunState.table]. */
    TABLE,

    /** The table is cleared: pick the next one from [RunState.offers]. */
    CHOOSE,
    GAME_OVER,
}

enum class TableStatus { IN_PROGRESS, CLEARED, FAILED }

/** Where the current table stands between hands, so a run can be saved mid-table. */
@Serializable
data class TableProgress(
    /** Stacks by seat (player first). */
    val chips: List<Int>,
    val handsPlayed: Int = 0,
    /** Button of the last hand played. */
    val button: Int = 0,
)

@Serializable
data class RunStats(
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val biggestPot: Int = 0,
    val tablesCleared: Int = 0,
    val bestHand: HandCategory? = null,
    /** The most chips the player has had between tables. */
    val peakChips: Int = 0,
)

/** The bonus chips paid for clearing a table, itemized for the cleared screen. */
@Serializable
data class TableReward(val bonus: Int, val handsLeftBonus: Int) {
    val total: Int get() = bonus + handsLeftBonus
}

/** The player's side of a finished hand, as the run's scoring needs it. */
data class HandOutcome(
    /** Chips collected from pots (0 when the player won nothing). */
    val chipsWon: Int,
    /** Chips won or lost overall. */
    val net: Int,
    val showdown: Boolean,
    /** The player's hand when it won at showdown. */
    val category: HandCategory?,
    val potSize: Int,
    /** Short-deck ranking applies (a flush beats a full house). */
    val shortDeck: Boolean = false,
) {
    val won: Boolean get() = chipsWon > 0

    companion object {
        /** Reads [seat]'s outcome from a finished [hand]. */
        fun from(hand: Hand, seat: Int = 0): HandOutcome {
            val result = requireNotNull(hand.result) { "The hand is not finished" }
            val collected = result.collected[seat] ?: 0
            val wonAtShowdown = result.showdown && collected > 0
            return HandOutcome(
                chipsWon = collected,
                net = result.net[seat],
                showdown = result.showdown,
                category = if (wonAtShowdown) result.handValues[seat]?.category else null,
                potSize = result.pots.sumOf { it.amount },
                shortDeck = hand.variant.usesShortDeck,
            )
        }

        /** Walking away from a hand in play: a fold that costs what the player had put in. */
        fun forfeit(committed: Int, potSize: Int): HandOutcome =
            HandOutcome(chipsWon = 0, net = -committed, showdown = false, category = null, potSize = potSize)
    }
}

/** The run after a hand, and whether the table is decided. */
data class HandScoring(val state: RunState, val status: TableStatus)

/**
 * The Poker Tour: a ladder of tables of rising stakes. The player's chips carry
 * from table to table. After clearing one, the player picks the next from
 * [offers]: a low-stakes table or a high-stakes one, bigger risk for a bigger
 * chip reward. Clearing stop [TableSpec.FINAL_LEVEL] wins the tour; it goes
 * on, endless. Immutable: every action returns a new
 * state. Serializable for save/resume ([toJson], [fromJson]); fully determined
 * by [seed] and the player's choices.
 */
@Serializable
data class RunState(
    val seed: Long,
    /** The tour stop of [table], from 1. */
    val level: Int = 1,
    val phase: RunPhase = RunPhase.TABLE,
    val table: TableSpec,
    val progress: TableProgress,
    /** The player's chips between tables (set when a table is decided). */
    val bankroll: Int = STARTING_CHIPS,
    /** The tables to choose from ([RunPhase.CHOOSE]). */
    val offers: List<TableSpec> = emptyList(),
    val stats: RunStats = RunStats(),
    /** Set when the last table was cleared. */
    val lastReward: TableReward? = null,
    /** True once stop [TableSpec.FINAL_LEVEL] is cleared; the tour goes on, endless. */
    val victory: Boolean = false,
    val daily: Boolean = false,
    /** Save format; saves of another format are dropped (see [fromJson]). */
    val version: Int = 0,
) {
    val handsLeft: Int get() = (table.handLimit - progress.handsPlayed).coerceAtLeast(0)

    /** The player's chips right now: at the table, or the bankroll between tables. */
    val playerChips: Int get() = if (phase == RunPhase.TABLE) progress.chips[0] else bankroll

    /** The [Match] to play this table on, resumed from [progress]. Deal with [startNextHand]. */
    fun newMatch(): Match = table.newMatch(progress)

    /** Deals the next hand of [match] at this table's game. */
    fun startNextHand(match: Match): Hand = match.startNextHand(table.variant)

    fun tableStatus(chips: List<Int> = progress.chips, handsPlayed: Int = progress.handsPlayed): TableStatus = when {
        chips[0] <= 0 -> TableStatus.FAILED
        chips.drop(1).all { it <= 0 } || chips[0] >= table.chipTarget -> TableStatus.CLEARED
        handsPlayed >= table.handLimit -> TableStatus.FAILED
        else -> TableStatus.IN_PROGRESS
    }

    /**
     * Scores a finished hand: records [stacksAfter] (the match's stacks after the hand)
     * and [button], and clears or fails the table when it is decided.
     */
    fun applyHandResult(outcome: HandOutcome, stacksAfter: List<Int>, button: Int = progress.button): HandScoring {
        val best = stats.bestHand.let { b ->
            val c = outcome.category
            if (c != null && (b == null || c.strength(outcome.shortDeck) > b.strength(outcome.shortDeck))) c else b
        }
        var next = copy(
            progress = progress.copy(chips = stacksAfter.toList(), handsPlayed = progress.handsPlayed + 1, button = button),
            stats = stats.copy(
                handsPlayed = stats.handsPlayed + 1,
                handsWon = stats.handsWon + if (outcome.won) 1 else 0,
                biggestPot = if (outcome.won) maxOf(stats.biggestPot, outcome.potSize) else stats.biggestPot,
                bestHand = best,
            ),
        )
        val status = next.tableStatus()
        next = when (status) {
            TableStatus.CLEARED -> next.clearTable()
            TableStatus.FAILED -> next.copy(phase = RunPhase.GAME_OVER, bankroll = stacksAfter[0])
            TableStatus.IN_PROGRESS -> next
        }
        return HandScoring(next, status)
    }

    /** Pays out the table and offers the next ones. */
    private fun clearTable(): RunState {
        val reward = TableReward(bonus = table.reward, handsLeftBonus = handsLeft * table.bigBlind)
        val chips = progress.chips[0] + reward.total
        return copy(
            phase = RunPhase.CHOOSE,
            bankroll = chips,
            lastReward = reward,
            offers = offersFor(level + 1, chips),
            victory = victory || level == TableSpec.FINAL_LEVEL,
            stats = stats.copy(tablesCleared = stats.tablesCleared + 1, peakChips = maxOf(stats.peakChips, chips)),
        )
    }

    /** Sits down at offer [index] ([RunPhase.CHOOSE] only); null when there is no such offer. */
    fun choose(index: Int): RunState? {
        if (phase != RunPhase.CHOOSE) return null
        val spec = offers.getOrNull(index) ?: return null
        return copy(
            level = spec.level,
            phase = RunPhase.TABLE,
            table = spec,
            progress = TableProgress(spec.startingChips),
            offers = emptyList(),
        )
    }

    private fun offersFor(level: Int, bankroll: Int): List<TableSpec> =
        Risk.entries.map { TableSpec.generate(seed, level, it, bankroll) }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        const val STARTING_CHIPS = 1000

        /** Bump when the format changes incompatibly; older saves are dropped, not crashed on. */
        const val SAVE_VERSION = 3

        private val JSON = Json { ignoreUnknownKeys = true }

        /** A fresh tour at its first table. */
        fun new(seed: Long): RunState {
            val spec = TableSpec.generate(seed, 1, Risk.LOW, STARTING_CHIPS)
            return RunState(
                seed = seed,
                table = spec,
                progress = TableProgress(spec.startingChips),
                version = SAVE_VERSION,
            )
        }

        /** Everyone gets the same tour on the same day. */
        fun daily(date: LocalDate): RunState = new(dailySeed(date)).copy(daily = true)

        fun dailySeed(date: LocalDate): Long = Match.mix(date.toEpochDay(), 0x5EED)

        /** Reads a saved run; throws on a save from another format (callers treat that as no save). */
        fun fromJson(json: String): RunState {
            val state = JSON.decodeFromString(serializer(), json)
            require(state.version == SAVE_VERSION) { "Old save format ${state.version}" }
            require(state.progress.chips.size == state.table.startingChips.size) { "Corrupt save" }
            require(state.phase != RunPhase.CHOOSE || state.offers.isNotEmpty()) { "Corrupt save" }
            return state
        }
    }
}
