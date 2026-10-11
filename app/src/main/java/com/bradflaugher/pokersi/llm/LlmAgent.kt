package com.bradflaugher.pokersi.llm

import android.os.SystemClock
import android.util.Log
import com.bradflaugher.pokersi.engine.AgentReply
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.PlayerAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.random.Random

/** Per-agent numbers for the debug overlay. */
data class AgentStats(
    val decisions: Int = 0,
    /** Decisions the [LlmAgent.fallback] made (no model, timeout, unusable reply, error). */
    val fallbacks: Int = 0,
    val totalMs: Long = 0,
    val lastMs: Long = 0,
    /** The last raw model reply, or why there wasn't one. */
    val lastRaw: String = "",
) {
    val averageMs: Long get() = if (decisions > 0) totalMs / decisions else 0
}

/**
 * A seat played by the local model as [persona]. Each decision is one
 * grammar-constrained generation; the say text streams to the speech bubble
 * as it is written. Anything that goes wrong (no model, timeout, an
 * unusable reply) hands the decision to [fallback], keeping any table talk
 * the model already produced.
 */
class LlmAgent(
    private val engine: LlamaEngine,
    val persona: Persona,
    val fallback: PlayerAgent,
    private val maxTokens: Int = 96,
    private val timeoutMs: Long = 25_000,
    private val random: Random = Random(persona.seed),
) : PlayerAgent {
    private val _stats = MutableStateFlow(AgentStats())
    val stats: StateFlow<AgentStats> = _stats.asStateFlow()

    override suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit): AgentReply {
        val start = SystemClock.elapsedRealtime()
        if (!engine.isReady) return fallBack(context, onChatter, start, null, "no model")

        var lastSay: String? = null
        val raw = try {
            engine.generate(
                messages = PokerPrompt.build(context, persona),
                grammar = PokerGrammar.forContext(context),
                maxTokens = maxTokens,
                temperature = persona.temperature,
                seed = random.nextInt(Int.MAX_VALUE),
                timeoutMs = timeoutMs,
            ) { soFar ->
                val full = LlmReply.sayPrefix(soFar, Int.MAX_VALUE)
                val say = full?.let { LlmReply.sayPrefix(soFar) }
                if (!say.isNullOrEmpty() && say != lastSay) {
                    lastSay = say
                    onChatter(say)
                }
                // The decision comes first; stop once the say has run long (it gets trimmed anyway).
                full == null || full.length <= PokerGrammar.SAY_MAX + 10
            }
        } catch (e: TimeoutCancellationException) {
            return fallBack(context, onChatter, start, lastSay, "timed out after ${timeoutMs / 1000} s")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "${persona.name}: generation failed", e)
            return fallBack(context, onChatter, start, lastSay, "error: ${e.message}")
        }

        val reply = LlmReply.parse(raw, context.legal, context.holeCards.size)
        val decision = reply.decision ?: return fallBack(context, onChatter, start, reply.say ?: lastSay, "unusable: $raw")
        val say = reply.say ?: lastSay
        say?.let(onChatter)
        record(start, fellBack = false, raw = raw)
        return AgentReply(decision, say)
    }

    private suspend fun fallBack(
        context: DecisionContext,
        onChatter: (String) -> Unit,
        start: Long,
        say: String?,
        why: String,
    ): AgentReply {
        val reply = fallback.decide(context, onChatter)
        record(start, fellBack = true, raw = why)
        return if (say != null) reply.copy(say = say) else reply
    }

    private fun record(start: Long, fellBack: Boolean, raw: String) {
        val ms = SystemClock.elapsedRealtime() - start
        _stats.update {
            it.copy(
                decisions = it.decisions + 1,
                fallbacks = it.fallbacks + if (fellBack) 1 else 0,
                totalMs = it.totalMs + ms,
                lastMs = ms,
                lastRaw = raw,
            )
        }
    }

    private companion object {
        const val TAG = "LlmAgent"
    }
}
