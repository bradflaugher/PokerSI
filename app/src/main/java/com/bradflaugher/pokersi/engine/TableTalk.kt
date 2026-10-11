package com.bradflaugher.pokersi.engine

/**
 * What has been said at one table, by everyone, newest last: the memory that
 * keeps the characters from repeating themselves (or each other). One per
 * table, shared by every seat's agent; a new table starts a new one.
 *
 * Thread-safe: agents read it on their own threads while the table records.
 */
class TableTalk(private val capacity: Int = 40) {
    /** One line of table talk. */
    data class Line(val speaker: String, val text: String)

    private val lines = ArrayDeque<Line>()

    /** Remembers that [speaker] said [text] (blank lines are ignored). */
    fun record(speaker: String, text: String) {
        val line = text.trim()
        if (line.isEmpty()) return
        synchronized(lines) {
            lines.addLast(Line(speaker, line))
            while (lines.size > capacity) lines.removeFirst()
        }
    }

    /** The last [n] lines said by anyone, oldest first. */
    fun recent(n: Int): List<Line> = synchronized(lines) { lines.toList().takeLast(n) }

    /** The last [n] lines [speaker] said, oldest first. */
    fun saidBy(speaker: String, n: Int): List<String> =
        synchronized(lines) { lines.filter { it.speaker == speaker }.takeLast(n).map { it.text } }

    fun clear() = synchronized(lines) { lines.clear() }

    val isEmpty: Boolean get() = synchronized(lines) { lines.isEmpty() }

    /**
     * Whether [text] would repeat something: the same words as anything said at
     * this table (or in [also], e.g. a catchphrase), or mostly the same words as
     * one of [speaker]'s recent lines or the table's latest ones.
     */
    fun isRepeat(text: String, speaker: String, also: Collection<String> = emptyList()): Boolean {
        val words = normalize(text)
        if (words.isEmpty()) return false
        val (all, close) = synchronized(lines) {
            lines.map { it.text } to (lines.filter { it.speaker == speaker }.takeLast(OWN_LINES) + lines.takeLast(TABLE_LINES)).map { it.text }
        }
        val exact = (all + also).map(::normalize)
        if (words in exact) return true
        return (close + also).any { overlap(words, normalize(it)) >= SIMILAR }
    }

    /**
     * Whether a line still being written that starts with [prefix] may turn out
     * to be a repeat by [isRepeat]'s rules, so a streaming speech bubble should
     * hold it back: it is the start of a remembered line (or [also]), a
     * remembered line is the start of it, or it is on its way to sharing
     * [SIMILAR] of its words with one of [speaker]'s recent lines, the table's
     * latest ones or [also] ("Well, I reckon..." after "I reckon..."). Until it
     * has [JUDGE_WORDS] whole words it is held while it could still get there;
     * after that, while [SIMILAR] of its words are that line's. The last word
     * of [prefix] may be cut off mid-word, so it only counts once it can't be
     * the start of a word of the line it is compared with.
     */
    fun mightRepeat(prefix: String, also: Collection<String> = emptyList(), speaker: String? = null): Boolean {
        val start = normalize(prefix)
        if (start.isEmpty()) return false
        val (all, close) = synchronized(lines) {
            lines.map { it.text } to (lines.filter { it.speaker == speaker }.takeLast(OWN_LINES) + lines.takeLast(TABLE_LINES)).map { it.text }
        }
        val samePrefix = (all + also).any { line ->
            val n = normalize(line)
            n.isNotEmpty() && (n.startsWith(start) || start == n || start.startsWith("$n "))
        }
        if (samePrefix) return true
        val words = start.split(' ')
        val whole = words.dropLast(1).toSet()
        val last = words.last()
        return (close + also).any { line ->
            val other = normalize(line).split(' ').filter { it.isNotEmpty() }.toSet()
            if (other.isEmpty()) return@any false
            // The best the finished line can do is add only [other]'s words, so the
            // overlap can still reach |other| / |other + words outside it|.
            val outside = (whole - other) + (if (last in whole || other.any { it.startsWith(last) }) emptySet() else setOf(last))
            val reachable = other.size.toDouble() / (other.size + outside.size) >= SIMILAR
            reachable && (whole.size < JUDGE_WORDS || (whole intersect other).size.toDouble() / whole.size >= SIMILAR)
        }
    }

    /** Whether [text] (normalized) was said by anyone in the last [n] lines. */
    fun saidRecently(text: String, n: Int = capacity): Boolean {
        val words = normalize(text)
        return synchronized(lines) { lines.toList().takeLast(n) }.any { normalize(it.text) == words }
    }

    companion object {
        private const val OWN_LINES = 8
        private const val TABLE_LINES = 12

        /** Whole words a streaming line needs before [mightRepeat] judges it by its own words. */
        const val JUDGE_WORDS = 4

        /** Word overlap (0..1) at or above which two lines count as the same line. */
        const val SIMILAR = 0.6

        private val nonWord = Regex("[^a-z0-9' ]+")
        private val spaces = Regex("\\s+")

        /** Lowercase words only: "Saddle up, partner!" -> "saddle up partner". */
        fun normalize(text: String): String =
            text.lowercase().replace('’', '\'').replace(nonWord, " ").replace("'", "").replace(spaces, " ").trim()

        /** Jaccard overlap of the word sets of two normalized lines. */
        fun overlap(a: String, b: String): Double {
            val x = a.split(' ').filter { it.isNotEmpty() }.toSet()
            val y = b.split(' ').filter { it.isNotEmpty() }.toSet()
            if (x.isEmpty() || y.isEmpty()) return 0.0
            return (x intersect y).size.toDouble() / (x union y).size
        }
    }
}
