package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max

/**
 * A frame of chasing marquee bulbs around [content], for the title and the
 * pay table. Every third bulb is lit and the pattern marches around the
 * frame (frozen in [PixelRenderMode.Static]).
 *
 * @param frameColor band color behind the bulbs.
 * @param bulbColor lit bulb color.
 * @param spacingUnits distance between bulb centres in chrome pixels.
 */
@Composable
fun MarqueeFrame(
    modifier: Modifier = Modifier,
    frameColor: Color = LocalPixelPalette.current.suitRed,
    bulbColor: Color = LocalPixelPalette.current.yellow,
    spacingUnits: Int = 7,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPixelPalette.current
    val unit = LocalPixelUnit.current
    val band = unit * 7
    val phase = chasePhase()
    val density = LocalDensity.current
    Box(
        modifier
            .drawWithContent {
                drawMarquee(band, unit, spacingUnits, frameColor, bulbColor, p, phase.value)
                drawContent()
            }
            .padding(with(density) { (band + unit * 2).toDp() }),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
private fun chasePhase(): State<Int> {
    if (!PixelTheme.animated) return remember { mutableIntStateOf(0) }
    val t = rememberInfiniteTransition(label = "marquee")
    val f = t.animateFloat(0f, 3f, infiniteRepeatable(tween(540, easing = LinearEasing)), label = "phase")
    return remember { derivedPhase(f) }
}

private fun derivedPhase(f: State<Float>): State<Int> = object : State<Int> {
    override val value: Int get() = f.value.toInt() % 3
}

private fun DrawScope.drawMarquee(band: Int, u: Int, spacingUnits: Int, frame: Color, bulb: Color, p: PixelPalette, phase: Int) {
    val w = size.width.toInt()
    val h = size.height.toInt()
    // Band: ink outline, frame color, gold pinstripe inside, ink inner edge.
    notchedRect(0, 0, w, h, u, 2, p.ink)
    notchedRect(u, u, w - 2 * u, h - 2 * u, u, 1, frame)
    pxRect(u * 2, u, w - 4 * u, u, lerp(frame, Color.White, 0.35f))
    pxRect(band - u, band - u, w - 2 * band + 2 * u, h - 2 * band + 2 * u, p.gold)
    pxRect(band, band, w - 2 * band, h - 2 * band, p.ink)

    // Bulbs along the centreline of the band, clockwise from the top-left.
    val c = band / 2
    val spacing = spacingUnits * u
    val pts = ArrayList<Pair<Int, Int>>()
    fun side(x0: Int, y0: Int, x1: Int, y1: Int) {
        val len = max(abs(x1 - x0), abs(y1 - y0))
        val n = max(1, len / spacing)
        for (i in 0 until n) {
            pts += (x0 + (x1 - x0) * i / n) to (y0 + (y1 - y0) * i / n)
        }
    }
    side(c, c, w - c, c)
    side(w - c, c, w - c, h - c)
    side(w - c, h - c, c, h - c)
    side(c, h - c, c, c)
    val dim = lerp(bulb, p.ink, 0.62f)
    pts.forEachIndexed { i, (x, y) ->
        val lit = (i + phase) % 3 == 0
        drawBulb(x, y, u, if (lit) bulb else dim, lit)
    }
}

private fun abs(v: Int) = if (v < 0) -v else v

/** A round 4x4-unit pixel bulb with a soft square halo when lit. */
private fun DrawScope.drawBulb(cx: Int, cy: Int, u: Int, color: Color, lit: Boolean) {
    val x = cx - u * 2
    val y = cy - u * 2
    if (lit) {
        pxRect(x - u, y, u * 6, u * 4, color.copy(alpha = 0.22f))
        pxRect(x, y - u, u * 4, u * 6, color.copy(alpha = 0.22f))
    }
    pxRect(x + u, y, u * 2, u * 4, color)
    pxRect(x, y + u, u * 4, u * 2, color)
    if (lit) pxRect(x + u, y + u, u, u, Color.White)
}
