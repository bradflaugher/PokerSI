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

/** One opponent at a run table: a name, a [BotStyle] name for the House Bot, a stack. */
@Serializable
data class OpponentSpec(val name: String, val style: String, val chips: Int) {
    val botStyle: BotStyle get() = BotStyle.ALL.firstOrNull { it.name == style } ?: BotStyle.BALANCED
}

/**
 * One table of a run. The player is seat 0, opponents follow. Clear it by
 * busting every opponent or holding [chipTarget] chips within [handLimit] hands.
 */
@Serializable
data class TableSpec(
    val ante: Int,
    /** 0 = small table, 1 = big table, 2 = boss table. */
    val stage: Int,
    val name: String,
    val variant: GameVariant,
    val opponents: List<OpponentSpec>,
    val playerChips: Int,
    val smallBlind: Int,
    val bigBlind: Int,
    val tableAnte: Int,
    val handsPerLevel: Int,
    val blindGrowth: Double,
    val chipTarget: Int,
    val handLimit: Int,
    /** Coins for clearing the table, before bonuses. */
    val reward: Int,
    val bossId: String? = null,
    val seed: Long,
) {
    val boss: Boss? get() = bossId?.let(Bosses::byId)
    val isBoss: Boolean get() = bossId != null

    val seatNames: List<String> get() = listOf(PLAYER_NAME) + opponents.map { it.name }
    val startingChips: List<Int> get() = listOf(playerChips) + opponents.map { it.chips }

    val schedule: BlindSchedule get() = BlindSchedule(BlindLevel(smallBlind, bigBlind, tableAnte), handsPerLevel, blindGrowth)

    /** The game for 1-based [handNumber] (The Shapeshifter changes it every hand). */
    fun variantForHand(handNumber: Int): GameVariant =
        if (boss?.rules?.rotateVariants == true) GameVariant.entries[(handNumber - 1 + (seed and 0xFF).toInt()) % GameVariant.entries.size]
        else variant

    /** A fresh [Match] for this table, or one resumed from [progress]. */
    fun newMatch(progress: TableProgress? = null): Match = Match(
        names = seatNames,
        startingChips = progress?.chips ?: startingChips,
        variant = variantForHand((progress?.handsPlayed ?: 0) + 1),
        schedule = schedule,
        seed = seed,
        button = progress?.button ?: 0,
        handsPlayed = progress?.handsPlayed ?: 0,
    )

    companion object {
        const val PLAYER_NAME = "You"
        const val STAGES_PER_ANTE = 3
        const val FINAL_ANTE = 8
        const val BASE_CHIPS = 1000

        private val OPPONENT_NAMES = listOf(
            "Lucky Lou", "Big Deb", "Chip Chester", "Aunt Ante", "Captain Fold", "Dee Dee Dealer", "Slick Rick",
            "Grandma Gamble", "The Professor", "Two-Pair Terry", "Wild Card Willa", "Bluffalo Bill", "Penny Pincher",
            "Señor Showdown", "River Rita", "Count Chipula", "Pocket Rocket", "Kitty Kicker", "Flushy McFlush", "Tilt Tilda",
        )

        /**
         * Builds the table for [ante] and [stage] of the run seeded with [runSeed].
         * [jokers] (owned ids) can add starting chips, hands or lower the target.
         */
        fun generate(runSeed: Long, ante: Int, stage: Int, jokers: List<String> = emptyList()): TableSpec {
            val seed = Match.mix(runSeed, ante * 16 + stage)
            val rng = Random(seed)
            val boss = if (stage == 2) Bosses.ALL[rng.nextInt(Bosses.ALL.size)] else null
            val rules = boss?.rules ?: BossRules()
            val effects = if (rules.jokersDisabled) emptyList() else jokers.mapNotNull { Jokers.byId(it)?.effect }

            // Difficulty grows through the eight antes, then faster in endless mode.
            val endless = (ante - FINAL_ANTE).coerceAtLeast(0)
            val scale = (1.0 + 0.15 * (ante - 1)) * 1.4.pow(endless)

            val opponentCount = (1 + stage + (if (ante >= 3) 1 else 0) + (if (ante >= 6) 1 else 0) + rng.nextInt(0, 2))
                .coerceIn(1, 5)
            val names = OPPONENT_NAMES.shuffled(rng).take(opponentCount)
            val styles = BotStyle.ALL.filter { stage == 2 || ante > 2 || it != BotStyle.SHARK }
            val opponentChips = roundTo((BASE_CHIPS * scale).roundToInt(), 50)
            val opponents = names.map { OpponentSpec(it, styles[rng.nextInt(styles.size)].name, opponentChips) }

            val chipBonus = effects.filterIsInstance<JokerEffect.StartingChipsPercent>().sumOf { it.percent }
            val playerChips = roundTo((BASE_CHIPS * scale * rules.playerChipsMultiplier * (100 + chipBonus) / 100).roundToInt(), 50)

            val bigBlind = BlindSchedule.tidy((20 * scale * (1 + 0.25 * stage)).toLong())
            val smallBlind = maxOf(1, bigBlind / 2)
            val tableAnte = if (rules.anteFraction > 0) maxOf(1, (bigBlind * rules.anteFraction).roundToInt()) else 0

            // Win enough of the table's chips: a bigger share as the antes climb, always reachable.
            val opponentTotal = opponentChips * opponentCount
            val wanted = BASE_CHIPS * scale * (1.6 + 0.15 * (ante - 1) + 0.2 * stage)
            val reachable = playerChips + opponentTotal * 0.8
            val chipTarget = roundTo(minOf(wanted, reachable).roundToInt(), 50).coerceAtLeast(playerChips + 50)

            val extraHands = effects.filterIsInstance<JokerEffect.ExtraHands>().sumOf { it.hands }
            val handLimit = ((12 + 4 * opponentCount) * rules.handLimitMultiplier).roundToInt() + extraHands

            val variant = rules.forcedVariant ?: when {
                ante == 1 -> GameVariant.TEXAS_HOLDEM
                rng.nextInt(3) == 0 -> GameVariant.TEXAS_HOLDEM
                else -> GameVariant.entries[rng.nextInt(GameVariant.entries.size)]
            }
            return TableSpec(
                ante = ante,
                stage = stage,
                name = boss?.name ?: if (stage == 0) "Small Table" else "Big Table",
                variant = variant,
                opponents = opponents,
                playerChips = playerChips,
                smallBlind = smallBlind,
                bigBlind = bigBlind,
                tableAnte = tableAnte,
                handsPerLevel = rules.blindsUpEvery ?: 8,
                blindGrowth = if (rules.blindsUpEvery != null) rules.blindsGrowth else 1.5,
                chipTarget = chipTarget,
                handLimit = handLimit,
                reward = 3 + 2 * stage + ante / 2 + endless,
                bossId = boss?.id,
                seed = seed,
            )
        }

        private fun roundTo(value: Int, step: Int): Int = maxOf(step, (value + step / 2) / step * step)
    }
}
