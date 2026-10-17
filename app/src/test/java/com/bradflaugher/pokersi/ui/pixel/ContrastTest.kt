package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in WCAG contrast for the palette pairs the screens actually draw:
 * 4.5:1 for text, 3:1 for big glyphs (LED digits) and disabled captions.
 * Felt text is checked on the felt's mid and light tones, where labels sit;
 * the lamp's brightest spot is the middle of the table, under the cards.
 */
class ContrastTest {
    private val p = PixelPalette.Default

    private fun assertContrast(name: String, fg: Color, bg: Color, min: Float = CONTRAST_TEXT) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$name: ${"%.2f".format(ratio)}:1, wants $min:1", ratio >= min)
    }

    @Test
    fun ratioMatchesWcag() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(1f, contrastRatio(p.felt, p.felt), 0.001f)
        assertEquals(contrastRatio(p.ink, p.gold), contrastRatio(p.gold, p.ink), 0.0001f)
    }

    @Test
    fun textOnFeltIsLegible() {
        val felts = listOf(
            "lobby mid" to FeltColors.Lobby.mid, "lobby light" to FeltColors.Lobby.light,
            "table mid" to FeltColors.Table.mid, "table dark" to FeltColors.Table.dark,
        )
        val inks = listOf("gray" to p.gray, "white" to p.white, "cream" to p.cardCream, "yellow" to p.yellow, "teal" to p.teal, "amber" to p.ledAmber)
        for ((fn, felt) in felts) for ((tn, text) in inks) assertContrast("$tn on $fn", text, felt)
    }

    @Test
    fun textOnPanelsIsLegible() {
        val panels = listOf("cabinet deep" to p.cabinetDeep, "cabinet black" to p.cabinetBlack, "felt panel" to p.feltDark)
        val inks = listOf(
            "gray" to p.gray, "glow" to p.cabinetGlow, "white" to p.white, "yellow" to p.yellow,
            "teal" to p.teal, "green" to p.green, "cream" to p.cardCream,
        )
        for ((pn, panel) in panels) for ((tn, text) in inks) assertContrast("$tn on $pn", text, panel)
        // Losses, failures and TILT! are only ever on the cabinet blues (seats, the engine panel).
        listOf(p.cabinetDeep, p.cabinetBlack).forEach { assertContrast("red on $it", p.red, it) }
        // Panel headers and the lit pay table row.
        listOf(p.signRed, p.purple, p.hotPinkDark).forEach { assertContrast("yellow header on $it", p.yellow, it) }
        assertContrast("lit pay row", p.white, p.signRed)
    }

    @Test
    fun buttonCaptionsAreLegible() {
        PixelButtonVariant.entries.forEach { v ->
            val c = buttonColors(p, v, enabled = true)
            assertContrast("$v caption", c.text, c.face)
        }
        PixelButtonVariant.entries.forEach { v ->
            val c = buttonColors(p, v, enabled = false)
            assertContrast("disabled $v caption", c.text, c.face, CONTRAST_LARGE)
        }
    }

    @Test
    fun tagsAreLegible() {
        // ActionTag, PixelTag and the "START HERE" ribbon.
        listOf(p.grayDark, p.cabinetLight, p.greenDark, p.purple, p.purpleDark, p.cabinet, p.redDark, p.hotPinkDark)
            .forEach { assertContrast("white tag on $it", p.white, it) }
        assertContrast("yellow tag on sign red", p.yellow, p.signRed)
        assertContrast("raise tag", p.ink, p.gold)
    }

    @Test
    fun ledDigitsReadAsGlyphs() {
        val led = Color(0xFF0A0306)
        listOf(p.ledRed, p.ledAmber, p.ledGreen).forEach { assertContrast("LED $it", it, led, CONTRAST_LARGE) }
    }
}
