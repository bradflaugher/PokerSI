package com.bradflaugher.pokersi.engine

/** Card basics. Pure Kotlin: nothing in `engine/` may import android.*. */
enum class Suit(val symbol: Char, val letter: Char, val label: String) {
    CLUBS('♣', 'c', "clubs"),
    DIAMONDS('♦', 'd', "diamonds"),
    HEARTS('♥', 'h', "hearts"),
    SPADES('♠', 's', "spades");

    val isRed: Boolean get() = this == DIAMONDS || this == HEARTS
}

enum class Rank(val value: Int, val letter: Char, val label: String, val plural: String) {
    TWO(2, '2', "Two", "Twos"),
    THREE(3, '3', "Three", "Threes"),
    FOUR(4, '4', "Four", "Fours"),
    FIVE(5, '5', "Five", "Fives"),
    SIX(6, '6', "Six", "Sixes"),
    SEVEN(7, '7', "Seven", "Sevens"),
    EIGHT(8, '8', "Eight", "Eights"),
    NINE(9, '9', "Nine", "Nines"),
    TEN(10, 'T', "Ten", "Tens"),
    JACK(11, 'J', "Jack", "Jacks"),
    QUEEN(12, 'Q', "Queen", "Queens"),
    KING(13, 'K', "King", "Kings"),
    ACE(14, 'A', "Ace", "Aces");

    /** "10" rather than "T" for display on a card face. */
    val face: String get() = if (this == TEN) "10" else letter.toString()

    companion object {
        fun fromLetter(c: Char): Rank = entries.first { it.letter == c.uppercaseChar() || (c == '1' && it == TEN) }
    }
}

data class Card(val rank: Rank, val suit: Suit) {
    /** Compact form used in prompts and tests: "As", "Td", "7h". */
    override fun toString(): String = "${rank.letter}${suit.letter}"

    /** "A♠", "10♦". */
    val pretty: String get() = "${rank.face}${suit.symbol}"

    companion object {
        /** Parses "As" or "Td" (also "10d"). */
        fun parse(s: String): Card {
            val t = s.trim()
            val suit = Suit.entries.first { it.letter == t.last().lowercaseChar() }
            val rank = if (t.length == 3) Rank.TEN else Rank.fromLetter(t[0])
            return Card(rank, suit)
        }

        /** Parses "As Kd 7h" into a list. */
        fun parseList(s: String): List<Card> = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::parse)
    }
}
