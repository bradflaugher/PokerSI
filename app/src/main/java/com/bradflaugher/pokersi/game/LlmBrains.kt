package com.bradflaugher.pokersi.game

import com.bradflaugher.pokersi.engine.AgentReply
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.PlayerAgent
import com.bradflaugher.pokersi.llm.EngineState
import com.bradflaugher.pokersi.llm.LlamaEngine
import com.bradflaugher.pokersi.llm.LlmAgent
import com.bradflaugher.pokersi.llm.Persona
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

/**
 * Opponents played by the loaded model. Whether a model is loaded is checked
 * at every decision, not when the table is set up, so loading or unloading a
 * model mid-game takes effect on the next decision. With no model, the House
 * Bot fallback plays.
 */
class LlmBrains(private val engine: LlamaEngine, scope: CoroutineScope) : OpponentBrains {
    override val label: StateFlow<String> = engine.state
        .map(::labelFor)
        .stateIn(scope, SharingStarted.Eagerly, labelFor(engine.state.value))

    override fun agentFor(seat: Int, opponent: OpponentProfile, fallback: PlayerAgent): PlayerAgent {
        val llm = LlmAgent(engine, opponent.toPersona(), fallback)
        return object : PlayerAgent {
            override suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit): AgentReply =
                if (engine.isReady) llm.decide(context, onChatter) else fallback.decide(context, onChatter)
        }
    }

    private fun labelFor(state: EngineState): String = when (state) {
        is EngineState.Ready -> state.name.uppercase(Locale.US)
        is EngineState.Loading -> "LOADING ${state.name.uppercase(Locale.US)}…"
        else -> HOUSE_LABEL
    }

    companion object {
        const val HOUSE_LABEL = "HOUSE BOT (no model)"
    }
}

/** The LLM persona for a table profile: the House Bot style's numbers become the persona's traits. */
fun OpponentProfile.toPersona(): Persona = Persona(
    name = name,
    bio = bio,
    voice = voice.ifEmpty { "a quirky poker player with a catchphrase: \"$catchphrase\"" },
    catchphrase = catchphrase,
    tightness = style.tightness.toFloat(),
    aggression = style.aggression.toFloat(),
    bluffiness = (style.bluffing / 0.35).coerceIn(0.0, 1.0).toFloat(),
    seed = avatarSeed,
)
