package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.DiscardRule
import com.bradflaugher.pokersi.engine.LegalActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmReplyTest {
    private val facingBet = LegalActions(canFold = true, canCheck = false, toCall = 60, minRaiseTo = 100, maxRaiseTo = 1000)
    private val checkable = LegalActions(canFold = true, canCheck = true, toCall = 0, minRaiseTo = 20, maxRaiseTo = 500)
    private val draw = LegalActions(false, false, 0, null, null, discard = DiscardRule(0, 3))

    @Test
    fun parsesGrammarOutput() {
        val reply = LlmReply.parse("""{"action": "raise", "amount": 240, "say": "Arr, pay up!"}""", facingBet)
        assertEquals(Decision.RaiseTo(240), reply.decision)
        assertEquals("Arr, pay up!", reply.say)
        assertEquals(Decision.Call, LlmReply.parse("""{"action": "call", "amount": 60, "say": "Fine."}""", facingBet).decision)
        assertEquals(Decision.Fold, LlmReply.parse("""{"action": "fold", "amount": 0, "say": "Bye."}""", facingBet).decision)
        assertEquals(Decision.RaiseTo(1000), LlmReply.parse("""{"action": "allin", "amount": 1000, "say": "YOLO"}""", facingBet).decision)
    }

    @Test
    fun clampsOutOfRangeAndIllegalChoices() {
        assertEquals(Decision.RaiseTo(1000), LlmReply.parse("""{"action": "raise", "amount": 99999}""", facingBet).decision)
        assertEquals(Decision.RaiseTo(100), LlmReply.parse("""{"action": "raise", "amount": 5}""", facingBet).decision)
        assertEquals(Decision.RaiseTo(100), LlmReply.parse("""{"action": "raise", "amount": -40}""", facingBet).decision)
        // Raise without an amount: the minimum.
        assertEquals(Decision.RaiseTo(100), LlmReply.parse("""{"action": "raise"}""", facingBet).decision)
        // Check when facing a bet becomes a call; fold when checking is free becomes a check.
        assertEquals(Decision.Call, LlmReply.parse("""{"action": "check"}""", facingBet).decision)
        assertEquals(Decision.Check, LlmReply.parse("""{"action": "fold"}""", checkable).decision)
    }

    @Test
    fun toleratesGarbledReplies() {
        // Old field order, extra spaces, single words, prose.
        assertEquals(Decision.Call, LlmReply.parse("""{ "say" : "hmm" , "action":"Call" }""", facingBet).decision)
        assertEquals(Decision.RaiseTo(1000), LlmReply.parse("""{"action": "all-in"}""", facingBet).decision)
        assertEquals(Decision.RaiseTo(300), LlmReply.parse("""```json
            {"action": "bet", "amount": "300"}
            ```""", facingBet).decision)
        assertEquals(Decision.Fold, LlmReply.parse("I think I will fold this one.", facingBet).decision)
        assertNull(LlmReply.parse("¯\\_(ツ)_/¯", facingBet).decision)
        assertNull(LlmReply.parse("", facingBet).decision)
    }

    @Test
    fun talkIsNotReadAsTheAction() {
        val reply = LlmReply.parse("""{"say": "I would never fold!", "action": "call", "amount": 60}""", facingBet)
        assertEquals(Decision.Call, reply.decision)
        // Truncated inside the say: no action yet, so no decision.
        assertNull(LlmReply.parse("""{"say": "I fold to no one, I raise""", facingBet).decision)
    }

    @Test
    fun truncatedReplyKeepsTheDecision() {
        val reply = LlmReply.parse("""{"action": "raise", "amount": 400, "say": "Batten down the hatc""", facingBet)
        assertEquals(Decision.RaiseTo(400), reply.decision)
        assertEquals("Batten down the hatc", reply.say)
    }

    @Test
    fun parsesDiscards() {
        val reply = LlmReply.parse("""{"discard": [1, 3, 5], "say": "Out with the junk."}""", draw, handSize = 5)
        assertEquals(Decision.Discard(setOf(0, 2, 4)), reply.decision)
        // Duplicates and out-of-range positions are dropped, then the rule's max applies.
        assertEquals(Decision.Discard(setOf(1)), LlmReply.parse("""{"discard": [2, 2, 9, 0]}""", draw, handSize = 5).decision)
        assertEquals(Decision.Discard(setOf(0, 1, 2)), LlmReply.parse("""{"discard": [1, 2, 3, 4]}""", draw, handSize = 5).decision)
        assertEquals(Decision.Discard(emptySet()), LlmReply.parse("""{"discard": [], "say": "Pat."}""", draw, 5).decision)
        // Crazy Pineapple must throw exactly one.
        val pineapple = draw.copy(discard = DiscardRule(1, 1))
        assertEquals(Decision.Discard(setOf(0)), LlmReply.parse("""{"discard": []}""", pineapple, 3).decision)
        assertNull(LlmReply.parse("""{"say": "thinking""", draw, 5).decision)
    }

    @Test
    fun sayStreamsFromAPrefix() {
        val full = """{"action": "call", "amount": 60, "say": "Yo ho \"ho\", matey!"}"""
        val seen = (1..full.length).map { LlmReply.sayPrefix(full.substring(0, it)) }
        assertNull(seen.first())
        assertEquals("", LlmReply.sayPrefix("""{"action": "call", "amount": 60, "say": """"))
        assertEquals("Yo ho", LlmReply.sayPrefix("""{"action": "call", "amount": 60, "say": "Yo ho"""))
        // A dangling escape is held back until the next character arrives.
        assertEquals("Yo ho", LlmReply.sayPrefix("""{"action": "call", "amount": 60, "say": "Yo ho \"""))
        assertEquals("Yo ho \"ho\", matey!", seen.last())
        // Each streamed value extends the previous one.
        val nonNull = seen.filterNotNull()
        nonNull.zipWithNext().forEach { (a, b) -> assertTrue("$a -> $b", b.startsWith(a.trimEnd())) }
    }

    @Test
    fun longSayIsTrimmedAtAWord() {
        val long = "word ".repeat(40).trim()
        val say = LlmReply.sayPrefix("""{"action": "check", "amount": 0, "say": "$long"}""")!!
        assertTrue(say.length <= PokerGrammar.SAY_MAX)
        assertTrue(say.endsWith("word…"))
    }
}
