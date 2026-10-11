package com.bradflaugher.pokersi.debug

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class AiLogTest {
    @After
    fun tearDown() {
        AiLog.enabled = false
    }

    @Test
    fun offRecordsNothingAndBuildsNothing() {
        AiLog.enabled = false
        var built = false
        val id = AiLog.add(AiLogKind.PROMPT, "Mo", { "title" }) { built = true; "a very long prompt" }
        assertEquals(0L, id)
        assertFalse("the body lambda never ran", built)
        assertTrue(AiLog.entries.value.isEmpty())
    }

    @Test
    fun keepsTheLatestEntriesAndUpdatesStreamingOnes() {
        AiLog.enabled = true
        repeat(AiLog.CAPACITY + 50) { AiLog.note(AiLogKind.TABLE, "TABLE", "line $it") }
        val entries = AiLog.entries.value
        assertEquals(AiLog.CAPACITY, entries.size)
        assertEquals("line ${AiLog.CAPACITY + 49}", entries.last().title)

        val id = AiLog.add(AiLogKind.REPLY, "Mo", { "streaming…" })
        AiLog.update(id, body = """{"action": "fo""")
        AiLog.update(id, title = "done", body = """{"action": "fold"}""")
        val reply = AiLog.entries.value.last()
        assertEquals("done", reply.title)
        assertEquals("""{"action": "fold"}""", reply.body)
    }

    @Test
    fun copiesAsPlainText() {
        val text = AiLog.plainText(
            listOf(AiLogEntry(1, 3_723_004L, AiLogKind.DECISION, "Mo", "FOLD", "say: \"Nope.\"")),
            TimeZone.getTimeZone("UTC"),
        )
        assertEquals("01:02:03.004 DECIDE Mo: FOLD\n    say: \"Nope.\"\n", text)
    }
}
