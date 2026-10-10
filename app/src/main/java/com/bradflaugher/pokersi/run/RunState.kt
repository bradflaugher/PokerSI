package com.bradflaugher.pokersi.run

import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Hand
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.Match
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.random.Random

@Serializable
enum class RunPhase { TABLE, SHOP, GAME_OVER }

enum class TableStatus { IN_PROGRESS, CLEARED, FAILED }

/** Where the current table stands between hands, so a run can be saved mid-table. */
@Serializable
data class TableProgress(
    /** Stacks by seat (player first). */
    val chips: List<Int>,
    val handsPlayed: Int = 0,
    /** Button of the last hand played. */
    val button: Int = 0,
    val mulliganUsed: Boolean = false,
)

@Serializable
data class RunStats(
    val handsPlayed: Int = 0,
    val handsWon: Int = 0,
    val biggestPot: Int = 0,
    val tablesCleared: Int = 0,
    val coinsEarned: Int = 0,
    val bestHand: HandCategory? = null,
)

/** The coins paid out for clearing a table, itemized for the cash-out screen. */
@Serializable
data class TableReward(val base: Int, val handsLeftBonus: Int, val interest: Int, val jokerBonus: Int) {
    val total: Int get() = base + handsLeftBonus + interest + jokerBonus
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
    /** The player's best five when it won at showdown (empty otherwise). */
    val bestFive: List<Card>,
    val potSize: Int,
    val bigBlind: Int,
    val wasBigBlind: Boolean,
    val wentAllIn: Boolean,
    /** Opponents who went broke in a hand the player won chips in. */
    val opponentsBusted: Int,
) {
    val won: Boolean get() = chipsWon > 0

    companion object {
        /** Reads [seat]'s outcome from a finished [hand]. */
        fun from(hand: Hand, seat: Int = 0): HandOutcome {
            val result = requireNotNull(hand.result) { "The hand is not finished" }
            val collected = result.collected[seat] ?: 0
            val value = result.handValues[seat]
            val wonAtShowdown = result.showdown && collected > 0
            return HandOutcome(
                chipsWon = collected,
                net = result.net[seat],
                showdown = result.showdown,
                category = if (wonAtShowdown) value?.category else null,
                bestFive = if (wonAtShowdown) value?.cards.orEmpty() else emptyList(),
                potSize = result.pots.sumOf { it.amount },
                bigBlind = hand.stakes.bigBlind,
                wasBigBlind = hand.bigBlindSeat == seat,
                wentAllIn = seat in result.allInSeats,
                opponentsBusted = if (collected == 0) 0 else result.startingChips.indices.count {
                    it != seat && result.startingChips[it] > 0 && result.finalChips[it] == 0
                },
            )
        }
    }
}

/** A Joker that fired this hand, for the UI to pop Balatro-style. */
data class JokerTrigger(val jokerId: String, val name: String, val text: String, val coins: Int = 0, val chips: Int = 0)

/** What the player's Jokers paid for one hand. [chips] are already added to the player's stack in the new state. */
data class HandPayout(val coins: Int, val chips: Int, val triggers: List<JokerTrigger>)

/** The run after a hand, plus what was paid and whether the table is decided. */
data class HandScoring(val state: RunState, val payout: HandPayout, val status: TableStatus)

/**
 * A roguelite run: a ladder of tables, three per ante (small, big, boss) for
 * eight antes, then endless. Between tables the shop sells [Jokers] for coins.
 * Immutable: every action returns a new state. Serializable for save/resume
 * ([toJson], [fromJson]); fully determined by [seed] and the player's choices.
 */
@Serializable
data class RunState(
    val seed: Long,
    val ante: Int = 1,
    val stage: Int = 0,
    val coins: Int = STARTING_COINS,
    /** Owned Joker ids, in purchase order. */
    val jokers: List<String> = emptyList(),
    val phase: RunPhase = RunPhase.TABLE,
    val table: TableSpec,
    val progress: TableProgress,
    /** Joker ids for sale (the shop phase). */
    val shop: List<String> = emptyList(),
    val rerolls: Int = 0,
    val stats: RunStats = RunStats(),
    /** Set when the last table was cleared. */
    val lastReward: TableReward? = null,
    /** True once the eighth ante's boss is beaten; the run goes on, endless. */
    val victory: Boolean = false,
    val daily: Boolean = false,
) {
    /** Jokers that work at this table (none at The Killjoy's). */
    val activeJokers: List<Joker>
        get() = if (table.boss?.rules?.jokersDisabled == true) emptyList() else jokers.mapNotNull(Jokers::byId)

    fun hasEffect(predicate: (JokerEffect) -> Boolean): Boolean = activeJokers.any { predicate(it.effect) }

    val hasXRay: Boolean get() = hasEffect { it == JokerEffect.XRay }
    val hasTellDetector: Boolean get() = hasEffect { it == JokerEffect.TellDetector }
    val canMulligan: Boolean get() = phase == RunPhase.TABLE && !progress.mulliganUsed && hasEffect { it == JokerEffect.Mulligan }

    val handsLeft: Int get() = (table.handLimit - progress.handsPlayed).coerceAtLeast(0)

    /** The [Match] to play this table on, resumed from [progress]. Deal with [startNextHand]. */
    fun newMatch(): Match = table.newMatch(progress)

    /** Deals the next hand of [match] with the right game for it. */
    fun startNextHand(match: Match): Hand = match.startNextHand(table.variantForHand(match.handsPlayed + 1))

    /** Marks the Mulligan used; the game layer re-deals with [Hand.redealHoleCards]. */
    fun useMulligan(): RunState = copy(progress = progress.copy(mulliganUsed = true))

    fun tableStatus(chips: List<Int> = progress.chips, handsPlayed: Int = progress.handsPlayed): TableStatus = when {
        chips[0] <= 0 -> TableStatus.FAILED
        chips.drop(1).all { it <= 0 } || chips[0] >= table.chipTarget -> TableStatus.CLEARED
        handsPlayed >= table.handLimit -> TableStatus.FAILED
        else -> TableStatus.IN_PROGRESS
    }

    /**
     * Scores a finished hand: applies Joker payouts (coins, bonus chips, insurance),
     * records [stacksAfter] (the match's stacks after the hand; the player's bonus chips
     * are added here, so copy [HandScoring.state]'s progress chips back into the match),
     * and clears or fails the table when it is decided.
     */
    fun applyHandResult(outcome: HandOutcome, stacksAfter: List<Int>, button: Int = progress.button): HandScoring {
        val payout = score(outcome)
        val chips = stacksAfter.toMutableList()
        chips[0] += payout.chips
        val best = stats.bestHand.let { b -> if (outcome.category != null && (b == null || outcome.category > b)) outcome.category else b }
        var next = copy(
            coins = coins + payout.coins,
            progress = progress.copy(chips = chips, handsPlayed = progress.handsPlayed + 1, button = button),
            stats = stats.copy(
                handsPlayed = stats.handsPlayed + 1,
                handsWon = stats.handsWon + if (outcome.won) 1 else 0,
                biggestPot = if (outcome.won) maxOf(stats.biggestPot, outcome.potSize) else stats.biggestPot,
                coinsEarned = stats.coinsEarned + payout.coins,
                bestHand = best,
            ),
        )
        val status = next.tableStatus()
        next = when (status) {
            TableStatus.CLEARED -> next.clearTable()
            TableStatus.FAILED -> next.copy(phase = RunPhase.GAME_OVER)
            TableStatus.IN_PROGRESS -> next
        }
        return HandScoring(next, payout, status)
    }

    private fun score(outcome: HandOutcome): HandPayout {
        val triggers = mutableListOf<JokerTrigger>()
        var doubleDown = 1
        for (joker in activeJokers) {
            fun coins(n: Int, text: String) {
                if (n > 0) triggers += JokerTrigger(joker.id, joker.name, text, coins = n)
            }
            when (val e = joker.effect) {
                is JokerEffect.CategoryCoins ->
                    if (outcome.won && outcome.category in e.categories) coins(e.coins, "+${e.coins} coins")
                is JokerEffect.PotPercentChips -> if (outcome.won) {
                    val bonus = outcome.chipsWon * e.percent / 100
                    if (bonus > 0) triggers += JokerTrigger(joker.id, joker.name, "+$bonus chips", chips = bonus)
                }
                is JokerEffect.RankCoins -> if (outcome.won) {
                    val n = outcome.bestFive.count { it.rank == e.rank }
                    coins(n * e.coins, "+${n * e.coins} coins")
                }
                is JokerEffect.SuitCoins -> if (outcome.won) {
                    val n = outcome.bestFive.count { it.suit == e.suit }
                    coins(n * e.coins, "+${n * e.coins} coins")
                }
                is JokerEffect.NoShowdownCoins -> if (outcome.won && !outcome.showdown) coins(e.coins, "+${e.coins} coins")
                is JokerEffect.ShowdownCoins -> if (outcome.won && outcome.showdown) coins(e.coins, "+${e.coins} coins")
                is JokerEffect.BigBlindCoins -> if (outcome.won && outcome.wasBigBlind) coins(e.coins, "+${e.coins} coins")
                is JokerEffect.BigPotCoins ->
                    if (outcome.won && outcome.potSize >= e.bigBlinds * outcome.bigBlind) coins(e.coins, "+${e.coins} coins")
                is JokerEffect.BustCoins -> coins(outcome.opponentsBusted * e.coins, "+${outcome.opponentsBusted * e.coins} coins")
                is JokerEffect.Insurance -> if (outcome.wentAllIn && outcome.net < 0) {
                    val back = -outcome.net * e.percent / 100
                    if (back > 0) triggers += JokerTrigger(joker.id, joker.name, "+$back chips back", chips = back)
                }
                is JokerEffect.AllInWinMultiplier -> if (outcome.won && outcome.wentAllIn) doubleDown *= e.times
                else -> Unit
            }
        }
        var coins = triggers.sumOf { it.coins }
        if (doubleDown > 1 && coins > 0) {
            val extra = coins * (doubleDown - 1)
            val joker = activeJokers.first { it.effect is JokerEffect.AllInWinMultiplier }
            triggers += JokerTrigger(joker.id, joker.name, "x$doubleDown coins", coins = extra)
            coins += extra
        }
        return HandPayout(coins, triggers.sumOf { it.chips }, triggers)
    }

    /** Pays out the table and opens the shop. */
    private fun clearTable(): RunState {
        val interestCap = INTEREST_CAP + activeJokers.sumOf { (it.effect as? JokerEffect.InterestCap)?.coins ?: 0 }
        val reward = TableReward(
            base = table.reward,
            handsLeftBonus = minOf(5, handsLeft / 2),
            interest = minOf(interestCap, coins / 5),
            jokerBonus = activeJokers.sumOf { (it.effect as? JokerEffect.ClearBonusCoins)?.coins ?: 0 },
        )
        val cleared = copy(
            coins = coins + reward.total,
            phase = RunPhase.SHOP,
            lastReward = reward,
            rerolls = 0,
            victory = victory || (ante == TableSpec.FINAL_ANTE && stage == TableSpec.STAGES_PER_ANTE - 1),
            stats = stats.copy(tablesCleared = stats.tablesCleared + 1, coinsEarned = stats.coinsEarned + reward.total),
        )
        return cleared.copy(shop = cleared.rollShop())
    }

    // ---------------------------------------------------------------- shop

    /** What [joker] costs right now (Coupon Book discounts). */
    fun price(joker: Joker): Int {
        val discount = activeJokers.sumOf { (it.effect as? JokerEffect.ShopDiscount)?.percent ?: 0 }.coerceAtMost(75)
        return maxOf(1, joker.price * (100 - discount) / 100)
    }

    fun sellValue(joker: Joker): Int = maxOf(1, joker.price / 2)

    val rerollCost: Int get() = REROLL_BASE_COST + rerolls

    fun canBuy(jokerId: String): Boolean {
        val joker = Jokers.byId(jokerId) ?: return false
        return phase == RunPhase.SHOP && jokerId in shop && jokers.size < MAX_JOKERS && coins >= price(joker)
    }

    /** Buys a Joker from the shop, or returns null when it can't be bought. */
    fun buy(jokerId: String): RunState? {
        if (!canBuy(jokerId)) return null
        val joker = Jokers.byId(jokerId)!!
        return copy(coins = coins - price(joker), jokers = jokers + jokerId, shop = shop - jokerId)
    }

    /** Sells an owned Joker for half its price, or returns null. */
    fun sell(jokerId: String): RunState? {
        if (jokerId !in jokers) return null
        val joker = Jokers.byId(jokerId) ?: return copy(jokers = jokers - jokerId)
        return copy(coins = coins + sellValue(joker), jokers = jokers - jokerId)
    }

    /** New shop offers, or null when the player can't afford it. */
    fun reroll(): RunState? {
        if (phase != RunPhase.SHOP || coins < rerollCost) return null
        val next = copy(coins = coins - rerollCost, rerolls = rerolls + 1)
        return next.copy(shop = next.rollShop())
    }

    /** Leaves the shop for the next table up the ladder. */
    fun leaveShop(): RunState {
        check(phase == RunPhase.SHOP) { "Not in the shop" }
        val (nextAnte, nextStage) = if (stage + 1 >= TableSpec.STAGES_PER_ANTE) ante + 1 to 0 else ante to stage + 1
        val spec = TableSpec.generate(seed, nextAnte, nextStage, jokers)
        return copy(
            ante = nextAnte,
            stage = nextStage,
            phase = RunPhase.TABLE,
            table = spec,
            progress = TableProgress(spec.startingChips),
            shop = emptyList(),
            rerolls = 0,
        )
    }

    /** Up to [SHOP_SIZE] Jokers not owned, weighted by rarity, seeded by position in the run. */
    private fun rollShop(): List<String> {
        val rng = Random(Match.mix(seed, 100_000 + ante * 100 + stage * 10 + rerolls))
        val pool = Jokers.ALL.filter { it.id !in jokers }.toMutableList()
        val offer = mutableListOf<String>()
        while (offer.size < SHOP_SIZE && pool.isNotEmpty()) {
            var roll = rng.nextInt(pool.sumOf { it.rarity.weight })
            val pick = pool.first { roll -= it.rarity.weight; roll < 0 }
            offer += pick.id
            pool -= pick
        }
        return offer
    }

    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        const val STARTING_COINS = 4
        const val MAX_JOKERS = 5
        const val SHOP_SIZE = 3
        const val INTEREST_CAP = 5
        const val REROLL_BASE_COST = 2

        private val JSON = Json { ignoreUnknownKeys = true }

        /** A fresh run at the first table. */
        fun new(seed: Long): RunState {
            val spec = TableSpec.generate(seed, 1, 0)
            return RunState(seed = seed, table = spec, progress = TableProgress(spec.startingChips))
        }

        /** Everyone gets the same run on the same day. */
        fun daily(date: LocalDate): RunState = new(dailySeed(date)).copy(daily = true)

        fun dailySeed(date: LocalDate): Long = Match.mix(date.toEpochDay(), 0x5EED)

        fun fromJson(json: String): RunState = JSON.decodeFromString(serializer(), json)
    }
}
