package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.GameVariant

/** The game picker's choices: everything a [QuickGameConfig] needs. */
data class QuickGameSetup(
    val variant: GameVariant = GameVariant.TEXAS_HOLDEM,
    val opponentCount: Int = 3,
    val stack: Int = 1000,
    /** Seeds the opponents (SHUFFLE re-rolls it) and the deck. */
    val seed: Long = 1L,
) {
    val opponents: List<OpponentProfile> get() = OpponentGenerator.table(seed, opponentCount)

    fun config(): QuickGameConfig = QuickGameConfig(variant, opponents, stack, seed)

    companion object {
        val STACKS = listOf(500, 1000, 2500, 5000)
    }
}
