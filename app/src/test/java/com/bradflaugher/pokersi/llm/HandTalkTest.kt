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
            "I have eight-seven, partner.",
            "I flopped top pair, yeehaw.",
            "One pair is plenty.",
            "I have a royal flush!",
            "I have As Kd, partner.",
            "Holding 7h, nothing more.",
            "I'm holding As, partner.",
            "I have as kd, partner.",
            "I hold AS KD.",
            "I'm holding KD, partner.",
            "I'm holding an ace, partner.",
            "I've got a king up here.",
            "I drew an ace, yeehaw.",
            "I was dealt a king.",
            "I drew As, partner.",
            "I flopped a set, yeehaw.",
            "Nine four offsuit, yikes.",
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
            "Ah, as I said: I'm in!",
            "As if, partner.",
            "We're all set, let's play.",
            "I have as much grit as anyone.",
            "The king's decree stands!",
            "THE KING'S DECREE STANDS!",
            "I have the king's blessing.",
            "",
        ).forEach { assertFalse(it, HandTalk.revealsHand(it)) }
    }
}
