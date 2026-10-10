package com.bradflaugher.pokersi.engine

/** Poker hand categories, weakest first (standard ranking; see [HandValue] for short deck). */
enum class HandCategory(val label: String) {
    HIGH_CARD("High Card"),
    PAIR("Pair"),
    TWO_PAIR("Two Pair"),
    THREE_OF_A_KIND("Three of a Kind"),
    STRAIGHT("Straight"),
    FLUSH("Flush"),
    FULL_HOUSE("Full House"),
    FOUR_OF_A_KIND("Four of a Kind"),
    STRAIGHT_FLUSH("Straight Flush"),

    /** An ace-high straight flush. Display only: it scores as a [STRAIGHT_FLUSH]. */
    ROYAL_FLUSH("Royal Flush"),
}

/**
 * A finished high hand: its [category], the best [cards] (five, strongest
 * group first) and a [description] such as "Two Pair, Kings and Sevens".
 * Compare values to rank hands; equal [score]s split the pot. Values from
 * a short-deck evaluation only compare with other short-deck values.
 */
data class HandValue(
    val category: HandCategory,
    val cards: List<Card>,
    val description: String,
    /** Higher is better; packs the category strength and the kicker ranks. */
    val score: Int,
) : Comparable<HandValue> {
    override fun compareTo(other: HandValue): Int = score.compareTo(other.score)
}

/**
 * Fast high-hand evaluation. Cards are packed as rank bitmasks, so best-five-of-seven
 * needs no combinations for Hold'em or stud; Omaha tries its 60 legal two-plus-three
 * hands. The `score*` functions are allocation-free and used by [Equity].
 */
object HandEvaluator {
    private const val CATEGORY_SHIFT = 20

    /** Top rank value (5..14) of the best straight in a 13-bit rank mask, or 0. */
    private val straightTop = IntArray(1 shl 13)

    /** Same for short deck, where A-6-7-8-9 (top 9) is the lowest straight. */
    private val straightTopShort = IntArray(1 shl 13)

    init {
        for (mask in 0 until (1 shl 13)) {
            straightTop[mask] = findStraight(mask, wheel = 0b1_0000_0000_1111)
            straightTopShort[mask] = findStraight(mask, wheel = 0b1_0000_1111_0000)
        }
    }

    private fun findStraight(mask: Int, wheel: Int): Int {
        for (top in 12 downTo 4) {
            val run = 0b11111 shl (top - 4)
            if (mask and run == run) return top + 2
        }
        if (mask and wheel == wheel) return if (wheel and 1 != 0) 5 else 9
        return 0
    }

    // ---------------------------------------------------------------- public API

    /** Best five-card hand from any 5 to 7 [cards] (Hold'em, stud, draw). */
    fun evaluate(cards: List<Card>, shortDeck: Boolean = false): HandValue {
        require(cards.size >= 5) { "Need at least 5 cards, got ${cards.size}" }
        val codes = IntArray(cards.size) { cards[it].code }
        return toValue(score(codes, codes.size, shortDeck), cards, shortDeck)
    }

    /** Omaha: the best hand using exactly two of [hole] and exactly three of [board]. */
    fun evaluateOmaha(hole: List<Card>, board: List<Card>, shortDeck: Boolean = false): HandValue {
        require(hole.size >= 2 && board.size >= 3) { "Omaha needs 2+ hole cards and 3+ board cards" }
        val h = IntArray(hole.size) { hole[it].code }
        val b = IntArray(board.size) { board[it].code }
        val five = IntArray(5)
        var best = -1
        var bestCards: List<Card> = emptyList()
        forEachOmahaHand(h, h.size, b, b.size, shortDeck, five) { s ->
            if (s > best) {
                best = s
                bestCards = five.map(CardCodes::card)
            }
        }
        return toValue(best, bestCards, shortDeck)
    }

    /** The showdown hand for [variant]: [hole] (all of a stud player's cards) plus [board]. */
    fun evaluate(hole: List<Card>, board: List<Card>, variant: GameVariant): HandValue =
        if (variant.mustUseTwoHoleCards) evaluateOmaha(hole, board, variant.usesShortDeck)
        else evaluate(hole + board, variant.usesShortDeck)

    /**
     * The seat's hand in words for [DecisionContext.madeHand], also with fewer than five
     * cards: "Pocket Kings", "Ace-King suited", "Pair of Nines", "Flush, Ace high",
     * "Pair of Sevens, flush draw". [moreCardsToCome] adds draws ("open-ended straight draw").
     */
    fun describe(hole: List<Card>, board: List<Card>, variant: GameVariant, moreCardsToCome: Boolean = true): String? {
        if (hole.isEmpty()) return null
        val short = variant.usesShortDeck
        val all = hole + board
        if (variant.hasBoard && board.isEmpty()) return describeStartingHand(hole)
        val made: String
        val category: HandCategory
        if (variant.mustUseTwoHoleCards) {
            val v = evaluateOmaha(hole, board, short)
            made = v.description
            category = v.category
        } else if (all.size >= 5) {
            val v = evaluate(all, short)
            made = v.description
            category = v.category
        } else {
            val codes = IntArray(all.size) { all[it].code }
            val s = score(codes, codes.size, short)
            category = categoryOf(s, short)
            made = describeScore(category, ranksOf(s))
        }
        if (!moreCardsToCome || category >= HandCategory.STRAIGHT) return made
        val draws = draws(hole, board, variant)
        return if (draws.isEmpty()) made else "$made, ${draws.joinToString(" and ")}"
    }

    // ---------------------------------------------------------------- scoring core

    /**
     * Score of the best five of the first [n] card codes. Works for fewer than five cards
     * too (no straights or flushes then), which ranks a stud player's up cards.
     */
    internal fun score(codes: IntArray, n: Int, shortDeck: Boolean): Int {
        var m1 = 0
        var m2 = 0
        var m3 = 0
        var m4 = 0
        var s0 = 0
        var s1 = 0
        var s2 = 0
        var s3 = 0
        for (i in 0 until n) {
            val c = codes[i]
            val b = 1 shl (c shr 2)
            when (c and 3) {
                0 -> s0 = s0 or b
                1 -> s1 = s1 or b
                2 -> s2 = s2 or b
                else -> s3 = s3 or b
            }
            if (m3 and b != 0) m4 = m4 or b
            else if (m2 and b != 0) m3 = m3 or b
            else if (m1 and b != 0) m2 = m2 or b
            else m1 = m1 or b
        }
        val straights = if (shortDeck) straightTopShort else straightTop
        val flush = when {
            Integer.bitCount(s0) >= 5 -> s0
            Integer.bitCount(s1) >= 5 -> s1
            Integer.bitCount(s2) >= 5 -> s2
            Integer.bitCount(s3) >= 5 -> s3
            else -> 0
        }
        if (flush != 0) {
            val top = straights[flush]
            if (top > 0) return pack(HandCategory.STRAIGHT_FLUSH, shortDeck, top)
        }
        if (m4 != 0) {
            val q = highBit(m4)
            return packTop(HandCategory.FOUR_OF_A_KIND, shortDeck, m1 and (1 shl q).inv(), 1, q + 2)
        }
        var fullHouse = 0
        if (m3 != 0) {
            val t = highBit(m3)
            val rest = m2 and (1 shl t).inv()
            if (rest != 0) fullHouse = pack(HandCategory.FULL_HOUSE, shortDeck, t + 2, highBit(rest) + 2)
        }
        val flushScore = if (flush != 0) packTop(HandCategory.FLUSH, shortDeck, flush, 5) else 0
        if (shortDeck) {
            if (flushScore != 0) return flushScore
            if (fullHouse != 0) return fullHouse
        } else {
            if (fullHouse != 0) return fullHouse
            if (flushScore != 0) return flushScore
        }
        val straight = straights[m1]
        if (straight > 0) return pack(HandCategory.STRAIGHT, shortDeck, straight)
        if (m3 != 0) {
            val t = highBit(m3)
            return packTop(HandCategory.THREE_OF_A_KIND, shortDeck, m1 and (1 shl t).inv(), 2, t + 2)
        }
        if (m2 != 0) {
            val p1 = highBit(m2)
            val others = m2 and (1 shl p1).inv()
            if (others != 0) {
                val p2 = highBit(others)
                val kick = m1 and ((1 shl p1) or (1 shl p2)).inv()
                return packTop(HandCategory.TWO_PAIR, shortDeck, kick, 1, p1 + 2, p2 + 2)
            }
            return packTop(HandCategory.PAIR, shortDeck, m1 and (1 shl p1).inv(), 3, p1 + 2)
        }
        return packTop(HandCategory.HIGH_CARD, shortDeck, m1, 5)
    }

    /** Best Omaha score: exactly two of the first [hn] [hole] codes plus exactly three of [board]. */
    internal fun scoreOmaha(hole: IntArray, hn: Int, board: IntArray, bn: Int, shortDeck: Boolean, scratch: IntArray): Int {
        var best = 0
        forEachOmahaHand(hole, hn, board, bn, shortDeck, scratch) { s -> if (s > best) best = s }
        return best
    }

    private inline fun forEachOmahaHand(
        hole: IntArray, hn: Int, board: IntArray, bn: Int, shortDeck: Boolean, five: IntArray, action: (Int) -> Unit,
    ) {
        for (a in 0 until hn - 1) for (b in a + 1 until hn) {
            five[0] = hole[a]
            five[1] = hole[b]
            for (x in 0 until bn - 2) for (y in x + 1 until bn - 1) for (z in y + 1 until bn) {
                five[2] = board[x]
                five[3] = board[y]
                five[4] = board[z]
                action(score(five, 5, shortDeck))
            }
        }
    }

    private fun highBit(mask: Int): Int = 31 - Integer.numberOfLeadingZeros(mask)

    private fun strength(category: HandCategory, shortDeck: Boolean): Int = when {
        shortDeck && category == HandCategory.FLUSH -> HandCategory.FULL_HOUSE.ordinal
        shortDeck && category == HandCategory.FULL_HOUSE -> HandCategory.FLUSH.ordinal
        category == HandCategory.ROYAL_FLUSH -> HandCategory.STRAIGHT_FLUSH.ordinal
        else -> category.ordinal
    }

    private fun categoryOf(score: Int, shortDeck: Boolean): HandCategory {
        val c = HandCategory.entries[score ushr CATEGORY_SHIFT]
        return when {
            shortDeck && c == HandCategory.FLUSH -> HandCategory.FULL_HOUSE
            shortDeck && c == HandCategory.FULL_HOUSE -> HandCategory.FLUSH
            else -> c
        }
    }

    /** Packs up to five rank values (2..14) after the category, highest nibble first. */
    private fun pack(category: HandCategory, shortDeck: Boolean, vararg ranks: Int): Int {
        var s = strength(category, shortDeck) shl CATEGORY_SHIFT
        for (i in ranks.indices) s = s or (ranks[i] shl (16 - 4 * i))
        return s
    }

    /** Like [pack]: [leading] ranks first, then the top [count] ranks of [mask] as kickers. */
    private fun packTop(category: HandCategory, shortDeck: Boolean, mask: Int, count: Int, vararg leading: Int): Int {
        var s = strength(category, shortDeck) shl CATEGORY_SHIFT
        var slot = 0
        for (r in leading) s = s or (r shl (16 - 4 * slot++))
        var m = mask
        var taken = 0
        while (m != 0 && taken < count) {
            val r = highBit(m)
            s = s or ((r + 2) shl (16 - 4 * slot++))
            m = m and (1 shl r).inv()
            taken++
        }
        return s
    }

    /** The non-zero rank values packed in a score, in order. */
    private fun ranksOf(score: Int): List<Int> =
        (0 until 5).map { (score shr (16 - 4 * it)) and 0xF }.filter { it != 0 }

    // ---------------------------------------------------------------- HandValue construction

    private fun toValue(score: Int, candidates: List<Card>, shortDeck: Boolean): HandValue {
        var category = categoryOf(score, shortDeck)
        val ranks = ranksOf(score)
        val five = bestFive(category, ranks, candidates, shortDeck)
        if (category == HandCategory.STRAIGHT_FLUSH && ranks[0] == 14) category = HandCategory.ROYAL_FLUSH
        return HandValue(category, five, describeScore(category, ranks), score)
    }

    private fun straightRanks(top: Int, shortDeck: Boolean): List<Int> = when {
        top == 5 && !shortDeck -> listOf(5, 4, 3, 2, 14)
        top == 9 && shortDeck -> listOf(9, 8, 7, 6, 14)
        else -> (top downTo top - 4).toList()
    }

    private fun bestFive(category: HandCategory, ranks: List<Int>, candidates: List<Card>, shortDeck: Boolean): List<Card> {
        val pool = candidates.sortedByDescending { it.rank }.toMutableList()
        fun take(rank: Int, suit: Suit? = null): Card {
            val c = pool.first { it.rank.value == rank && (suit == null || it.suit == suit) }
            pool.remove(c)
            return c
        }
        return when (category) {
            HandCategory.STRAIGHT_FLUSH, HandCategory.FLUSH -> {
                val suit = Suit.entries.first { s -> candidates.count { it.suit == s } >= 5 }
                val rs = if (category == HandCategory.FLUSH) ranks else straightRanks(ranks[0], shortDeck)
                rs.map { take(it, suit) }
            }
            HandCategory.STRAIGHT -> straightRanks(ranks[0], shortDeck).map { take(it) }
            HandCategory.FOUR_OF_A_KIND -> List(4) { take(ranks[0]) } + take(ranks[1])
            HandCategory.FULL_HOUSE -> List(3) { take(ranks[0]) } + List(2) { take(ranks[1]) }
            HandCategory.THREE_OF_A_KIND -> List(3) { take(ranks[0]) } + ranks.drop(1).map { take(it) }
            HandCategory.TWO_PAIR -> List(2) { take(ranks[0]) } + List(2) { take(ranks[1]) } + take(ranks[2])
            HandCategory.PAIR -> List(2) { take(ranks[0]) } + ranks.drop(1).map { take(it) }
            else -> ranks.map { take(it) }
        }
    }

    private fun rank(value: Int): Rank = Rank.entries[value - 2]

    private fun describeScore(category: HandCategory, ranks: List<Int>): String {
        val r = ranks.map(::rank)
        return when (category) {
            HandCategory.HIGH_CARD -> "${r[0].label} high"
            HandCategory.PAIR -> "Pair of ${r[0].plural}"
            HandCategory.TWO_PAIR -> "Two Pair, ${r[0].plural} and ${r[1].plural}"
            HandCategory.THREE_OF_A_KIND -> "Three of a Kind, ${r[0].plural}"
            HandCategory.STRAIGHT -> "Straight, ${r[0].label} high"
            HandCategory.FLUSH -> "Flush, ${r[0].label} high"
            HandCategory.FULL_HOUSE -> "Full House, ${r[0].plural} full of ${r[1].plural}"
            HandCategory.FOUR_OF_A_KIND -> "Four of a Kind, ${r[0].plural}"
            HandCategory.STRAIGHT_FLUSH ->
                if (r[0] == Rank.ACE) "Royal Flush" else "Straight Flush, ${r[0].label} high"
            HandCategory.ROYAL_FLUSH -> "Royal Flush"
        }
    }

    // ---------------------------------------------------------------- partial hands

    private fun describeStartingHand(hole: List<Card>): String {
        val sorted = hole.sortedByDescending { it.rank }
        if (sorted.size == 2) {
            val (a, b) = sorted
            return when {
                a.rank == b.rank -> "Pocket ${a.rank.plural}"
                a.suit == b.suit -> "${a.rank.label}-${b.rank.label} suited"
                else -> "${a.rank.label}-${b.rank.label} offsuit"
            }
        }
        val counts = sorted.groupingBy { it.rank }.eachCount()
        val pairs = counts.filterValues { it >= 2 }.keys.sortedDescending()
        val trips = counts.filterValues { it >= 3 }.keys
        val names = when {
            trips.isNotEmpty() -> "Three ${trips.first().plural} in hand"
            pairs.size >= 2 -> "${pairs[0].plural} and ${pairs[1].plural} in hand"
            pairs.size == 1 -> "Pocket ${pairs[0].plural} with " + sorted.filter { it.rank != pairs[0] }.joinToString(" and ") { it.rank.label }
            else -> sorted.joinToString("-") { it.rank.label }
        }
        val suitCounts = sorted.groupingBy { it.suit }.eachCount().values.sortedDescending()
        val suits = when {
            suitCounts[0] == sorted.size -> "all one suit"
            suitCounts.size >= 2 && suitCounts[0] == 2 && suitCounts[1] == 2 -> "double-suited"
            suitCounts[0] >= 2 -> "suited"
            else -> "rainbow"
        }
        return "$names, $suits"
    }

    /** Draw names for a hand that is not yet a straight or better. */
    private fun draws(hole: List<Card>, board: List<Card>, variant: GameVariant): List<String> {
        val all = hole + board
        val out = mutableListOf<String>()
        val bySuit = Suit.entries.associateWith { s ->
            if (variant.mustUseTwoHoleCards) minOf(2, hole.count { it.suit == s }).let { h -> if (h == 0) 0 else h + minOf(3, board.count { it.suit == s }) }
            else all.count { it.suit == s }
        }
        val bestSuit = bySuit.values.max()
        when {
            bestSuit == 4 -> out += "flush draw"
            bestSuit == 3 && all.size <= 3 -> out += "three to a flush"
        }
        val short = variant.usesShortDeck
        // Rank values present, with the ace also playing low (1, or 5 in short deck).
        var mask = 0
        for (c in all) {
            mask = mask or (1 shl c.rank.value)
            if (c.rank == Rank.ACE) mask = mask or (1 shl if (short) 5 else 1)
        }
        val lowest = if (short) 5 else 1
        var openEnded = false
        var gutshot = false
        for (v in lowest..11) {
            val window4 = 0b1111 shl v
            if (mask and window4 == window4 && v - 1 >= lowest && v + 4 <= 14) openEnded = true
            val window5 = 0b11111 shl v
            if (v + 4 <= 14 && Integer.bitCount(mask and window5) == 4) gutshot = true
        }
        when {
            openEnded -> out += "open-ended straight draw"
            gutshot && all.size >= 4 -> out += "straight draw"
        }
        return out
    }
}
