package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Pixel-snapped box drawing for UI chrome (buttons, panels, bubbles). All
 * coordinates are whole device pixels and every border is a whole number
 * of chrome pixels ([LocalPixelUnit]), so edges stay crisp at any density.
 */
internal fun DrawScope.pxRect(x: Int, y: Int, w: Int, h: Int, color: Color) {
    if (w <= 0 || h <= 0) return
    drawRect(color, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()))
}

/**
 * A filled rectangle with its corners notched by [cut] units (the classic
 * pixel "rounded" corner): [cut] = 1 removes one unit square per corner,
 * 2 removes a 2-step staircase.
 */
internal fun DrawScope.notchedRect(x: Int, y: Int, w: Int, h: Int, u: Int, cut: Int, color: Color) {
    if (cut <= 0) { pxRect(x, y, w, h, color); return }
    for (i in 0..cut) {
        val inset = (cut - i) * u
        val top = y + i * u
        val rowH = if (i == cut) h - 2 * cut * u else u
        if (i == cut) {
            pxRect(x, top, w, rowH, color)
        } else {
            pxRect(x + inset, top, w - 2 * inset, u, color)
            pxRect(x + inset, y + h - (i + 1) * u, w - 2 * inset, u, color)
        }
    }
}

/**
 * A chunky bevelled box: [ink] outline, [face] fill, a [light] band along
 * the top and left, a [dark] band along the bottom and right.
 */
internal fun DrawScope.bevelBox(
    x: Int, y: Int, w: Int, h: Int, u: Int,
    face: Color, light: Color, dark: Color, ink: Color,
    cut: Int = 1, bevel: Int = 1,
) {
    notchedRect(x, y, w, h, u, cut, ink)
    val ix = x + u; val iy = y + u; val iw = w - 2 * u; val ih = h - 2 * u
    val innerCut = (cut - 1).coerceAtLeast(0)
    notchedRect(ix, iy, iw, ih, u, innerCut, dark)
    notchedRect(ix, iy, iw - bevel * u, ih - bevel * u, u, innerCut, light)
    notchedRect(ix + bevel * u, iy + bevel * u, iw - 2 * bevel * u, ih - 2 * bevel * u, u, 0, face)
}
