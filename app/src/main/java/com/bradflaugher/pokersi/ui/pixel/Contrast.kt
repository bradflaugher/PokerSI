package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow

/**
 * WCAG 2 contrast, for keeping text legible on the cabinet's loud colors.
 * Small text wants at least [CONTRAST_TEXT], big text and UI glyphs at least
 * [CONTRAST_LARGE]. `ContrastTest` holds the palette's key pairs to these.
 */
const val CONTRAST_TEXT = 4.5f
const val CONTRAST_LARGE = 3f

/** WCAG relative luminance of an opaque sRGB color, 0 (black) to 1 (white). */
fun relativeLuminance(color: Color): Float {
    fun channel(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    return (0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)).toFloat()
}

/** The WCAG contrast ratio of two opaque colors, 1 to 21. Order doesn't matter. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * The marquee red behind yellow or white captions (DEAL buttons, panel
 * headers, the lit pay table row): [PixelPalette.suitRed] pressed toward
 * [PixelPalette.suitRedDark] until both pass 4.5:1. Cards keep the bright red.
 */
val PixelPalette.signRed: Color get() = lerp(suitRed, suitRedDark, 0.4f)

/** Hot pink deep enough for white captions (pink buttons). */
val PixelPalette.signPink: Color get() = lerp(hotPink, hotPinkDark, 0.45f)
