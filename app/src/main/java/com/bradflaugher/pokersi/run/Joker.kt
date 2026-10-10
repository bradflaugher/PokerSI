package com.bradflaugher.pokersi.run

import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.Rank
import com.bradflaugher.pokersi.engine.Suit
import kotlinx.serialization.Serializable

@Serializable
enum class Rarity(val label: String, val weight: Int) {
    COMMON("Common", 60),
    UNCOMMON("Uncommon", 30),
    RARE("Rare", 10),
}

/**
 * What a Joker does. Scoring effects are applied by [RunState.applyHandResult];
 * table effects by [TableSpec] generation; the "UI" effects ([XRay], [Mulligan],
 * [TellDetector]) are flags the game layer checks (see [Perks]).
 */
sealed interface JokerEffect {
    /** +[coins] for winning a pot with one of [categories]. */
    data class CategoryCoins(val categories: Set<HandCategory>, val coins: Int) : JokerEffect

    /** +[percent]% of every pot you win, paid in chips. */
    data class PotPercentChips(val percent: Int) : JokerEffect

    /** +[coins] for each card of [rank] in your winning five. */
    data class RankCoins(val rank: Rank, val coins: Int) : JokerEffect

    /** +[coins] for each card of [suit] in your winning five. */
    data class SuitCoins(val suit: Suit, val coins: Int) : JokerEffect

    /** +[coins] for winning when everyone else folds. */
    data class NoShowdownCoins(val coins: Int) : JokerEffect

    /** +[coins] for winning at showdown. */
    data class ShowdownCoins(val coins: Int) : JokerEffect

    /** +[coins] for winning a pot from the big blind. */
    data class BigBlindCoins(val coins: Int) : JokerEffect

    /** +[coins] for winning a pot of at least [bigBlinds] big blinds. */
    data class BigPotCoins(val bigBlinds: Int, val coins: Int) : JokerEffect

    /** +[coins] for each opponent you bust. */
    data class BustCoins(val coins: Int) : JokerEffect

    /** [percent]% of the chips lost back when you lose an all-in. */
    data class Insurance(val percent: Int) : JokerEffect

    /** Multiplies the hand's coins when you win while all-in. */
    data class AllInWinMultiplier(val times: Int) : JokerEffect

    /** +[percent]% starting chips at every table. */
    data class StartingChipsPercent(val percent: Int) : JokerEffect

    /** [hands] more hands to reach each table's chip target. */
    data class ExtraHands(val hands: Int) : JokerEffect

    /** +[coins] every time you clear a table. */
    data class ClearBonusCoins(val coins: Int) : JokerEffect

    /** Raises the interest cap by [coins]. */
    data class InterestCap(val coins: Int) : JokerEffect

    /** Shop prices [percent]% off. */
    data class ShopDiscount(val percent: Int) : JokerEffect

    /** See one random face-down opponent card each hand. */
    data object XRay : JokerEffect

    /** Once per table, re-deal your starting cards before acting. */
    data object Mulligan : JokerEffect

    /** Shows how confident each opponent is (its equity). */
    data object TellDetector : JokerEffect
}

/** A perk bought in the shop. Only [id]s are saved; the catalog is [Jokers.ALL]. */
data class Joker(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val rarity: Rarity,
    val effect: JokerEffect,
)

object Jokers {
    val ALL: List<Joker> = listOf(
        Joker("flush_fund", "Flush Fund", "+6 coins when you win with a Flush. Suits you.", 5, Rarity.COMMON,
            JokerEffect.CategoryCoins(setOf(HandCategory.FLUSH), 6)),
        Joker("straight_shooter", "Straight Shooter", "+5 coins when you win with a Straight. No funny business.", 4, Rarity.COMMON,
            JokerEffect.CategoryCoins(setOf(HandCategory.STRAIGHT), 5)),
        Joker("two_pair_tango", "Two-Pair Tango", "+3 coins when you win with Two Pair. It takes two. Twice.", 3, Rarity.COMMON,
            JokerEffect.CategoryCoins(setOf(HandCategory.TWO_PAIR), 3)),
        Joker("full_house_party", "Full House Party", "+8 coins when you win with a Full House. Everyone's invited.", 6, Rarity.UNCOMMON,
            JokerEffect.CategoryCoins(setOf(HandCategory.FULL_HOUSE), 8)),
        Joker("quad_squad", "Quad Squad", "+20 coins when you win with Four of a Kind.", 7, Rarity.RARE,
            JokerEffect.CategoryCoins(setOf(HandCategory.FOUR_OF_A_KIND), 20)),
        Joker("royal_treatment", "Royal Treatment", "+50 coins when you win with a Straight Flush. Bow, peasants.", 7, Rarity.RARE,
            JokerEffect.CategoryCoins(setOf(HandCategory.STRAIGHT_FLUSH, HandCategory.ROYAL_FLUSH), 50)),
        Joker("rake_back", "Rake Back", "+10% of every pot you win, in chips. The house feels generous.", 6, Rarity.UNCOMMON,
            JokerEffect.PotPercentChips(10)),
        Joker("high_roller", "High Roller", "+25% of every pot you win, in chips. Diamonds on the soles.", 9, Rarity.RARE,
            JokerEffect.PotPercentChips(25)),
        Joker("lucky_sevens", "Lucky Sevens", "+7 coins for every 7 in your winning hand. Jackpot!", 5, Rarity.COMMON,
            JokerEffect.RankCoins(Rank.SEVEN, 7)),
        Joker("ace_collector", "Ace Collector", "+3 coins for every Ace in your winning hand.", 4, Rarity.COMMON,
            JokerEffect.RankCoins(Rank.ACE, 3)),
        Joker("heartbreaker", "Heartbreaker", "+2 coins for every heart in your winning hand. Aww.", 3, Rarity.COMMON,
            JokerEffect.SuitCoins(Suit.HEARTS, 2)),
        Joker("poker_face", "Poker Face", "+3 coins when everyone folds to you. They never knew.", 4, Rarity.COMMON,
            JokerEffect.NoShowdownCoins(3)),
        Joker("showboat", "Showboat", "+3 coins when you win at showdown. Take a bow.", 4, Rarity.COMMON,
            JokerEffect.ShowdownCoins(3)),
        Joker("big_blind_energy", "Big Blind Energy", "+5 coins when you win a pot from the big blind.", 4, Rarity.COMMON,
            JokerEffect.BigBlindCoins(5)),
        Joker("whale_watcher", "Whale Watcher", "+5 coins when you win a pot of 20 big blinds or more.", 5, Rarity.UNCOMMON,
            JokerEffect.BigPotCoins(20, 5)),
        Joker("bounty_hunter", "Bounty Hunter", "+4 coins for every opponent you bust. Yeehaw.", 5, Rarity.COMMON,
            JokerEffect.BustCoins(4)),
        Joker("insurance", "Insurance", "Lose an all-in? Get 25% of the chips back. Read the fine print.", 6, Rarity.UNCOMMON,
            JokerEffect.Insurance(25)),
        Joker("double_down", "Double Down", "Coins from a hand you win all-in are doubled.", 8, Rarity.RARE,
            JokerEffect.AllInWinMultiplier(2)),
        Joker("deep_pockets", "Deep Pockets", "Start every table with 25% more chips. Lint not included.", 7, Rarity.UNCOMMON,
            JokerEffect.StartingChipsPercent(25)),
        Joker("overtime", "Overtime", "+5 hands to hit every table's target.", 4, Rarity.COMMON,
            JokerEffect.ExtraHands(5)),
        Joker("piggy_bank", "Piggy Bank", "Interest cap +5 coins. Oink.", 6, Rarity.UNCOMMON,
            JokerEffect.InterestCap(5)),
        Joker("coupon_book", "Coupon Book", "Everything in the shop is 25% off.", 5, Rarity.UNCOMMON,
            JokerEffect.ShopDiscount(25)),
        Joker("xray_specs", "X-Ray Specs", "See one random opponent hole card every hand. Don't tell.", 8, Rarity.RARE,
            JokerEffect.XRay),
        Joker("mulligan", "Mulligan", "Once per table, re-deal your starting cards before you act.", 6, Rarity.UNCOMMON,
            JokerEffect.Mulligan),
        Joker("tell_detector", "Tell Detector", "See how confident every opponent feels. Sweaty palms!", 5, Rarity.UNCOMMON,
            JokerEffect.TellDetector),
    )

    private val BY_ID = ALL.associateBy { it.id }

    fun byId(id: String): Joker? = BY_ID[id]
}
