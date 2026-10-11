package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.AgentReply
import com.bradflaugher.pokersi.engine.BlindLevel
import com.bradflaugher.pokersi.engine.BlindSchedule
import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.Hand
import com.bradflaugher.pokersi.engine.HouseBot
import com.bradflaugher.pokersi.engine.Match
import com.bradflaugher.pokersi.engine.PlayerAgent
import com.bradflaugher.pokersi.engine.TableTalk
import com.bradflaugher.pokersi.debug.AiLog
import com.bradflaugher.pokersi.debug.AiLogKind
import com.bradflaugher.pokersi.engine.isDraw
import com.bradflaugher.pokersi.run.HandOutcome
import com.bradflaugher.pokersi.run.RunPhase
import com.bradflaugher.pokersi.run.RunState
import com.bradflaugher.pokersi.run.TableStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** How fast the table plays. */
enum class GameSpeed(val factor: Double, val label: String) {
    NORMAL(1.0, "NORMAL"),
    FAST(0.4, "FAST"),
}

/** Pauses between table events so the UI can animate them (milliseconds, at [GameSpeed.NORMAL]). */
data class Pacing(
    val dealMs: Long = 700,
    val streetMs: Long = 500,
    val actionMs: Long = 350,
    /** Bots never answer faster than this, so their turn reads. */
    val thinkMinMs: Long = 450,
    val runOutMs: Long = 750,
    /** How long a finished line of table talk stays up: a base, a little more per character, a cap. 0 keeps lines until the street ends. */
    val chatterHoldMs: Long = 2500,
    val chatterPerCharMs: Long = 35,
    val chatterMaxMs: Long = 6000,
) {
    /** How long [text] stays up once it has finished streaming (at [GameSpeed.NORMAL]). */
    fun chatterHold(text: String): Long =
        if (chatterHoldMs <= 0) 0 else minOf(chatterMaxMs, chatterHoldMs + chatterPerCharMs * text.length)

    /**
     * The real time [text] stays up at [speedFactor]: the hold, scaled, but
     * never less than the bubble takes to type it out plus a moment to read
     * its end (the typewriter runs at a fixed pace, whatever the game speed).
     */
    fun chatterHoldAt(text: String, speedFactor: Double): Long {
        val hold = chatterHold(text)
        if (hold <= 0) return 0
        return maxOf((hold * speedFactor).toLong(), TYPE_MS_PER_CHAR * text.length + READ_END_MS)
    }

    companion object {
        /** No pauses at all, and table talk stays until the street ends: tests. */
        val Instant = Pacing(0, 0, 0, 0, 0, chatterHoldMs = 0)

        /** The speech bubble's typewriter pace (SpeechBubble's 40 characters a second). */
        const val TYPE_MS_PER_CHAR = 25L
        private const val READ_END_MS = 800L
    }
}

/** A quick game: one table, a variant, 1 to 5 opponents, a starting stack. */
data class QuickGameConfig(
    val variant: GameVariant,
    val opponents: List<OpponentProfile>,
    val startingStack: Int,
    val seed: Long,
) {
    init {
        require(opponents.size in 1..5) { "1 to 5 opponents" }
    }

    /** Fifty big blinds deep, blinds up every ten hands. */
    val bigBlind: Int get() = (startingStack / 50).coerceAtLeast(2)
    val schedule: BlindSchedule get() = BlindSchedule(BlindLevel(bigBlind / 2, bigBlind), handsPerLevel = 10, growth = 1.5)
}

/** Where a run is kept between launches. */
interface RunSaver {
    /** Saves [state] in its own slot: the daily tour never overwrites a regular one. */
    fun save(state: RunState)
    fun load(daily: Boolean = false): RunState?
    fun clear(daily: Boolean = false)

    /** Keeps the runs in memory only (tests). */
    class InMemory : RunSaver {
        var saved: RunState? = null
        var savedDaily: RunState? = null
        override fun save(state: RunState) { if (state.daily) savedDaily = state else saved = state }
        override fun load(daily: Boolean): RunState? = if (daily) savedDaily else saved
        override fun clear(daily: Boolean) { if (daily) savedDaily = null else saved = null }
    }
}

/** Why the table is paused (several can hold at once). */
enum class PauseReason {
    /** The table menu is open. */
    Menu,

    /** The app is not visible. */
    Background,
}

/**
 * Runs a poker table for the UI: deals hands from a [Match], asks each seat's
 * agent to act (the human through [act]/[confirmDiscard], opponents through
 * [OpponentBrains] with a [HouseBot] fallback), paces events for the
 * animations and publishes everything as [table] (and [run] in Run mode).
 *
 * Single-threaded: call every method from the thread of [scope] (Main in the
 * app, the test thread in tests). Only agent decisions run on [compute],
 * while the hand is not changing; table talk an agent streams from another
 * thread is posted back to [scope] before it touches any state.
 *
 * Leaving a Poker Tour table in the middle of a hand counts as folding it:
 * the chips the player has put in are lost and the hand counts as played.
 * The saved run always holds that "folded now" result while a hand is in
 * play, so killing the app mid-hand can't be used to dodge a bad hand (or
 * replay it, since a table's deals are seeded by hand number) either.
 */
class GameController(
    private val scope: CoroutineScope,
    private val brains: OpponentBrains = HouseOnlyBrains,
    private val pacing: Pacing = Pacing(),
    private val speed: StateFlow<GameSpeed> = MutableStateFlow(GameSpeed.NORMAL),
    /** SETTINGS > FAST FOLD: once the player has folded, the rest of the hand plays out at once ([outOfIt]). */
    private val fastFold: StateFlow<Boolean> = MutableStateFlow(false),
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val runSaver: RunSaver? = null,
    /** Plays the human seat and skips every wait (tests, attract mode). */
    private val autopilot: PlayerAgent? = null,
    private val houseBotIterations: Int = 250,
    /** Longest an opponent agent may think before its House Bot fallback decides. */
    private val agentTimeoutMs: Long = 45_000,
    /** Quick games: a busted opponent's seat is taken by a newcomer after this many hands (null: never). */
    private val replaceBustedAfterHands: Int? = 1,
    /** Lifetime stats: told about each hand the human finishes (never in autopilot). */
    private val recorder: HandRecorder? = null,
) {
    private val _table = MutableStateFlow(TableUiState())
    val table: StateFlow<TableUiState> = _table.asStateFlow()

    private val _run = MutableStateFlow<RunUiState?>(null)

    /** Run mode only. */
    val run: StateFlow<RunUiState?> = _run.asStateFlow()

    private val pauses = MutableStateFlow<Set<PauseReason>>(emptySet())

    /** True while something holds the table still (the bots wait; the human may still act). */
    val paused: StateFlow<Set<PauseReason>> = pauses.asStateFlow()

    private var job: Job? = null
    private var match: Match? = null
    private var hand: Hand? = null
    private var quick: QuickGameConfig? = null
    private var runState: RunState? = null

    /** Run mode: the hand in play has been scored into [runState]. */
    private var handScored = true

    private var profiles: List<OpponentProfile?> = emptyList()
    private var agents: List<PlayerAgent> = emptyList()
    private var fallbacks: List<HouseBot> = emptyList()

    /** What has been said at this table (by every seat); a new table starts a new one. */
    var talk = TableTalk()
        private set

    /** Quick game: seat -> the match's hand count when that opponent went broke. */
    private val bustedAt = HashMap<Int, Int>()
    private var replacements = 0

    /** Table news ("Mo has left the table") shown in the log next to the hand's history. */
    private var news: List<String> = emptyList()

    // Decorations of the hand in play.
    private val chatter = LinkedHashMap<Int, String>()
    /** Each shown line's countdown ([expireChatter]), by seat. */
    private val chatterExpiry = HashMap<Int, Job>()
    /**
     * Parent of the countdowns: not the game loop's job, so they never keep a
     * finished table "running", and they start even before that job is assigned.
     */
    private val chatterJobs = SupervisorJob(scope.coroutineContext[Job])
    private var streamingSeat: Int? = null
    private var thinkingSeat: Int? = null

    /** Bumps with every bot decision, so late table talk from an earlier one is dropped. */
    private var decisionId = 0
    private var phase = TablePhase.Idle
    private var humanTurn: HumanTurn? = null
    private var selected: Set<Int> = emptySet()
    private var boardLimit: Int? = null
    private var celebrate = 0

    private var humanDecision: CompletableDeferred<Decision>? = null
    private var proceed: CompletableDeferred<Unit>? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** Holds (or releases, with [on] false) the table for [reason]. Bots wait at their next step. */
    fun setPaused(reason: PauseReason, on: Boolean) {
        pauses.update { if (on) it + reason else it - reason }
    }

    // ---------------------------------------------------------------- quick game

    /** Sits down at a quick game table and starts dealing. */
    fun startQuick(config: QuickGameConfig) {
        stop()
        quick = config
        runState = null
        _run.value = null
        bustedAt.clear()
        replacements = 0
        news = emptyList()
        val names = listOf(PLAYER_NAME) + config.opponents.map { it.name }
        match = Match(names, List(names.size) { config.startingStack }, config.variant, config.schedule, config.seed)
        setUpAgents(listOf<OpponentProfile?>(null) + config.opponents, config.seed)
        job = scope.launch { quickLoop() }
    }

    /** Busted in a quick game: buy back in for a starting stack. */
    fun rebuy() {
        val config = quick ?: return
        val m = match ?: return
        if (phase != TablePhase.Busted) return
        m.adjustChips(0, config.startingStack)
        release()
    }

    /** After winning (or leaving) a quick table: a fresh table with the same game and opponents. */
    fun playAgain() {
        val config = quick ?: return
        startQuick(config.copy(seed = Random(config.seed).nextLong()))
    }

    private suspend fun quickLoop() {
        val m = match!!
        while (true) {
            when {
                m.chips[0] <= 0 -> {
                    phase = TablePhase.Busted
                    publish(showBanner = true)
                    if (autopilot != null) return // Autopilot can't press REBUY: the game is over.
                    awaitProceed()
                    continue
                }
                m.isOver -> {
                    phase = TablePhase.TableWon
                    publish(showBanner = true)
                    if (autopilot != null) return
                    awaitProceed()
                    return
                }
            }
            // Everyone at a fresh table sits down with a hello; later, only newcomers.
            val arrivals = seatNewcomers(m) + if (m.handsPlayed == 0) seatedOpponents(m) else emptySet()
            val h = m.startNextHand()
            playHand(h, arrivals)
            m.settle()
            noteBusts(m)
            phase = TablePhase.HandOver
            publish()
            if (m.chips[0] > 0 && !m.isOver) awaitProceed()
        }
    }

    /** Quick game: "X has left the table" for every opponent who just went broke. */
    private fun noteBusts(m: Match) {
        if (replaceBustedAfterHands == null) return
        val gone = (1 until m.chips.size).filter { m.chips[it] <= 0 && it !in bustedAt }
        gone.forEach { bustedAt[it] = m.handsPlayed }
        if (gone.isNotEmpty()) news = news + gone.map { "${m.names[it]} has left the table" }
    }

    /**
     * Quick game: seats a fresh random opponent in each seat that has been empty
     * for [replaceBustedAfterHands] hands, so a table never runs dry. Returns
     * the seats that changed hands.
     */
    /** The opponent seats with chips: who sits down at a fresh table. */
    private fun seatedOpponents(m: Match): Set<Int> = m.chips.indices.filter { it != 0 && m.chips[it] > 0 }.toSet()

    private fun seatNewcomers(m: Match): Set<Int> {
        val config = quick ?: return emptySet()
        val after = replaceBustedAfterHands ?: return emptySet()
        news = emptyList()
        val due = bustedAt.filter { (seat, at) -> m.chips[seat] <= 0 && m.handsPlayed - at >= after }.keys.sorted()
        for (seat in due) {
            bustedAt.remove(seat)
            val taken = m.names.toSet()
            var profile: OpponentProfile
            var attempt = 0
            do {
                profile = OpponentGenerator.profile(Match.mix(config.seed, 50_000 + replacements * 31 + attempt++))
            } while (profile.name in taken && attempt < 20)
            replacements++
            m.replaceSeat(seat, profile.name, config.startingStack)
            profiles = profiles.toMutableList().also { it[seat] = profile }
            val fallback = HouseBot(profile.style, Match.mix(config.seed, 1000 + seat + 7 * replacements), houseBotIterations, tableTalk = talk)
            fallbacks = fallbacks.toMutableList().also { it[seat] = fallback }
            agents = agents.toMutableList().also { it[seat] = brains.agentFor(seat, profile, fallback, talk) }
            news = news + "${profile.name} sits down"
        }
        return due.toSet()
    }

    // ---------------------------------------------------------------- run mode

    /** Opens a run (new or resumed): the table intro, the table choice or the game over screen. */
    fun startRun(state: RunState) {
        stop()
        quick = null
        runState = state
        save(state)
        match = null
        hand = null
        news = emptyList()
        phase = TablePhase.Idle
        _table.value = TableUiState(brainLabel = brains.label.value)
        val screen = when (state.phase) {
            RunPhase.TABLE -> RunScreen.Intro
            RunPhase.CHOOSE -> RunScreen.Choose
            RunPhase.GAME_OVER -> RunScreen.GameOver
        }
        _run.value = runUi(state, screen)
    }

    private fun runUi(state: RunState, screen: RunScreen) = RunUiState(
        state,
        screen,
        reward = state.lastReward,
        opponents = runProfiles(state),
        offerOpponents = offerProfiles(state),
    )

    private fun offerProfiles(state: RunState): List<List<OpponentProfile>> =
        state.offers.map { OpponentGenerator.forTable(it, state.seed).filterNotNull() }

    /** From the table intro: sit down and deal. */
    fun beginTable() {
        val state = runState ?: return
        if (state.phase != RunPhase.TABLE || isRunning) return
        val ps = runProfiles(state)
        // The table as the run would deal it, with the players' display names.
        val p = state.progress
        val m = Match(
            names = listOf(PLAYER_NAME) + ps.drop(1).map { it!!.name },
            startingChips = p.chips,
            variant = state.table.variant,
            schedule = state.table.schedule,
            seed = state.table.seed,
            button = p.button,
            handsPlayed = p.handsPlayed,
        )
        match = m
        setUpAgents(ps, state.table.seed)
        _run.update { it?.copy(screen = RunScreen.Table, opponents = ps) }
        job = scope.launch { runTableLoop() }
    }

    private fun runProfiles(state: RunState): List<OpponentProfile?> = OpponentGenerator.forRunTable(state)

    private suspend fun runTableLoop() {
        val m = match!!
        while (true) {
            val state = runState!!
            val arrivals = if (m.handsPlayed == 0) seatedOpponents(m) else emptySet()
            val h = state.startNextHand(m)
            handScored = false
            hand = h
            saveForfeit(h)
            playHand(h, arrivals)
            val status = scoreRunHand(h)

            when (status) {
                TableStatus.IN_PROGRESS -> {
                    phase = TablePhase.HandOver
                    publish()
                    awaitProceed()
                }
                TableStatus.CLEARED, TableStatus.FAILED -> {
                    phase = TablePhase.RunTableDone
                    publish()
                    awaitProceed()
                    val next = runState!!
                    _run.update {
                        it?.copy(
                            state = next,
                            reward = next.lastReward,
                            offerOpponents = offerProfiles(next),
                            screen = if (status == TableStatus.CLEARED) RunScreen.Cleared else RunScreen.GameOver,
                        )
                    }
                    return
                }
            }
        }
    }

    /** Scores the finished run hand [h] (once), saves the run and returns how the table stands. */
    private fun scoreRunHand(h: Hand): TableStatus {
        val m = match!!
        m.settle()
        val scoring = runState!!.applyHandResult(HandOutcome.from(h), m.chips, m.button)
        handScored = true
        runState = scoring.state
        save(scoring.state)
        _run.update { it?.copy(state = scoring.state) }
        return scoring.status
    }

    /**
     * The run as it stands if the player folded [h] right now: the chips they put
     * in go to the opponents still in the hand and the hand counts as played.
     */
    private fun forfeitState(h: Hand): RunState? {
        val state = runState ?: return null
        val m = match ?: return null
        if (h.isFinished) return null
        val seats = h.snapshot(0).seats
        val committed = (m.chips[0] - seats[0].chips).coerceAtLeast(0)
        val stacks = m.chips.toMutableList()
        stacks[0] -= committed
        val takers = seats.filter { it.seat != 0 && !it.folded && !it.sittingOut }.map { it.seat }
            .ifEmpty { seats.filter { it.seat != 0 && !it.sittingOut }.map { it.seat } }
        if (takers.isNotEmpty()) {
            takers.forEachIndexed { i, seat -> stacks[seat] += committed / takers.size + if (i < committed % takers.size) 1 else 0 }
        }
        return state.applyHandResult(HandOutcome.forfeit(committed, h.pot), stacks, h.button).state
    }

    /** Keeps the "folded now" result of [h] as the saved run while it is in play (see the class doc). */
    private fun saveForfeit(h: Hand) {
        if (runSaver == null || autopilot != null) return
        forfeitState(h)?.let(::save)
    }

    /** From the cleared-table screen: on to choosing the next table (via the victory screen after the final stop). */
    fun toChoose() {
        val ui = _run.value ?: return
        val firstVictory = ui.screen == RunScreen.Cleared && ui.state.victory &&
            ui.state.level == com.bradflaugher.pokersi.run.TableSpec.FINAL_LEVEL
        _run.value = ui.copy(screen = if (firstVictory) RunScreen.Victory else RunScreen.Choose)
    }

    /** Victory screen: keep going in endless mode. */
    fun continueEndless() {
        _run.update { it?.copy(screen = RunScreen.Choose) }
    }

    /** Picks table [index] from the offers: on to its intro. */
    fun chooseTable(index: Int): Boolean {
        val state = runState ?: return false
        val next = state.choose(index) ?: return false
        runState = next
        save(next)
        hand = null
        _table.value = TableUiState(brainLabel = brains.label.value)
        _run.value = runUi(next, RunScreen.Intro)
        return true
    }

    private fun save(state: RunState) {
        if (state.phase == RunPhase.GAME_OVER) runSaver?.clear(state.daily) else runSaver?.save(state)
    }

    // ---------------------------------------------------------------- human input

    /** The human's betting decision (ignored when it is not their turn). */
    fun act(decision: Decision) {
        val d = humanDecision ?: return
        if (humanTurn?.isDiscard == true && decision !is Decision.Discard) return
        humanDecision = null
        humanTurn = null
        d.complete(decision)
    }

    /** Draw games: HOLD / un-HOLD a card. Pineapple: pick the card to toss. */
    fun toggleSelect(index: Int) {
        val turn = humanTurn ?: return
        if (!turn.isDiscard) return
        val h = hand ?: return
        if (index !in h.holeCards(0).indices) return
        selected = if (h.variant.isDraw) {
            if (index in selected) selected - index else selected + index
        } else {
            if (index in selected) emptySet() else setOf(index)
        }
        publish()
    }

    /** Draw: discard every card not HELD. Pineapple: toss the picked card. */
    fun confirmDiscard() {
        val turn = humanTurn ?: return
        val rule = turn.legal.discard ?: return
        val h = hand ?: return
        val count = h.holeCards(0).size
        val discard = if (h.variant.isDraw) (0 until count).toSet() - selected else selected
        if (discard.size !in rule.min..rule.max) return
        act(Decision.Discard(discard))
    }

    /** NEXT HAND / REBUY / continue after a decided run table. */
    fun next() {
        if (phase == TablePhase.Busted) {
            rebuy()
            return
        }
        release()
    }

    private fun release() {
        proceed?.complete(Unit)
        proceed = null
    }

    /**
     * Stops dealing (leaving the table) and cancels any opponent still thinking
     * (an LLM generation in flight stops at its next token). In a run, a hand
     * already decided is scored; a hand still in play is folded (see the class doc).
     */
    fun stop() {
        job?.cancel()
        job = null
        clearChatter()
        humanDecision = null
        humanTurn = null
        proceed = null
        thinkingSeat = null
        streamingSeat = null
        val h = hand
        if (runState != null && h != null && !handScored && match != null) {
            if (h.isFinished) {
                scoreRunHand(h)
            } else if (autopilot == null) {
                forfeitState(h)?.let { folded ->
                    runState = folded
                    save(folded)
                    _run.update { it?.copy(state = folded) }
                }
                handScored = true
            }
        }
    }

    // ---------------------------------------------------------------- the hand

    private fun setUpAgents(ps: List<OpponentProfile?>, seed: Long) {
        profiles = ps
        talk = TableTalk()
        fallbacks = ps.mapIndexed { seat, p ->
            HouseBot(p?.style ?: BotStyle.BALANCED, Match.mix(seed, 1000 + seat), houseBotIterations, tableTalk = talk)
        }
        agents = ps.mapIndexed { seat, p -> if (p == null) fallbacks[seat] else brains.agentFor(seat, p, fallbacks[seat], talk) }
        AiLog.add(AiLogKind.TABLE, TABLE, { "new table: " + ps.filterNotNull().joinToString { it.name } })
    }

    private suspend fun playHand(h: Hand, arrivals: Set<Int> = emptySet()) {
        hand = h
        clearChatter()
        streamingSeat = null
        thinkingSeat = null
        humanTurn = null
        selected = emptySet()
        boardLimit = null
        phase = TablePhase.Playing
        // Newcomers say hello.
        arrivals.forEach { seat ->
            val p = profiles.getOrNull(seat) ?: return@forEach
            p.catchphrase.takeIf { it.isNotBlank() }?.let {
                setChatter(seat, it)
                talk.record(p.name, it)
            }
        }
        AiLog.add(AiLogKind.TABLE, TABLE, { "hand ${h.handNumber}: ${h.variant.displayName}, blinds ${h.stakes.smallBlind}/${h.stakes.bigBlind}" })
        var logged = h.history.size
        publish()
        pause(pacing.dealMs)

        var street = h.street
        var shownBoard = h.board.size
        while (true) {
            val seat = h.toAct ?: break
            if (h.street != street) {
                street = h.street
                shownBoard = h.board.size
                clearChatter()
                publish()
                if (!outOfIt(h)) pause(pacing.streetMs)
            }
            if (seat == 0) humanAct(h) else botAct(h, seat)
            if (AiLog.enabled) {
                h.history.drop(logged).forEach { AiLog.note(AiLogKind.TABLE, TABLE, it) }
            }
            logged = h.history.size
            publish()
            if (!outOfIt(h)) pause(pacing.actionMs)
        }

        // All-in run-outs: turn the remaining board cards one street at a time.
        if (h.board.size > shownBoard + 1 && shownBoard < 5) {
            val steps = listOf(3, 4, 5).filter { it > shownBoard && it <= h.board.size }
            for (n in steps) {
                boardLimit = n
                publish()
                if (!outOfIt(h)) pause(pacing.runOutMs)
            }
            boardLimit = null
        }
        thinkingSeat = null
        streamingSeat = null
        if ((h.result?.collected?.get(0) ?: 0) > 0) celebrate++
        if (autopilot == null) recorder?.handFinished(h)
        phase = TablePhase.HandOver
        publish()
    }

    private suspend fun humanAct(h: Hand) {
        val legal = h.legalActions(0)
        val snap = h.snapshot(0).seats[0]
        if (legal.discard != null) {
            selected = if (h.variant.isDraw) suggestHolds(h.holeCards(0)) else emptySet()
        }
        val auto = autopilot
        if (auto != null) {
            val reply = auto.decide(h.context(0))
            h.apply(0, reply.decision)
            return
        }
        val deferred = CompletableDeferred<Decision>()
        humanDecision = deferred
        humanTurn = HumanTurn(
            legal = legal,
            pot = h.pot,
            bigBlind = h.stakes.bigBlind,
            betThisRound = snap.bet,
            stack = snap.chips,
        )
        publish()
        val decision = deferred.await()
        humanTurn = null
        h.apply(0, decision)
        selected = emptySet()
        if (runState != null) saveForfeit(h)
    }

    /**
     * Fast fold: the player has folded this hand (and the setting is on), so
     * how the others finish it doesn't matter to them. The rest plays out at
     * once: each opponent's House Bot decides, silently, with no pauses.
     */
    private fun outOfIt(h: Hand): Boolean =
        fastFold.value && autopilot == null && h.snapshot(0).seats.firstOrNull { it.seat == 0 }?.folded == true

    private suspend fun botAct(h: Hand, seat: Int) {
        awaitUnpaused()
        if (outOfIt(h)) {
            thinkingSeat = null
            streamingSeat = null
            removeChatter(seat)
            h.apply(seat, fallbacks[seat].decideNow(h.context(seat)).decision)
            return
        }
        val id = ++decisionId
        thinkingSeat = seat
        removeChatter(seat)
        publish()
        val ctx = h.context(seat)
        val started = System.nanoTime()
        // Table talk streams in on the agent's thread (the LLM's inference thread):
        // hop back to the table's thread, as a child of this game so leaving cancels it.
        val game = currentCoroutineContext()[Job]
        val onChatter: (String) -> Unit = { text ->
            scope.launch(game ?: Job()) { streamChatter(id, seat, text) }
        }
        val reply = decideSafely(agents[seat], fallbacks[seat], ctx, onChatter)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        pause((pacing.thinkMinMs - elapsed).coerceAtLeast(0))
        awaitUnpaused()
        val decision = reply.decision
        thinkingSeat = null
        streamingSeat = null
        val say = reply.say?.let(::tidySay)
        if (say != null) {
            setChatter(seat, say)
            talk.record(ctx.seats.firstOrNull { it.seat == seat }?.name ?: "Seat $seat", say)
        } else {
            // A line that streamed and was then dropped (a repeat) must not linger.
            removeChatter(seat)
        }
        h.apply(seat, decision, say)
    }

    /**
     * [agent]'s reply, or [fallback]'s when the agent throws, times out after
     * [agentTimeoutMs] or hangs. The hand never stalls on an opponent.
     */
    private suspend fun decideSafely(agent: PlayerAgent, fallback: HouseBot, ctx: DecisionContext, onChatter: (String) -> Unit): AgentReply =
        try {
            withTimeoutOrNull(agentTimeoutMs) {
                withContext(compute) { agent.decide(ctx, onChatter) }
            } ?: fallback.decideNow(ctx)
        } catch (e: CancellationException) {
            // Our own cancellation (leaving the table) propagates; an agent's stray one doesn't.
            currentCoroutineContext()[Job]?.let { if (!it.isActive) throw e }
            fallback.decideNow(ctx)
        } catch (_: Exception) {
            fallback.decideNow(ctx)
        }

    private fun streamChatter(id: Int, seat: Int, text: String) {
        if (id != decisionId || thinkingSeat != seat) return
        streamingSeat = seat
        setChatter(seat, tidySay(text) ?: return, expires = false)
        publish()
    }

    /**
     * Shows [text] in [seat]'s bubble, keeping the two most recent speakers'
     * lines (bubbles crowd a phone). A finished line ([expires]; not one still
     * streaming in) goes away on its own after a short hold.
     */
    private fun setChatter(seat: Int, text: String, expires: Boolean = true) {
        removeChatter(seat)
        chatter[seat] = text
        while (chatter.size > 2) removeChatter(chatter.keys.first())
        if (expires) expireChatter(seat, text)
    }

    private fun removeChatter(seat: Int) {
        chatter.remove(seat)
        chatterExpiry.remove(seat)?.cancel()
    }

    private fun clearChatter() {
        chatter.clear()
        chatterExpiry.values.forEach { it.cancel() }
        chatterExpiry.clear()
    }

    /**
     * Takes [seat]'s [text] down after its hold ([Pacing.chatterHoldAt]: scaled
     * by the game speed, but never before it has finished typing). The countdown stops while the table is paused, and a
     * newer line from the same seat replaces this one (and its countdown).
     */
    private fun expireChatter(seat: Int, text: String) {
        val hold = pacing.chatterHoldAt(text, speed.value.factor)
        if (hold <= 0) return
        chatterExpiry[seat] = scope.launch(chatterJobs) {
            var left = hold
            while (left > 0) {
                awaitUnpaused()
                val step = minOf(left, 100L)
                delay(step)
                // Only time spent unpaused counts: a pause that began mid-step keeps the step.
                if (autopilot != null || pauses.value.isEmpty()) left -= step
            }
            awaitUnpaused()
            if (chatter[seat] == text) {
                chatter.remove(seat)
                chatterExpiry.remove(seat)
                publish()
            }
        }
    }

    private fun publish(showBanner: Boolean = true) {
        val h = hand
        if (h == null) {
            _table.update { it.copy(phase = phase, brainLabel = brains.label.value) }
            return
        }
        val extras = TableExtras(
            profiles = profiles,
            chatter = chatter.toMap(),
            streamingSeat = streamingSeat,
            thinkingSeat = thinkingSeat,
            brainLabel = brains.label.value,
        )
        _table.value = TableStates.fromHand(
            h, extras, phase, humanTurn, selected, boardLimit, celebrate,
            showBanner = showBanner && phase != TablePhase.Playing,
        ).let { ui ->
            // The match's stacks are the truth between hands (rebuys, newcomers).
            val m = match
            val seated = if (m != null && h.isFinished) {
                ui.copy(seats = ui.seats.map { s -> s.copy(chips = m.chips[s.seat], busted = m.chips[s.seat] <= 0 && !s.isWinner) })
            } else ui
            if (news.isEmpty()) seated
            else seated.copy(log = (seated.log + news).takeLast(4))
        }
    }

    private suspend fun awaitProceed() {
        if (autopilot != null) return
        val d = CompletableDeferred<Unit>()
        proceed = d
        d.await()
    }

    private suspend fun awaitUnpaused() {
        if (autopilot != null) return
        pauses.first { it.isEmpty() }
    }

    private suspend fun pause(ms: Long) {
        awaitUnpaused()
        val scaled = (ms * speed.value.factor).toLong()
        if (scaled > 0) delay(scaled)
    }

    companion object {
        const val PLAYER_NAME = "You"
        private const val TABLE = "TABLE"

        /** Longest speech bubble, whatever an agent says. */
        const val SAY_MAX = 120

        /** A speech line trimmed to one bubble, or null when there is nothing to say. */
        fun tidySay(text: String): String? {
            val line = text.replace(Regex("\\s+"), " ").trim()
            if (line.isEmpty()) return null
            return if (line.length <= SAY_MAX) line else line.take(SAY_MAX - 1).trimEnd() + "…"
        }

        /** Video poker courtesy: pre-HOLD pairs and better, else the two highest cards. */
        fun suggestHolds(cards: List<Card>): Set<Int> {
            val byRank = cards.indices.groupBy { cards[it].rank }
            val made = byRank.values.filter { it.size >= 2 }.flatten().toSet()
            if (made.isNotEmpty()) return made
            val flush = cards.indices.groupBy { cards[it].suit }.values.firstOrNull { it.size >= 4 }
            if (flush != null) return flush.toSet()
            return cards.indices.sortedByDescending { cards[it].rank.value }.take(2).toSet()
        }
    }
}
