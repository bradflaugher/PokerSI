package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Shakes the element when [trigger] changes (not on first composition):
 * a quick decaying jitter, the arcade "that hit hard" feel. Use on a
 * screen root for screen shake.
 */
fun Modifier.shake(trigger: Any?, strength: Dp = 8.dp, durationMillis: Int = 420): Modifier = composed {
    val progress = remember { Animatable(1f) }
    val first = remember { mutableStateOf(true) }
    val animated = PixelTheme.animated
    LaunchedEffect(trigger) {
        if (first.value) { first.value = false; return@LaunchedEffect }
        if (!animated) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
    }
    graphicsLayer {
        val t = progress.value
        if (t < 1f) {
            val amp = strength.toPx() * (1f - t) * (1f - t)
            translationX = sin(t * 61f) * amp
            translationY = cos(t * 47f) * amp * 0.7f
            rotationZ = sin(t * 33f) * 2.2f * (1f - t)
        }
    }
}

/**
 * A big arcade announcement ("FULL HOUSE!") that pops in with an
 * overshooting scale bounce and a wobble when [visible] becomes true; it
 * settles level, so its pixels rest on the grid.
 * Chunky pixel text with an 8-way ink outline and a hard drop shadow on a
 * bevelled ribbon.
 */
@Composable
fun BigBanner(
    text: String,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    color: Color = LocalPixelPalette.current.yellow,
    ribbon: Color = LocalPixelPalette.current.hotPink,
    style: TextStyle = PixelType.Banner,
    sound: Sfx? = null,
) {
    val p = LocalPixelPalette.current
    val unit = LocalPixelUnit.current
    val animated = PixelTheme.animated
    val player = LocalSoundPlayer.current
    val scale = remember { Animatable(if (visible && !animated) 1f else 0f) }
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(visible, text) {
        if (!visible) { scale.snapTo(0f); return@LaunchedEffect }
        if (!animated) { scale.snapTo(1f); tilt.snapTo(0f); return@LaunchedEffect }
        sound?.let(player::play)
        scale.snapTo(0.2f)
        tilt.snapTo(-14f)
        launchBoth(
            { scale.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow)) },
            { tilt.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow)) },
        )
    }
    if (scale.value <= 0f) return
    val measurer = rememberTextMeasurer()
    val layout = remember(text, style) { measurer.measure(text, style.copy(textAlign = TextAlign.Center)) }
    val padX = unit * 6
    val padY = unit * 4
    val w = layout.size.width + padX * 2 + unit * 2
    val h = layout.size.height + padY * 2 + unit * 2
    Box(
        modifier
            .wrapContentSize()
            .layout { m, _ ->
                val pl = m.measure(Constraints.fixed(w, h))
                layout(w, h) { pl.place(0, 0) }
            }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = tilt.value
            }
            .drawBehind {
                // Ribbon.
                notchedRect(unit * 2, unit * 2, w - unit * 2, h - unit * 2, unit, 2, p.ink.copy(alpha = 0.5f))
                bevelBox(0, 0, w - unit * 2, h - unit * 2, unit, ribbon, lerp(ribbon, Color.White, 0.4f), lerp(ribbon, p.ink, 0.35f), p.ink, cut = 2)
                // Ribbon tails.
                val tailY = h / 2 - unit * 3
                pxRect(-unit * 3, tailY, unit * 4, unit * 6, lerp(ribbon, p.ink, 0.45f))
                pxRect(w - unit * 3, tailY, unit * 4, unit * 6, lerp(ribbon, p.ink, 0.45f))
                val tx = padX.toFloat()
                val ty = padY.toFloat()
                // Hard shadow, 8-way outline, then the fill with a light top half.
                val o = unit.toFloat()
                drawText(layout, color = p.ink, topLeft = Offset(tx + o * 2, ty + o * 2))
                for (dx in -1..1) for (dy in -1..1) if (dx != 0 || dy != 0) {
                    drawText(layout, color = p.ink, topLeft = Offset(tx + dx * o, ty + dy * o))
                }
                drawText(layout, color = color, topLeft = Offset(tx, ty))
            },
    )
}

private suspend fun launchBoth(a: suspend () -> Unit, b: suspend () -> Unit) {
    coroutineScope {
        launch { a() }
        launch { b() }
    }
}

/**
 * A burst of pixel confetti from the centre whenever [trigger] changes
 * (non-null). Particles are whole chrome pixels that flutter and fall.
 * In [PixelRenderMode.Static] a frozen mid-burst frame is drawn instead.
 */
@Composable
fun ConfettiBurst(
    trigger: Any?,
    modifier: Modifier = Modifier,
    particles: Int = 90,
    colors: List<Color> = LocalPixelPalette.current.let { listOf(it.yellow, it.hotPink, it.teal, it.white, it.purple, it.gold, it.green) },
    durationMillis: Int = 1800,
) {
    val unit = LocalPixelUnit.current
    val animated = PixelTheme.animated
    var t by remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(trigger, animated) {
        if (trigger == null) { t = -1f; return@LaunchedEffect }
        if (!animated) { t = 0.42f; return@LaunchedEffect }
        val start = withFrameNanos { it }
        var now = start
        while ((now - start) / 1e6 < durationMillis) {
            now = withFrameNanos { it }
            t = ((now - start) / 1e6f / durationMillis)
        }
        t = -1f
    }
    val seed = trigger?.hashCode() ?: 0
    val parts = remember(seed, particles) {
        val r = Random(seed)
        List(particles) {
            val a = -PI / 2 + (r.nextDouble() - 0.5) * PI * 0.95
            val v = 0.55 + r.nextDouble() * 0.75
            floatArrayOf(cos(a).toFloat() * v.toFloat(), sin(a).toFloat() * v.toFloat() * 1.25f, r.nextFloat() * 6.28f, r.nextFloat(), (it % colors.size).toFloat())
        }
    }
    Canvas(modifier) {
        if (t < 0f) return@Canvas
        val s = size.minDimension
        val g = 2.4f
        val time = t * 1.6f
        val fade = if (t > 0.8f) (1f - t) / 0.2f else 1f
        for (pt in parts) {
            val x = size.width / 2 + pt[0] * s * time * 0.9f + sin(time * 9f + pt[2]) * unit * 3
            val y = size.height * 0.55f + pt[1] * s * time + 0.5f * g * s * time * time * 0.45f
            val flutter = ((sin(time * 14f + pt[2]) + 1f) / 2f)
            val pw = (unit * (1 + (flutter * 2).roundToInt())).coerceAtLeast(1) * 2
            val ph = unit * 3
            val sx = (x / unit).roundToInt() * unit
            val sy = (y / unit).roundToInt() * unit
            pxRect(sx, sy, pw, ph, colors[pt[4].toInt()].copy(alpha = fade))
        }
    }
}
