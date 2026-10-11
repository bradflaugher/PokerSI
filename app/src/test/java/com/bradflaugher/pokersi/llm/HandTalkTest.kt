package com.bradflaugher.pokersi.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandTalkTest {
    @Test
    fun linesThatNameAHandAreGiveaways() {
        listOf(
            "I got me a pair of kings!",
            "Two pair, baby!",
            "Pocket aces, folks.",
            "Read 'em and weep: a flush!",
            "Ace-King suited, I'm all in.",
            "Trips! Can't lose now.",
            "Full house, pay up.",
            "My cards are on fire tonight.",
            "Queen high and feeling lucky.",
            "The ace of spades never lies.",
            "Got a straight, partner.",
            "I have an ace and a king, yeehaw.",
            "A queen with a jack, how sweet.",
        ).forEach { assertTrue(it, HandTalk.revealsHand(it)) }
    }

    @Test
    fun ordinaryTableTalkIsFine() {
        listOf(
            "Yeehaw, saddle up partner!",
            "Arr, this pot be mine.",
            "Keep a straight face, now.",
            "Bow before the royal flush!",
            "High five, everybody!",
            "I'll see your bet and raise you a cookie.",
            "My horse Biscuit says hush.",
            "",
        ).forEach { assertFalse(it, HandTalk.revealsHand(it)) }
    }
}
