package com.bradflaugher.pokersi.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonasTest {
    @Test
    fun generatorIsSeededAndVaried() {
        assertEquals(Personas.generate(42), Personas.generate(42))
        val many = (0L until 400L).map(Personas::generate)
        assertTrue(many.map { it.name }.toSet().size >= 40)
        assertTrue(Personas.generatedNames.toSet().size >= 80)
        for (p in many + Personas.handWritten) {
            assertTrue(p.name.isNotBlank() && p.bio.isNotBlank() && p.catchphrase.isNotBlank())
            assertTrue(p.temperature in 0.6f..0.9f)
            listOf(p.tightness, p.aggression, p.bluffiness).forEach { assertTrue(it in 0f..1f) }
            // Catchphrases are spoken inside the prompt's quotes.
            assertTrue(!p.catchphrase.contains('"'))
        }
    }

    @Test
    fun tableSeatsDistinctNames() {
        val table = Personas.table(seed = 7, count = 8, exclude = setOf("Granny Pearl"))
        assertEquals(8, table.size)
        assertEquals(8, table.map { it.name }.toSet().size)
        assertTrue(table.none { it.name == "Granny Pearl" })
        assertEquals(table, Personas.table(seed = 7, count = 8, exclude = setOf("Granny Pearl")))
    }

    @Test
    fun styleWordsReflectTraits() {
        val rock = Personas.handWritten.first { it.name == "Penny Pincher" }
        assertTrue(rock.styleWords.contains("tight"))
        assertTrue(rock.styleWords.contains("never bluffs"))
    }
}
