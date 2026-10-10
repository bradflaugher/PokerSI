package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BlindLevel
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.LegalActions
import com.bradflaugher.pokersi.run.JokerTrigger
import com.bradflaugher.pokersi.run.RunState
import com.bradflaugher.pokersi.run.TableReward

/** One card in front of a seat, as the human sees it. */
data class CardUi(
    /** Null when face down to the human. */
    val card: Card?,
    /** Stud: dealt face up for everyone. */
    val publicUp: Boolean = false,
    /** Seen through X-Ray Specs (the opponent doesn't know). */
    val xray: Boolean = false,
    /** Part of the winning five. */
    val highlighted: Boolean = false,
)

/** One seat as the table screen draws it. Seat 0 is always the human. */
data class SeatUi(
    val seat: Int,
    val name: String,
    /** Null for the human. */
    val profile: OpponentProfile?,
    val chips: Int,
    /** Chips in front of the seat this betting round. */
    val bet: Int,
    val folded: Boolean,
    val allIn: Boolean,
    /** Out of chips: not dealt in. */
    val busted: Boolean,
    val isDealer: Boolean,
    val isToAct: Boolean,
    val cards: List<CardUi>,
    val lastAction: String? = null,
    /** Table talk to show in the speech bubble (streams in while [chatterStreaming]). */
    val chatter: String? = null,
    val chatterStreaming: Boolean = false,
    /** The seat's agent is deciding. */
    val thinking: Boolean = false,
    val isWinner: Boolean = false,
    /** The hand shown at showdown ("Full House, Kings full of Sevens"). */
    val shownHand: String? = null,
    /** Tell Detector: how good the opponent feels, 0..1. */
    val confidence: Float? = null,
    val isBoss: Boolean = false,
    /** The Mime hides opponents' chip counts. */
    val chipsHidden: Boolean = false,
    /** Chips won (positive) or lost this hand, set when the hand is over. */
    val net: Int? = null,
)

/** The human's decision, when it is their turn. */
data class HumanTurn(
    val legal: LegalActions,
    val pot: Int,
    val bigBlind: Int,
    /** Chips already in front of the human this round (raise totals include them). */
    val betThisRound: Int,
    val stack: Int,
    val canMulligan: Boolean = false,
) {
    val isDiscard: Boolean get() = legal.discard != null
}

/** The big announcement at the end of a hand. */
data class BannerUi(val text: String, val subtitle: String?, val humanWon: Boolean)

enum class TablePhase {
    /** Before the first hand. */
    Idle,

    /** A hand is in play. */
    Playing,

    /** The hand is over; waiting for NEXT HAND. */
    HandOver,

    /** Quick game: the human is out of chips (REBUY). */
    Busted,

    /** Quick game: every opponent is out of chips. */
    TableWon,

    /** Run mode: the table is decided; the run screens take over. */
    RunTableDone,
}

data class TableUiState(
    val phase: TablePhase = TablePhase.Idle,
    val variant: GameVariant = GameVariant.TEXAS_HOLDEM,
    val handNumber: Int = 0,
    val street: String = "",
    val board: List<Card> = emptyList(),
    /** The winning five at showdown (board cards in it glow). */
    val winningCards: Set<Card> = emptySet(),
    val pot: Int = 0,
    val seats: List<SeatUi> = emptyList(),
    val humanTurn: HumanTurn? = null,
    /** Draw games: the HELD cards. Pineapple: the card picked to toss. */
    val selected: Set<Int> = emptySet(),
    val banner: BannerUi? = null,
    val blinds: BlindLevel = BlindLevel(0, 0),
    /** The human's best hand right now in words, while live. */
    val madeHand: String? = null,
    /** ...and its category, once there are five cards to make one. */
    val madeCategory: HandCategory? = null,
    /** Last few public history lines. */
    val log: List<String> = emptyList(),
    /** Bumps when the human wins a pot (confetti, shake). */
    val celebrate: Int = 0,
    val brainLabel: String = "",
    /** Opponents' speech bubbles are dropped (The Mime). */
    val silentOpponents: Boolean = false,
) {
    val human: SeatUi? get() = seats.firstOrNull { it.seat == 0 }
    val opponents: List<SeatUi> get() = seats.filter { it.seat != 0 }
}

/** The Balatro-style payout after a run hand: Joker triggers pop one by one. */
data class PayoutUi(
    val triggers: List<JokerTrigger>,
    /** How many triggers have popped so far (the last one is "hot"). */
    val shown: Int,
    /** Coins counted up so far. */
    val coins: Int,
)

enum class RunScreen {
    /** The table intro card ("ANTE 2 · THE WALL"). */
    Intro,

    /** Playing the table. */
    Table,

    /** The table was cleared: the reward breakdown. */
    Cleared,
    Shop,

    /** Beat the eighth ante's boss. */
    Victory,
    GameOver,
}

data class RunUiState(
    val state: RunState,
    val screen: RunScreen,
    val payout: PayoutUi? = null,
    val reward: TableReward? = null,
    /** Opponent profiles of the current table, by seat (index 0 unused). */
    val opponents: List<OpponentProfile?> = emptyList(),
)
