package com.bradflaugher.pokersi.engine

import kotlin.random.Random

/** What a bot just did, for picking a line of table talk. */
enum class TalkMoment { FOLD, CHECK, CALL, BET, RAISE, ALL_IN, BLUFF, MONSTER, DRAW, STAND_PAT, DISCARD }

/** Short, silly, family-friendly table talk for [HouseBot]. */
object BotLines {
    val lines: Map<TalkMoment, List<String>> = mapOf(
        TalkMoment.FOLD to listOf("Nope.", "I'll sit this one out.", "Too rich for me.", "Run along, cards.", "Hard pass.", "Fold like laundry."),
        TalkMoment.CHECK to listOf("Check.", "Tap tap.", "Your move.", "I'm just looking.", "Knock knock."),
        TalkMoment.CALL to listOf("I'll see that.", "Sure, why not.", "Call me maybe.", "Let's see a card.", "I'm curious.", "Fine, fine."),
        TalkMoment.BET to listOf("Feeling lucky.", "Let's make it interesting.", "A little something.", "Chips go brrr.", "Pay to play."),
        TalkMoment.RAISE to listOf("Raise!", "More, more, more!", "Bump it up.", "You sure about that?", "Spicy."),
        TalkMoment.ALL_IN to listOf("All of it. Everything.", "Shove o'clock!", "YOLO, respectfully.", "Ride or die.", "Let's dance."),
        TalkMoment.BLUFF to listOf("Trust me.", "I never bluff. Ever.", "Totally have it.", "Read 'em and weep. Eventually."),
        TalkMoment.MONSTER to listOf("Oh, this is fun.", "Hehehe.", "Don't mind me.", "I like my cards."),
        TalkMoment.DRAW to listOf("Gimme new ones.", "Shuffle up my luck.", "Fresh cards, please."),
        TalkMoment.STAND_PAT to listOf("I'm good.", "Pat. Happy as a clam.", "Wouldn't change a thing."),
        TalkMoment.DISCARD to listOf("Bye-bye, little card.", "You're fired.", "Off the island."),
    )

    fun pick(moment: TalkMoment, random: Random): String = lines.getValue(moment).random(random)
}
