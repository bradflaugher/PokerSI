package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.debug.AiLog
import com.bradflaugher.pokersi.debug.AiLogKind
import com.bradflaugher.pokersi.engine.AgentReply
import com.bradflaugher.pokersi.engine.BotStyle
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.engine.HouseBot
import com.bradflaugher.pokersi.engine.PlayerAgent
import com.bradflaugher.pokersi.engine.TableTalk
import com.bradflaugher.pokersi.game.GameController
import com.bradflaugher.pokersi.game.GameSpeed
import com.bradflaugher.pokersi.game.OpponentBrains
import com.bradflaugher.pokersi.game.OpponentGenerator
import com.bradflaugher.pokersi.game.OpponentProfile
import com.bradflaugher.pokersi.game.Pacing
import com.bradflaugher.pokersi.game.QuickGameConfig
import com.bradflaugher.pokersi.game.toPersona
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * A real table, one seat played by a real model through the desktop harness
 * (tools/llm-harness), for judging the table talk over a run of decisions:
 * the [GameController] fills the shared [TableTalk] exactly as in the app.
 * Does nothing unless POKERSI_HARNESS_BIN (the built `pokersi-harness`) and
 * POKERSI_HARNESS_MODEL (a GGUF) are set; prints the transcript, and writes it
 * to POKERSI_HARNESS_TRANSCRIPT when that is set. POKERSI_HARNESS_DECISIONS
 * (default 10) and POKERSI_HARNESS_SEED pick the length and the deal.
 */
class HarnessSessionTest {
    private val bin = System.getenv("POKERSI_HARNESS_BIN")?.let(::File)
    private val model = System.getenv("POKERSI_HARNESS_MODEL")?.let(::File)

    /** Runs each generation as one harness invocation (the model loads each time: slow but simple). */
    private class HarnessBackend(val bin: File, val model: File) : LlmBackend {
        override val isReady = true

        override suspend fun generate(
            messages: List<ChatMessage>,
            grammar: String?,
            maxTokens: Int,
            temperature: Float,
            seed: Int,
            timeoutMs: Long,
            onText: (String) -> Boolean,
        ): String {
            val dir = Files.createTempDirectory("pokersi-session").toFile()
            try {
                File(dir, "step.system.txt").writeText(messages[0].content)
                File(dir, "step.user.txt").writeText(messages[1].content)
                grammar?.let { File(dir, "step.gbnf").writeText(it) }
                val process = ProcessBuilder(
                    bin.path, "-m", model.path, "-d", dir.path, "-o", File(dir, "out").path,
                    "-t", "4", "--temp", temperature.toString(), "--seed", seed.toUInt().toString(), "--max", maxTokens.toString(),
                ).redirectErrorStream(true).start()
                process.inputStream.bufferedReader().readText()
                process.waitFor()
                val reply = File(dir, "out/step.reply.txt").readLines().firstOrNull().orEmpty()
                // Stream it word by word, as the app's token callback would.
                var i = 0
                while (i < reply.length) {
                    i = reply.indexOf(' ', i + 1).let { if (it < 0) reply.length else it }
                    if (!onText(reply.substring(0, i))) break
                }
                return reply
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test
    fun tenDecisionsOfTableTalk() = runBlocking {
        assumeTrue(bin != null && bin.canExecute() && model != null && model.isFile)
        val decisions = System.getenv("POKERSI_HARNESS_DECISIONS")?.toIntOrNull() ?: 10
        val seed = System.getenv("POKERSI_HARNESS_SEED")?.toLongOrNull() ?: 11L
        // The cowboy whose "Saddle up, partner." started this.
        val hero = (1L..10_000L).asSequence().map(Personas::generate).first { it.catchphrase == "Saddle up, partner." }
        val heroProfile = OpponentGenerator.fromPersona(hero)
        val rivals = OpponentGenerator.table(seed, 3).filter { it.name != hero.name }.take(2)
            .map { it.copy(style = it.style.copy(chattiness = 0.9)) }

        var heroDecisions = 0
        val brains = object : OpponentBrains {
            override val label = MutableStateFlow(model!!.name)
            override fun agentFor(seat: Int, opponent: OpponentProfile, fallback: PlayerAgent, talk: TableTalk, talks: () -> Boolean): PlayerAgent {
                if (opponent.name != hero.name) return fallback
                val llm = LlmAgent(HarnessBackend(bin!!, model!!), opponent.toPersona(), fallback, talk, timeoutMs = 120_000, random = kotlin.random.Random(System.nanoTime()))
                return object : PlayerAgent {
                    override suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit): AgentReply =
                        llm.decide(context, onChatter).also { heroDecisions++ }
                }
            }
        }
        AiLog.enabled = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = GameController(
            scope = scope,
            brains = brains,
            pacing = Pacing.Instant,
            speed = MutableStateFlow(GameSpeed.NORMAL),
            compute = Dispatchers.IO,
            autopilot = HouseBot(BotStyle.BALANCED, seed = 3, iterations = 60, talk = false),
            houseBotIterations = 60,
            agentTimeoutMs = 180_000,
        )
        try {
            // A busted hero (or human) ends the table: deal a fresh one (and a fresh memory).
            var table = 0
            fun heroSeated() = controller.table.value.let { t -> t.handNumber == 0 || t.opponents.any { it.name == hero.name && !it.busted } }
            withTimeout(decisions * 90_000L) {
                while (heroDecisions < decisions) {
                    if (!controller.isRunning || !heroSeated()) {
                        controller.startQuick(QuickGameConfig(GameVariant.TEXAS_HOLDEM, listOf(heroProfile) + rivals, 1000, seed + table++))
                    }
                    delay(200)
                }
            }
        } finally {
            controller.stop()
            scope.cancel()
        }

        val transcript = buildString {
            appendLine("model: ${model!!.name}, hero: ${hero.name} (${hero.voice}; catchphrase \"${hero.catchphrase}\")")
            for (e in AiLog.entries.value) {
                when {
                    e.kind == AiLogKind.TABLE -> appendLine("      | ${e.title}")
                    e.source != hero.name -> Unit
                    e.kind == AiLogKind.PROMPT -> appendLine("  hint: ${e.title}")
                    e.kind == AiLogKind.DECISION -> appendLine("  >>>   ${e.title}\n        ${e.body}")
                    e.kind == AiLogKind.ERROR -> appendLine("  !!    ${e.title} ${e.body}")
                }
            }
            val lastPrompt = AiLog.entries.value.lastOrNull { it.kind == AiLogKind.PROMPT && it.source == hero.name }
            appendLine("\nlast prompt (~${(lastPrompt?.body?.length ?: 0) / 4} tokens):\n${lastPrompt?.body}")
        }
        AiLog.enabled = false
        println(transcript)
        System.getenv("POKERSI_HARNESS_TRANSCRIPT")?.let { File(it).writeText(transcript) }
        assertTrue("the hero made $heroDecisions decisions", heroDecisions >= decisions)
    }
}
