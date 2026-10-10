package com.bradflaugher.pokersi.llm

import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Bridges the JVM code and the desktop harness (tools/llm-harness). Both
 * tests do nothing unless POKERSI_HARNESS_CASES names a directory:
 *  - [dumpCases] writes every fixture's prompt and grammar there;
 *  - [checkReplies] parses the replies the harness wrote to `out/` with
 *    the real [LlmReply] parser and prints the decisions.
 */
class HarnessCasesTest {
    private val dir = System.getenv("POKERSI_HARNESS_CASES")?.let(::File)

    private fun personaFor(index: Int): Persona = Personas.handWritten[index % Personas.handWritten.size]

    @Test
    fun dumpCases() {
        assumeTrue(dir != null)
        dir!!.mkdirs()
        Fixtures.all.entries.forEachIndexed { i, (name, context) ->
            val seatName = context.seats.first { it.seat == context.seat }.name
            val messages = PokerPrompt.build(context, personaFor(i).copy(name = seatName))
            File(dir, "$name.system.txt").writeText(messages[0].content)
            File(dir, "$name.user.txt").writeText(messages[1].content)
            File(dir, "$name.gbnf").writeText(PokerGrammar.forContext(context))
        }
    }

    @Test
    fun checkReplies() {
        val out = dir?.let { File(it, System.getenv("POKERSI_HARNESS_OUT") ?: "out") }
        assumeTrue(out != null && out.isDirectory)
        for ((name, context) in Fixtures.all) {
            val file = File(out, "$name.reply.txt")
            if (!file.exists()) continue
            for (line in file.readLines().filter { it.isNotBlank() }) {
                val reply = LlmReply.parse(line, context.legal, context.holeCards.size)
                println("$name: ${reply.decision} | ${reply.say}")
                assertNotNull("$name: no decision in $line", reply.decision)
            }
        }
    }
}
