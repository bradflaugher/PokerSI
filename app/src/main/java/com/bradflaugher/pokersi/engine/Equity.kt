package com.bradflaugher.pokersi.engine

import kotlin.random.Random

/** Monte Carlo hand equity: how often a hand wins (ties split) against random opponent hands. */
object Equity {
    /**
     * The hero's expected share of the pot, 0..1, holding [hole] with [board] showing,
     * against [opponents] hands dealt at random from the unseen cards, with the
     * rest of the board (or the rest of a stud hand) run out [iterations] times.
     *
     * [opponentUpCards] gives what each opponent shows (stud up cards); their
     * other cards are random. [dead] cards are known out of play (discards).
     * Draws are not simulated: a draw hand is valued as it stands.
     */
    fun estimate(
        hole: List<Card>,
        board: List<Card>,
        opponents: Int,
        variant: GameVariant,
        iterations: Int = 300,
        rng: Random = Random.Default,
        opponentUpCards: List<List<Card>> = emptyList(),
        dead: Collection<Card> = emptyList(),
    ): Double {
        if (opponents <= 0) return 1.0
        if (hole.isEmpty()) return 1.0 / (opponents + 1)
        val short = variant.usesShortDeck
        val omaha = variant.mustUseTwoHoleCards
        val boardTarget = if (variant.hasBoard) 5 else board.size
        val playerTarget = when {
            variant.hasBoard -> hole.size
            variant.isStud -> 7 - board.size
            else -> hole.size
        }
        val known = HashSet<Int>()
        hole.forEach { known += it.code }
        board.forEach { known += it.code }
        dead.forEach { known += it.code }
        val ups = List(opponents) { opponentUpCards.getOrNull(it).orEmpty() }
        ups.forEach { cards -> cards.forEach { known += it.code } }
        val pool = Deck.fullDeck(short).map { it.code }.filter { it !in known }.toIntArray()

        val boardNeed = (boardTarget - board.size).coerceAtLeast(0)
        val heroNeed = (playerTarget - hole.size).coerceAtLeast(0)
        // Deal as many opponents as the unseen cards allow.
        var opps = opponents
        fun cardsNeeded(o: Int) = boardNeed + heroNeed + (0 until o).sumOf { (playerTarget - ups[it].size).coerceAtLeast(0) }
        while (opps > 1 && cardsNeeded(opps) > pool.size) opps--
        if (cardsNeeded(opps) > pool.size) return 1.0 / (opponents + 1)

        val boardCodes = IntArray(boardTarget)
        board.forEachIndexed { i, c -> boardCodes[i] = c.code }
        val heroCodes = IntArray(playerTarget + boardTarget)
        hole.forEachIndexed { i, c -> heroCodes[i] = c.code }
        val oppCodes = Array(opps) { IntArray(playerTarget + boardTarget) }
        for (o in 0 until opps) ups[o].forEachIndexed { i, c -> oppCodes[o][i] = c.code }
        val scratch = IntArray(5)

        var share = 0.0
        repeat(iterations) {
            var next = 0
            fun draw(): Int {
                val j = next + rng.nextInt(pool.size - next)
                val c = pool[j]
                pool[j] = pool[next]
                pool[next] = c
                next++
                return c
            }
            for (i in board.size until boardTarget) boardCodes[i] = draw()
            for (i in hole.size until playerTarget) heroCodes[i] = draw()
            val heroScore = scoreOf(heroCodes, playerTarget, boardCodes, boardTarget, short, omaha, scratch)
            var best = 0
            var ties = 0
            for (o in 0 until opps) {
                val codes = oppCodes[o]
                for (i in ups[o].size until playerTarget) codes[i] = draw()
                val s = scoreOf(codes, playerTarget, boardCodes, boardTarget, short, omaha, scratch)
                if (s > best) {
                    best = s
                    ties = 0
                }
                if (s == best && s == heroScore) ties++
            }
            if (heroScore > best) share += 1.0
            else if (heroScore == best) share += 1.0 / (ties + 1)
        }
        return share / iterations
    }

    private fun scoreOf(
        player: IntArray, pn: Int, board: IntArray, bn: Int, short: Boolean, omaha: Boolean, scratch: IntArray,
    ): Int {
        if (omaha) return HandEvaluator.scoreOmaha(player, pn, board, bn, short, scratch)
        for (i in 0 until bn) player[pn + i] = board[i]
        return HandEvaluator.score(player, pn + bn, short)
    }
}
