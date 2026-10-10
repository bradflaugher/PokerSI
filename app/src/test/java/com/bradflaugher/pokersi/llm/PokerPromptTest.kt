package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerPromptTest {
    private val persona = Personas.handWritten.first()

    private fun user(context: com.bradflaugher.pokersi.engine.DecisionContext) = PokerPrompt.build(context, persona)[1].content

    @Test
    fun systemMessageSetsPersonaAndFormat() {
        val messages = PokerPrompt.build(Fixtures.preflopAces, persona)
        assertEquals(listOf("system", "user"), messages.map { it.role })
        val system = messages[0].content
        assertTrue(system.contains(persona.name))
        assertTrue(system.contains(persona.catchphrase))
        assertTrue(system.contains(persona.styleWords))
        assertTrue(system.contains(""""action""""))
    }

    @Test
    fun bettingPromptHasEverythingTheSeatMaySee() {
        val text = user(Fixtures.preflopAces)
        assertTrue(text.contains(Fixtures.preflopAces.variant.rulesForPrompt))
        assertTrue(text.contains("Pre-flop"))
        assertTrue(text.contains("Your cards: As Ad"))
        assertTrue(text.contains("Pair of Aces (very strong)"))
        assertTrue(text.contains("Pot: 90"))
        assertTrue(text.contains("To call: 60"))
        assertTrue(text.contains("40%"))
        assertTrue(text.contains("You have 1000 chips"))
        assertTrue(text.contains("- Granny Pearl (dealer): 940 chips, bet 60 this round"))
        assertTrue(text.contains("- Granny Pearl raises to 60"))
        assertTrue(text.contains("""- "call" (amount 60)"""))
        assertTrue(text.contains("100 to 1000"))
        assertTrue(text.contains("""Advice: "raise""""))
        // The seat itself is not listed as an opponent.
        assertFalse(text.contains("- Captain Barnacle"))
    }

    @Test
    fun onlyLegalActionsAreListed() {
        val text = user(Fixtures.flopTopPairCheckedTo)
        assertTrue(text.contains("check for free"))
        assertFalse(text.contains(""""fold""""))
        assertFalse(text.contains(""""call""""))
        assertFalse(user(Fixtures.omahaPotLimit).contains(""""allin""""))
    }

    @Test
    fun adviceFollowsHandStrength() {
        assertTrue(user(Fixtures.preflopTrash).contains("""Advice: "fold""""))
        assertTrue(user(Fixtures.riverAirFacingBet).contains("""Advice: "fold""""))
        assertTrue(user(Fixtures.turnFlush).contains("""Advice: "raise""""))
        assertTrue(user(Fixtures.shortStackAllInOnly).contains("""Advice: "allin""""))
        // Bluffers get nudged toward a bluff with weak hands.
        val bluffer = persona.copy(bluffiness = 0.95f)
        assertTrue(PokerPrompt.build(Fixtures.riverAirFacingBet, bluffer)[1].content.contains("""might "raise" instead"""))
    }

    @Test
    fun studShowsOpponentsUpCards() {
        assertTrue(user(Fixtures.studFourthStreet).contains("- Disco Donna: 480 chips, bet 20 this round, showing Ah Ac"))
    }

    @Test
    fun discardPromptNumbersTheCards() {
        val text = user(Fixtures.drawPairOfSevens)
        assertTrue(text.contains("Your cards: 1=Ks 2=7h 3=2c 4=7d 5=4s"))
        assertTrue(text.contains("0 to 3"))
        assertTrue(text.contains("get new ones"))
        assertTrue(text.contains("""Advice: "discard": [1, 3, 5]"""))
        assertTrue(PokerPrompt.build(Fixtures.drawPairOfSevens, persona)[0].content.contains(""""discard""""))

        val pineapple = user(Fixtures.pineappleDiscard)
        assertTrue(pineapple.contains("exactly 1"))
        assertFalse(pineapple.contains("get new ones"))
        assertTrue(pineapple.contains("""Advice: "discard": [1]"""))
    }

    @Test
    fun preflopStrengthWithoutAMadeHand() {
        val ak = Fixtures.preflopAces.copy(holeCards = Card.parseList("Ah Kh"), madeHand = null)
        assertEquals("Ace-King suited" to PokerPrompt.Strength.STRONG, PokerPrompt.strength(ak))
        val junk = ak.copy(holeCards = Card.parseList("9c 3d"))
        assertEquals(PokerPrompt.Strength.WEAK, PokerPrompt.strength(junk).second)
    }

    @Test
    fun longHistoryIsCut() {
        val busy = Fixtures.preflopAces.copy(history = (1..40).map { "event $it" })
        val text = user(busy)
        assertFalse(text.contains("event 30\n"))
        assertTrue(text.contains("event 40"))
    }

    @Test
    fun promptsStaySmall() {
        // Roughly 4 characters per token: stay well under ~900 tokens.
        for ((name, context) in Fixtures.all) {
            val chars = PokerPrompt.build(context, persona).sumOf { it.content.length }
            assertTrue("$name is $chars chars", chars < 3000)
        }
    }
}
