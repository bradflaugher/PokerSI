package com.bradflaugher.pokersi.engine

enum class BettingStructure { NO_LIMIT, POT_LIMIT, FIXED_LIMIT }

/**
 * Every game on the machine. The engine (`engine/Hand.kt`) reads these
 * fields to run the hand; the UI shows [displayName], [tagline] and
 * [howToPlay]; the LLM prompt includes [rulesForPrompt].
 */
enum class GameVariant(
    val displayName: String,
    val tagline: String,
    val structure: BettingStructure,
    /** Cards dealt to each player at the start (Pineapple deals 3 and discards 1). */
    val holeCards: Int,
    val howToPlay: String,
    val rulesForPrompt: String,
) {
    TEXAS_HOLDEM(
        "Texas Hold'em",
        "The classic. Two cards, five on the board.",
        BettingStructure.NO_LIMIT,
        2,
        "You get 2 cards. 5 shared cards come out: the flop (3), the turn and the river. Make the best 5-card hand from any of your 2 and the 5 on the board. No limit: bet anything up to your stack.",
        "No-limit Texas Hold'em. Each player has 2 private hole cards; up to 5 community cards. Best 5-card hand from any combination of hole and board cards wins.",
    ),
    OMAHA(
        "Pot-Limit Omaha",
        "Four cards. Use exactly two. Chaos.",
        BettingStructure.POT_LIMIT,
        4,
        "You get 4 cards and must use exactly 2 of them with exactly 3 from the board. Bets are capped at the size of the pot. Big hands happen a lot here.",
        "Pot-limit Omaha. Each player has 4 hole cards and must use EXACTLY 2 of them plus EXACTLY 3 community cards. Max raise is the size of the pot.",
    ),
    FIVE_CARD_DRAW(
        "Five-Card Draw",
        "Video poker's grandpa. Hold, draw, bluff.",
        BettingStructure.FIXED_LIMIT,
        5,
        "You get 5 cards. Bet, then tap the cards you want to HOLD and draw replacements for the rest (up to 3). Bet again, then show down. Fixed limit: bets come in set sizes.",
        "Fixed-limit five-card draw. Each player has 5 private cards, one betting round, one draw (discard 0-3 cards and get replacements), then a final betting round.",
    ),
    SEVEN_CARD_STUD(
        "Seven-Card Stud",
        "No board. Some cards up, some down.",
        BettingStructure.FIXED_LIMIT,
        3,
        "Everyone antes. You get 2 down and 1 up, then three more up cards and a final down card, with betting on every street. Best 5 of your 7 cards wins. Watch what the others are showing.",
        "Fixed-limit seven-card stud. Each player gets 7 cards (3 face down, 4 face up), no community cards. Best 5 of 7 wins. Players can see each other's face-up cards.",
    ),
    SHORT_DECK(
        "Short Deck",
        "Sixes and up. Flush beats a full house.",
        BettingStructure.NO_LIMIT,
        2,
        "Hold'em with the 2s through 5s removed (36 cards). A flush beats a full house, and A-6-7-8-9 is a straight. Big hands, big pots.",
        "No-limit Short Deck Hold'em (6+). The deck has only 6 through Ace. A flush beats a full house. A-6-7-8-9 is the lowest straight. Otherwise plays like Texas Hold'em.",
    ),
    CRAZY_PINEAPPLE(
        "Crazy Pineapple",
        "Three cards, toss one after the flop.",
        BettingStructure.NO_LIMIT,
        3,
        "Hold'em where you get 3 cards. After the flop betting, throw one away and play on with 2. Starting hands look great; see if they stay that way.",
        "No-limit Crazy Pineapple. Like Texas Hold'em, but each player gets 3 hole cards and must discard 1 after the flop betting round.",
    ),
}
