package com.bradflaugher.pokersi.run

import com.bradflaugher.pokersi.engine.BlindLevel
import com.bradflaugher.pokersi.engine.BlindSchedule
import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.Match
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.random.Random

/** One opponent at a tour table: a name, a [BotStyle] name for the House Bot, a stack. */
@Serializable
data class OpponentSpec(val name: String, val style: String, val chips: Int) {
    val botStyle: BotStyle get() = BotStyle.ALL.firstOrNull { it.name == style } ?: BotStyle.BALANCED
}

/** How dangerous a table on offer is. Bigger risk, bigger reward. */
@Serializable
enum class Risk(val label: String) {
    /** Fewer, softer opponents, low blinds. */
    LOW("LOW STAKES"),

    /** One more opponent, sharks allowed, bigger blinds and target. */
    HIGH("HIGH STAKES"),
}

/**
 * One table of the Poker Tour. The player is seat 0, opponents follow. Clear it by
 * busting every opponent or holding [chipTarget] chips within [handLimit] hands;
 * clearing pays [reward] bonus chips on top of what the player won.
 */
@Serializable
data class TableSpec(
    /** The tour stop, from 1. */
    val level: Int,
    val risk: Risk,
    val name: String,
    val variant: GameVariant,
    val opponents: List<OpponentSpec>,
    /** What the player sits down with: their whole bankroll. */
    val playerChips: Int,
    val smallBlind: Int,
    val bigBlind: Int,
    val handsPerLevel: Int,
    val blindGrowth: Double,
    val chipTarget: Int,
    val handLimit: Int,
    /** Bonus chips for clearing the table. */
    val reward: Int,
    val seed: Long,
) {
    val seatNames: List<String> get() = listOf(PLAYER_NAME) + opponents.map { it.name }
    val startingChips: List<Int> get() = listOf(playerChips) + opponents.map { it.chips }

    val schedule: BlindSchedule get() = BlindSchedule(BlindLevel(smallBlind, bigBlind), handsPerLevel, blindGrowth)

    /** A fresh [Match] for this table, or one resumed from [progress]. */
    fun newMatch(progress: TableProgress? = null): Match = Match(
        names = seatNames,
        startingChips = progress?.chips ?: startingChips,
        variant = variant,
        schedule = schedule,
        seed = seed,
        button = progress?.button ?: 0,
        handsPlayed = progress?.handsPlayed ?: 0,
    )

    companion object {
        const val PLAYER_NAME = "You"

        /** Clearing this stop wins the tour (it goes on, endless). */
        const val FINAL_LEVEL = 10
        const val BASE_CHIPS = 1000

        private val OPPONENT_NAMES = listOf(
            "Lucky Lou", "Big Deb", "Chip Chester", "Aunt Ante", "Captain Fold", "Dee Dee Dealer", "Slick Rick",
            "Grandma Gamble", "The Professor", "Two-Pair Terry", "Wild Card Willa", "Bluffalo Bill", "Penny Pincher",
            "Señor Showdown", "River Rita", "Count Chipula", "Pocket Rocket", "Kitty Kicker", "Flushy McFlush", "Tilt Tilda",
        )

        /**
         * Builds the [risk] table offered at tour stop [level] of the run seeded with
         * [runSeed], for a player holding [bankroll] chips.
         */
        fun generate(runSeed: Long, level: Int, risk: Risk, bankroll: Int): TableSpec {
            val seed = Match.mix(runSeed, level * 16 + risk.ordinal)
            val rng = Random(seed)
            val r = risk.ordinal

            // Stakes grow along the tour, then faster in endless mode.
            val endless = (level - FINAL_LEVEL).coerceAtLeast(0)
            val scale = (1.0 + 0.2 * (level - 1)) * 1.4.pow(endless)

            val opponentCount = (1 + r + (if (level >= 4) 1 else 0) + (if (level >= 7) 1 else 0) + rng.nextInt(0, 2))
                .coerceIn(1, 5)
            val names = OPPONENT_NAMES.shuffled(rng).take(opponentCount)
            val styles = BotStyle.ALL.filter { risk != Risk.LOW || level > 3 || it != BotStyle.SHARK }
            val opponentChips = roundTo((BASE_CHIPS * scale).roundToInt(), 50)
            val opponents = names.map { OpponentSpec(it, styles[rng.nextInt(styles.size)].name, opponentChips) }

            val playerChips = maxOf(1, bankroll)

            val bigBlind = BlindSchedule.tidy((20 * scale * (1 + 0.25 * r)).toLong())
            val smallBlind = maxOf(1, bigBlind / 2)

            // Win a share of the table's chips: a bigger share at riskier tables and later stops.
            val opponentTotal = opponentChips * opponentCount
            val share = (0.3 + 0.12 * r + 0.02 * (level - 1)).coerceAtMost(0.8)
            val chipTarget = roundTo((playerChips + opponentTotal * share).roundToInt(), 50)
                .coerceIn(playerChips + 50, maxOf(playerChips + 50, playerChips + (opponentTotal * 0.8).toInt()))

            val handLimit = 12 + 4 * opponentCount

            val variant = when {
                level == 1 -> GameVariant.TEXAS_HOLDEM
                rng.nextInt(3) == 0 -> GameVariant.TEXAS_HOLDEM
                else -> GameVariant.entries[rng.nextInt(GameVariant.entries.size)]
            }
            return TableSpec(
                level = level,
                risk = risk,
                name = if (risk == Risk.LOW) "Low Stakes" else "High Stakes",
                variant = variant,
                opponents = opponents,
                playerChips = playerChips,
                smallBlind = smallBlind,
                bigBlind = bigBlind,
                handsPerLevel = 8,
                blindGrowth = 1.5,
                chipTarget = chipTarget,
                handLimit = handLimit,
                reward = roundTo((opponentChips * (0.2 + 0.3 * r)).roundToInt(), 50),
                seed = seed,
            )
        }

        private fun roundTo(value: Int, step: Int): Int = maxOf(step, (value + step / 2) / step * step)
    }
}
