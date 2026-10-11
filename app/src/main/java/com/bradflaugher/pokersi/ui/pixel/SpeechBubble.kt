package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

enum class BubbleTail {
    BottomStart, BottomEnd, TopStart, TopEnd, None;

    internal val onTop: Boolean get() = this == TopStart || this == TopEnd
    internal val atEnd: Boolean get() = this == BottomEnd || this == TopEnd
}

/**
 * A pixel speech bubble for table talk. New text types out like a
 * terminal; a blinking cursor shows while [streaming] (the LLM is still
 * talking). With [thinking] and no text yet, three dots bounce instead.
 */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
    thinking: Boolean = false,
    tail: BubbleTail = BubbleTail.BottomStart,
    charsPerSecond: Int = 40,
    textStyle: androidx.compose.ui.text.TextStyle = PixelType.Body,
) {
    val p = LocalPixelPalette.current
    val unit = LocalPixelUnit.current
    val animated = PixelTheme.animated
    val density = LocalDensity.current
    val tailH = if (tail == BubbleTail.None) 0 else unit * 5

    // Typewriter: reveal characters until caught up with the (growing) text.
    var revealed by remember { mutableIntStateOf(if (animated) 0 else text.length) }
    LaunchedEffect(text, animated) {
        if (!animated) { revealed = text.length; return@LaunchedEffect }
        if (revealed > text.length) revealed = 0
        while (revealed < text.length) {
            delay(1000L / charsPerSecond)
            revealed++
        }
    }
    var blinkOn by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(streaming, animated) {
        if (!animated) { blinkOn = 1f; return@LaunchedEffect }
        while (streaming) { blinkOn = 1f; delay(420); blinkOn = 0f; delay(420) }
        blinkOn = 1f
    }

    Box(
        modifier
            .drawBehind {
                val w = size.width.toInt()
                val h = size.height.toInt() - tailH
                val top = if (tail.onTop) tailH else 0
                val bodyH = if (tail.onTop) size.height.toInt() - tailH else h
                // Hard shadow, outline, fill, bottom shade.
                notchedRect(unit, top + unit, w - unit, bodyH - unit, unit, 2, p.ink.copy(alpha = 0.4f))
                notchedRect(0, top, w - unit, bodyH - unit, unit, 2, p.ink)
                notchedRect(unit, top + unit, w - 3 * unit, bodyH - 3 * unit, unit, 1, p.cardCream)
                pxRect(unit * 2, top + bodyH - 3 * unit, w - 5 * unit, unit, p.cardShade)
                drawTail(tail, w - unit, top, bodyH - unit, unit, p.ink, p.cardCream)
            }
            .padding(
                start = with(density) { (unit * 4).toDp() },
                end = with(density) { (unit * 5).toDp() },
                top = with(density) { (unit * 3 + if (tail.onTop) tailH else 0).toDp() },
                bottom = with(density) { (unit * 4 + if (tail.onTop) 0 else tailH).toDp() },
            ),
    ) {
        if (text.isEmpty() && thinking) {
            ThinkingDots(p.inkSoft)
        } else {
            val visible = text.take(revealed.coerceAtMost(text.length))
            val typing = revealed < text.length
            val showCursor = (streaming || typing) && blinkOn > 0.5f
            BasicText(
                buildAnnotatedString {
                    append(visible)
                    if (streaming || typing) {
                        withStyle(SpanStyle(color = if (showCursor) p.hotPink else Color.Transparent)) { append("_") }
                    }
                },
                style = textStyle.copy(color = p.ink),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTail(
    tail: BubbleTail, w: Int, top: Int, h: Int, u: Int, ink: Color, fill: Color,
) {
    if (tail == BubbleTail.None) return
    // A 6-step staircase tail leaning outwards, outlined in ink. Row 0
    // overwrites the body's bottom outline so the tail opens into the bubble.
    val steps = 6
    val baseX = if (tail.atEnd) w - u * 11 else u * 4
    val down = !tail.onTop
    for (i in 0 until steps) {
        val rowW = (steps - i) * u
        // Slant: the tail's outer edge drifts outward one unit every two rows.
        val drift = (i / 2) * u
        val x = if (tail.atEnd) baseX + i * u + drift else baseX - drift
        val y = if (down) top + h - 2 * u + i * u else top + u - i * u
        if (i == steps - 1) {
            pxRect(x - u, y, rowW + u, u, ink)
        } else {
            pxRect(x - u, y, rowW + 2 * u, u, ink)
            pxRect(x, y, rowW, u, fill)
        }
    }
}

/** Three dots bouncing in turn: the opponent is thinking. */
@Composable
fun ThinkingDots(color: Color, modifier: Modifier = Modifier) {
    val unit = LocalPixelUnit.current
    val density = LocalDensity.current
    val t = if (PixelTheme.animated) {
        rememberInfiniteTransition(label = "dots").animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t")
    } else remember { androidx.compose.runtime.mutableFloatStateOf(0.15f) }
    val dot = unit * 2
    Canvas(modifier.size(with(density) { (dot * 7).toDp() }, with(density) { (dot * 3).toDp() })) {
        for (i in 0 until 3) {
            val ph = (t.value - i * 0.16f)
            val bounce = if (ph in 0f..0.4f) sin(ph / 0.4f * PI).toFloat() else 0f
            val y = dot * 2 - (bounce * dot * 1.5f).toInt()
            pxRect(i * dot * 3, y - dot / 2, dot, dot, color)
        }
    }
}
