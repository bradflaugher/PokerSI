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
        val plan = TalkPlan.make(context, cowboy, talk, Random(3), alwaysSpeak = true)
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
    fun charactersTalkOnSomeTurnsNotAll() {
        val quiet = TableTalk()
        val random = Random(11)
        val talking = (1..1000).count { TalkPlan.make(context, cowboy, quiet, random).speak }
        assertTrue("about ${TalkPlan.SPEAK_OTHERWISE}: $talking/1000", talking in 220..380)
        // Spoken to in this hand since our last move: more often.
        val talkedAt = context.copy(history = context.history + "Miss Quill says \"Shhh! I'm raising.\"")
        val answering = (1..1000).count { TalkPlan.make(talkedAt, cowboy, quiet, random).speak }
        assertTrue("about ${TalkPlan.SPEAK_ANSWER}: $answering/1000", answering in 470..630)
        // Once we've acted since, the boost is used up, however recent the line in TableTalk.
        val answered = talkedAt.copy(history = talkedAt.history + "${cowboy.name} checks")
        val after = (1..1000).count { TalkPlan.make(answered, cowboy, talkWith("Miss Quill" to "Shhh! I'm raising."), random).speak }
        assertTrue("back to about ${TalkPlan.SPEAK_OTHERWISE}: $after/1000", after in 220..380)
        assertTrue((1..50).all { TalkPlan.make(context, cowboy, quiet, random, alwaysSpeak = true).speak })
        // Facing a bet (whatever happened since): the pressure rate.
        val facing = context.copy(legal = context.legal.copy(canCheck = false, toCall = context.bigBlind * 3))
        val pressed = (1..1000).count { TalkPlan.make(facing, cowboy, quiet, random).speak }
        assertTrue("about ${TalkPlan.SPEAK_PRESSURE}: $pressed/1000", pressed in 370..530)
    }

    @Test
    fun aQuietTurnLeavesTheTalkOutOfThePromptAndTheReply() {
        val talk = talkWith("Miss Quill" to "Shhh! I'm raising.")
        val plan = (1..200).map { TalkPlan.make(context, cowboy, talk, Random(it)) }.first { !it.speak }
        val user = PokerPrompt.build(context, cowboy, plan)[1].content
        assertTrue(user.contains("Stay quiet this turn"))
        assertFalse("no table talk to read", user.contains("Table talk:"))
        assertFalse(user.contains("For \"say\""))
        assertFalse("never offers the catchphrase on a quiet turn", plan.catchphraseOk)
        val grammar = PokerGrammar.forContext(context, speak = false)
        assertFalse("the reply can't talk", grammar.lines().first().contains("say"))
        assertTrue(PokerGrammar.forContext(context).lines().first().contains("say"))
        // The system prompt is the same either way.
        assertEquals(PokerPrompt.build(context, cowboy, plan)[0], PokerPrompt.build(context, cowboy, plan.copy(speak = true))[0])
    }

    @Test
    fun withTableTalkOffEveryTurnIsQuiet() = runBlocking {
        val backend = ScriptedBackend("""{"action": "check", "amount": 0}""")
        val agent = LlmAgent(backend, cowboy, houseBot, talkWith("Miss Quill" to "hi"), alwaysTalks = true, talks = { false })
        repeat(5) {
            val reply = agent.decide(context)
            assertEquals(null, reply.say)
        }
        assertTrue(backend.prompts.all { it[1].content.contains("Stay quiet this turn") })
    }

    @Test
    fun aQuietReplyDecidesAndSaysNothing() = runBlocking {
        val backend = ScriptedBackend("""{"action": "check", "amount": 0}""")
        // A random that always rolls high: every turn is quiet.
        val agent = LlmAgent(backend, cowboy, houseBot, talkWith("Miss Quill" to "hi"), random = object : Random() {
            override fun nextBits(bitCount: Int) = (1 shl bitCount) - 1
        })
        val bubble = mutableListOf<String>()
        val reply = agent.decide(context) { bubble += it }
        assertEquals(Decision.Check, reply.decision)
        assertEquals(null, reply.say)
        assertTrue(bubble.isEmpty())
        assertTrue(backend.prompts.single()[1].content.contains("Stay quiet this turn"))
    }

    @Test
    fun aQuietTurnStaysQuietWhenTheHouseBotTakesOver() = runBlocking {
        val broken = object : LlmBackend {
            override val isReady = true
            override suspend fun generate(
                messages: List<ChatMessage>, grammar: String?, maxTokens: Int, temperature: Float, seed: Int, timeoutMs: Long,
                onText: (String) -> Boolean,
            ): String = "not json at all"
        }
        val agent = LlmAgent(broken, cowboy, houseBot, talkWith("Miss Quill" to "hi"), random = object : Random() {
            override fun nextBits(bitCount: Int) = (1 shl bitCount) - 1
        })
        val bubble = mutableListOf<String>()
        val reply = agent.decide(context) { bubble += it }
        assertEquals(Decision.Check, reply.decision)
        assertEquals("the House Bot's \"House!\" is dropped", null, reply.say)
        assertTrue(bubble.isEmpty())
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
        val agent = LlmAgent(backend, cowboy, houseBot, talk, random = Random(2), alwaysTalks = true)
        val bubble = mutableListOf<String>()
        val reply = agent.decide(context) { bubble += it }
        assertEquals(Decision.Check, reply.decision)
        assertTrue("streamed: $bubble", bubble.none { "saddle" in it.lowercase() })
        // Swapped for a fresh House Bot line for the decision, or silence.
        assertTrue(reply.say == null || reply.say in BotLines.lines.getValue(com.bradflaugher.pokersi.engine.TalkMoment.CHECK))
        assertEquals(reply.say, bubble.lastOrNull())
    }

    @Test
    fun aLineThatGivesTheHandAwayNeverReachesTheBubble() = runBlocking {
        val backend = ScriptedBackend("""{"action": "check", "amount": 0, "say": "Yeehaw, I got me a pair of kings!"}""")
        val agent = LlmAgent(backend, cowboy, houseBot, TableTalk(), random = Random(2), alwaysTalks = true)
        val bubble = mutableListOf<String>()
        val reply = agent.decide(context) { bubble += it }
        assertEquals(Decision.Check, reply.decision)
        assertTrue("streamed: $bubble", bubble.none { "kings" in it.lowercase() })
        assertTrue(reply.say == null || reply.say in BotLines.lines.getValue(com.bradflaugher.pokersi.engine.TalkMoment.CHECK))
        assertEquals(reply.say, bubble.lastOrNull())
        assertTrue(backend.prompts.single()[1].content.contains("Never mention your cards or say what hand you have."))
    }

    @Test
    fun aSayCutOffByTheTokenLimitKeepsItsWholeWords() = runBlocking {
        val backend = ScriptedBackend("""{"action": "check", "amount": 0, "say": "Howdy, Miss Quill, my fri""")
        val bubble = mutableListOf<String>()
        val reply = LlmAgent(backend, cowboy, houseBot, TableTalk(), alwaysTalks = true).decide(context) { bubble += it }
        assertEquals("Howdy, Miss Quill, my", reply.say)
        assertTrue("streamed: $bubble", bubble.none { it.endsWith("fri") })
        // Punctuation ends a word, so a cut-off line ending in it is kept whole.
        val punct = ScriptedBackend("""{"action": "check", "amount": 0, "say": "Howdy, partner!""")
        assertEquals("Howdy, partner!", LlmAgent(punct, cowboy, houseBot, TableTalk(), alwaysTalks = true).decide(context).say)
        val paren = ScriptedBackend("""{"action": "check", "amount": 0, "say": "Howdy, (partner)""")
        assertEquals("Howdy, (partner)", LlmAgent(paren, cowboy, houseBot, TableTalk(), alwaysTalks = true).decide(context).say)
    }

    @Test
    fun anEmptySayWhileStreamingIsNotAFailure() = runBlocking {
        // A token that ends right after the opening quote: the say so far is "".
        val backend = object : LlmBackend {
            override val isReady = true
            override suspend fun generate(
                messages: List<ChatMessage>, grammar: String?, maxTokens: Int, temperature: Float, seed: Int, timeoutMs: Long,
                onText: (String) -> Boolean,
            ): String {
                val reply = """{"action": "check", "amount": 0, "say": "Howdy there!"}"""
                onText(reply.substringBefore("Howdy"))
                onText(reply)
                return reply
            }
        }
        val reply = LlmAgent(backend, cowboy, houseBot, TableTalk(), alwaysTalks = true).decide(context)
        assertEquals("the model decided, not the House Bot", "Howdy there!", reply.say)
    }

    @Test
    fun aFreshLineStreamsAndIsKept() = runBlocking {
        val talk = talkWith("Miss Quill" to "Shhh! I'm raising.")
        val backend = ScriptedBackend("""{"action": "check", "amount": 0, "say": "Howdy, Miss Quill, my horse says hush."}""")
        val agent = LlmAgent(backend, cowboy, houseBot, talk, alwaysTalks = true)
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
        LlmAgent(backend, cowboy, houseBot, alwaysTalks = true).decide(context)
        assertTrue(AiLog.entries.value.isEmpty())

        AiLog.enabled = true
        LlmAgent(backend, cowboy, houseBot, alwaysTalks = true).decide(context)
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
        val reply = LlmAgent(broken, cowboy, houseBot, alwaysTalks = true).decide(context)
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
        val reply = LlmAgent(dies, cowboy, chatty, TableTalk(), alwaysTalks = true).decide(context) { bubble += it }
        assertEquals(Decision.Check, reply.decision)
        // Up to its last whole word: the stream died, so "hush" may have been cut off.
        val kept = line.substringBeforeLast(' ')
        assertEquals("the model's line is kept", kept, reply.say)
        assertTrue("streamed: $bubble", bubble.none { it == "House!" })
        assertEquals(kept, bubble.last())
    }
}
