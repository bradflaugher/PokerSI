package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.run.RunPhase
import com.bradflaugher.pokersi.run.RunState
import java.util.Locale

/** What the title screen knows, for its attract line. */
data class TitleFacts(
    /** The saved (regular) Poker Tour, if any. */
    val tour: RunState? = null,
    val daily: DailyResult = DailyResult(),
    val stats: LifetimeStats = LifetimeStats(),
    /** Who plays the opponents ("QWEN 3 1.7B", "HOUSE BOT", "LOADING …"). */
    val brainLabel: String = LlmBrains.HOUSE_LABEL,
)

/**
 * The title screen's cabinet status line, like a video poker machine's
 * attract line: a few short messages, each true for the player's
 * state right now, cycled by the screen. A first launch gets welcoming ones.
 */
object AttractLines {
    const val MAX = 6

    fun lines(f: TitleFacts): List<String> = buildList {
        val tour = f.tour?.takeIf { it.phase != RunPhase.GAME_OVER }
        val fresh = tour == null && f.stats.handsPlayed == 0 && !f.daily.played
        if (fresh) add("WELCOME, PLAYER ONE")
        if (tour != null) {
            val next = if (tour.phase == RunPhase.CHOOSE) " · PICK A TABLE" else ""
            add("CHIPS ${formatChips(tour.playerChips)} · TOUR STOP ${tour.level}$next")
        }
        add(
            when {
                f.daily.finished -> "DAILY TOUR: BEST ${f.daily.bestTables} TABLE${if (f.daily.bestTables == 1) "" else "S"}"
                f.daily.played -> "DAILY TOUR IN PROGRESS"
                else -> "TODAY'S DAILY IS OPEN"
            },
        )
        val brain = f.brainLabel.uppercase(Locale.US)
        add(
            when {
                brain == LlmBrains.HOUSE_LABEL -> "HOUSE BOT ON DUTY"
                brain.startsWith("LOADING") -> brain
                else -> "$brain IS AT THE TABLE"
            },
        )
        if (f.stats.biggestPot > 0) add("BIGGEST POT ${formatChips(f.stats.biggestPot)}")
        f.stats.bestHand?.let { add("BEST HAND: ${it.category.label.uppercase(Locale.US)}") }
        add("${GameVariant.entries.size} GAMES · PICK ONE")
    }.take(MAX)
}
