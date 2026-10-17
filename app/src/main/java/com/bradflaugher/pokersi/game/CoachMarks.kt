package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.isDraw

/**
 * A one-time hint at a real table: the first time each situation comes up,
 * a small note points at the control that matters. [text] is what it says.
 */
enum class Coach(val text: String) {
    /** The raise panel is open: slider, steps, shortcuts. */
    Raise("Drag the slider or tap - and + to pick an amount. 1/2 POT, POT and ALL IN are shortcuts. Then tap RAISE."),

    /** A draw: tap cards to hold them. */
    Hold("Tap the cards you want to keep: they say HELD. DRAW swaps the rest for new ones."),

    /** Crazy Pineapple's discard after the flop. */
    Toss("Crazy Pineapple: tap the one card you don't want, then TOSS IT. You play the other two."),

    /** Your first betting decision. */
    YourTurn("Your move! FOLD gives up the hand. CHECK passes. CALL matches the bet. RAISE bets more."),

    /** A hand just ended. */
    HandOver("Hand over! The winning cards glow, and every seat shows what it won or lost."),

    /** The pause button, once there's a quiet moment. */
    Pause("PAUSE (or Back) stops the table: change the speed or quit."),

    /** Poker Tour: picking the next table. */
    ChooseTable("LOW STAKES is the safer climb. HIGH STAKES adds a rival, bigger blinds and a bigger target, for a bigger bonus."),
}

/**
 * Which one-time hint fits what is on screen: pure decisions, no Android, so
 * they are unit-tested. The UI shows at most one at a time, for [HOLD_MS] or
 * until the next touch, and records each as seen the moment it shows.
 */
object CoachMarks {
    /** How long a hint stays up when nobody touches the screen. */
    const val HOLD_MS = 6000L

    /** The pause before a hint appears, so it doesn't land on top of a deal. */
    const val DELAY_MS = 700L

    /** The hints that fit the table right now, most pressing first. [raising]: the raise panel is open. */
    fun forTable(table: TableUiState, raising: Boolean): List<Coach> = buildList {
        val turn = table.humanTurn
        when {
            turn != null && turn.isDiscard -> add(if (table.variant.isDraw) Coach.Hold else Coach.Toss)
            turn != null -> {
                if (raising) add(Coach.Raise)
                add(Coach.YourTurn)
            }
            table.phase == TablePhase.HandOver && table.banner != null -> add(Coach.HandOver)
            table.phase == TablePhase.Playing && table.handNumber >= 2 && table.human?.folded != true -> add(Coach.Pause)
        }
    }

    /** The hints that fit a Poker Tour screen. */
    fun forRun(screen: RunScreen): List<Coach> = if (screen == RunScreen.Choose) listOf(Coach.ChooseTable) else emptyList()

    /** The hint to show next: the first of [wanted] not [seen] yet. */
    fun next(wanted: List<Coach>, seen: Set<Coach>): Coach? = wanted.firstOrNull { it !in seen }

    /**
     * Whether [showing] stays up: only while its situation lasts (a YOUR MOVE
     * hint goes when the turn does), and only one hint is ever up.
     */
    fun keeps(showing: Coach, wanted: List<Coach>): Boolean = showing in wanted
}

