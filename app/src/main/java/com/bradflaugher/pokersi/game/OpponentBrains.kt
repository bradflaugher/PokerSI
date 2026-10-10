package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.PlayerAgent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Who plays the opponents. The app wires the LLM implementation in (see
 * `AppServices.brains`); [HouseOnlyBrains] is the default and is what plays
 * whenever no model is loaded.
 *
 * [agentFor] is called once per seat whenever a table is set up (a quick game
 * starts, a run table starts). [fallback] is a [com.bradflaugher.pokersi.engine.HouseBot]
 * with the opponent's style: an LLM agent should hand any decision it fumbles
 * (timeout, bad output) to it so a hand never stalls.
 */
interface OpponentBrains {
    /** Short status for the table header, e.g. "QWEN 3 1.7B" or "HOUSE BOT (no model)". */
    val label: StateFlow<String>

    fun agentFor(seat: Int, opponent: OpponentProfile, fallback: PlayerAgent): PlayerAgent
}

/** Every seat is played by the built-in House Bot. */
object HouseOnlyBrains : OpponentBrains {
    private val _label = MutableStateFlow("HOUSE BOT (no model)")
    override val label: StateFlow<String> = _label.asStateFlow()

    override fun agentFor(seat: Int, opponent: OpponentProfile, fallback: PlayerAgent): PlayerAgent = fallback
}

/**
 * An opponent at the table: what the UI shows (name, procedural pixel face from
 * [avatarSeed], bio, catchphrase) and how the House Bot fallback plays ([style]).
 * An LLM agent builds its persona prompt from the same fields.
 */
data class OpponentProfile(
    val name: String,
    val avatarSeed: Long,
    val style: BotStyle,
    val bio: String,
    val catchphrase: String,
)
