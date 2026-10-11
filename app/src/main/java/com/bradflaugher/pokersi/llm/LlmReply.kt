package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.LegalActions

/**
 * What the model said, read leniently: the grammar should produce clean
 * JSON, but a truncated, unconstrained or garbled reply still yields
 * whatever it clearly meant. [decision] is null when nothing usable was
 * found (the caller then asks the fallback agent).
 */
data class LlmReply(val say: String?, val decision: Decision?) {
    companion object {
        private val sayStart = Regex(""""say"\s*:\s*"""")
        private val actionField = Regex(""""action"\s*:\s*"?([A-Za-z_ -]+)""")
        private val amountField = Regex(""""amount"\s*:\s*"?(-?\d+)""")
        private val discardField = Regex(""""discard"\s*:\s*\[([^\]]*)\]?""")
        private val bareAction = Regex("""\b(fold|check|call|raise|bet|all[ _-]?in|shove)\b""", RegexOption.IGNORE_CASE)
        private val separators = Regex("[ _-]")
        private val number = Regex("""\d+""")

        /**
         * Parses a whole reply and clamps the decision to [legal]. Discard
         * positions are 1-based; ones beyond [handSize] are dropped.
         */
        fun parse(text: String, legal: LegalActions, handSize: Int = Int.MAX_VALUE): LlmReply {
            val say = sayPrefix(text)?.takeIf { it.isNotEmpty() }
            // Ignore the talk when looking for the decision: "I fold!" is not a fold.
            val rest = withoutSay(text)
            val decision = if (legal.discard != null) parseDiscard(rest, handSize) else parseBet(rest, legal)
            return LlmReply(say, decision?.let(legal::coerce))
        }

        /**
         * The `say` text so far in a reply that may still be streaming, or
         * null before the field has started. Unescapes `\"` and friends,
         * stops at the closing quote and trims to [maxLength] characters at
         * a word boundary.
         */
        fun sayPrefix(partial: String, maxLength: Int = PokerGrammar.SAY_MAX): String? {
            val match = sayStart.find(partial) ?: return null
            val out = StringBuilder()
            var i = match.range.last + 1
            while (i < partial.length) {
                val c = partial[i]
                if (c == '"') break
                if (c == '\\') {
                    if (i + 1 >= partial.length) break
                    val e = partial[++i]
                    out.append(if (e == 'n' || e == 't' || e == 'r') ' ' else e)
                } else {
                    out.append(c)
                }
                i++
            }
            return shorten(out.toString().trim(), maxLength)
        }

        private fun shorten(s: String, max: Int): String {
            if (s.length <= max) return s
            val cut = s.lastIndexOf(' ', max - 1).takeIf { it > max / 2 } ?: (max - 1)
            return s.substring(0, cut).trimEnd(' ', ',', ';', ':', '-') + "…"
        }

        private fun parseBet(text: String, legal: LegalActions): Decision? {
            val word = actionField.find(text)?.groupValues?.get(1)
                ?: bareAction.find(text)?.groupValues?.get(1)
                ?: return null
            val amount = amountField.find(text)?.groupValues?.get(1)?.toIntOrNull()
            return when (word.trim().lowercase().replace(separators, "")) {
                "fold" -> Decision.Fold
                "check" -> Decision.Check
                "call" -> Decision.Call
                "raise", "bet" -> Decision.RaiseTo(amount ?: legal.minRaiseTo ?: 0)
                "allin", "shove" -> Decision.AllIn
                else -> null
            }
        }

        private fun parseDiscard(text: String, handSize: Int): Decision? {
            val list = discardField.find(text)?.groupValues?.get(1) ?: return null
            val positions = number.findAll(list).mapNotNull { it.value.toIntOrNull() }
            return Decision.Discard(positions.map { it - 1 }.filter { it in 0 until handSize }.toSortedSet())
        }

        /** [text] with the say string's contents cut out. */
        private fun withoutSay(text: String): String {
            val match = sayStart.find(text) ?: return text
            var i = match.range.last + 1
            while (i < text.length) {
                when (text[i]) {
                    '\\' -> i++
                    '"' -> return text.substring(0, match.range.last + 1) + text.substring(i)
                }
                i++
            }
            return text.substring(0, match.range.last + 1)
        }
    }
}
