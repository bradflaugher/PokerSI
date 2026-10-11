package com.bradflaugher.pokersi.engine

import com.bradflaugher.pokersi.llm.Fixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TableTalkTest {
    @Test
    fun normalizesToLowercaseWords() {
        assertEquals("saddle up partner", TableTalk.normalize("  Saddle up,   PARTNER! "))
        assertEquals("ill see that", TableTalk.normalize("I’ll see that."))
    }

    @Test
    fun exactRepeatsAnywhereAtTheTable() {
        val talk = TableTalk()
        talk.record("Dusty", "Saddle up, partner.")
        assertTrue(talk.isRepeat("saddle up partner!!", "Dusty"))
        // Someone else saying it is a repeat too: no parroting.
        assertTrue(talk.isRepeat("Saddle up, partner.", "Granny"))
        assertFalse(talk.isRepeat("Yeehaw, the river's rising!", "Dusty"))
        assertFalse(talk.isRepeat("", "Dusty"))
    }

    @Test
    fun nearRepeatsByWordOverlap() {
        val talk = TableTalk()
        talk.record("Dusty", "I reckon the cards ain't lookin' my way.")
        assertTrue(talk.isRepeat("Well, I reckon the cards ain't lookin' my way", "Dusty"))
        assertFalse(talk.isRepeat("My horse Biscuit could play better.", "Dusty"))
        // A catchphrase passed as "already said" counts even when nobody said it here.
        assertTrue(talk.isRepeat("Saddle up partner, yeehaw", "Dusty", also = listOf("Saddle up, partner.")))
    }

    @Test
    fun streamingPrefixesThatMayRepeatAreHeldBack() {
        val talk = TableTalk()
        talk.record("Dusty", "Saddle up, partner.")
        assertTrue(talk.mightRepeat("Saddle"))
        assertTrue(talk.mightRepeat("Saddle up, partner"))
        assertTrue(talk.mightRepeat("Saddle up, partner. Yee"))
        // A word or two can't be judged yet: "Howdy, saddle up partner" would be a repeat.
        assertTrue(talk.mightRepeat("Howdy"))
        assertFalse(talk.mightRepeat("Howdy there, the river"))
        assertFalse(talk.mightRepeat("Saddlebags full of"))
        assertTrue(talk.mightRepeat("Arr", also = listOf("Arr, matey!")))
    }

    @Test
    fun streamingNearRepeatsAreHeldBackToo() {
        val talk = TableTalk()
        talk.record("Dusty", "I reckon the cards ain't lookin' my way.")
        // Mostly the same words, not the same start: held all the way, as isRepeat would drop it.
        val nearRepeat = "Well, I reckon the cards ain't lookin' my way"
        for (end in 1..nearRepeat.length) assertTrue(nearRepeat.take(end), talk.mightRepeat(nearRepeat.take(end), speaker = "Dusty"))
        assertTrue(talk.isRepeat(nearRepeat, "Dusty"))
        // A fresh line streams once it has enough words of its own that it can't become one.
        assertTrue("could still end \"i reckon the cards aint lookin way\"", talk.mightRepeat("My horse Biscuit could play", speaker = "Dusty"))
        assertFalse(talk.mightRepeat("My horse Biscuit could play poker better than you", speaker = "Dusty"))
        assertFalse(talk.isRepeat("My horse Biscuit could play poker better than you.", "Dusty"))
        // ...or sooner, when the words it would need no longer fit in the bubble.
        assertFalse(talk.mightRepeat("My horse Biscuit could play", speaker = "Dusty", maxLength = 40))
        // An uninvited catchphrase with a word in front is held too, and a line that
        // only starts like it streams once it clearly isn't it.
        val catchphrase = listOf("Yeehaw, saddle up partner and pay up!")
        assertTrue(talk.mightRepeat("Well yeehaw, saddle up partner and", also = catchphrase, speaker = "Dusty"))
        assertTrue(talk.isRepeat("Well yeehaw, saddle up partner and pay up", "Dusty", also = catchphrase))
        assertFalse(talk.mightRepeat("Yeehaw, the river came in hot tonight", also = catchphrase, speaker = "Dusty"))
    }

    @Test
    fun aLineShownWhileStreamingIsNeverSwappedForARepeat() {
        val talk = TableTalk()
        talk.record("Granny", "Back in my day we bet buttons and liked it.")
        talk.record("Dusty", "I reckon the cards ain't lookin' my way.")
        talk.record("Dusty", "Yeehaw!")
        // Different first words, then mostly someone else's line: once a prefix was
        // shown, the whole line must not turn out a repeat.
        val lines = listOf(
            "Well partner, back in my day we bet buttons and liked it",
            "Howdy folks, I reckon the cards ain't lookin' my way",
            "Shucks, my horse Biscuit says the cards ain't lookin' my way",
            "My horse Biscuit could play poker better than you",
        )
        for (line in lines) {
            val shownEarly = (1..line.length).any { !talk.mightRepeat(line.take(it), speaker = "Dusty", maxLength = 80) }
            if (talk.isRepeat(line, "Dusty")) assertFalse("\"$line\" was shown, then dropped", shownEarly)
        }
        assertTrue(talk.isRepeat(lines[0], "Dusty") && talk.isRepeat(lines[1], "Dusty"))
        assertFalse(talk.isRepeat(lines[3], "Dusty"))
    }

    @Test
    fun remembersOnlyTheLatestLines() {
        val talk = TableTalk(capacity = 3)
        listOf("one", "two", "three", "four").forEach { talk.record("A", it) }
        talk.record("B", "  ")
        assertEquals(listOf("two", "three", "four"), talk.recent(10).map { it.text })
        assertEquals(listOf("three", "four"), talk.saidBy("A", 2))
        assertFalse(talk.saidRecently("one"))
        talk.clear()
        assertTrue(talk.isEmpty)
    }

    @Test
    fun houseBotLinesSkipWhatWasJustSaid() {
        val talk = TableTalk()
        val all = BotLines.lines.getValue(TalkMoment.FOLD)
        all.dropLast(1).forEach { talk.record("Someone", it) }
        repeat(20) { assertEquals(all.last(), BotLines.pickFresh(TalkMoment.FOLD, Random(it), talk)) }
        talk.record("Someone", all.last())
        assertNull(BotLines.pickFresh(TalkMoment.FOLD, Random(1), talk))
    }

    @Test
    fun aChattyHouseBotNeverRepeatsARecentLine() = runBlocking {
        val talk = TableTalk()
        val bot = HouseBot(BotStyle.MANIAC.copy(chattiness = 1.0), seed = 4, iterations = 20, tableTalk = talk)
        repeat(40) {
            val say = bot.decide(Fixtures.flopTopPairCheckedTo).say ?: return@repeat
            assertFalse("repeated \"$say\"", talk.saidRecently(say))
            talk.record("Bot", say)
        }
    }
}
