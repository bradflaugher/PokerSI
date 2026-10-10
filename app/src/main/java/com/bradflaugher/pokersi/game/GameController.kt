package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BlindLevel
import com.bradflaugher.pokersi.engine.BlindSchedule
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.Hand
import com.bradflaugher.pokersi.engine.HouseBot
import com.bradflaugher.pokersi.engine.Match
import com.bradflaugher.pokersi.engine.PlayerAgent
import com.bradflaugher.pokersi.engine.isDraw
import com.bradflaugher.pokersi.run.BossTwists
import com.bradflaugher.pokersi.run.HandOutcome
import com.bradflaugher.pokersi.run.Perks
import com.bradflaugher.pokersi.run.RunPhase
import com.bradflaugher.pokersi.run.RunState
import com.bradflaugher.pokersi.run.TableStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val dealMs: Long = 900,
    val streetMs: Long = 700,
    val actionMs: Long = 450,
    /** Bots never answer faster than this, so their turn reads. */
    val thinkMinMs: Long = 650,
    val runOutMs: Long = 900,
    val payoutStepMs: Long = 700,
) {
    companion object {
        /** No pauses at all: tests. */
        val Instant = Pacing(0, 0, 0, 0, 0, 0)
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
    fun save(state: RunState)
    fun load(): RunState?
    fun clear()

    /** Keeps the run in memory only (tests). */
    class InMemory : RunSaver {
        var saved: RunState? = null
        override fun save(state: RunState) { saved = state }
        override fun load(): RunState? = saved
        override fun clear() { saved = null }
    }
}

/**
 * Runs a poker table for the UI: deals hands from a [Match], asks each seat's
 * agent to act (the human through [act]/[confirmDiscard], opponents through
 * [OpponentBrains] with a [HouseBot] fallback), paces events for the
 * animations and publishes everything as [table] (and [run] in Run mode).
 *
 * Single-threaded: call every method from the thread of [scope] (Main in the
 * app, the test thread in tests). Only agent decisions and equity estimates
 * run on [compute], while the hand is not changing.
 */
class GameController(
    private val scope: CoroutineScope,
    private val brains: OpponentBrains = HouseOnlyBrains,
    private val pacing: Pacing = Pacing(),
    private val speed: StateFlow<GameSpeed> = MutableStateFlow(GameSpeed.NORMAL),
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val runSaver: RunSaver? = null,
    /** Plays the human seat and skips every wait (tests, attract mode). */
    private val autopilot: PlayerAgent? = null,
    private val houseBotIterations: Int = 250,
    /** Longest an opponent agent may think before its House Bot fallback decides. */
    private val agentTimeoutMs: Long = 45_000,
) {
    private val _table = MutableStateFlow(TableUiState())
    val table: StateFlow<TableUiState> = _table.asStateFlow()

    private val _run = MutableStateFlow<RunUiState?>(null)

    /** Run mode only. */
    val run: StateFlow<RunUiState?> = _run.asStateFlow()

    private var job: Job? = null
    private var match: Match? = null
    private var hand: Hand? = null
    private var quick: QuickGameConfig? = null
    private var runState: RunState? = null

    private var profiles: List<OpponentProfile?> = emptyList()
    private var agents: List<PlayerAgent> = emptyList()
    private var fallbacks: List<HouseBot> = emptyList()

    // Decorations of the hand in play.
    private val chatter = LinkedHashMap<Int, String>()
    private var streamingSeat: Int? = null
    private var thinkingSeat: Int? = null
    private var confidence: Map<Int, Float> = emptyMap()
    private var xray: Pair<Int, Card>? = null
    private var phase = TablePhase.Idle
    private var humanTurn: HumanTurn? = null
    private var selected: Set<Int> = emptySet()
    private var boardLimit: Int? = null
    private var celebrate = 0

    private var humanDecision: CompletableDeferred<Decision>? = null
    private var proceed: CompletableDeferred<Unit>? = null

    val isRunning: Boolean get() = job?.isActive == true

    // ---------------------------------------------------------------- quick game

    /** Sits down at a quick game table and starts dealing. */
    fun startQuick(config: QuickGameConfig) {
        stop()
        quick = config
        runState = null
        _run.value = null
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
            val h = m.startNextHand()
            playHand(h)
            m.settle()
            phase = TablePhase.HandOver
            publish()
            if (m.chips[0] > 0 && !m.isOver) awaitProceed()
            else if (autopilot != null && m.chips[0] <= 0) {
                // Autopilot can't press REBUY: the game is over.
                phase = TablePhase.Busted
                publish()
                return
            }
        }
    }

    // ---------------------------------------------------------------- run mode

    /** Opens a run (new or resumed): the table intro, the shop or the game over screen. */
    fun startRun(state: RunState) {
        stop()
        quick = null
        runState = state
        save(state)
        match = null
        hand = null
        phase = TablePhase.Idle
        _table.value = TableUiState(brainLabel = brains.label.value)
        val screen = when (state.phase) {
            RunPhase.TABLE -> RunScreen.Intro
            RunPhase.SHOP -> RunScreen.Shop
            RunPhase.GAME_OVER -> RunScreen.GameOver
        }
        _run.value = RunUiState(state, screen, reward = state.lastReward, opponents = runProfiles(state))
    }

    /** From the table intro: sit down and deal. */
    fun beginTable() {
        val state = runState ?: return
        if (state.phase != RunPhase.TABLE || isRunning) return
        val ps = runProfiles(state)
        // The table as the run would deal it, with the players' display names (the boss is seat 1).
        val p = state.progress
        val m = Match(
            names = listOf(PLAYER_NAME) + ps.drop(1).map { it!!.name },
            startingChips = p.chips,
            variant = state.table.variantForHand(p.handsPlayed + 1),
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
            val h = state.startNextHand(m)
            playHand(h)
            m.settle()
            val before = runState!!
            val scoring = before.applyHandResult(HandOutcome.from(h), m.chips, m.button)
            // The run's chips include Joker bonus chips: copy them back into the match.
            scoring.state.progress.chips.forEachIndexed { seat, c ->
                val delta = c - m.chips[seat]
                if (delta != 0) m.adjustChips(seat, delta)
            }
            runState = scoring.state
            if (scoring.state.phase == RunPhase.GAME_OVER) runSaver?.clear() else save(scoring.state)
            _run.update { it?.copy(state = scoring.state) }

            // Balatro-style payout: each Joker trigger pops in turn while the coins count up.
            val triggers = scoring.payout.triggers
            if (triggers.isNotEmpty()) {
                var coins = before.coins
                for (i in triggers.indices) {
                    coins += triggers[i].coins
                    _run.update { it?.copy(payout = PayoutUi(triggers, i + 1, coins)) }
                    pause(pacing.payoutStepMs)
                }
            }

            when (scoring.status) {
                TableStatus.IN_PROGRESS -> {
                    phase = TablePhase.HandOver
                    publish()
                    awaitProceed()
                    _run.update { it?.copy(payout = null) }
                }
                TableStatus.CLEARED, TableStatus.FAILED -> {
                    phase = TablePhase.RunTableDone
                    publish()
                    awaitProceed()
                    val next = runState!!
                    _run.update {
                        it?.copy(
                            state = next,
                            payout = null,
                            reward = next.lastReward,
                            screen = if (scoring.status == TableStatus.CLEARED) RunScreen.Cleared else RunScreen.GameOver,
                        )
                    }
                    return
                }
            }
        }
    }

    /** From the cleared-table screen: on to the shop (via the victory screen after the final boss). */
    fun toShop() {
        val ui = _run.value ?: return
        val firstVictory = ui.state.victory && ui.screen == RunScreen.Cleared && ui.state.ante == com.bradflaugher.pokersi.run.TableSpec.FINAL_ANTE &&
            ui.state.stage == com.bradflaugher.pokersi.run.TableSpec.STAGES_PER_ANTE - 1
        _run.value = ui.copy(screen = if (firstVictory) RunScreen.Victory else RunScreen.Shop)
    }

    /** Victory screen: keep going in endless mode. */
    fun continueEndless() {
        _run.update { it?.copy(screen = RunScreen.Shop) }
    }

    fun buy(jokerId: String) = shopAction { it.buy(jokerId) }
    fun sell(jokerId: String) = shopAction { it.sell(jokerId) }
    fun reroll() = shopAction { it.reroll() }

    /** Leaves the shop for the next table's intro. */
    fun leaveShop() {
        val state = runState ?: return
        if (state.phase != RunPhase.SHOP) return
        val next = state.leaveShop()
        runState = next
        save(next)
        _table.value = TableUiState(brainLabel = brains.label.value)
        _run.value = RunUiState(next, RunScreen.Intro, opponents = runProfiles(next))
    }

    private fun shopAction(change: (RunState) -> RunState?): Boolean {
        val state = runState ?: return false
        val next = change(state) ?: return false
        runState = next
        save(next)
        _run.update { it?.copy(state = next) }
        return true
    }

    private fun save(state: RunState) {
        if (state.phase == RunPhase.GAME_OVER) runSaver?.clear() else runSaver?.save(state)
    }

    /** Run mode: the Mulligan Joker re-deals the human's starting cards. */
    fun mulligan() {
        val state = runState ?: return
        val h = hand ?: return
        if (!state.canMulligan || !TableStates.mulliganAllowed(h)) return
        if (!h.redealHoleCards(0)) return
        runState = state.useMulligan()
        save(runState!!)
        _run.update { it?.copy(state = runState!!) }
        humanTurn = humanTurn?.copy(canMulligan = false)
        selected = if (h.variant.isDraw) suggestHolds(h.holeCards(0)) else emptySet()
        publish()
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

    /** Stops dealing (leaving the table). The run, if any, is saved between hands. */
    fun stop() {
        job?.cancel()
        job = null
        humanDecision = null
        proceed = null
    }

    // ---------------------------------------------------------------- the hand

    private fun setUpAgents(ps: List<OpponentProfile?>, seed: Long) {
        profiles = ps
        fallbacks = ps.mapIndexed { seat, p ->
            HouseBot(p?.style ?: com.bradflaugher.pokersi.engine.BotStyle.BALANCED, Match.mix(seed, 1000 + seat), houseBotIterations)
        }
        agents = ps.mapIndexed { seat, p -> if (p == null) fallbacks[seat] else brains.agentFor(seat, p, fallbacks[seat]) }
    }

    private suspend fun playHand(h: Hand) {
        hand = h
        chatter.clear()
        streamingSeat = null
        thinkingSeat = null
        confidence = emptyMap()
        humanTurn = null
        selected = emptySet()
        boardLimit = null
        val state = runState
        xray = if (state?.hasXRay == true) Perks.xRayPeek(h) else null
        phase = TablePhase.Playing
        publish()
        pause(pacing.dealMs)
        updateConfidence(h)

        var street = h.street
        var shownBoard = h.board.size
        while (true) {
            val seat = h.toAct ?: break
            if (h.street != street) {
                street = h.street
                shownBoard = h.board.size
                chatter.clear()
                publish()
                pause(pacing.streetMs)
                updateConfidence(h)
            }
            if (seat == 0) humanAct(h) else botAct(h, seat)
            publish()
            pause(pacing.actionMs)
        }

        // All-in run-outs: turn the remaining board cards one street at a time.
        if (h.board.size > shownBoard + 1 && shownBoard < 5) {
            val steps = listOf(3, 4, 5).filter { it > shownBoard && it <= h.board.size }
            for (n in steps) {
                boardLimit = n
                publish()
                pause(pacing.runOutMs)
            }
            boardLimit = null
        }
        thinkingSeat = null
        streamingSeat = null
        if ((h.result?.collected?.get(0) ?: 0) > 0) celebrate++
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
            canMulligan = runState?.canMulligan == true && TableStates.mulliganAllowed(h),
        )
        publish()
        val decision = deferred.await()
        humanTurn = null
        h.apply(0, decision)
        selected = emptySet()
    }

    private suspend fun botAct(h: Hand, seat: Int) {
        val state = runState
        val silent = state?.table?.boss?.rules?.silentOpponents == true
        thinkingSeat = seat
        chatter.remove(seat)
        publish()
        val ctx = h.context(seat)
        val started = System.nanoTime()
        val reply = try {
            withTimeoutOrNull(agentTimeoutMs) {
                withContext(compute) {
                    agents[seat].decide(ctx) { text ->
                        if (!silent) scope.launch { streamChatter(seat, text) }
                    }
                }
            } ?: fallbacks[seat].decideNow(ctx)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fallbacks[seat].decideNow(ctx)
        }
        val elapsed = (System.nanoTime() - started) / 1_000_000
        pause((pacing.thinkMinMs - elapsed).coerceAtLeast(0))
        var decision = reply.decision
        if (state != null) {
            decision = BossTwists.adjustOpponentDecision(state.table.boss, ctx, decision, BossTwists.lastActionOf(h.events, 0))
        }
        thinkingSeat = null
        streamingSeat = null
        val say = if (silent) null else reply.say?.trim()?.takeIf { it.isNotEmpty() }
        if (say != null) setChatter(seat, say)
        h.apply(seat, decision, say)
    }

    private fun streamChatter(seat: Int, text: String) {
        if (thinkingSeat != seat) return
        streamingSeat = seat
        setChatter(seat, text)
        publish()
    }

    /** Keeps the two most recent speakers' lines (bubbles crowd a phone). */
    private fun setChatter(seat: Int, text: String) {
        chatter.remove(seat)
        chatter[seat] = text
        while (chatter.size > 2) chatter.remove(chatter.keys.first())
    }

    private suspend fun updateConfidence(h: Hand) {
        if (runState?.hasTellDetector != true || h.isFinished) return
        val seats = h.snapshot(0).seats.filter { it.seat != 0 && !it.folded && !it.sittingOut }.map { it.seat }
        confidence = withContext(compute) {
            seats.associateWith { Perks.confidence(h, it, iterations = 150).toFloat() }
        }
        publish()
    }

    private fun publish(showBanner: Boolean = true) {
        val h = hand
        val state = runState
        val rules = state?.table?.boss?.rules
        if (h == null) {
            _table.update { it.copy(phase = phase, brainLabel = brains.label.value) }
            return
        }
        val extras = TableExtras(
            profiles = profiles,
            chatter = chatter.toMap(),
            streamingSeat = streamingSeat,
            thinkingSeat = thinkingSeat,
            confidence = confidence,
            xray = xray,
            bossSeat = if (state?.table?.isBoss == true) 1 else null,
            hideChips = rules?.hideChipCounts == true,
            silent = rules?.silentOpponents == true,
            brainLabel = brains.label.value,
        )
        _table.value = TableStates.fromHand(
            h, extras, phase, humanTurn, selected, boardLimit, celebrate,
            showBanner = showBanner && phase != TablePhase.Playing,
        ).let { ui ->
            // The match's stacks are the truth between hands (rebuys, Joker bonus chips).
            val m = match
            if (m != null && h.isFinished) ui.copy(seats = ui.seats.map { s -> s.copy(chips = m.chips[s.seat], busted = m.chips[s.seat] <= 0 && !s.isWinner) })
            else ui
        }
    }

    private suspend fun awaitProceed() {
        if (autopilot != null) return
        val d = CompletableDeferred<Unit>()
        proceed = d
        d.await()
    }

    private suspend fun pause(ms: Long) {
        val scaled = (ms * speed.value.factor).toLong()
        if (scaled > 0) delay(scaled)
    }

    companion object {
        const val PLAYER_NAME = "You"

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
