package com.bradflaugher.pokersi.engine

/**
 * SETTINGS > HAND BLUR: blacks out the words of a line of table talk that give
 * the speaker's own hand away, so the table can chat without small models
 * handing their cards to everyone. The giveaway itself stays visible as a
 * censor bar ("I've got ░░░░░ of ░░░░!"): you see that the model blurted,
 * not what. Switch it off to watch the models leak (the debug console always
 * shows the raw reply).
 *
 * What counts: the seat's face-down cards ("Kh", "K♥", "king of hearts"),
 * their ranks ("kings", "K's", any "ace"; plain number words like "two" only
 * as plurals, "sevens"), pocket-pair nicknames ("cowboys"), the words naming
 * the seat's made hand ("pair", "flush", "full house", "trips"...) and a
 * first-person strength claim ("my hand is strong"). Board cards and face-up
 * cards are public and left alone.
 */
object HandBlur {
    /** One blurred character; a run of them is drawn as a censor bar. */
    const val MARK = '░'

    private val markRun = Regex("$MARK+")

    /**
     * [line] with the giveaways for a seat holding [holeCards] (of which
     * [faceUp] are showing) and [madeHand] ("Pair of Kings") blurred, letter
     * for letter. Any [MARK] already in [line] is dropped first.
     */
    fun blur(line: String, holeCards: List<Card>, faceUp: Collection<Card> = emptyList(), madeHand: String? = null): String {
        var out = clean(line)
        val hidden = holeCards.filter { it !in faceUp }
        if (hidden.isEmpty()) return out
        for (pattern in patterns(hidden, madeHand)) {
            out = pattern.replace(out) { m ->
                // Only the group that gives it away, if the pattern marks one.
                val g = m.groups.takeIf { it.size > 1 }?.get(1)
                if (g == null) bar(m.value) else m.value.replaceRange(g.range.first - m.range.first, g.range.last + 1 - m.range.first, bar(g.value))
            }
        }
        return out
    }

    /** [line] without [MARK]s (a model writing its own censor bars). */
    fun clean(line: String): String = line.replace(MARK.toString(), "")

    /** Whether [line] has a blurred part. */
    fun blurred(line: String): Boolean = MARK in line

    /** [line] as plain text (the hand log, TalkBack): each censor bar a "bleep". */
    fun spoken(line: String): String = line.replace(markRun, "bleep")

    private fun bar(text: String): String = text.map { if (it.isWhitespace()) it else MARK }.joinToString("")

    private fun patterns(hidden: List<Card>, madeHand: String?): List<Regex> {
        val words = mutableListOf<String>()
        val ranks = hidden.map { it.rank }.distinct()
        for (card in hidden) {
            // "king of hearts", "7 of clubs".
            words += "(?:${rankWords(card.rank).joinToString("|")}|${card.rank.face}|${card.rank.letter}) of ${card.suit.label}"
        }
        for (rank in ranks) {
            words += rank.plural.lowercase()
            if (rank.value >= Rank.JACK.value) words += rank.label.lowercase()
        }
        hidden.groupBy { it.rank }.filterValues { it.size >= 2 }.keys.forEach { rank -> nicknames[rank]?.let(words::add) }
        madeHand?.lowercase()?.let { made -> categories.filter { (key, _) -> key in made }.forEach { (_, said) -> words += said } }
        val list = mutableListOf<Regex>()
        if (words.isNotEmpty()) list += Regex("(?<![\\w'])(?:${words.joinToString("|")})(?![\\w'])", RegexOption.IGNORE_CASE)
        // Card codes ("Kh", "10d", "K♥") and plural ranks ("Ks", "7's"). Case-sensitive, and not
        // "As"/"Ah"/"Ad", which are words too.
        val plurals = ranks.flatMap { listOf("${it.face}'s", "${it.face}s") }.filter { it !in wordLike }
        val codes = plurals + hidden.flatMap { c ->
            val pretty = listOf("${Regex.escape(c.rank.face)}${c.suit.symbol}")
            val letters = listOf("${c.rank.face}${c.suit.letter}", "${c.rank.letter}${c.suit.letter}").distinct()
                .filter { it !in wordLike }
            pretty + letters
        }
        list += Regex("(?<![\\w'])(?:${codes.joinToString("|")})(?![\\w'])")
        list += strengthClaim
        return list
    }

    private fun rankWords(rank: Rank): List<String> = listOf(rank.label.lowercase(), rank.plural.lowercase())

    private val wordLike = setOf("As", "Ah", "Ad")

    /** Pocket-pair nicknames a model might reach for. */
    private val nicknames = mapOf(
        Rank.ACE to "pocket rockets|bullets",
        Rank.KING to "cowboys",
        Rank.QUEEN to "ladies",
        Rank.JACK to "hooks|fishhooks",
        Rank.EIGHT to "snowmen",
        Rank.SEVEN to "hockey sticks",
        Rank.TWO to "ducks|deuces",
    )

    /** The words for a made hand, by what its description contains (longest first wins nothing: all are added). */
    private val categories = listOf(
        "pair" to "pairs?|pocket pairs?|pockets?",
        "pocket" to "pockets?|pocket pairs?",
        "two pair" to "two pairs?",
        "suited" to "suited",
        "three of a kind" to "three of a kind|trips|a set",
        "straight" to "straights?",
        "flush" to "flush(?:es)?",
        "full house" to "full house|a boat|full boat",
        "four of a kind" to "four of a kind|quads",
        "straight flush" to "straight flush",
        "royal" to "royal(?: flush)?",
    )

    /** "my hand is strong", "I've got a monster hand": the claim is blurred, true or not. */
    private val strengthClaim = Regex(
        "(?<![\\w'])(?:(?:my|this) (?:hand|cards?) (?:is|are|looks?|feels?) (?:so |really )?|(?:i have|i've got|ive got|i got|i'm holding|im holding|holding) (?:a |an |such a )?)" +
            "((?:very strong|strong|good|great|medium|weak|monster|terrible|bad|lousy|awful|garbage|trash)(?: hands?)?)(?![\\w'])",
        RegexOption.IGNORE_CASE,
    )
}
