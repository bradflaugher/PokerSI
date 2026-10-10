package com.bradflaugher.pokersi.engine

import kotlin.random.Random

/**
 * A shuffled deck: the standard 52 cards, or the 36-card short deck (6 through Ace).
 * Shuffling uses the caller's seeded [Random], so a hand is reproducible from its seed.
 */
class Deck private constructor(val short: Boolean, private val random: Random, order: List<Card>) {
    constructor(short: Boolean = false, random: Random) : this(short, random, fullDeck(short).shuffled(random))

    private val cards: ArrayDeque<Card> = ArrayDeque(order)

    /** Cards left to deal. */
    val remaining: Int get() = cards.size

    /** Deals the top card. Throws [IllegalStateException] when the deck is empty. */
    fun deal(): Card = cards.removeFirstOrNull() ?: throw IllegalStateException("Deck is empty")

    fun deal(count: Int): List<Card> = List(count) { deal() }

    /** Burns the top card face down (no-op when the deck is empty). */
    fun burn() {
        cards.removeFirstOrNull()
    }

    /** Shuffles [returned] cards (a draw game's muck) back in under the remaining cards. */
    fun reshuffleIn(returned: Collection<Card>) {
        cards.addAll(returned.shuffled(random))
    }

    companion object {
        private val STANDARD: List<Card> = Rank.entries.flatMap { r -> Suit.entries.map { s -> Card(r, s) } }
        private val SHORT: List<Card> = STANDARD.filter { it.rank >= Rank.SIX }

        /** Every card in a fresh deck, unshuffled. */
        fun fullDeck(short: Boolean = false): List<Card> = if (short) SHORT else STANDARD

        fun forVariant(variant: GameVariant, random: Random): Deck = Deck(variant.usesShortDeck, random)

        /**
         * A pre-arranged deck (tests, puzzles, replays): [top] comes off first, in order,
         * then the rest of the deck shuffled with [random].
         */
        fun stacked(top: List<Card>, short: Boolean = false, random: Random = Random(0)): Deck {
            require(top.toSet().size == top.size) { "Duplicate cards in a stacked deck" }
            return Deck(short, random, top + (fullDeck(short) - top.toSet()).shuffled(random))
        }
    }
}

/** Dense index 0..51 used by the evaluator's hot paths: rank * 4 + suit. */
internal val Card.code: Int get() = (rank.ordinal shl 2) or suit.ordinal

internal object CardCodes {
    private val BY_CODE: Array<Card> = Array(52) { Card(Rank.entries[it shr 2], Suit.entries[it and 3]) }
    fun card(code: Int): Card = BY_CODE[code]
}
