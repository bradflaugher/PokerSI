package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.DiscardRule
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.LegalActions
import com.bradflaugher.pokersi.engine.SeatView

/** Hand-built decision situations shared by the prompt, grammar and harness tests. */
object Fixtures {
    private fun seat(seat: Int, name: String, chips: Int, bet: Int = 0, folded: Boolean = false, allIn: Boolean = false, dealer: Boolean = false, cards: List<Card?> = listOf(null, null)) =
        SeatView(seat, name, chips, bet, folded, allIn, dealer, cards)

    /** Pre-flop, holding pocket aces, facing a raise. */
    val preflopAces = DecisionContext(
        variant = GameVariant.TEXAS_HOLDEM,
        seat = 0,
        handNumber = 3,
        street = "Pre-flop",
        holeCards = Card.parseList("As Ad"),
        board = emptyList(),
        pot = 90,
        bigBlind = 20,
        seats = listOf(
            seat(0, "Captain Barnacle", 1000, bet = 0),
            seat(1, "Granny Pearl", 940, bet = 60, dealer = true),
            seat(2, "Zorp", 980, bet = 10),
            seat(3, "Coach Flex", 980, bet = 20),
        ),
        history = listOf("Zorp posts small blind 10", "Coach Flex posts big blind 20", "Granny Pearl raises to 60"),
        legal = LegalActions(canFold = true, canCheck = false, toCall = 60, minRaiseTo = 100, maxRaiseTo = 1000),
        madeHand = "Pair of Aces",
    )

    /** Pre-flop trash (7-2 offsuit) facing a big raise. */
    val preflopTrash = preflopAces.copy(
        holeCards = Card.parseList("7c 2d"),
        madeHand = "High card Seven",
        history = listOf("Zorp posts small blind 10", "Coach Flex posts big blind 20", "Granny Pearl raises to 300"),
        pot = 330,
        seats = preflopAces.seats.map { if (it.seat == 1) it.copy(chips = 700, betThisRound = 300) else it },
        legal = LegalActions(canFold = true, canCheck = false, toCall = 300, minRaiseTo = 540, maxRaiseTo = 1000),
    )

    /** Flop, top pair, checked to us. */
    val flopTopPairCheckedTo = DecisionContext(
        variant = GameVariant.TEXAS_HOLDEM,
        seat = 2,
        handNumber = 7,
        street = "Flop",
        holeCards = Card.parseList("Kh Qs"),
        board = Card.parseList("Kd 7c 2h"),
        pot = 120,
        bigBlind = 20,
        seats = listOf(
            seat(0, "Dusty Boots", 820),
            seat(1, "Miss Quill", 0, folded = true),
            seat(2, "Sir Whiskers", 1060, dealer = true),
        ),
        history = listOf("Dusty Boots raises to 60", "Sir Whiskers calls 60", "Flop: Kd 7c 2h", "Dusty Boots checks"),
        legal = LegalActions(canFold = true, canCheck = true, toCall = 0, minRaiseTo = 20, maxRaiseTo = 1060),
        madeHand = "Pair of Kings",
    )

    /** River, nothing, facing a pot-sized bet. */
    val riverAirFacingBet = flopTopPairCheckedTo.copy(
        street = "River",
        holeCards = Card.parseList("9s 8s"),
        board = Card.parseList("Kd 7c 2h Ad 3c"),
        pot = 400,
        seats = listOf(seat(0, "Dusty Boots", 600, bet = 200), seat(1, "Miss Quill", 0, folded = true), seat(2, "Sir Whiskers", 900, dealer = true)),
        history = listOf("Flop: Kd 7c 2h", "Turn: Ad", "River: 3c", "Dusty Boots bets 200"),
        legal = LegalActions(canFold = true, canCheck = false, toCall = 200, minRaiseTo = 400, maxRaiseTo = 900),
        madeHand = "High card Ace",
    )

    /** Turn, a made flush, opponent bets small. */
    val turnFlush = flopTopPairCheckedTo.copy(
        street = "Turn",
        holeCards = Card.parseList("Ah 5h"),
        board = Card.parseList("Kh 7h 2c 9h"),
        pot = 300,
        seats = listOf(seat(0, "Dusty Boots", 700, bet = 50), seat(1, "Miss Quill", 0, folded = true), seat(2, "Sir Whiskers", 900, dealer = true)),
        history = listOf("Flop: Kh 7h 2c", "Dusty Boots bets 80", "Sir Whiskers calls 80", "Turn: 9h", "Dusty Boots bets 50"),
        legal = LegalActions(canFold = true, canCheck = false, toCall = 50, minRaiseTo = 100, maxRaiseTo = 900),
        madeHand = "Flush, Ace high",
    )

    /** Short stack: the only raise is all in. */
    val shortStackAllInOnly = preflopAces.copy(
        seats = preflopAces.seats.map { if (it.seat == 0) it.copy(chips = 80) else it },
        legal = LegalActions(canFold = true, canCheck = false, toCall = 60, minRaiseTo = 80, maxRaiseTo = 80),
    )

    /** Pot-limit Omaha: the max raise is the pot, not all in. */
    val omahaPotLimit = DecisionContext(
        variant = GameVariant.OMAHA,
        seat = 1,
        handNumber = 2,
        street = "Flop",
        holeCards = Card.parseList("Jh Td 9h 8c"),
        board = Card.parseList("Qs 7d 2h"),
        pot = 200,
        bigBlind = 20,
        seats = listOf(seat(0, "Ember", 900, bet = 100, cards = List(4) { null }), seat(1, "Lucky Lou", 1000, cards = List(4) { null })),
        history = listOf("Flop: Qs 7d 2h", "Ember bets 100"),
        legal = LegalActions(canFold = true, canCheck = false, toCall = 100, minRaiseTo = 200, maxRaiseTo = 500),
        madeHand = "Straight, Queen high",
    )

    /** Seven-card stud: opponents show up-cards. */
    val studFourthStreet = DecisionContext(
        variant = GameVariant.SEVEN_CARD_STUD,
        seat = 0,
        handNumber = 5,
        street = "4th street",
        holeCards = Card.parseList("Qc Qd 4s 9h"),
        board = emptyList(),
        pot = 60,
        bigBlind = 20,
        seats = listOf(
            seat(0, "Professor Pip", 500, cards = Card.parseList("Qc Qd 4s 9h").mapIndexed { i, c -> if (i < 2) null else c }),
            seat(1, "Disco Donna", 480, bet = 20, cards = listOf(null, null, Card.parse("Ah"), Card.parse("Ac"))),
        ),
        history = listOf("Disco Donna bets 20"),
        legal = LegalActions(canFold = true, canCheck = false, toCall = 20, minRaiseTo = 40, maxRaiseTo = 40),
        madeHand = "Pair of Queens",
    )

    /** Five-card draw: hold the pair, draw three. */
    val drawPairOfSevens = DecisionContext(
        variant = GameVariant.FIVE_CARD_DRAW,
        seat = 0,
        handNumber = 4,
        street = "Draw",
        holeCards = Card.parseList("Ks 7h 2c 7d 4s"),
        board = emptyList(),
        pot = 80,
        bigBlind = 20,
        seats = listOf(seat(0, "Granny Pearl", 480, cards = List(5) { null }), seat(1, "Gus the Goose", 460, cards = List(5) { null })),
        history = listOf("Gus the Goose bets 20", "Granny Pearl calls 20"),
        legal = LegalActions(canFold = false, canCheck = false, toCall = 0, minRaiseTo = null, maxRaiseTo = null, discard = DiscardRule(0, 3)),
        madeHand = "Pair of Sevens",
    )

    /** Crazy Pineapple: throw exactly one of three. */
    val pineappleDiscard = DecisionContext(
        variant = GameVariant.CRAZY_PINEAPPLE,
        seat = 0,
        handNumber = 9,
        street = "Flop",
        holeCards = Card.parseList("Ah 3c Kd"),
        board = Card.parseList("Ks 8h 3d"),
        pot = 120,
        bigBlind = 20,
        seats = listOf(seat(0, "Rocket Rosa", 900, cards = List(3) { null }), seat(1, "Sleepy Sid", 880, cards = List(3) { null })),
        history = listOf("Flop: Ks 8h 3d", "Sleepy Sid checks", "Rocket Rosa checks"),
        legal = LegalActions(canFold = false, canCheck = false, toCall = 0, minRaiseTo = null, maxRaiseTo = null, discard = DiscardRule(1, 1)),
        madeHand = "Two pair, Kings and Threes",
    )

    val all: Map<String, DecisionContext> = mapOf(
        "01-preflop-aces" to preflopAces,
        "02-preflop-trash" to preflopTrash,
        "03-flop-top-pair" to flopTopPairCheckedTo,
        "04-river-air" to riverAirFacingBet,
        "05-turn-flush" to turnFlush,
        "06-short-stack" to shortStackAllInOnly,
        "07-omaha-pot-limit" to omahaPotLimit,
        "08-stud" to studFourthStreet,
        "09-draw" to drawPairOfSevens,
        "10-pineapple" to pineappleDiscard,
    )
}
