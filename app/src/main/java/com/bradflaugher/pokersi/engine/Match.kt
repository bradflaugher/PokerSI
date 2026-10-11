package com.bradflaugher.pokersi.engine

import kotlin.math.pow

/** Blinds (and ante) for a stretch of hands. */
data class BlindLevel(val smallBlind: Int, val bigBlind: Int, val ante: Int = 0)

/**
 * Blinds that go up every [handsPerLevel] hands, multiplied by [growth] each
 * level and rounded to tidy numbers. A schedule with [handsPerLevel] of
 * [Int.MAX_VALUE] never goes up.
 */
data class BlindSchedule(val start: BlindLevel, val handsPerLevel: Int = 10, val growth: Double = 1.5) {
    /** The level number (0-based) for 1-based [handNumber]. */
    fun levelIndex(handNumber: Int): Int = if (handsPerLevel == Int.MAX_VALUE) 0 else (handNumber - 1).coerceAtLeast(0) / handsPerLevel

    fun levelFor(handNumber: Int): BlindLevel {
        val level = levelIndex(handNumber)
        if (level == 0) return start
        val factor = growth.pow(level)
        val bb = tidy((start.bigBlind * factor).toLong())
        val sb = if (start.bigBlind == 0) 0 else maxOf(1, (bb.toLong() * start.smallBlind / start.bigBlind).toInt())
        val ante = if (start.ante == 0) 0 else maxOf(1, (bb.toLong() * start.ante / start.bigBlind).toInt())
        return BlindLevel(sb, bb, ante)
    }

    companion object {
        fun fixed(level: BlindLevel) = BlindSchedule(level, Int.MAX_VALUE, 1.0)

        /** Rounds up to a table-friendly number: a multiple of 5 in the top two digits (37 -> 40, 112 -> 150). */
        fun tidy(value: Long): Int {
            if (value < 10) return value.toInt().coerceAtLeast(1)
            var magnitude = 1L
            while (value / magnitude >= 100) magnitude *= 10
            val step = magnitude * 5
            return (((value + step - 1) / step) * step).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}

/**
 * A sequence of hands at one table: rotates the button, sits out busted seats,
 * raises the blinds on [schedule] and carries chip stacks from hand to hand.
 * Seat indices stay fixed for the whole match (a busted seat keeps its index
 * with 0 chips), so the UI can keep everyone in place.
 */
class Match(
    names: List<String>,
    startingChips: List<Int>,
    var variant: GameVariant,
    val schedule: BlindSchedule,
    private val seed: Long,
    button: Int = 0,
    handsPlayed: Int = 0,
) {
    private val _chips = startingChips.toMutableList()
    private val _names = names.toMutableList()

    /** Seat names. A seat keeps its index for the whole match, but [replaceSeat] can seat someone new. */
    val names: List<String> get() = _names

    /** Current stacks, by seat. Includes the current hand's result once it is [settle]d. */
    val chips: List<Int> get() = _chips

    /** Button of the hand in play (or of the last hand). */
    var button: Int = button
        private set

    /** Hands dealt so far. */
    var handsPlayed: Int = handsPlayed
        private set

    var currentHand: Hand? = null
        private set
    private var settled = true

    init {
        require(_names.size == startingChips.size) { "One stack per name" }
        require(_names.size in 2..6) { "A table seats 2 to 6" }
    }

    val activeSeats: List<Int> get() = _chips.indices.filter { _chips[it] > 0 }

    /** True when fewer than two seats have chips. */
    val isOver: Boolean get() = activeSeats.size < 2

    /** The last seat standing once [isOver]. */
    val winner: Int? get() = activeSeats.singleOrNull()

    /** Blinds for the next hand. */
    val nextLevel: BlindLevel get() = schedule.levelFor(handsPlayed + 1)

    /**
     * Deals the next hand (settling the previous one first if needed), moving the
     * button to the next seat with chips. [variant] may change hand to hand.
     */
    fun startNextHand(variant: GameVariant = this.variant): Hand {
        settle()
        check(!isOver) { "The match is over" }
        this.variant = variant
        if (handsPlayed > 0 || _chips[button] == 0) button = nextActive(button)
        handsPlayed++
        val level = schedule.levelFor(handsPlayed)
        val stakes = Stakes.forVariant(variant, level.smallBlind, level.bigBlind, level.ante)
        val hand = Hand(
            variant = variant,
            seats = _names.indices.map { SeatSetup(_names[it], _chips[it]) },
            button = button,
            stakes = stakes,
            seed = mix(seed, handsPlayed),
            handNumber = handsPlayed,
        )
        currentHand = hand
        settled = false
        return hand
    }

    /** Copies a finished hand's final stacks into [chips]. Safe to call more than once. */
    fun settle() {
        val hand = currentHand ?: return
        if (settled) return
        val result = hand.result ?: return
        result.finalChips.forEachIndexed { i, c -> _chips[i] = c }
        settled = true
    }

    /**
     * Between hands: a new player takes [seat] (busted, usually) with [chips].
     * The next hand deals them in under [name].
     */
    fun replaceSeat(seat: Int, name: String, chips: Int) {
        settle()
        check(currentHand?.isFinished != false) { "A hand is in play" }
        require(chips > 0) { "A new player needs chips" }
        _names[seat] = name
        _chips[seat] = chips
    }

    /** Adds [delta] chips to [seat] between hands (rebuys and the like). Never below 0. */
    fun adjustChips(seat: Int, delta: Int) {
        settle()
        _chips[seat] = (_chips[seat] + delta).coerceAtLeast(0)
    }

    private fun nextActive(from: Int): Int {
        for (k in 1.._chips.size) {
            val i = (from + k) % _chips.size
            if (_chips[i] > 0) return i
        }
        return from
    }

    companion object {
        /** A well-mixed per-hand seed (SplitMix64 finalizer). */
        fun mix(seed: Long, salt: Int): Long {
            var z = seed + salt * -0x61c8864680b583ebL
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
