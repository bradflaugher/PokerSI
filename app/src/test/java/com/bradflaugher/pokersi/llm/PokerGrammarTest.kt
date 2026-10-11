package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.DiscardRule
import com.bradflaugher.pokersi.engine.LegalActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerGrammarTest {

    /** Rule name -> body, after checking quotes, brackets and parentheses balance. */
    private fun rules(gbnf: String): Map<String, String> {
        val rules = gbnf.lines().filter { it.isNotBlank() }.associate { line ->
            val (name, body) = line.split("::=", limit = 2).map { it.trim() }
            name to body
        }
        for ((name, body) in rules) {
            var depth = 0
            var inString = false
            var inClass = false
            var i = 0
            while (i < body.length) {
                val c = body[i]
                when {
                    c == '\\' -> i++
                    inString -> if (c == '"') inString = false
                    inClass -> if (c == ']') inClass = false
                    c == '"' -> inString = true
                    c == '[' -> inClass = true
                    c == '(' -> depth++
                    c == ')' -> depth--
                }
                assertTrue("unbalanced ) in $name", depth >= 0)
                i++
            }
            assertFalse("unterminated string in $name", inString)
            assertFalse("unterminated class in $name", inClass)
            assertEquals("unbalanced ( in $name", 0, depth)
        }
        assertTrue("root" in rules)
        // Every identifier used outside strings and classes is defined.
        val stripped = rules.values.joinToString(" ") {
            it.replace(Regex(""""(\\.|[^"\\])*""""), " ").replace(Regex("""\[(\\.|[^\]\\])*]"""), " ")
        }
        for (id in Regex("""[a-z][a-z0-9-]*""").findAll(stripped).map { it.value }) {
            assertTrue("undefined rule $id", id in rules)
        }
        return rules
    }

    private fun actionWords(gbnf: String): Set<String> =
        Regex("""\\"(fold|check|call|raise|allin)\\"""").findAll(rules(gbnf).getValue("action")).map { it.groupValues[1] }.toSet()

    @Test
    fun bettingOffersOnlyLegalActions() {
        val facingBet = PokerGrammar.forContext(Fixtures.preflopAces)
        assertEquals(setOf("fold", "call", "raise", "allin"), actionWords(facingBet))
        assertTrue(facingBet.contains("""\"call\", \"amount\": 60"""))
        assertTrue(facingBet.contains("""\"allin\", \"amount\": 1000"""))

        // Checking is free: no fold, no call.
        assertEquals(setOf("check", "raise", "allin"), actionWords(PokerGrammar.forContext(Fixtures.flopTopPairCheckedTo)))
        // The only raise is all in.
        assertEquals(setOf("fold", "call", "allin"), actionWords(PokerGrammar.forContext(Fixtures.shortStackAllInOnly)))
        // Pot limit: the biggest raise is the pot, not the stack.
        assertEquals(setOf("fold", "call", "raise"), actionWords(PokerGrammar.forContext(Fixtures.omahaPotLimit)))
    }

    @Test
    fun cannotRaiseMeansNoRaiseWords() {
        val context = Fixtures.preflopAces.copy(legal = LegalActions(true, false, 60, null, null))
        assertEquals(setOf("fold", "call"), actionWords(PokerGrammar.forContext(context)))
    }

    @Test
    fun discardGrammarListsValidPositionsAndCounts() {
        val draw = rules(PokerGrammar.discard(5, DiscardRule(0, 3)))
        assertEquals(""""1" | "2" | "3" | "4" | "5"""", draw["pos"])
        assertEquals(""""" | pos | pos ", " pos | pos ", " pos ", " pos""", draw["list"])

        val pineapple = rules(PokerGrammar.forContext(Fixtures.pineappleDiscard))
        assertEquals("pos", pineapple["list"])
        assertEquals(""""1" | "2" | "3"""", pineapple["pos"])
    }

    @Test
    fun sayCannotBreakOutOfItsString() {
        val say = rules(PokerGrammar.forContext(Fixtures.turnFlush)).getValue("sayc")
        assertEquals("""[^"\\\x00-\x1F]""", say)
    }

    @Test
    fun intRangeMatchesExactlyTheRange() {
        val ranges = listOf(0 to 0, 0 to 9, 3 to 7, 5 to 10, 20 to 1060, 100 to 1000, 99 to 101, 123 to 4567, 540 to 1000, 1 to 99999)
        for ((lo, hi) in ranges) {
            val regex = Regex(
                PokerGrammar.rangePatterns(lo, hi).joinToString("|") { p -> p.joinToString("") { "[${it.first}-${it.last}]" } },
            )
            for (n in 0..minOf(hi * 2 + 10, 120_000)) {
                assertEquals("$n in $lo..$hi", n in lo..hi, regex.matches(n.toString()))
            }
            // Never a leading zero, which JSON forbids.
            assertFalse(regex.matches("0$lo"))
        }
    }

    @Test
    fun intRangeIsValidGbnf() {
        assertEquals(""""7"""", PokerGrammar.intRange(7, 7))
        assertEquals("""( [1-9] [0-9] [0-9] | "1" "0" "0" "0" )""", PokerGrammar.intRange(100, 1000))
    }
}
