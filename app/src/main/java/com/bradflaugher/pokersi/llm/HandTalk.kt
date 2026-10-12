package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Rank
import com.bradflaugher.pokersi.engine.Suit
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

    /** Words a card follows when a player names what they hold or drew. */
    private val cardWords = anyOf(listOf("holding", "hold", "have", "got", "drew", "draw", "dealt", "caught"))

    private val reveal = Regex(
        listOf(
            anyOf(plural),
            // Any pair: "top pair", "one pair", "a pair of"; a pair of boots is a fair loss.
            "pairs?", "overpair",
            "trips", "three of a kind", "four of a kind", "quads", "full house", "set of", "a set", "my set",
            // "the Royal Flush" (a dinghy, a catchphrase) and "the Holy Flush" (a quest) are
            // persona jokes; "a royal flush" is a hand.
            "(?<!the royal )(?<!holy )flush", "high card", "offsuit", "my cards", "the nuts",
            // Everyday words only count in poker phrasing: "a straight" (not "ride straight"),
            // "my kicker" (not "here's the kicker"); "suited" comes with its two ranks.
            "(?:a|my) straight(?! face)", "straight draw", "(?:my|good|big|weak|${anyOf(singular)}) kicker",
            "${anyOf(singular)} high",
            // "ace-king", "eight-seven offsuit"
            "${anyOf(singular)} ${anyOf(singular)}",
            // "an ace and a king", "a queen with a jack"
            "${anyOf(singular)} (?:and|with) (?:an? )?${anyOf(singular)}",
            "${anyOf(singular)} of ${anyOf(suits)}",
            // One card in words after card words: "im holding an ace", "i drew a king".
            "$cardWords (?:me )?(?:an? |the )?${anyOf(singular)}",
            // "my ace looks good": only ranks that aren't everyday words ("my nine cats", "my
            // ten chips", "my queen" as a royal says it are fine).
            "my (?:ace|jack|deuce)",
        ).joinToString("|", "\\b(?:", ")\\b"),
    )

    /**
     * Cards as the prompt writes them ("Kd", "7h", "Ts"), read off the raw line. An
     * ace alone ("As", "Ah", "Ad") is an ordinary word, so it only counts next to
     * another card ("As Kd") or after card words ("holding As", "drew As").
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
            "(?i:$cardWords|cards?)[:\\s]+(?:(?i:$lowCard)|A[shdcSHDC])",
        ).joinToString("|", "\\b(?:", ")\\b"),
    )

    private val oneCode = Regex("(?i)(10|[2-9tjqka])([shdc])")

    /**
     * Whether [text] says what hand (or cards) its speaker holds. Card codes for
     * [seen] cards (the board, others' face-up cards) are fair to talk about: "that
     * Kd on the board".
     */
    fun revealsHand(text: String, seen: Collection<Card> = emptyList()): Boolean {
        val named = cardCodes.findAll(text).flatMap { oneCode.findAll(it.value) }.map(::parse)
        if (named.any { it !in seen }) return true
        // "The king's decree" names no card: a royal possessive becomes a plain word ("my
        // ace's looking good" still names one), and other possessives lose their 's. "High
        // five" is a celebration, not a hand of fives.
        // Seen cards spelled out are fair too: "the king of diamonds on the board".
        val unseen = seen.fold(text) { line, c -> line.replace(Regex("\\b${c.rank.label} of ${c.suit.label}\\b", RegexOption.IGNORE_CASE), "that card") }
        val plain = unseen.replace(rankPossessive, "the someone").replace(possessive, "")
        val words = TableTalk.normalize(plain).replace(highFive, "")
        return reveal.containsMatchIn(words)
    }

    private val highFive = Regex("\\bhigh fives?\\b")
    // Only royals own things ("the king's decree", "the queen's guard"); "the ace's in my
    // hand" is "the ace is".
    private val rankPossessive = Regex("\\bthe (?:king|queen)['’]s\\b", RegexOption.IGNORE_CASE)

    private fun parse(code: MatchResult): Card {
        val (rank, suit) = code.destructured
        return Card(Rank.fromLetter(rank[0]), Suit.entries.first { it.letter == suit[0].lowercaseChar() })
    }

    // A rank keeps its 's ("ace's" reads as "aces"), so it still names a card.
    private val possessive = Regex("(?<!\\b${anyOf(singular)})['’]s\\b", RegexOption.IGNORE_CASE)
}
