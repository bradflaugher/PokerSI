package com.bradflaugher.pokersi.llm

import com.bradflaugher.pokersi.debug.AiLog
import com.bradflaugher.pokersi.debug.AiLogKind
import com.bradflaugher.pokersi.engine.AgentReply
import com.bradflaugher.pokersi.engine.BotLines
import com.bradflaugher.pokersi.engine.Decision
import com.bradflaugher.pokersi.engine.DecisionContext
import com.bradflaugher.pokersi.engine.PlayerAgent
import com.bradflaugher.pokersi.engine.TableTalk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The table talk: what the prompt shows, the hints, and how the agent keeps lines fresh. */
class TableTalkPromptTest {
    private val cowboy = Persona(
        "Calamity Kate", "Rode into town on a very slow horse.", "an old-west cowboy: says howdy and yeehaw",
        "Saddle up, partner.", 0.5f, 0.5f, 0.2f, seed = 7,
    )
    private val context = Fixtures.flopTopPairCheckedTo.let { c ->
        // Seat 2 is our cowboy here.
        c.copy(seats = c.seats.map { if (it.seat == c.seat) it.copy(name = cowboy.name) else it })
    }

    @After
    fun tearDown() {
        AiLog.enabled = false
    }

    private fun talkWith(vararg lines: Pair<String, String>) = TableTalk().apply { lines.forEach { (who, text) -> record(who, text) } }

    @Test
    fun theCatchphraseIsNotInEveryPrompt() {
        val system = PokerPrompt.build(context, cowboy)[0].content
        assertFalse(system.contains(cowboy.catchphrase))
        val talk = TableTalk()
        val random = Random(1)
        var last: String? = null
        val withCatchphrase = (1..400).count {
            val plan = TalkPlan.make(context, cowboy, talk, random, last)
            last = plan.kind
            val user = PokerPrompt.build(context, cowboy, plan)[1].content
            assertEquals(plan.catchphraseOk, user.contains(cowboy.catchphrase))
            plan.catchphraseOk
        }
        assertTrue("offered now and then: $withCatchphrase/400", withCatchphrase in 1..80)
        // Once said, it isn't offered again.
        talk.record(cowboy.name, cowboy.catchphrase)
        assertTrue((1..100).none { TalkPlan.make(context, cowboy, talk, random).catchphraseOk })
    }

    @Test
    fun promptShowsRecentTalkOwnLinesAndAHint() {
        val talk = talkWith(
            "Calamity Kate" to "Yeehaw, deal 'em!",
            "Miss Quill" to "Shhh! I'm raising.",
            "Calamity Kate" to "My horse could bluff better.",
        )
        val plan = TalkPlan.make(context, cowboy, talk, Random(3))
        val user = PokerPrompt.build(context, cowboy, plan)[1].content
        assertTrue(user.contains("Table talk:\n- Miss Quill: \"Shhh! I'm raising.\""))
        assertTrue(user.contains("You already said \"Yeehaw, deal 'em!\", \"My horse could bluff better.\". Don't repeat yourself."))
        assertTrue(user.contains("For \"say\", as an old-west cowboy: ${plan.hint}"))
        // The hand and the advice stay last.
        assertTrue(user.indexOf("For \"say\"") < user.indexOf("Advice:"))
        // Talk isn't repeated in the hand's history.
        val chatty = context.copy(history = context.history + "Miss Quill says \"Shhh! I'm raising.\"")
        assertFalse(PokerPrompt.build(chatty, cowboy, null)[1].content.contains("says \""))
    }

    @Test
    fun hintsReactToTheTableAndVary() {
        assertEquals("Dusty Boots checks", TalkPlan.lastAction(context, cowboy.name))
        val talk = talkWith("Miss Quill" to "Shhh! I'm raising.")
        val random = Random(5)
        var last: String? = null
        val plans = (1..60).map { TalkPlan.make(context, cowboy, talk, random, last).also { p -> last = p.kind } }
        val hints = plans.map { it.hint }
        assertTrue(hints.any { it == "React to this: Dusty Boots checks." })
        assertTrue(hints.any { it.startsWith("Answer Miss Quill, who said \"Shhh! I'm raising.\"") })
        assertTrue(hints.any { it.contains("Tease") || it.contains("Ask") })
        assertTrue(hints.any { it.contains("board (Kd 7c 2h)") })
        assertTrue("many different hints", hints.toSet().size >= 8)
        assertTrue("never the same kind twice running", plans.zipWithNext().none { (a, b) -> a.kind == b.kind })
    }

    /** A model that always says [say], streaming it a word at a time. */
    private class ScriptedBackend(var reply: String) : LlmBackend {
        override val isReady = true
        var prompts = mutableListOf<List<ChatMessage>>()
        override suspend fun generate(
            messages: List<ChatMessage>, grammar: String?, maxTokens: Int, temperature: Float, seed: Int, timeoutMs: Long,
            onText: (String) -> Boolean,
        ): String {
            prompts += messages
            var i = 0
            while (i < reply.length) {
                i = reply.indexOf(' ', i + 1).let { if (it < 0) reply.length else it }
                onText(reply.substring(0, i))
            }
            return reply
        }
    }

    private val houseBot = object : PlayerAgent {
        override suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit) = AgentReply(Decision.Check, "House!")
    }

    @Test
    fun aRepeatedLineNeverReachesTheBubble() = runBlocking {
        val talk = talkWith("Calamity Kate" to "Saddle up, partner.")
        val backend = ScriptedBackend("""{"action": "check", "amount": 0, "say": "Saddle up, partner."}""")
        val agent = LlmAgent(backend, cowboy, houseBot, talk, random = Random(2))
        val bubble = mutableListOf<String>()
        val reply = agent.decide(context) { bubble += it }
        assertEquals(Decision.Check, reply.decision)
        assertTrue("streamed: $bubble", bubble.none { "saddle" in it.lowercase() })
        // Swapped for a fresh House Bot line for the decision, or silence.
        assertTrue(reply.say == null || reply.say in BotLines.lines.getValue(com.bradflaugher.pokersi.engine.TalkMoment.CHECK))
        assertEquals(reply.say, bubble.lastOrNull())
    }

    @Test
    fun aFreshLineStreamsAndIsKept() = runBlocking {
        val talk = talkWith("Miss Quill" to "Shhh! I'm raising.")
        val backend = ScriptedBackend("""{"action": "check", "amount": 0, "say": "Howdy, Miss Quill, my horse says hush."}""")
        val agent = LlmAgent(backend, cowboy, houseBot, talk)
        val bubble = mutableListOf<String>()
        val reply = agent.decide(context) { bubble += it }
        assertEquals("Howdy, Miss Quill, my horse says hush.", reply.say)
        assertTrue("it streamed", bubble.size > 2)
        // The agent doesn't record; the table does once the line is said.
        assertEquals(1, talk.recent(10).size)
        val prompt = backend.prompts.single()[1].content
        assertTrue(prompt.contains("Miss Quill: \"Shhh! I'm raising.\""))
    }

    @Test
    fun theConsoleRecordsOnlyWhenEnabled() = runBlocking {
        AiLog.enabled = false
        val backend = ScriptedBackend("""{"action": "raise", "amount": 99999, "say": "Yeehaw!"}""")
        LlmAgent(backend, cowboy, houseBot).decide(context)
        assertTrue(AiLog.entries.value.isEmpty())

        AiLog.enabled = true
        LlmAgent(backend, cowboy, houseBot).decide(context)
        val kinds = AiLog.entries.value.map { it.kind }
        assertEquals(listOf(AiLogKind.PROMPT, AiLogKind.GRAMMAR, AiLogKind.REPLY, AiLogKind.DECISION), kinds)
        val entries = AiLog.entries.value
        assertTrue(entries[0].body.contains("[system]") && entries[0].body.contains("[user]"))
        assertEquals(backend.reply, entries[2].body)
        // Coerced: the console shows what the model asked for.
        assertTrue(entries[3].title, entries[3].title.startsWith("RAISE 1060 (model said RAISE 99999)"))
        AiLog.enabled = false
        assertTrue("switching off forgets", AiLog.entries.value.isEmpty())
    }

    @Test
    fun aFailedGenerationFallsBackAndSaysWhy() = runBlocking {
        AiLog.enabled = true
        val broken = object : LlmBackend {
            override val isReady = true
            override suspend fun generate(
                messages: List<ChatMessage>, grammar: String?, maxTokens: Int, temperature: Float, seed: Int, timeoutMs: Long,
                onText: (String) -> Boolean,
            ): String = error("boom")
        }
        val reply = LlmAgent(broken, cowboy, houseBot).decide(context)
        assertEquals(AgentReply(Decision.Check, "House!"), reply)
        val error = AiLog.entries.value.last { it.kind == AiLogKind.ERROR }
        assertTrue(error.title, error.title.contains("House Bot decides (error: boom)"))
        assertNotEquals(0, AiLog.entries.value.size)
    }

    @Test
    fun aFallbackAfterAStreamedLineNeverFlashesTheHouseBotsLine() = runBlocking {
        val line = "Howdy there, my horse Biscuit says hush"
        val dies = object : LlmBackend {
            override val isReady = true
            override suspend fun generate(
                messages: List<ChatMessage>, grammar: String?, maxTokens: Int, temperature: Float, seed: Int, timeoutMs: Long,
                onText: (String) -> Boolean,
            ): String {
                onText("""{"action": "check", "amount": 0, "say": "$line""")
                error("boom")
            }
        }
        val chatty = object : PlayerAgent {
            override suspend fun decide(context: DecisionContext, onChatter: (String) -> Unit): AgentReply {
                onChatter("House!")
                return AgentReply(Decision.Check, "House!")
            }
        }
        val bubble = mutableListOf<String>()
        val reply = LlmAgent(dies, cowboy, chatty, TableTalk()).decide(context) { bubble += it }
        assertEquals(Decision.Check, reply.decision)
        assertEquals("the model's line is kept", line, reply.say)
        assertTrue("streamed: $bubble", bubble.none { it == "House!" })
        assertEquals(line, bubble.last())
    }
}
