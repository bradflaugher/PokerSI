package com.bradflaugher.pokersi.ui.screens

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.bradflaugher.pokersi.ui.models.ModelsContent
import com.bradflaugher.pokersi.ui.models.ModelsScenes

import androidx.compose.runtime.Composable
import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Deck
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.Hand
import com.bradflaugher.pokersi.engine.SeatSetup
import com.bradflaugher.pokersi.engine.Stakes
import com.bradflaugher.pokersi.engine.hasBoard
import com.bradflaugher.pokersi.engine.usesShortDeck
import com.bradflaugher.pokersi.game.Coach
import com.bradflaugher.pokersi.game.GameController
import com.bradflaugher.pokersi.game.HumanTurn
import com.bradflaugher.pokersi.game.OpponentProfile
import com.bradflaugher.pokersi.game.QuickGameSetup
import com.bradflaugher.pokersi.game.RunScreen
import com.bradflaugher.pokersi.game.RunUiState
import com.bradflaugher.pokersi.game.TableExtras
import com.bradflaugher.pokersi.game.TablePhase
import com.bradflaugher.pokersi.game.TableStates
import com.bradflaugher.pokersi.game.TableUiState
import com.bradflaugher.pokersi.run.RunPhase
import com.bradflaugher.pokersi.run.Risk
import com.bradflaugher.pokersi.run.RunState
import com.bradflaugher.pokersi.run.RunStats
import com.bradflaugher.pokersi.run.TableProgress
import com.bradflaugher.pokersi.run.TableSpec
import com.bradflaugher.pokersi.engine.HandCategory
import com.bradflaugher.pokersi.engine.HandEvaluator
import com.bradflaugher.pokersi.game.BestHand
import com.bradflaugher.pokersi.game.LifetimeStats
import com.bradflaugher.pokersi.ui.pixel.LARGER_TEXT_SCALE
import com.bradflaugher.pokersi.ui.pixel.PixelPalette
import com.bradflaugher.pokersi.ui.pixel.PixelTheme

/** A named screen in a scripted state, for screenshot tests and store screenshots. */
class Scene(val name: String, val content: @Composable () -> Unit)

/**
 * Deterministic, good-looking game states for every screen: real [Hand]s
 * dealt from stacked decks and played to an interesting spot.
 */
object ScreenshotScenes {
    private const val HOUSE = "HOUSE BOT"

    private val BOB = OpponentProfile("Byte-Sized Bob", 42L, BotStyle.LOOSE_CANNON, "Learned poker from a fortune cookie.", "Beep boop, pay up.")
    private val DOT = OpponentProfile("Lady Dot", 1337L, BotStyle.ROCK, "Counts cards. Mostly up to seven.", "Trust the process.")
    private val CHIP = OpponentProfile("Captain Chip", 2024L, BotStyle.MANIAC, "Runs on 3 AA batteries and spite.", "All in on vibes.")
    private val FIFI = OpponentProfile("Disco Fifi", 777L, BotStyle.CALLING_STATION, "Moonlights as a karaoke robot.", "Let's dance, meatbag.")

    /**
     * A hand from a stacked deck: [holes] by seat, then [board] (burn cards
     * added automatically), dealt in the engine's order (left of the button,
     * each seat's cards together).
     */
    fun stackedHand(
        variant: GameVariant,
        names: List<String>,
        chips: List<Int>,
        button: Int,
        holes: Map<Int, String>,
        board: String = "",
        bigBlind: Int = 20,
        handNumber: Int = 7,
        extraTop: String = "",
    ): Hand {
        val n = names.size
        val order = (1..n).map { (button + it) % n }
        val holeCards = holes.mapValues { Card.parseList(it.value) }
        val boardCards = Card.parseList(board)
        val extra = Card.parseList(extraTop)
        val used = (holeCards.values.flatten() + boardCards + extra).toSet()
        val spare = Deck(variant.usesShortDeck, kotlin.random.Random(9)).let { d -> List(d.remaining) { d.deal() } }.filter { it !in used }.toMutableList()
        val top = mutableListOf<Card>()
        order.forEach { seat -> top += holeCards[seat] ?: List(variant.holeCards) { spare.removeAt(0) } }
        top += extra
        if (variant.hasBoard && boardCards.isNotEmpty()) {
            top += spare.removeAt(0); top += boardCards.take(3)
            if (boardCards.size > 3) { top += spare.removeAt(0); top += boardCards[3] }
            if (boardCards.size > 4) { top += spare.removeAt(0); top += boardCards[4] }
        }
        return Hand(
            variant = variant,
            seats = names.indices.map { SeatSetup(names[it], chips[it]) },
            button = button,
            stakes = Stakes.forVariant(variant, bigBlind / 2, bigBlind),
            seed = 1L,
            handNumber = handNumber,
            deck = Deck.stacked(top, variant.usesShortDeck),
        )
    }

    /** Applies [decisions] to whoever is to act, in order. */
    fun Hand.script(vararg decisions: Pair<Decision, String?>): Hand {
        decisions.forEach { (d, say) -> apply(checkNotNull(toAct) { "nobody to act" }, d, say) }
        return this
    }

    private fun turnFor(hand: Hand): HumanTurn {
        val seat = hand.snapshot(0).seats[0]
        return HumanTurn(hand.legalActions(0), hand.pot, hand.stakes.bigBlind, seat.bet, seat.chips)
    }

    // ---------------------------------------------------------------- tables

    /** Hold'em on the flop: you have top pair + nut flush draw, the Captain just bet and is talking trash. */
    fun holdemMidHand(): TableUiState {
        val names = listOf("You", BOB.name, DOT.name, CHIP.name)
        val hand = stackedHand(
            GameVariant.TEXAS_HOLDEM, names, listOf(1480, 920, 1210, 1650), button = 0,
            holes = mapOf(0 to "Ah Kh", 1 to "9s 4d", 2 to "Qc Jd", 3 to "7c 7d"),
            board = "Ks 7h 2h Td 3c", handNumber = 12,
        ).script(
            Decision.Call to null,           // Captain limps
            Decision.RaiseTo(80) to null,    // you raise
            Decision.Fold to "Nope.",        // Bob
            Decision.Call to null,           // Dot
            Decision.Call to "Let's see a card.", // Captain
            Decision.Check to null,          // Dot (flop)
            Decision.RaiseTo(160) to null,   // Captain bets
        )
        val extras = TableExtras(
            profiles = listOf(null, BOB, DOT, CHIP),
            chatter = mapOf(3 to "Big bet. Huge. You don't have the chips for my bluffs, meatbag."),
            streamingSeat = 3,
            brainLabel = "QWEN 3 1.7B",
        )
        return TableStates.fromHand(hand, extras, humanTurn = turnFor(hand))
    }

    /** The same flop with the whole table talking at once: the bubbles stack instead of overlapping. */
    fun holdemCrosstalk(): TableUiState {
        val base = holdemMidHand()
        val lines = mapOf(
            1 to "I folded, but I am still emotionally invested in this pot.",
            2 to "Checking. Not scared. Just pacing myself, like a marathon.",
            3 to "Big bet. Huge. You don't have the chips for my bluffs, meatbag.",
        )
        return base.copy(seats = base.seats.map { s -> lines[s.seat]?.let { s.copy(chatter = it, chatterStreaming = false) } ?: s })
    }

    /** Five-card draw at the draw: three queens HELD, Disco Fifi chirping. */
    fun drawWithHolds(): TableUiState {
        val names = listOf("You", FIFI.name, BOB.name)
        val hand = stackedHand(
            GameVariant.FIVE_CARD_DRAW, names, listOf(860, 1120, 1020), button = 2,
            holes = mapOf(0 to "Qs Qh 4c Qd 9s", 1 to "8c 8d 3s Kc 2h", 2 to "Ah Jc 6d 5s Tc"),
            handNumber = 5,
        ).script(
            Decision.Call to "Call me maybe.", // Bob (button)
            Decision.RaiseTo(40) to null,      // you raise
            Decision.Call to null,             // Fifi
            Decision.Call to null,             // Bob
        )
        val held = GameController.suggestHolds(hand.holeCards(0))
        val extras = TableExtras(
            profiles = listOf(null, FIFI, BOB),
            chatter = mapOf(1 to "Fixed limit? More like fixed LOSSES. For you."),
            brainLabel = HOUSE,
        )
        return TableStates.fromHand(hand, extras, humanTurn = turnFor(hand), selected = held)
    }

    /** Hold'em showdown: your full house beats Bob's two pair. Banner, confetti, glowing cards. */
    fun showdownWin(): TableUiState {
        val names = listOf("You", BOB.name, DOT.name)
        val hand = stackedHand(
            GameVariant.TEXAS_HOLDEM, names, listOf(1200, 1350, 900), button = 2,
            holes = mapOf(0 to "7s Ks", 1 to "Ad Kc", 2 to "4h 3h"),
            board = "7h 7c Kd 2s 9h", handNumber = 18,
        ).script(
            Decision.Call to null,          // Dot (button)
            Decision.Call to null,          // you (SB)
            Decision.RaiseTo(60) to "Spicy.", // Bob raises
            Decision.Fold to null,          // Dot
            Decision.Call to null,          // you
            Decision.Check to null,         // flop: you
            Decision.RaiseTo(80) to null,   // Bob bets
            Decision.RaiseTo(240) to null,  // you raise
            Decision.Call to null,          // Bob
            Decision.Check to null,         // turn
            Decision.Check to null,
            Decision.RaiseTo(400) to null,  // river: you bet
            Decision.Call to "I'm curious.", // Bob calls
        )
        val extras = TableExtras(
            profiles = listOf(null, BOB, DOT),
            chatter = mapOf(1 to "Two pair! Kings! How do you lose with KINGS?"),
            brainLabel = "LLAMA 3.2 1B",
        )
        return TableStates.fromHand(hand, extras, phase = TablePhase.HandOver, celebrate = 1)
    }

    // ---------------------------------------------------------------- run

    /** A high-stakes Hold'em table at tour stop 5, ten hands in. */
    fun tourRun(phase: RunPhase = RunPhase.TABLE): RunState {
        var seed = 1L
        var spec: TableSpec
        do {
            spec = TableSpec.generate(seed++, 5, Risk.HIGH, 1840)
        } while (spec.opponents.size < 3)
        spec = spec.copy(variant = GameVariant.TEXAS_HOLDEM, opponents = spec.opponents.take(3))
        return RunState(
            seed = seed,
            level = 5,
            phase = phase,
            table = spec,
            progress = TableProgress(listOf(1840) + spec.opponents.map { 980 }, handsPlayed = 9),
            bankroll = 1840,
            stats = RunStats(handsPlayed = 41, handsWon = 17, biggestPot = 2360, tablesCleared = 4, bestHand = HandCategory.FULL_HOUSE, peakChips = 2410),
            version = RunState.SAVE_VERSION,
        )
    }

    private fun runProfiles(state: RunState): List<OpponentProfile?> = com.bradflaugher.pokersi.game.OpponentGenerator.forRunTable(state)

    /** The tour table on the turn, and a flush for you. */
    fun runTourTable(): Pair<TableUiState, RunUiState> {
        val state = tourRun()
        val spec = state.table
        val names = listOf("You") + runProfiles(state).drop(1).map { it!!.name }
        val hand = stackedHand(
            GameVariant.TEXAS_HOLDEM, names, listOf(1840, 980, 980, 980), button = 3,
            holes = mapOf(0 to "Jh 9h", 1 to "Ac Qd", 2 to "6s 6d", 3 to "Kc 8s"),
            board = "Qh 5h 2c 3h 7d", bigBlind = spec.bigBlind, handNumber = 10,
        ).script(
            Decision.Call to null,               // seat 2 (left of the big blind)
            Decision.Call to null,               // seat 3
            Decision.Call to null,               // you, completing the small blind
            Decision.Check to null,              // seat 1 in the big blind
        )
        // Flop: everyone checks to seat 1, who bets; all call.
        while (hand.street == "Flop") {
            val seat = hand.toAct ?: break
            hand.apply(seat, if (seat == 1 && hand.legalActions(seat).canCheck) Decision.RaiseTo(spec.bigBlind * 3) else Decision.Call)
        }
        val ps = runProfiles(state)
        val extras = TableExtras(
            profiles = ps,
            chatter = mapOf(1 to "High stakes, high hopes. Mostly hopes."),
            brainLabel = "GEMMA 3 1B",
        )
        val table = TableStates.fromHand(hand, extras, humanTurn = if (hand.toAct == 0) turnFor(hand) else null)
        return table to RunUiState(state, RunScreen.Table, opponents = ps)
    }

    /** After clearing stop 5: two tables to choose from. */
    fun chooseTable(): RunUiState {
        val run = tourRun()
        val state = run.copy(
            phase = RunPhase.CHOOSE,
            offers = Risk.entries.map { TableSpec.generate(run.seed, 6, it, 2410) },
            bankroll = 2410,
        )
        return RunUiState(
            state,
            RunScreen.Choose,
            offerOpponents = state.offers.map { com.bradflaugher.pokersi.game.OpponentGenerator.forTable(it, state.seed).filterNotNull() },
        )
    }

    fun runIntro(): RunUiState {
        val state = tourRun().copy(progress = tourRun().progress.copy(handsPlayed = 0))
        return RunUiState(state, RunScreen.Intro, opponents = runProfiles(state))
    }

    // ---------------------------------------------------------------- records

    /** A few weeks of play: a royal flush on the books, Hold'em the favorite. */
    fun lifetimeStats(): LifetimeStats {
        val royal = HandEvaluator.evaluate(Card.parseList("Ah Kh Qh Jh Th 4c 2d"))
        return LifetimeStats(
            handsPlayed = 1284,
            handsWon = 437,
            biggestPot = 18_450,
            bestHand = BestHand.of(royal, GameVariant.TEXAS_HOLDEM),
            handsByGame = mapOf(GameVariant.TEXAS_HOLDEM to 702, GameVariant.OMAHA to 311, GameVariant.FIVE_CARD_DRAW to 271),
            toursStarted = 23,
            bestTour = 8,
            dailyBest = 6,
            holdemHands = sampleChart(),
        )
    }

    /**
     * About 700 Hold'em hands of a fairly tight player: dealt in proportion to
     * how many combos each class has, played more the stronger it is, and a
     * few rare hands not dealt yet.
     */
    private fun sampleChart(): Map<String, com.bradflaugher.pokersi.game.HandTally> {
        val random = kotlin.random.Random(12)
        val values = com.bradflaugher.pokersi.game.StartingHands.ranks.associate { it.letter to it.value }
        return com.bradflaugher.pokersi.game.StartingHands.all.mapNotNull { cls ->
            val hi = values.getValue(cls[0])
            val lo = values.getValue(cls[1])
            val combos = when (cls.getOrNull(2)) { 's' -> 4; 'o' -> 12; else -> 6 }
            val dealt = (combos * 700 / 1326.0 + random.nextDouble(-1.0, 1.5)).toInt()
            if (dealt <= 0) return@mapNotNull null
            val score = hi * 2 + lo + (if (hi == lo) 22 else 0) + (if (cls.endsWith("s")) 4 else 0) - (if (hi - lo > 4) 3 else 0)
            val rate = ((score - 24) / 22.0).coerceIn(0.0, 1.0)
            val played = (dealt * rate + random.nextDouble(-0.4, 0.4)).toInt().coerceIn(0, dealt)
            cls to com.bradflaugher.pokersi.game.HandTally(dealt, played)
        }.toMap()
    }

    // ---------------------------------------------------------------- the list

    val all: List<Scene> = listOf(
        Scene("title") {
            val attract = com.bradflaugher.pokersi.game.AttractLines.lines(
                com.bradflaugher.pokersi.game.TitleFacts(tourRun(), com.bradflaugher.pokersi.game.DailyResult(true, true, 4), lifetimeStats(), "QWEN 3 1.7B"),
            )
            PixelTheme { TitleScreen("QWEN 3 1.7B", hasSavedRun = true, callbacks = TitleCallbacks(), dailyLabel = "DAILY: BEST 4", attract = attract) }
        },
        Scene("picker") {
            PixelTheme { PickerScreen(QuickGameSetup(GameVariant.TEXAS_HOLDEM, opponentCount = 4, stack = 1000, seed = 3L), PickerCallbacks()) }
        },
        Scene("table_holdem") { PixelTheme { TableScreen(holdemMidHand(), TableCallbacks()) } },
        Scene("table_draw") { PixelTheme { TableScreen(drawWithHolds(), TableCallbacks()) } },
        Scene("table_showdown") { PixelTheme { TableScreen(showdownWin(), TableCallbacks()) } },
        Scene("table_crosstalk") { PixelTheme { TableScreen(holdemCrosstalk(), TableCallbacks()) } },
        Scene("run_table") {
            val (t, r) = runTourTable()
            PixelTheme { TableScreen(t, TableCallbacks(), run = r) }
        },
        Scene("run_intro") { PixelTheme { RunIntroScreen(runIntro(), {}, {}) } },
        Scene("run_choose") { PixelTheme { ChooseTableScreen(chooseTable(), {}, {}) } },
        Scene("run_over") { PixelTheme { RunOverScreen(RunUiState(tourRun(RunPhase.GAME_OVER), RunScreen.GameOver), victory = false, onNewRun = {}, onMenu = {}) } },
        Scene("how_to_play") { PixelTheme { HowToPlayScreen({}) } },
        Scene("stats") { PixelTheme { StatsScreen(lifetimeStats(), onReset = {}, onBack = {}) } },
        Scene("stats_chart") {
            PixelTheme {
                ArcadeScreen {
                    androidx.compose.foundation.layout.Column(
                        androidx.compose.ui.Modifier.padding(14.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) { HandChart(lifetimeStats().holdemHands) }
                }
            }
        },
    ) + TourSteps.indices.map { i ->
        // The first-run tour, page by page, over the title screen.
        Scene("tour_${i + 1}_" + listOf("table", "cards", "move", "game", "brains")[i]) {
            PixelTheme {
                androidx.compose.foundation.layout.Box {
                    TitleScreen("HOUSE BOT", hasSavedRun = false, callbacks = TitleCallbacks())
                    TourOverlay(onDismiss = {}, startStep = i)
                }
            }
        }
    } + listOf(
        Scene("table_coach") { PixelTheme { TableScreen(holdemMidHand(), TableCallbacks(), coach = Coach.YourTurn) } },
        Scene("table_coach_hold") { PixelTheme { TableScreen(drawWithHolds(), TableCallbacks(), coach = Coach.Hold) } },
        Scene("table_coach_pause") { PixelTheme { TableScreen(holdemMidHand().copy(humanTurn = null), TableCallbacks(), coach = Coach.Pause) } },
        Scene("table_coach_showdown") { PixelTheme { TableScreen(showdownWin().copy(celebrate = 0), TableCallbacks(), coach = Coach.HandOver) } },
        Scene("run_choose_coach") { PixelTheme { ChooseTableScreen(chooseTable(), {}, {}, coach = Coach.ChooseTable) } },
        Scene("how_to_play_faq") { PixelTheme { HowToPlayScreen({}, openFaq = 1) } },
        Scene("settings") { PixelTheme { SettingsScreen(SettingsUi(hasSavedRun = true), SettingsCallbacks()) } },
        // SETTINGS > LARGER TEXT and HIGH CONTRAST, on the busiest screens.
        Scene("settings_large_text") {
            PixelTheme(textScale = LARGER_TEXT_SCALE) { SettingsScreen(SettingsUi(hasSavedRun = true, largerText = true), SettingsCallbacks()) }
        },
        Scene("table_large_text") { PixelTheme(textScale = LARGER_TEXT_SCALE) { TableScreen(holdemMidHand(), TableCallbacks()) } },
        Scene("draw_large_text") { PixelTheme(textScale = LARGER_TEXT_SCALE) { TableScreen(drawWithHolds(), TableCallbacks()) } },
        Scene("title_large_text") {
            PixelTheme(textScale = LARGER_TEXT_SCALE) { TitleScreen("QWEN 3 1.7B", hasSavedRun = true, callbacks = TitleCallbacks(), dailyLabel = "DAILY: BEST 4") }
        },
        Scene("how_to_play_large_text") { PixelTheme(textScale = LARGER_TEXT_SCALE) { HowToPlayScreen({}) } },
        Scene("table_high_contrast") { PixelTheme(palette = PixelPalette.HighContrast) { TableScreen(holdemMidHand(), TableCallbacks()) } },
        Scene("settings_high_contrast") {
            PixelTheme(palette = PixelPalette.HighContrast) { SettingsScreen(SettingsUi(hasSavedRun = true, highContrast = true), SettingsCallbacks()) }
        },
        // The "are you sure?" dialogs: leaving the app, and quitting a quick table.
        Scene("confirm_exit") {
            PixelTheme {
                androidx.compose.foundation.layout.Box {
                    TitleScreen("QWEN 3 1.7B", hasSavedRun = true, callbacks = TitleCallbacks(), dailyLabel = "DAILY: BEST 4")
                    ConfirmDialog(
                        "LEAVE THE CABINET?", "The cards will miss you. Your tour and records are saved for next time.",
                        confirmLabel = "EXIT", stayLabel = "STAY", onConfirm = {}, onStay = {}, mood = com.bradflaugher.pokersi.ui.pixel.Mood.Sad,
                    )
                }
            }
        },
        Scene("confirm_quit_large_text") {
            PixelTheme(textScale = LARGER_TEXT_SCALE) {
                androidx.compose.foundation.layout.Box {
                    TableScreen(holdemMidHand(), TableCallbacks())
                    ConfirmDialog("QUIT THIS TABLE?", "Your chips at this table are lost.", confirmLabel = "QUIT", stayLabel = "KEEP PLAYING", onConfirm = {}, onStay = {})
                }
            }
        },
        Scene("models") { PixelTheme { ModelsContent(ModelsScenes.scene(), ModelsScenes.noActions) } },
        Scene("table_console_closed") {
            PixelTheme {
                androidx.compose.foundation.layout.Box {
                    TableScreen(holdemMidHand(), TableCallbacks(consoleSlot = true))
                    ConsoleToggle(open = false, onToggle = {})
                }
            }
        },
        Scene("debug_console") {
            PixelTheme {
                androidx.compose.foundation.layout.Box {
                    TableScreen(holdemMidHand(), TableCallbacks(consoleSlot = true))
                    DebugConsole(
                        ConsoleScene.entries, open = true, onClose = {}, onClear = {},
                        zone = java.util.TimeZone.getTimeZone("UTC"), initiallyExpanded = ConsoleScene.expanded,
                    )
                    ConsoleToggle(open = true, onToggle = {})
                }
            }
        },
    )
}
