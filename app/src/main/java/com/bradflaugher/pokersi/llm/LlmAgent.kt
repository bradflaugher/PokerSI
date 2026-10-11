package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.debug.AiLog
import com.bradflaugher.pokersi.debug.AiLogKind
import com.bradflaugher.pokersi.engine.AgentReply
import com.bradflaugher.pokersi.engine.BotLines
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.PlayerAgent
import com.bradflaugher.pokersi.engine.TableTalk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.util.Locale
import kotlin.random.Random

/**
 * A seat played by the local model as [persona]. Each decision is one
 * grammar-constrained generation; the say text streams to the speech bubble
 * as it is written. Anything that goes wrong (no model, timeout, an
 * unusable reply) hands the decision to [fallback], keeping any table talk
 * the model already produced.
 *
 * [talk] is the table's shared memory of what was said: the prompt shows the
 * latest lines and a hint of what to talk about (see [TalkPlan]), and a line
 * that repeats something already said, or names the seat's hand
 * ([HandTalk]), is held back while it streams and then swapped for a fresh
 * House Bot line, or silence.
 */
class LlmAgent(
    private val engine: LlmBackend,
    val persona: Persona,
    val fallback: PlayerAgent,
    private val talk: TableTalk = TableTalk(),
    private val maxTokens: Int = 96,
    private val timeoutMs: Long = 25_000,
    private val random: Random = Random(persona.seed),
    /** Talk every turn instead of now and then (tests). */
    private val alwaysTalks: Boolean = false,
) : PlayerAgent {
    private val who = persona.name

    /** The kind of the last talk hint, so the next one differs. */
    @Volatile private var lastHintKind: String? = null

    override suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit): AgentReply {
        val start = now()
        if (!engine.isReady) return fallBack(context, onChatter, null, "no model")

        val plan = TalkPlan.make(context, persona, talk, random, lastHintKind, alwaysSpeak = alwaysTalks)
        lastHintKind = plan.kind
        // Off-turn, the catchphrase counts as already said.
        val avoid = if (plan.catchphraseOk || persona.catchphrase.isBlank()) emptyList() else listOf(persona.catchphrase)
        val messages = PokerPrompt.build(context, persona, plan)
        val grammar = PokerGrammar.forContext(context, plan.speak)
        AiLog.add(AiLogKind.PROMPT, who, { "hand ${context.handNumber}, ${context.street}: ${if (plan.speak) plan.hint else "(quiet turn)"}" }) {
            messages.joinToString("\n\n") { "[${it.role}]\n${it.content.trimEnd()}" }
        }
        AiLog.add(AiLogKind.GRAMMAR, who, { "grammar (${grammar.lines().size} rules)" }) { grammar }
        val replyId = AiLog.add(AiLogKind.REPLY, who, { "streaming…" })

        // What the bubble shows (whole words): kept if the House Bot has to decide.
        var shown: String? = null
        val seen = seenCards(context)
        var firstTokenAt = 0L
        var tokens = 0
        val raw = try {
            engine.generate(
                messages = messages,
                grammar = grammar,
                maxTokens = maxTokens,
                temperature = persona.temperature,
                seed = random.nextInt(Int.MAX_VALUE),
                timeoutMs = timeoutMs,
            ) { soFar ->
                tokens++
                if (firstTokenAt == 0L) firstTokenAt = now()
                AiLog.update(replyId, body = soFar)
                val full = LlmReply.sayPrefix(soFar, Int.MAX_VALUE)
                val say = full?.let { LlmReply.sayPrefix(soFar) }
                // Whole words only until the say is closed: a cut-off word can't be judged yet ("ace" of
                // "aces"). Punctuation ends a word too ("Yeehaw!").
                val words = if (say == null || sayClosed.containsMatchIn(soFar) || say.last() in wordEnds) say
                    else say.substringBeforeLast(' ', "").trimEnd(' ', ',', ';', ':', '-')
                // Held back while it could still turn out to be a line already said, or gives the hand away.
                // The bubble shows [words], which may end sooner than [say]: both are checked.
                if (!words.isNullOrEmpty() && words != shown && !talk.mightRepeat(say!!, avoid, who, PokerGrammar.SAY_MAX) &&
                    !talk.isRepeat(words, who, avoid) && !HandTalk.revealsHand(say, seen) && !HandTalk.revealsHand(words, seen)
                ) {
                    shown = words
                    onChatter(words)
                }
                // The decision comes first; stop once the say has run long (it gets trimmed anyway).
                full == null || full.length <= PokerGrammar.SAY_MAX + 10
            }
        } catch (e: TimeoutCancellationException) {
            AiLog.update(replyId, title = "timed out")
            return fallBack(context, onChatter, shown, "timed out after ${timeoutMs / 1000} s", start, avoid, quiet = !plan.speak)
        } catch (e: CancellationException) {
            AiLog.update(replyId, title = "cancelled")
            throw e
        } catch (e: Exception) {
            AiLog.update(replyId, title = "failed")
            return fallBack(context, onChatter, shown, "error: ${e.message}", start, avoid, quiet = !plan.speak)
        }
        val end = now()
        AiLog.update(replyId, title = "${raw.length} chars", body = raw)

        // A say cut off by the token limit keeps only its whole words, as the bubble showed it.
        val reply = LlmReply.parse(raw, context.legal, context.holeCards.size).let { if (sayClosed.containsMatchIn(raw)) it else it.copy(say = shown) }
        val decision = reply.decision ?: return fallBack(context, onChatter, reply.say ?: shown, "unusable reply: $raw", start, avoid, quiet = !plan.speak)
        val say = fresh(reply.say ?: shown, decision, context, avoid)
        say?.let(onChatter)
        AiLog.add(AiLogKind.DECISION, who, {
            val coerced = if (reply.parsed != null && reply.parsed != decision) " (model said ${describe(reply.parsed)})" else ""
            "${describe(decision)}$coerced · ${timing(start, firstTokenAt, end, tokens)}"
        }) { say?.let { "say: \"$it\"" } ?: "say: (nothing)" }
        return AgentReply(decision, say)
    }

    /**
     * [say], unless it repeats something said at the table or gives the hand
     * away ([HandTalk]): then a House Bot line for [decision] nobody said
     * lately, or nothing at all.
     */
    private fun fresh(say: String?, decision: Decision, context: DecisionContext, avoid: List<String>): String? {
        if (say == null) return null
        val why = when {
            talk.isRepeat(say, who, avoid) -> "a repeat"
            HandTalk.revealsHand(say, seenCards(context)) -> "a hand giveaway"
            else -> return say
        }
        val instead = BotLines.pickFresh(BotLines.momentFor(decision, context.legal.canCheck), random, talk)
        AiLog.add(AiLogKind.ERROR, who, { "dropped $why: \"$say\"" }) { instead?.let { "said instead: \"$it\"" } ?: "said nothing instead" }
        return instead
    }

    private suspend fun fallBack(
        context: DecisionContext,
        onChatter: (String) -> Unit,
        say: String?,
        why: String,
        start: Long = now(),
        avoid: List<String> = emptyList(),
        /** A quiet turn ([TalkPlan.speak] false) stays quiet, whoever ends up deciding. */
        quiet: Boolean = false,
    ): AgentReply {
        // The House Bot's own line is not streamed: the model's line may still be kept,
        // and a bubble must not flash the one that loses.
        val decided = fallback.decide(context) {}
        val reply = if (quiet) decided.copy(say = null) else decided
        AiLog.add(AiLogKind.ERROR, who, { "House Bot decides (${why.lineSequence().first().take(80)}): ${describe(reply.decision)}" }) {
            "after ${now() - start} ms\n$why"
        }
        val kept = say?.takeUnless { quiet }?.let { fresh(it, reply.decision, context, avoid) }
        val final = if (kept != null) reply.copy(say = kept) else reply
        final.say?.let(onChatter)
        return final
    }

    /** Cards everyone can see, which may be talked about: "that Kd on the board". */
    private fun seenCards(context: DecisionContext): List<Card> =
        context.board + context.seats.filter { it.seat != context.seat }.flatMap { it.cards.filterNotNull() }

    private fun timing(start: Long, firstTokenAt: Long, end: Long, tokens: Int): String {
        val total = end - start
        if (firstTokenAt == 0L || tokens < 2 || end <= firstTokenAt) return "$total ms"
        val rate = (tokens - 1) * 1000.0 / (end - firstTokenAt)
        return String.format(Locale.US, "%d ms (prompt %d ms) · %d tok · %.1f tok/s", total, firstTokenAt - start, tokens, rate)
    }

    private fun now(): Long = System.nanoTime() / 1_000_000

    companion object {
        /** Characters after which the last word of a streaming say is complete. */
        private const val wordEnds = ".!?,;:…"

        /** A finished say field (the grammar allows no quotes inside it). */
        private val sayClosed = Regex(""""say"\s*:\s*"[^"]*"""")

        /** A decision in console words: "RAISE 120", "DISCARD 1 3". */
        fun describe(d: Decision): String = when (d) {
            Decision.Fold -> "FOLD"
            Decision.Check -> "CHECK"
            Decision.Call -> "CALL"
            Decision.AllIn -> "ALL IN"
            is Decision.RaiseTo -> "RAISE ${d.total}"
            is Decision.Discard -> if (d.indices.isEmpty()) "STAND PAT" else "DISCARD " + d.indices.joinToString(" ") { "${it + 1}" }
        }
    }
}
