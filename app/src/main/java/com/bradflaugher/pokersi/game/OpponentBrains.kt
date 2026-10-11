package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.PlayerAgent
import com.bradflaugher.pokersi.engine.TableTalk
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
 * (timeout, bad output) to it so a hand never stalls. [talk] is the table's
 * memory of what has been said, shared by every seat and new for each table.
 */
interface OpponentBrains {
    /** Short status for the table header, e.g. "QWEN 3 1.7B" or "HOUSE BOT". */
    val label: StateFlow<String>

    fun agentFor(seat: Int, opponent: OpponentProfile, fallback: PlayerAgent, talk: TableTalk): PlayerAgent
}

/** Every seat is played by the built-in House Bot. */
object HouseOnlyBrains : OpponentBrains {
    private val _label = MutableStateFlow("HOUSE BOT")
    override val label: StateFlow<String> = _label.asStateFlow()

    override fun agentFor(seat: Int, opponent: OpponentProfile, fallback: PlayerAgent, talk: TableTalk): PlayerAgent = fallback
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
    /** How they talk, for the LLM ("a pirate: says arr and matey"). Empty means the model picks. */
    val voice: String = "",
) {
    /** What TalkBack says for this character: "Lady Dot, rock". */
    val spoken: String get() = "$name, ${style.name.lowercase()}"
}
