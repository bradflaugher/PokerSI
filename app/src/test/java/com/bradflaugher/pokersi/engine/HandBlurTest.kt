package com.bradflaugher.pokersi.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandBlurTest {
    private fun cards(vararg s: String) = s.map(Card::parse)
    private val kings = cards("Kh", "Ks")
    private val m = HandBlur.MARK

    private fun blur(line: String, hole: List<Card> = kings, faceUp: List<Card> = emptyList(), made: String? = "Pocket Kings", board: List<Card> = emptyList()) =
        HandBlur.blur(line, hole, faceUp, made, board = board)

    /** The words of [line] that survived, censor bars dropped. */
    private fun left(line: String) = line.split(' ').filter { it.isNotEmpty() && m !in it }.joinToString(" ")

    @Test
    fun blursCardsRanksNicknamesAndTheMadeHand() {
        assertEquals("Yeehaw, I got", left(blur("Yeehaw, I got pocket kings!")))
        assertEquals("Read 'em and weep:", left(blur("Read 'em and weep: Kh Ks")))
        assertEquals("Howdy, holding the of", left(blur("Howdy, holding the K♥ of")).trim())
        assertEquals("The are coming home", left(blur("The cowboys are coming home")))
        assertEquals("I have", left(blur("I have T♥", hole = cards("Th", "2c"), made = null)))
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
    fun blursFeelingsInThePromptsStrengthWordsAndEveryStartingHandKind() {
        val nothing = cards("9h", "2c")
        assertEquals("I feel", left(blur("I feel strong", hole = nothing, made = null)))
        assertEquals("I'm feeling", left(blur("I'm feeling very strong", hole = nothing, made = null)))
        assertEquals("I feel lucky", blur("I feel lucky", hole = nothing, made = null))
        assertEquals("These are", left(blur("These are offsuit", hole = cards("Ah", "Kd"), made = "Ace-King offsuit")))
        assertEquals("I've only got", left(blur("I've only got high card", hole = nothing, made = "Nine high")))
    }

    @Test
    fun whatTheBoardMakesByItselfIsPublic() {
        val board = cards("9s", "9d", "4c")
        // A paired board: "pair" says nothing private; the seat's own ranks still do.
        assertEquals("That pair on the board!", blur("That pair on the board!", hole = cards("Kh", "2c"), made = "Pair of Nines", board = board))
        // Short Deck: A-6-7-8-9 on the board is a straight there, and public.
        val shortBoard = cards("As", "6d", "7c", "8h", "9s")
        assertEquals("Straight on the board!", HandBlur.blur("Straight on the board!", cards("Kh", "2c"), madeHand = "Straight, Nine high", board = shortBoard, shortDeck = true))
        // Two pair with a hole card in it is still the seat's own news.
        assertEquals("I've got", left(blur("I've got two pair", hole = cards("Kh", "Kc"), made = "Two Pair, Kings and Nines", board = board)))
    }

    @Test
    fun aStreamingLineHoldsBackWhatMayYetGiveTheHandAway() {
        val sevens = cards("7h", "2c")
        fun stream(line: String) = HandBlur.blur(line, sevens, emptyList(), "Seven high", streaming = true)
        assertEquals("I hold", stream("I hold seven"))
        assertEquals("I hold", stream("I hold seven of"))
        assertEquals("I hold", left(stream("I hold seven of hearts")))
        assertEquals("Lucky seven, partner", stream("Lucky seven, partner"))
        assertEquals("I have", stream("I have three of a"))
        // Not a rank this seat holds: shown as it comes.
        assertEquals("I see a king", stream("I see a king"))
        // Finished, the line keeps a plain singular number word.
        assertEquals("Lucky seven", HandBlur.blur("Lucky seven", sevens, emptyList(), "Seven high"))
    }

    @Test
    fun aModelsOwnMarksAreDroppedAndBarsAreSpokenAsBleeps() {
        assertEquals("hi", HandBlur.clean("h${m}i"))
        assertFalse(HandBlur.blurred(blur("${m}Howdy", made = null)))
        assertTrue(HandBlur.blurred(blur("pocket kings")))
        assertEquals("I got bleep bleep", HandBlur.spoken(blur("I got pocket kings")))
    }
}
