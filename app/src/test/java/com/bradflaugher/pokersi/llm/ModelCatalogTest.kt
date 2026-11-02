package com.bradflaugher.pokersi.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun idsAndFileNamesAreUnique() {
        // A new model version needs a new id and file name (see ModelCatalog).
        val models = ModelCatalog.models
        assertEquals(models.size, models.map { it.id }.toSet().size)
        assertEquals(models.size, models.map { it.fileName }.toSet().size)
    }

    @Test
    fun exactlyOneRecommended() {
        assertEquals(1, ModelCatalog.models.count { it.recommended })
    }

    @Test
    fun orphanPartsAreTheRetiredOnesOnly() {
        val current = ModelCatalog.models.map { it.fileName }.toSet()
        val live = current.first()
        val names = listOf("$live.part", live, "genaro-poker-0.6b-Q4_K_M.gguf.part", "genaro-poker-0.6b-Q4_K_M.gguf", "notes.txt")
        assertEquals(listOf("genaro-poker-0.6b-Q4_K_M.gguf.part"), ModelStore.orphanParts(names, current))
    }
}
