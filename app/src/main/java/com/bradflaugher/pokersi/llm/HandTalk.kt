package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.Rank
import com.bradflaugher.pokersi.engine.TableTalk

/**
 * Table talk that gives a hand away. The prompt tells the model what it holds
 * in words ("Your hand is Pair of Kings"), and a small model happily says so
 * out loud while it bets, which spoils the betting. A line that names a hand
 * ("two pair", "a flush", "pocket aces", "ace-king", "an ace and a king",
 * "my cards") is held back while it streams and swapped like a repeat.
 */
object HandTalk {
    private val singular = Rank.entries.map { it.label.lowercase() } + "deuce"
    private val plural = Rank.entries.map { it.plural.lowercase() } + "deuces"
    private val suits = listOf("spades", "hearts", "diamonds", "clubs")

    private fun anyOf(words: List<String>) = words.joinToString("|", "(?:", ")")

    private val reveal = Regex(
        listOf(
            anyOf(plural),
            // Any pair: "top pair", "one pair", "a pair of"; a pair of boots is a fair loss.
            "pairs?", "overpair",
            "trips", "three of a kind", "four of a kind", "quads", "full house", "set of", "a set", "my set",
            // "the Royal Flush" (a dinghy, a catchphrase) and "the Holy Flush" (a quest) are
            // persona jokes; "a royal flush" is a hand.
            "(?<!the royal )(?<!holy )flush", "straight(?! face)", "kicker", "high card", "suited", "offsuit", "my cards", "the nuts",
            "${anyOf(singular)} high",
            // "ace-king", "eight-seven offsuit"
            "${anyOf(singular)} ${anyOf(singular)}",
            // "an ace and a king", "a queen with a jack"
            "${anyOf(singular)} (?:and|with) (?:an? )?${anyOf(singular)}",
            "${anyOf(singular)} of ${anyOf(suits)}",
            // One card in words after card words: "im holding an ace", "ive got a king".
            "(?:holding|hold|have|got) (?:an? |the )?${anyOf(singular)}",
        ).joinToString("|", "\\b(?:", ")\\b"),
    )

    /**
     * Cards as the prompt writes them ("Kd", "7h", "Ts"), read off the raw line. An
     * ace alone ("As", "Ah", "Ad") is an ordinary word, so it only counts next to
     * another card ("As Kd") or after card words ("holding As").
     */
    private val card = "(?:[2-9TJQKA]|10)[shdc]"
    private val lowCard = "(?:[2-9TJQK]|10)[shdc]"
    private val cardCodes = Regex(
        listOf(
            // Two in a row as written ("As Kd"), or in any case with a non-ace in them
            // ("as kd", "AS KD"; "Ah, as I said" stays fine).
            "$card[\\s,]+$card", "(?i:$lowCard[\\s,]+$card|$card[\\s,]+$lowCard)",
            // One non-ace as written ("Kd", "7h").
            lowCard,
            // After card words: a non-ace in any case ("hold kd"), an ace with any case of
            // suit ("hold AS"; "have as much" stays fine).
            "(?i:holding|hold|have|got|cards?)[:\\s]+(?:(?i:$lowCard)|A[shdcSHDC])",
        ).joinToString("|", "\\b(?:", ")\\b"),
    )

    /** Whether [text] says what hand (or cards) its speaker holds. */
    fun revealsHand(text: String): Boolean {
        if (cardCodes.containsMatchIn(text)) return true
        // "The king's decree" is not "kings"; "high five" is a celebration, not a hand of fives.
        val words = TableTalk.normalize(text.replace(possessive, "")).replace(highFive, "")
        return reveal.containsMatchIn(words)
    }

    private val highFive = Regex("\\bhigh fives?\\b")
    private val possessive = Regex("['’]s\\b")
}
