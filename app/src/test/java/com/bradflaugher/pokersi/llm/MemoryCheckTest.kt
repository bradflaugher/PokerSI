package com.bradflaugher.pokersi.llm

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCheckTest {
    private val gb = 1L shl 30

    @Test
    fun smallModelFitsABigPhone() {
        assertNull(MemoryCheck.problem("Tiny", gb, totalMem = 8 * gb, availMem = 3 * gb))
    }

    @Test
    fun modelBiggerThanThePhoneIsRefused() {
        val why = MemoryCheck.problem("Huge", 7 * gb, totalMem = 6 * gb, availMem = 4 * gb)
        assertNotNull(why)
        assertTrue(why!!, why.contains("too big"))
    }

    @Test
    fun lowFreeMemoryAsksToCloseApps() {
        val why = MemoryCheck.problem("Mid", 2 * gb, totalMem = 8 * gb, availMem = gb / 4)
        assertNotNull(why)
        assertTrue(why!!, why.contains("Close some apps"))
    }

    @Test
    fun unknownSizesAreNotRefused() {
        assertNull(MemoryCheck.problem("Unknown", -1, totalMem = 4 * gb, availMem = gb))
        assertNull(MemoryCheck.problem("Unknown", 2 * gb, totalMem = 0, availMem = 0))
    }
}
