package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

/**
 * Small icons drawn on the chrome pixel grid, for buttons whose meaning is
 * a symbol rather than a word. Fonts draw these badly ("II" in a pixel
 * font reads as two letters, not a pause sign).
 */
enum class PixelGlyph(val rows: List<String>) {
    /** Two solid bars. */
    Pause(List(9) { "###..###" }),
}

/** [glyph] in [color], one grid cell per chrome pixel, with an optional one-pixel drop [shadow]. */
@Composable
fun PixelGlyphBox(glyph: PixelGlyph, color: Color, shadow: Color? = null, modifier: Modifier = Modifier) {
    val unit = LocalPixelUnit.current
    val cols = glyph.rows.maxOf { it.length }
    val pad = if (shadow != null) 1 else 0
    val density = LocalDensity.current
    val w = with(density) { (unit * (cols + pad)).toDp() }
    val h = with(density) { (unit * (glyph.rows.size + pad)).toDp() }
    Box(
        modifier.size(w, h).drawBehind {
            fun cells(dx: Int, c: Color) = glyph.rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch -> if (ch == '#') pxRect((x + dx) * unit, (y + dx) * unit, unit, unit, c) }
            }
            shadow?.let { cells(1, it) }
            cells(0, color)
        },
    )
}
