package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.Rank
import com.bradflaugher.pokersi.engine.TableTalk

/**
 * Table talk that gives a hand away. The prompt tells the model what it holds
 * in words ("Your hand is Pair of Kings"), and a small model happily says so
 * out loud while it bets, which spoils the betting. A line that names a hand
 * ("two pair", "a flush", "pocket aces", "ace-king", "my cards") is held back
 * while it streams and swapped like a repeat.
 */
object HandTalk {
    private val singular = Rank.entries.map { it.label.lowercase() } + "deuce"
    private val plural = Rank.entries.map { it.plural.lowercase() } + "deuces"
    private val faces = listOf("ace", "king", "queen", "jack")
    private val suits = listOf("spades", "hearts", "diamonds", "clubs")

    private fun anyOf(words: List<String>) = words.joinToString("|", "(?:", ")")

    private val reveal = Regex(
        listOf(
            anyOf(plural),
            "two pair", "a pair", "pair of", "pairs", "my pair", "pocket pair",
            "trips", "three of a kind", "four of a kind", "quads", "full house", "set of",
            // "Royal flush" / "Holy flush" are persona jokes (a dinghy, a quest), not a hand.
            "(?<!royal )(?<!holy )flush", "straight(?! face)", "kicker", "high card", "suited", "my cards", "the nuts",
            "${anyOf(singular)} high",
            "${anyOf(faces)} ${anyOf(singular)}",
            "${anyOf(singular)} of ${anyOf(suits)}",
        ).joinToString("|", "\\b(?:", ")\\b"),
    )

    /** Whether [text] says what hand (or cards) its speaker holds. */
    fun revealsHand(text: String): Boolean {
        // "High five" is a celebration, not a hand of fives.
        val words = TableTalk.normalize(text).replace(highFive, "")
        return reveal.containsMatchIn(words)
    }

    private val highFive = Regex("\\bhigh fives?\\b")
}
