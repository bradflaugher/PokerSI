package com.bradflaugher.pokersi.ui.screens

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BubbleStackTest {
    private fun box(x: Int, y: Int, w: Int, h: Int) = IntRect(IntOffset(x, y), IntSize(w, h))

    private fun assertNoOverlap(rects: List<IntRect>) {
        for (i in rects.indices) for (j in i + 1 until rects.size) {
            assertFalse("bubbles $i and $j overlap: ${rects[i]} ${rects[j]}", rects[i].overlaps(rects[j]))
        }
    }

    @Test
    fun bubblesThatDoNotTouchStayPut() {
        val wanted = listOf(box(0, 100, 200, 60), box(300, 100, 200, 60))
        assertEquals(wanted, stackBubbles(wanted, gap = 4))
    }

    @Test
    fun neighboursTalkingAtOnceStackInsteadOfOverlapping() {
        // Two seats side by side, both with long lines: the bubbles are wider than a seat.
        val wanted = listOf(box(10, 100, 300, 80), box(200, 100, 300, 80))
        val placed = stackBubbles(wanted, gap = 4)
        assertNoOverlap(placed)
        assertEquals(wanted[0], placed[0]) // the first (leftmost) keeps its place
        assertEquals(184, placed[1].top) // the second drops just below it
        assertEquals(200, placed[1].left) // and keeps its x, so its tail still points at its seat
    }

    @Test
    fun aBubbleFromTheRowAboveIsNotCoveredByTheRowBelow() {
        // A top-row bubble hangs down over the second row, whose seat is also talking.
        val wanted = listOf(box(200, 300, 250, 50), box(50, 100, 300, 260))
        val placed = stackBubbles(wanted, gap = 4)
        assertNoOverlap(placed)
        assertEquals(wanted[1], placed[1])
        assertEquals(364, placed[0].top)
    }

    @Test
    fun aWholeTableTalkingNeverOverlaps() {
        val wanted = (0 until 6).map { i -> box(40 * (i % 3), 100 + 120 * (i / 3), 260, 90 + 10 * i) }
        val placed = stackBubbles(wanted, gap = 4)
        assertNoOverlap(placed)
        placed.zip(wanted).forEach { (p, w) -> assertEquals(w.left, p.left); assert(p.top >= w.top) }
    }
}
