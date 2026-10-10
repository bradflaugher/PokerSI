package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

enum class LedColor { Red, Amber, Green }

/**
 * A 7-segment LED counter, like a cabinet's CREDIT and BET windows. Lit
 * segments glow; unlit ones stay faintly visible. When [value] changes the
 * digits roll to the new number (fast for small changes, longer for big
 * ones) and burn brighter while rolling, Balatro chip-count style.
 *
 * @param minDigits leading digits shown as dark ghosts.
 * @param segmentUnits thickness of one segment in chrome pixels.
 */
@Composable
fun LedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    color: LedColor = LedColor.Red,
    minDigits: Int = 6,
    segmentUnits: Int = 2,
    sound: Sfx? = null,
) {
    val p = LocalPixelPalette.current
    val unit = LocalPixelUnit.current
    val animated = PixelTheme.animated
    val player = LocalSoundPlayer.current
    val shown = remember { Animatable(value.toFloat()) }
    val heat = remember { Animatable(0f) }
    LaunchedEffect(value) {
        val delta = abs(value - shown.value)
        if (!animated || delta < 1f) {
            shown.snapTo(value.toFloat()); heat.snapTo(0f); return@LaunchedEffect
        }
        sound?.let(player::play)
        heat.snapTo(1f)
        val ms = (260 + log10(delta + 1) * 180).roundToInt().coerceIn(260, 1200)
        shown.animateTo(value.toFloat(), tween(ms, easing = FastOutSlowInEasing))
        heat.animateTo(0f, tween(300))
    }

    val lit = when (color) {
        LedColor.Red -> p.ledRed
        LedColor.Amber -> p.ledAmber
        LedColor.Green -> p.ledGreen
    }
    val digits = max(minDigits, value.coerceAtLeast(0).toString().length)
    val lp = unit * segmentUnits
    // Digit cell: 5 x 9 led-pixels, 2 between digits, 2 padding around.
    val wPx = (digits * 5 + (digits - 1) * 2 + 4) * lp
    val hPx = (9 + 4) * lp
    val density = LocalDensity.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(with(density) { unit.toDp() })) {
        if (label != null) PixelText(label, style = PixelType.Small, color = p.yellow)
        Canvas(Modifier.size(with(density) { (wPx + 2 * unit).toDp() }, with(density) { (hPx + 2 * unit).toDp() })) {
            // Bezel.
            notchedRect(0, 0, wPx + 2 * unit, hPx + 2 * unit, unit, 1, p.grayDark)
            pxRect(unit, unit, wPx, hPx, Color(0xFF0A0306))
            val n = shown.value.roundToInt().coerceAtLeast(0)
            val text = n.toString().padStart(digits, ' ')
            val hot = lerp(lit, Color.White, heat.value * 0.45f)
            text.forEachIndexed { i, ch ->
                val x = unit + (2 + i * 7) * lp
                drawLedDigit(ch, x, unit + 2 * lp, lp, hot, lit.copy(alpha = 0.10f))
            }
        }
    }
}

// Segment bits: a b c d e f g.
private val SEGMENTS = mapOf(
    '0' to 0b1111110, '1' to 0b0110000, '2' to 0b1101101, '3' to 0b1111001, '4' to 0b0110011,
    '5' to 0b1011011, '6' to 0b1011111, '7' to 0b1110000, '8' to 0b1111111, '9' to 0b1111011,
    '-' to 0b0000001, ' ' to 0,
)

/** Draws one pixel-style 7-segment digit (5 x 9 led pixels of [lp] device px). */
fun DrawScope.drawLedDigit(ch: Char, x: Int, y: Int, lp: Int, on: Color, off: Color) {
    val bits = SEGMENTS[ch] ?: 0
    // (x, y, w, h) per segment in led pixels.
    val segs = arrayOf(
        intArrayOf(1, 0, 3, 1), intArrayOf(4, 1, 1, 3), intArrayOf(4, 5, 1, 3), intArrayOf(1, 8, 3, 1),
        intArrayOf(0, 5, 1, 3), intArrayOf(0, 1, 1, 3), intArrayOf(1, 4, 3, 1),
    )
    for (i in 0 until 7) {
        val s = segs[i]
        val lit = bits and (1 shl (6 - i)) != 0
        val sx = x + s[0] * lp; val sy = y + s[1] * lp; val sw = s[2] * lp; val sh = s[3] * lp
        if (lit) {
            val g = max(1, lp / 2)
            pxRect(sx - g, sy - g, sw + 2 * g, sh + 2 * g, on.copy(alpha = 0.22f))
            pxRect(sx, sy, sw, sh, on)
            // Hot core.
            if (lp >= 4) pxRect(sx + lp / 4, sy + lp / 4, sw - lp / 2, sh - lp / 2, lerp(on, Color.White, 0.35f))
        } else {
            pxRect(sx, sy, sw, sh, off)
        }
    }
}
