package com.bradflaugher.pokersi.engine

import kotlin.random.Random

/**
 * One hand of any [GameVariant] for 2 to 6 seats: the state machine that deals,
 * asks seats to act, settles side pots and pays the winners.
 *
 * Drive it by asking [toAct] who must decide, building that seat's [context],
 * getting a [Decision] from its agent and passing it to [apply]. Everything that
 * happens is appended to [events]; [snapshot] is what the table looks like to a
 * viewer; [result] is set when the hand is over.
 *
 * Rules: heads-up the button posts the small blind and acts first before the
 * flop, last after it. A no-limit/pot-limit raise must be at least the last full
 * raise; an all-in short raise does not reopen betting for seats that already
 * acted. Fixed-limit betting is capped at four bets a round. Stud antes, the
 * lowest up card brings in (suits break ties: clubs, diamonds, hearts, spades),
 * and the best showing hand acts first from 4th street on. Odd chips of a split
 * pot go to the first winner left of the button.
 */
class Hand(
    val variant: GameVariant,
    seats: List<SeatSetup>,
    button: Int,
    val stakes: Stakes,
    seed: Long,
    val handNumber: Int = 1,
    /** A pre-arranged deck (see [Deck.stacked]); by default a fresh deck shuffled from [seed]. */
    deck: Deck? = null,
) {
    private class SeatState(val name: String, var chips: Int) {
        val dealtIn = chips > 0
        val cards = mutableListOf<Card>()
        val faceUp = mutableListOf<Boolean>()
        var bet = 0
        var committed = 0
        var folded = !dealtIn
        var allIn = false
        var acted = false
        var actedAtRaise = 0
        var revealed = false
        var lastAction: String? = null
        var everAllIn = false

        val live: Boolean get() = dealtIn && !folded
        val canBet: Boolean get() = live && !allIn
    }

    private val seats: List<SeatState> = seats.map { SeatState(it.name, it.chips) }
    private val n = seats.size
    private val deck = deck ?: Deck.forVariant(variant, Random(seed))
    private val muck = mutableListOf<Card>()
    private val streets = variant.streetNames
    private val _events = mutableListOf<HandEvent>()
    private val _board = mutableListOf<Card>()

    /** The dealer button (moved to the next dealt-in seat if the given one sits out). */
    val button: Int

    /** Seats that posted blinds or brought it in, when they apply. */
    var smallBlindSeat: Int? = null
        private set
    var bigBlindSeat: Int? = null
        private set
    var bringInSeat: Int? = null
        private set

    private var streetIndex = 0
    private var discardRound = false
    private val pendingDiscards = ArrayDeque<Int>()

    // Betting-round state.
    private var currentBet = 0
    private var lastRaiseSize = 0
    private var fullRaiseCount = 0
    private var betCount = 0
    private var lastAggressor: Int? = null

    private var _toAct: Int? = null
    private var _result: HandResult? = null
    private val startingChips = seats.map { it.chips }

    /** The seat whose decision is needed, or null once the hand is finished. */
    val toAct: Int? get() = _toAct

    val isFinished: Boolean get() = _result != null

    /** Set when the hand is over. */
    val result: HandResult? get() = _result

    /** Append-only log for the UI. */
    val events: List<HandEvent> get() = _events

    /** Public history lines for prompts ("Mo raises to 60"). */
    val history: List<String> get() = _events.mapNotNull { it.text }

    val board: List<Card> get() = _board

    /** Name of the current street ("Pre-flop", "Turn", "5th street", "Showdown"). */
    val street: String get() = if (isFinished) "Showdown" else streets[streetIndex]

    /** Every chip committed this hand, current bets included. */
    val pot: Int get() = this.seats.sumOf { it.committed }

    init {
        require(n in 2..6) { "A hand needs 2 to 6 seats, got $n" }
        require(this.seats.count { it.dealtIn } >= 2) { "A hand needs at least 2 seats with chips" }
        require(button in 0 until n) { "Button $button out of range" }
        this.button = if (this.seats[button].dealtIn) button else nextDealtIn(button)
        start()
    }

    // ---------------------------------------------------------------- public API

    /** Everything [seat] may know right now, for its agent. */
    fun context(seat: Int): DecisionContext {
        val me = seats[seat]
        return DecisionContext(
            variant = variant,
            seat = seat,
            handNumber = handNumber,
            street = street,
            holeCards = me.cards.toList(),
            board = _board.toList(),
            pot = pot,
            bigBlind = stakes.bigBlind,
            seats = seats.indices.filter { seats[it].dealtIn }.map { i ->
                val s = seats[i]
                SeatView(
                    seat = i,
                    name = s.name,
                    chips = s.chips,
                    betThisRound = s.bet,
                    folded = s.folded,
                    allIn = s.allIn,
                    isDealer = i == button,
                    cards = s.cards.mapIndexed { k, c -> if (i == seat || s.faceUp[k]) c else null },
                )
            },
            history = history,
            legal = legalActions(seat),
            madeHand = HandEvaluator.describe(me.cards, _board, variant, moreCardsToCome()),
        )
    }

    /** What [seat] may do now (meaningful for the seat in [toAct]). */
    fun legalActions(seat: Int): LegalActions {
        val s = seats[seat]
        if (discardRound) {
            return LegalActions(false, false, 0, null, null, discard = if (variant.isDraw) DiscardRule(0, 3) else DiscardRule(1, 1))
        }
        val toCall = (currentBet - s.bet).coerceIn(0, s.chips)
        val canCheck = s.bet >= currentBet
        val maxTotal = s.bet + s.chips
        val reopened = !s.acted || s.actedAtRaise < fullRaiseCount
        val capped = variant.structure == BettingStructure.FIXED_LIMIT && betCount >= FIXED_LIMIT_CAP
        val canRaise = s.chips > toCall && reopened && !capped && othersCanBet(seat)
        var minTo: Int? = null
        var maxTo: Int? = null
        if (canRaise) {
            when (variant.structure) {
                BettingStructure.FIXED_LIMIT -> {
                    minTo = minOf(fixedRaiseTo(), maxTotal)
                    maxTo = minTo
                }
                BettingStructure.NO_LIMIT -> {
                    minTo = currentBet + maxOf(lastRaiseSize, stakes.bigBlind, 1)
                    maxTo = maxTotal
                }
                BettingStructure.POT_LIMIT -> {
                    minTo = currentBet + maxOf(lastRaiseSize, stakes.bigBlind, 1)
                    maxTo = minOf(maxTotal, currentBet + pot + (currentBet - s.bet))
                }
            }
            if (minTo > maxTo) minTo = maxTo
        }
        return LegalActions(canFold = !canCheck, canCheck = canCheck, toCall = toCall, minRaiseTo = minTo, maxRaiseTo = maxTo)
    }

    /**
     * Applies [seat]'s decision, coerced to something legal (bad input from an agent
     * never breaks the hand), and posts [say] as table talk. Throws
     * [IllegalStateException] only if it is not [seat]'s turn.
     */
    fun apply(seat: Int, decision: Decision, say: String? = null) {
        check(seat == _toAct) { "Seat $seat acted but it is ${_toAct?.let { "seat $it's" } ?: "nobody's"} turn" }
        say?.trim()?.takeIf { it.isNotEmpty() }?.let { line ->
            _events += HandEvent.Chat(seat, line, "${seats[seat].name} says \"$line\"")
        }
        val cardCount = seats[seat].cards.size
        val cleaned = if (decision is Decision.Discard) Decision.Discard(decision.indices.filter { it in 0 until cardCount }.toSet()) else decision
        val d = legalActions(seat).coerce(cleaned)
        if (discardRound) applyDiscard(seat, (d as Decision.Discard).indices) else applyBet(seat, d)
    }

    /** Every card [seat] holds, face down ones included: for the game layer, never for other agents. */
    fun holeCards(seat: Int): List<Card> = seats[seat].cards.toList()

    /**
     * Throws [seat]'s starting cards away and deals new ones (a mulligan).
     * Only before the seat has acted on the first street, and not in stud (the up
     * card already set the bring-in). Returns false when not allowed.
     */
    fun redealHoleCards(seat: Int): Boolean {
        val s = seats[seat]
        if (isFinished || variant.isStud || streetIndex != 0 || discardRound || !s.live || s.acted) return false
        muck += s.cards
        val count = s.cards.size
        s.cards.clear()
        s.faceUp.clear()
        repeat(count) {
            s.cards += drawCard()
            s.faceUp += false
        }
        _events += HandEvent.Redealt(seat, "${s.name} takes a mulligan")
        return true
    }

    /** The table as [viewer] sees it (null: a spectator who sees only public cards). */
    fun snapshot(viewer: Int? = null): TableSnapshot {
        val res = _result
        return TableSnapshot(
            variant = variant,
            handNumber = handNumber,
            street = street,
            board = _board.toList(),
            pot = pot,
            pots = buildPots().map { it.first },
            currentBet = currentBet,
            seats = seats.mapIndexed { i, s ->
                val visible = i == viewer || s.revealed
                SeatSnapshot(
                    seat = i,
                    name = s.name,
                    chips = s.chips,
                    bet = s.bet,
                    committed = s.committed,
                    folded = s.folded && s.dealtIn,
                    allIn = s.allIn,
                    sittingOut = !s.dealtIn,
                    isDealer = i == button,
                    isSmallBlind = i == smallBlindSeat,
                    isBigBlind = i == bigBlindSeat,
                    isToAct = i == _toAct,
                    cards = s.cards.mapIndexed { k, c -> if (visible || (s.faceUp[k] && !s.folded)) c else null },
                    faceUp = s.faceUp.toList(),
                    lastAction = s.lastAction,
                    shownHand = res?.handValues?.get(i)?.description,
                    isWinner = res != null && i in res.winners,
                )
            },
            toAct = _toAct,
            finished = isFinished,
            viewer = viewer,
        )
    }

    // ---------------------------------------------------------------- dealing

    private fun nextDealtIn(from: Int): Int {
        for (k in 1..n) {
            val i = (from + k) % n
            if (seats[i].dealtIn) return i
        }
        error("No dealt-in seat")
    }

    /** Seats in order starting left of the button. */
    private fun fromLeftOfButton(): List<Int> = (1..n).map { (button + it) % n }

    private fun drawCard(): Card {
        if (deck.remaining == 0) {
            deck.reshuffleIn(muck)
            muck.clear()
        }
        return deck.deal()
    }

    private fun dealTo(seat: Int, count: Int, up: Boolean = false) {
        val s = seats[seat]
        val dealt = List(count) { drawCard() }
        s.cards += dealt
        repeat(count) { s.faceUp += up }
        _events += HandEvent.Dealt(seat, dealt.map { if (up) it else null })
    }

    private fun start() {
        val dealtIn = seats.indices.filter { seats[it].dealtIn }
        _events += HandEvent.HandStarted(
            handNumber, variant, button,
            "Hand #$handNumber: ${variant.displayName}, ${seats[button].name} has the button",
        )
        if (stakes.ante > 0) for (i in dealtIn) postForced(i, ForcedBet.ANTE, stakes.ante, live = false)
        val order = fromLeftOfButton().filter { seats[it].dealtIn }
        when {
            variant.isStud -> {
                for (i in order) dealTo(i, 2)
                for (i in order) dealTo(i, 1, up = true)
                val shown = order.joinToString(", ") { "${seats[it].name} ${seats[it].cards.last()}" }
                _events += HandEvent.StreetStarted(streets[0], "${streets[0]}: two down each, up cards $shown")
            }
            else -> for (i in order) dealTo(i, variant.holeCards)
        }
        startStreet()
        advanceIfIdle()
    }

    /** Posts a forced bet. [live] bets count toward the round's bet; antes are dead money. */
    private fun postForced(seat: Int, kind: ForcedBet, amount: Int, live: Boolean = true): Int {
        val s = seats[seat]
        val paid = minOf(amount, s.chips)
        if (paid <= 0) return 0
        s.chips -= paid
        s.committed += paid
        if (live) s.bet += paid
        if (s.chips == 0) markAllIn(s)
        val allInNote = if (s.chips == 0) " and is all-in" else ""
        _events += HandEvent.Forced(seat, kind, paid, "${s.name} ${kind.label} $paid$allInNote")
        if (kind != ForcedBet.ANTE) s.lastAction = kind.short
        return paid
    }

    private fun markAllIn(s: SeatState) {
        s.allIn = true
        s.everAllIn = true
    }

    private fun moreCardsToCome(): Boolean = when {
        variant.isStud -> streetIndex < streets.lastIndex
        variant.isDraw -> streetIndex == 0
        else -> _board.size < 5
    }

    // ---------------------------------------------------------------- streets

    /** Deals the current street and sets up its betting or discard round. */
    private fun startStreet() {
        val name = streets[streetIndex]
        discardRound = name == "Draw" || name == "Discard"
        resetRound()
        if (streetIndex > 0 && !variant.isStud) {
            _events += HandEvent.StreetStarted(name, if (variant.hasBoard && name in BOARD_CARDS) null else name)
        }
        when {
            variant.hasBoard && name in BOARD_CARDS -> {
                deck.burn()
                val cards = List(BOARD_CARDS.getValue(name)) { drawCard() }
                _board += cards
                _events += HandEvent.StreetDealt(name, cards, _board.toList(), "$name: ${_board.joinToString(" ")}")
            }
            variant.isStud && streetIndex > 0 -> dealStudStreet()
        }
        val live = fromLeftOfButton().filter { seats[it].live }
        if (discardRound) {
            pendingDiscards.clear()
            pendingDiscards.addAll(live)
            _toAct = pendingDiscards.firstOrNull()
            return
        }
        if (streetIndex == 0) postOpeningBets()
        val first = when {
            streetIndex == 0 && variant.usesBlinds -> (bigBlindSeat!! + 1) % n
            streetIndex == 0 -> bringInSeat?.let { (it + 1) % n } ?: lowestUpCardSeat()
            variant.isStud -> bestShowingSeat()
            else -> (button + 1) % n
        }
        _toAct = nextNeedingAction(first)
    }

    private fun dealStudStreet() {
        val live = fromLeftOfButton().filter { seats[it].live }
        deck.burn()
        val lastStreet = streetIndex == streets.lastIndex
        if (lastStreet && deck.remaining < live.size) {
            // Not enough cards for everyone: one community card plays for all.
            val card = drawCard()
            _board += card
            _events += HandEvent.StreetDealt(streets[streetIndex], listOf(card), _board.toList(), "${streets[streetIndex]}: community card $card")
            return
        }
        for (i in live) dealTo(i, 1, up = !lastStreet)
        val shown = if (lastStreet) "one card each, face down" else
            live.joinToString(", ") { "${seats[it].name} ${seats[it].cards.last()}" }
        _events += HandEvent.StreetStarted(streets[streetIndex], "${streets[streetIndex]}: $shown")
    }

    private fun postOpeningBets() {
        if (variant.usesBlinds) {
            val dealtIn = seats.count { it.dealtIn }
            val sb = if (dealtIn == 2) button else nextDealtIn(button)
            val bb = nextDealtIn(sb)
            smallBlindSeat = sb
            bigBlindSeat = bb
            postForced(sb, ForcedBet.SMALL_BLIND, stakes.smallBlind)
            postForced(bb, ForcedBet.BIG_BLIND, stakes.bigBlind)
            currentBet = stakes.bigBlind
            lastRaiseSize = stakes.bigBlind
            betCount = 1
        } else {
            val seat = lowestUpCardSeat()
            if (stakes.bringIn > 0 && seats[seat].canBet) {
                bringInSeat = seat
                val paid = postForced(seat, ForcedBet.BRING_IN, stakes.bringIn)
                currentBet = paid
                seats[seat].acted = true
                seats[seat].actedAtRaise = 0
            }
        }
    }

    /** Stud's bring-in: the lowest up card among seats that can still bet. */
    private fun lowestUpCardSeat(): Int {
        val candidates = seats.indices.filter { seats[it].canBet }.ifEmpty { seats.indices.filter { seats[it].live } }
        return candidates.minBy { i ->
            val c = seats[i].cards[2]
            c.rank.value * 4 + c.suit.ordinal
        }
    }

    /** Stud from 4th street: the best poker hand showing acts first (ties: nearest left of the button). */
    private fun bestShowingSeat(): Int {
        var best = -1
        var bestSeat = (button + 1) % n
        for (i in fromLeftOfButton()) {
            val s = seats[i]
            if (!s.live) continue
            val up = s.cards.filterIndexed { k, _ -> s.faceUp[k] }
            val codes = IntArray(up.size) { up[it].code }
            val score = HandEvaluator.score(codes, codes.size, variant.usesShortDeck)
            if (score > best) {
                best = score
                bestSeat = i
            }
        }
        return bestSeat
    }

    private fun resetRound() {
        for (s in seats) {
            s.bet = 0
            s.acted = false
            s.actedAtRaise = 0
            if (s.live && !s.allIn) s.lastAction = null
        }
        currentBet = 0
        lastRaiseSize = stakes.bigBlind
        fullRaiseCount = 0
        betCount = 0
        lastAggressor = null
    }

    private fun betSize(): Int = when {
        variant.isStud -> if (streetIndex <= 1) stakes.smallBet else stakes.bigBet
        variant.isDraw -> if (streetIndex == 0) stakes.smallBet else stakes.bigBet
        else -> if (streetIndex <= 1) stakes.smallBet else stakes.bigBet
    }

    private fun fixedRaiseTo(): Int = if (currentBet < betSize()) betSize() else currentBet + betSize()

    private fun othersCanBet(seat: Int): Boolean = seats.indices.any { it != seat && seats[it].canBet }

    private fun needsAction(i: Int): Boolean {
        val s = seats[i]
        if (!s.canBet) return false
        if (s.bet < currentBet) return true
        return !s.acted && othersCanBet(i)
    }

    private fun nextNeedingAction(from: Int): Int? {
        for (k in 0 until n) {
            val i = (from + k) % n
            if (needsAction(i)) return i
        }
        return null
    }

    // ---------------------------------------------------------------- actions

    private fun applyBet(seat: Int, d: Decision) {
        val s = seats[seat]
        val name = s.name
        when (d) {
            Decision.Fold -> {
                s.folded = true
                s.lastAction = "Fold"
                _events += HandEvent.Action(seat, ActionKind.FOLD, 0, s.bet, false, "$name folds")
            }
            Decision.Check -> {
                s.lastAction = "Check"
                _events += HandEvent.Action(seat, ActionKind.CHECK, 0, s.bet, false, "$name checks")
            }
            Decision.Call -> {
                val amount = minOf(currentBet - s.bet, s.chips)
                pay(s, amount)
                s.lastAction = if (s.allIn) "All-in" else "Call"
                val note = if (s.allIn) " and is all-in" else ""
                _events += HandEvent.Action(seat, ActionKind.CALL, amount, s.bet, s.allIn, "$name calls $amount$note")
            }
            is Decision.RaiseTo -> {
                val total = d.total
                val amount = total - s.bet
                val wasBet = currentBet == 0
                val completes = variant.isStud && !wasBet && currentBet < betSize()
                val full = if (variant.structure == BettingStructure.FIXED_LIMIT) total >= fixedRaiseTo()
                else total - currentBet >= lastRaiseSize
                if (full) {
                    if (variant.structure != BettingStructure.FIXED_LIMIT) lastRaiseSize = total - currentBet
                    fullRaiseCount++
                }
                betCount++
                pay(s, amount)
                currentBet = total
                lastAggressor = seat
                val kind = if (wasBet) ActionKind.BET else ActionKind.RAISE
                val verb = when {
                    wasBet -> "bets $total"
                    completes -> "completes to $total"
                    else -> "raises to $total"
                }
                s.lastAction = if (s.allIn) "All-in" else if (wasBet) "Bet $total" else "Raise $total"
                val note = if (s.allIn) " and is all-in" else ""
                _events += HandEvent.Action(seat, kind, amount, total, s.allIn, "$name $verb$note")
            }
            Decision.AllIn, is Decision.Discard -> error("coerce never returns $d here")
        }
        s.acted = true
        s.actedAtRaise = fullRaiseCount
        afterAction(seat)
    }

    private fun pay(s: SeatState, amount: Int) {
        s.chips -= amount
        s.bet += amount
        s.committed += amount
        if (s.chips == 0) markAllIn(s)
    }

    private fun afterAction(seat: Int) {
        val live = seats.indices.filter { seats[it].live }
        if (live.size == 1) {
            awardToLastStanding(live.single())
            return
        }
        _toAct = nextNeedingAction((seat + 1) % n)
        if (_toAct == null) endRound()
    }

    private fun applyDiscard(seat: Int, indices: Set<Int>) {
        val s = seats[seat]
        val valid = indices.filter { it in s.cards.indices }.sortedDescending()
        for (i in valid) {
            muck += s.cards.removeAt(i)
            s.faceUp.removeAt(i)
        }
        if (variant.isDraw) {
            if (valid.isNotEmpty()) dealTo(seat, valid.size)
            s.lastAction = if (valid.isEmpty()) "Stands pat" else "Draws ${valid.size}"
            val text = if (valid.isEmpty()) "${s.name} stands pat" else "${s.name} draws ${valid.size}"
            _events += HandEvent.Discarded(seat, valid.size, text)
        } else {
            s.lastAction = "Discards"
            _events += HandEvent.Discarded(seat, valid.size, "${s.name} discards ${valid.size}")
        }
        pendingDiscards.remove(seat)
        _toAct = pendingDiscards.firstOrNull()
        if (_toAct == null) endRound()
    }

    /** Closes the current round and deals on until a decision is needed or the hand ends. */
    private fun endRound() {
        returnUncalled()
        streetIndex++
        if (streetIndex >= streets.size) {
            streetIndex = streets.lastIndex
            showdown()
            return
        }
        startStreet()
        advanceIfIdle()
    }

    /** Skips rounds nobody needs to act in (everyone all-in), running the cards out. */
    private fun advanceIfIdle() {
        if (_toAct == null && !isFinished) endRound()
    }

    private fun returnUncalled() {
        val top = seats.maxOf { it.bet }
        val topSeats = seats.indices.filter { seats[it].bet == top }
        if (top == 0 || topSeats.size != 1) return
        val i = topSeats.single()
        val second = seats.indices.filter { it != i }.maxOf { seats[it].bet }
        val refund = top - second
        if (refund <= 0) return
        val s = seats[i]
        s.bet -= refund
        s.committed -= refund
        s.chips += refund
        if (s.chips > 0) s.allIn = false
        _events += HandEvent.UncalledReturned(i, refund, "$refund returned to ${s.name}")
    }

    // ---------------------------------------------------------------- settling

    private fun awardToLastStanding(winner: Int) {
        returnUncalled()
        _toAct = null
        val amount = pot
        val s = seats[winner]
        s.chips += amount
        _events += HandEvent.PotAwarded(winner, amount, 0, null, "${s.name} wins $amount")
        finish(listOf(PotResult(0, amount, listOf(winner), mapOf(winner to amount), null)), emptyMap(), showdown = false)
    }

    /** Main pot then side pots: (amount, eligible seats). Folded chips above the last level join the last pot. */
    private fun buildPots(): List<Pair<Int, List<Int>>> {
        val live = seats.indices.filter { seats[it].live }
        val levels = live.map { seats[it].committed }.filter { it > 0 }.distinct().sorted()
        val pots = mutableListOf<Pair<Int, List<Int>>>()
        var prev = 0
        for (level in levels) {
            val amount = seats.sumOf { minOf(it.committed, level) - minOf(it.committed, prev) }
            val eligible = live.filter { seats[it].committed >= level }
            if (amount > 0) pots += amount to eligible
            prev = level
        }
        val leftover = seats.sumOf { it.committed - minOf(it.committed, prev) }
        if (leftover > 0) {
            if (pots.isEmpty()) pots += leftover to live
            else pots[pots.lastIndex] = pots.last().let { (a, e) -> (a + leftover) to e }
        }
        return pots
    }

    private fun showdown() {
        _toAct = null
        discardRound = false
        val order = fromLeftOfButton().let { o ->
            val start = lastAggressor?.let { o.indexOf(it) } ?: 0
            o.drop(start) + o.take(start)
        }.filter { seats[it].live }
        val values = order.associateWith { i -> HandEvaluator.evaluate(seats[i].cards, _board, variant) }
        for (i in order) {
            val s = seats[i]
            s.revealed = true
            val v = values.getValue(i)
            _events += HandEvent.ShowdownReveal(i, s.cards.toList(), v, "${s.name} shows ${s.cards.joinToString(" ")}: ${v.description}")
        }
        val pots = buildPots()
        val results = pots.mapIndexed { index, (amount, eligible) ->
            val best = eligible.maxOf { values.getValue(it).score }
            val winners = fromLeftOfButton().filter { it in eligible && values.getValue(it).score == best }
            val share = amount / winners.size
            val odd = amount % winners.size
            val shares = winners.withIndex().associate { (k, w) -> w to share + if (k < odd) 1 else 0 }
            val description = values.getValue(winners.first()).description
            val label = when {
                pots.size == 1 -> "the pot"
                index == 0 -> "the main pot"
                else -> "side pot $index"
            }
            for ((w, chips) in shares) {
                seats[w].chips += chips
                _events += HandEvent.PotAwarded(w, chips, index, description, "${seats[w].name} wins $chips from $label with $description")
            }
            PotResult(index, amount, eligible, shares, description)
        }
        finish(results, values, showdown = true)
    }

    private fun finish(pots: List<PotResult>, values: Map<Int, HandValue>, showdown: Boolean) {
        _toAct = null
        _result = HandResult(
            startingChips = startingChips,
            finalChips = seats.map { it.chips },
            pots = pots,
            showdown = showdown,
            handValues = values,
            allInSeats = seats.indices.filter { seats[it].everAllIn }.toSet(),
        )
        _events += HandEvent.HandFinished()
    }

    private companion object {
        const val FIXED_LIMIT_CAP = 4
        val BOARD_CARDS = mapOf("Flop" to 3, "Turn" to 1, "River" to 1)
    }
}

/** Plays the hand to the end, asking [agents] (indexed by seat) for every decision. */
suspend fun Hand.playOut(agents: List<PlayerAgent>) {
    while (true) {
        val seat = toAct ?: return
        val reply = agents[seat].decide(context(seat))
        apply(seat, reply.decision, reply.say)
    }
}
