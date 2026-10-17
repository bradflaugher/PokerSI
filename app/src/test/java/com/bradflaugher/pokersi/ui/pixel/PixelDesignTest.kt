package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelDesignTest {
    @Test
    fun avatarLooksAreDeterministicAndVaried() {
        assertEquals(AvatarLook.from(1234L), AvatarLook.from(1234L))
        val looks = (0L until 200L).map { AvatarLook.from(it) }.toSet()
        assertTrue("expected mostly unique faces, got ${looks.size}", looks.size > 190)
        assertTrue(looks.any { it.species == AvatarLook.Species.Robot })
        assertTrue(looks.any { it.hat != AvatarLook.Hat.None })
        assertNotEquals("", AvatarLook.from(7L).describe())
    }

    @Test
    fun everySoundRendersNonSilentPcm() {
        Sfx.entries.forEach { sfx ->
            val pcm = Synth.render(sfx)
            assertTrue("$sfx is empty", pcm.isNotEmpty())
            assertTrue("$sfx is silent", pcm.any { it.toInt() != 0 })
        }
    }

    @Test
    fun chipBreakdownAddsUp() {
        listOf(0, 1, 7, 135, 2860, 99999).forEach { amount ->
            assertEquals(amount, Chips.breakdown(amount).sumOf { (d, n) -> d.value * n })
        }
        assertEquals("1,250", formatChips(1250))
    }

    @Test
    fun integerScaleFloorsAndNeverDropsBelowOne() {
        assertEquals(4, integerScale(183f, 45))
        assertEquals(1, integerScale(10f, 45))
    }

    @Test
    fun suitAndGlyphPatternsAreWellFormed() {
        // PixelPattern's init rejects ragged rows; touching them all runs it.
        "A23456789TJQK".forEach { PixelFont.Rank.glyph(it) }
        com.bradflaugher.pokersi.engine.Suit.entries.forEach { SuitArt.small(it); SuitArt.pip(it); SuitArt.big(it) }
        assertEquals(PixelFont.Regular.measure("HELD"), 4 * 5 + 3)
    }

    @Test
    fun highContrastLiftsSecondaryTextOnTheFelt() {
        // WCAG contrast ratio of gray text on the lobby felt and on a quiet (high contrast) felt.
        fun ratio(a: Color, b: Color): Float {
            val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
            return (hi + 0.05f) / (lo + 0.05f)
        }
        val felt = FeltColors.Lobby
        val normal = ratio(PixelPalette.Default.gray, felt.lamp)
        val high = ratio(PixelPalette.HighContrast.gray, felt.quiet().lamp)
        assertTrue("high contrast gray reads at $high:1", high >= 7f)
        assertTrue(high > normal)
        assertTrue(PixelPalette.HighContrast.highContrast)
        assertFalse(PixelPalette.Default.highContrast)
    }
}
