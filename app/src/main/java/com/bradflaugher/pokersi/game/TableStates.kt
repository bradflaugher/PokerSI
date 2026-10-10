package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BlindLevel
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Hand
import com.bradflaugher.pokersi.engine.HandEvent
import com.bradflaugher.pokersi.engine.HandEvaluator
import com.bradflaugher.pokersi.engine.isStud
import com.bradflaugher.pokersi.engine.streetNames

/** Everything the table shows besides the hand itself: profiles, table talk, perks, boss flags. */
data class TableExtras(
    /** Opponent profiles by seat (index 0, the human, is null). */
    val profiles: List<OpponentProfile?> = emptyList(),
    /** Latest table talk by seat. */
    val chatter: Map<Int, String> = emptyMap(),
    val streamingSeat: Int? = null,
    val thinkingSeat: Int? = null,
    /** Tell Detector readings by seat. */
    val confidence: Map<Int, Float> = emptyMap(),
    /** X-Ray Specs: a peeked (seat, card). */
    val xray: Pair<Int, Card>? = null,
    val bossSeat: Int? = null,
    val hideChips: Boolean = false,
    val silent: Boolean = false,
    val brainLabel: String = "",
)

/** Builds [TableUiState] from a [Hand]: shared by [GameController] and the screenshot scenes. */
object TableStates {
    fun fromHand(
        hand: Hand,
        extras: TableExtras,
        phase: TablePhase = if (hand.isFinished) TablePhase.HandOver else TablePhase.Playing,
        humanTurn: HumanTurn? = null,
        selected: Set<Int> = emptySet(),
        /** Show only this many board cards (run-outs are revealed street by street). */
        boardLimit: Int? = null,
        celebrate: Int = 0,
        showBanner: Boolean = true,
    ): TableUiState {
        val snap = hand.snapshot(0)
        val result = hand.result
        val board = boardLimit?.let { snap.board.take(it) } ?: snap.board
        // Highlight the winning five of the main pot at showdown.
        val winningCards: Set<Card> = if (result != null && result.showdown) {
            result.pots.firstOrNull()?.winners.orEmpty().flatMap { result.handValues[it]?.cards.orEmpty() }.toSet()
        } else emptySet()
        val revealHighlights = boardLimit == null || boardLimit >= snap.board.size
        val seats = snap.seats.map { s ->
            val holes = if (s.seat == 0 || (s.cards.any { it == null } && extras.xray?.first == s.seat)) hand.holeCards(s.seat) else null
            val cards = s.cards.mapIndexed { k, c ->
                val xrayHere = c == null && extras.xray?.first == s.seat && holes?.getOrNull(k) == extras.xray.second
                val shown = c ?: if (xrayHere) extras.xray?.second else null
                CardUi(
                    card = shown,
                    publicUp = s.faceUp.getOrElse(k) { false },
                    xray = xrayHere,
                    highlighted = revealHighlights && shown != null && shown in winningCards && s.isWinner,
                )
            }
            SeatUi(
                seat = s.seat,
                name = s.name,
                profile = extras.profiles.getOrNull(s.seat),
                chips = s.chips,
                bet = s.bet,
                folded = s.folded,
                allIn = s.allIn,
                busted = s.sittingOut,
                isDealer = s.isDealer,
                isToAct = s.isToAct,
                cards = cards,
                lastAction = s.lastAction,
                chatter = if (extras.silent && s.seat != 0) null else extras.chatter[s.seat],
                chatterStreaming = extras.streamingSeat == s.seat,
                thinking = extras.thinkingSeat == s.seat,
                isWinner = s.isWinner && revealHighlights,
                shownHand = s.shownHand.takeIf { revealHighlights },
                confidence = extras.confidence[s.seat],
                isBoss = extras.bossSeat == s.seat,
                chipsHidden = extras.hideChips && s.seat != 0,
                net = result?.net?.getOrNull(s.seat),
            )
        }
        val humanLive = !snap.seats[0].folded && !snap.seats[0].sittingOut
        val made = if (humanLive && !hand.isFinished) hand.context(0).madeHand else result?.handValues?.get(0)?.description
        val holes = hand.holeCards(0)
        val category = if (humanLive && holes.size + snap.board.size >= 5) {
            runCatching { HandEvaluator.evaluate(holes, snap.board, hand.variant).category }.getOrNull()
        } else null
        return TableUiState(
            phase = phase,
            variant = hand.variant,
            handNumber = hand.handNumber,
            street = snap.street,
            board = board,
            winningCards = if (revealHighlights) winningCards else emptySet(),
            pot = snap.pot,
            seats = seats,
            humanTurn = humanTurn,
            selected = selected,
            banner = if (showBanner && revealHighlights) banner(hand) else null,
            blinds = BlindLevel(hand.stakes.smallBlind, hand.stakes.bigBlind, hand.stakes.ante),
            madeHand = made,
            madeCategory = category,
            log = hand.history.takeLast(4),
            celebrate = celebrate,
            brainLabel = extras.brainLabel,
            silentOpponents = extras.silent,
        )
    }

    /** The end-of-hand announcement, or null while the hand is in play. */
    fun banner(hand: Hand): BannerUi? {
        val result = hand.result ?: return null
        val names = hand.snapshot(0).seats.map { it.name }
        val humanCollected = result.collected[0] ?: 0
        val winners = result.winners
        return if (humanCollected > 0) {
            val value = result.handValues[0]
            val text = when {
                winners.size > 1 && result.pots.size == 1 -> "SPLIT POT!"
                result.showdown && value != null -> value.category.label.uppercase() + "!"
                else -> "YOU TAKE IT!"
            }
            BannerUi(text, "+${formatChips(humanCollected)} CHIPS", humanWon = true)
        } else {
            val top = result.collected.maxByOrNull { it.value }?.key ?: return null
            val value = result.handValues[top]
            if (result.showdown && value != null) {
                BannerUi("${names[top].uppercase()} WINS", value.description, humanWon = false)
            } else {
                BannerUi("${names[top].uppercase()} TAKES IT", "+${formatChips(result.collected[top] ?: 0)} chips", humanWon = false)
            }
        }
    }

    /** True when the human may still take a Mulligan this hand (before acting on the first street). */
    fun mulliganAllowed(hand: Hand): Boolean =
        !hand.isFinished && !hand.variant.isStud && hand.street == hand.variant.streetNames.first() &&
            hand.events.none { (it is HandEvent.Action && it.seat == 0) || (it is HandEvent.Redealt && it.seat == 0) }
}

/** "1,250" style grouping for chip counts. */
fun formatChips(amount: Int): String = String.format(java.util.Locale.US, "%,d", amount)
