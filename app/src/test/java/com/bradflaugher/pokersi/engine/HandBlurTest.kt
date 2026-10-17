package com.bradflaugher.pokersi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandBlurTest {
    private fun cards(vararg s: String) = s.map(Card::parse)
    private val kings = cards("Kh", "Ks")
    private val m = HandBlur.MARK

    private fun blur(line: String, hole: List<Card> = kings, faceUp: List<Card> = emptyList(), made: String? = "Pocket Kings") =
        HandBlur.blur(line, hole, faceUp, made)

    /** The words of [line] that survived, censor bars dropped. */
    private fun left(line: String) = line.split(' ').filter { it.isNotEmpty() && m !in it }.joinToString(" ")

    @Test
    fun blursCardsRanksNicknamesAndTheMadeHand() {
        assertEquals("Yeehaw, I got", left(blur("Yeehaw, I got pocket kings!")))
        assertEquals("Read 'em and weep:", left(blur("Read 'em and weep: Kh Ks")))
        assertEquals("Howdy, holding the of", left(blur("Howdy, holding the K♥ of")).trim())
        assertEquals("The are coming home", left(blur("The cowboys are coming home")))
        assertEquals("I have the", left(blur("I have the king of hearts")))
        assertEquals("Two partner", left(blur("Two Ks, partner", made = null)))
    }

    @Test
    fun keepsTheLetterCountSoTheBarIsTheWordsWidth() {
        val out = blur("I got kings")
        assertEquals("I got kings".length, out.length)
        assertEquals("I got $m$m$m$m$m", out)
    }

    @Test
    fun leavesWhatIsNotTheSeatsOwn() {
        // Not its ranks, not public cards, and ordinary words that look like cards.
        assertEquals("Nice queens on the board, partner!", blur("Nice queens on the board, partner!"))
        assertEquals("As I said, two chips is nothing.", blur("As I said, two chips is nothing.", hole = cards("As", "2d"), made = "Ace-Two"))
        assertEquals("Ah, the kingdom calls.", blur("Ah, the kingdom calls.", hole = cards("Ah", "Kd"), made = null))
        // A stud up card is out there for all to see.
        assertEquals("My king is showing!", blur("My king is showing!", hole = cards("Kd", "7c", "2h"), faceUp = cards("Kd"), made = null))
    }

    @Test
    fun blursTheMadeHandAndStrengthClaims() {
        assertEquals("Brace yourselves, a", left(blur("Brace yourselves, a flush!", hole = cards("9h", "2h"), made = "Flush, Ace high")))
        assertEquals("I've got a", left(blur("I've got a monster hand", hole = cards("9h", "2h"), made = null)))
        assertEquals("My hand is", left(blur("My hand is very strong", hole = cards("9h", "2h"), made = null)))
        assertEquals("Nice hand, partner", blur("Nice hand, partner", hole = cards("9h", "2h"), made = null))
    }

    @Test
    fun aModelsOwnMarksAreDroppedAndBarsAreSpokenAsBleeps() {
        assertEquals("hi", HandBlur.clean("h${m}i"))
        assertFalse(HandBlur.blurred(blur("${m}Howdy", made = null)))
        assertTrue(HandBlur.blurred(blur("pocket kings")))
        assertEquals("I got bleep bleep", HandBlur.spoken(blur("I got pocket kings")))
    }
}
