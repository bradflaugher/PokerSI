package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bradflaugher.pokersi.engine.Card
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A fan of face-up cards as one piece of pixel art. Each card is rotated
 * on the art's own pixel grid (a RotSprite-style rotation: scale2x three
 * times, then nearest-neighbour sampling back at 1x), so the tilted cards
 * are as crisp as the straight ones instead of being smoothed or rotated
 * as a bitmap at screen resolution. Cached; the glow is a separate layer
 * so it can pulse.
 */
object CardFanArt {
    private val cache = object : LinkedHashMap<Any, Fan>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Fan>?) = size > 4
    }

    class Fan(val image: ImageBitmap, val glow: ImageBitmap)

    /** [cards] fanned [degrees] apart, each [step] pixels from the last, the middle one upright. */
    @Synchronized
    fun fan(cards: List<Card>, degrees: Float = 7f, step: Int = 33, palette: PixelPalette = PixelPalette.Default): Fan =
        cache.getOrPut(listOf(cards, degrees, step, palette)) {
            val (image, glow) = build(cards, degrees, step, palette)
            Fan(image.toImageBitmap(), glow.toImageBitmap())
        }

    private fun build(cards: List<Card>, degrees: Float, step: Int, p: PixelPalette): Pair<PixelCanvas, PixelCanvas> {
        val n = cards.size
        val mid = (n - 1) / 2f
        val margin = 6
        // Bounds of every rotated card.
        val placed = cards.mapIndexed { i, card ->
            val k = i - mid
            val a = k * degrees * PI / 180.0
            val cx = k * step.toDouble()
            val cy = k * k * 2.0
            Triple(card, a, cx to cy)
        }
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE; var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for ((_, a, c) in placed) {
            for ((ox, oy) in listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)) {
                val x = ox * CardArt.W / 2.0
                val y = oy * CardArt.H / 2.0
                val rx = c.first + x * cos(a) - y * sin(a)
                val ry = c.second + x * sin(a) + y * cos(a)
                minX = min(minX, rx); maxX = max(maxX, rx); minY = min(minY, ry); maxY = max(maxY, ry)
            }
        }
        val w = ceil(maxX - minX).toInt() + margin * 2
        val h = ceil(maxY - minY).toInt() + margin * 2
        val ox = -minX + margin
        val oy = -minY + margin
        val out = PixelCanvas(w, h)
        val shadow = p.ink.copy(alpha = 0.55f).toArgb()
        for ((card, a, c) in placed) {
            val src = upscale8(CardArt.faceCanvas(card, p))
            val layer = PixelCanvas(w, h)
            val cx = c.first + ox
            val cy = c.second + oy
            val ca = cos(a); val sa = sin(a)
            val reach = (CardArt.H / 2.0 + 2)
            for (y in floor(cy - reach).toInt()..ceil(cy + reach).toInt()) for (x in floor(cx - reach).toInt()..ceil(cx + reach).toInt()) {
                if (!layer.inBounds(x, y)) continue
                // Inverse-rotate this pixel's centre into the card, sample the 8x art.
                val dx = x + 0.5 - cx
                val dy = y + 0.5 - cy
                val u = dx * ca + dy * sa + CardArt.W / 2.0
                val v = -dx * sa + dy * ca + CardArt.H / 2.0
                val su = (u * 8).toInt()
                val sv = (v * 8).toInt()
                if (u < 0 || v < 0 || su >= src.width || sv >= src.height) continue
                val col = src.pixels[sv * src.width + su]
                if ((col ushr 24) != 0) layer.set(x, y, col)
            }
            // Hard shadow down-right, blended over what is already there, then the card.
            for (y in 0 until h) for (x in 0 until w) {
                if ((layer[x - 1, y - 2] ushr 24) != 0 && (layer[x, y] ushr 24) == 0) out.set(x, y, blend(out[x, y], shadow))
            }
            out.blit(layer, 0, 0)
        }
        // Glow: a yellow pinline hugging the fan and a translucent gold ring.
        val glow = PixelCanvas(w, h)
        val solid = PixelCanvas(w, h)
        for (y in 0 until h) for (x in 0 until w) if ((out[x, y] ushr 24) == 0xFF) solid.set(x, y, Color.White)
        val ring1 = PixelCanvas(w, h).also { it.blit(solid, 0, 0); it.outline(p.yellow) }
        val ring3 = PixelCanvas(w, h).also { it.blit(ring1, 0, 0); it.outline(p.gold, diagonal = true); it.outline(p.gold) }
        val gold = p.gold.copy(alpha = 0.45f).toArgb()
        val yellow = p.yellow.toArgb()
        for (y in 0 until h) for (x in 0 until w) {
            if ((solid[x, y] ushr 24) != 0) continue
            when {
                ring1[x, y] == yellow -> glow.set(x, y, yellow)
                (ring3[x, y] ushr 24) != 0 -> glow.set(x, y, gold)
            }
        }
        return out to glow
    }

    private fun blend(dst: Int, src: Int): Int {
        val a = (src ushr 24) / 255f
        val da = (dst ushr 24) / 255f
        val oa = a + da * (1 - a)
        if (oa <= 0f) return 0
        fun ch(shift: Int): Int {
            val s = (src ushr shift) and 0xFF
            val d = (dst ushr shift) and 0xFF
            return ((s * a + d * da * (1 - a)) / oa).roundToInt().coerceIn(0, 255)
        }
        return ((oa * 255).roundToInt() shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Scale2x (EPX) three times: an 8x copy whose diagonals are smoothed along the art's own edges. */
    private fun upscale8(c: PixelCanvas): PixelCanvas = scale2x(scale2x(scale2x(c)))

    private fun scale2x(c: PixelCanvas): PixelCanvas {
        val o = PixelCanvas(c.width * 2, c.height * 2)
        for (y in 0 until c.height) for (x in 0 until c.width) {
            val p = c[x, y]
            val a = if (y > 0) c[x, y - 1] else p
            val b = if (x < c.width - 1) c[x + 1, y] else p
            val l = if (x > 0) c[x - 1, y] else p
            val d = if (y < c.height - 1) c[x, y + 1] else p
            val e0 = if (l == a && l != d && a != b) a else p
            val e1 = if (a == b && a != l && b != d) b else p
            val e2 = if (d == l && d != b && l != a) l else p
            val e3 = if (b == d && b != a && d != l) d else p
            o.set(2 * x, 2 * y, e0); o.set(2 * x + 1, 2 * y, e1)
            o.set(2 * x, 2 * y + 1, e2); o.set(2 * x + 1, 2 * y + 1, e3)
        }
        return o
    }
}

/**
 * A fan of [cards] (title screen, store art) drawn as one crisp piece of
 * pixel art at a whole-number scale: each card about [cardWidth] wide,
 * fanned [degrees] apart, with a pulsing gold glow when [highlighted] and
 * a gentle bob when animated.
 */
@Composable
fun PixelCardFan(
    cards: List<Card>,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 54.dp,
    degrees: Float = 7f,
    highlighted: Boolean = true,
    contentDescription: String? = null,
) {
    val palette = LocalPixelPalette.current
    val fan = remember(cards, degrees, palette) { CardFanArt.fan(cards, degrees, palette = palette) }
    val animated = PixelTheme.animated
    val t = if (animated) {
        rememberInfiniteTransition(label = "fan").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "t")
    } else remember { mutableFloatStateOf(0f) }
    val pulse = if (animated && highlighted) {
        rememberInfiniteTransition(label = "fanGlow").animateFloat(0.55f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "g")
    } else remember { mutableFloatStateOf(1f) }
    val spoken = if (contentDescription == null) Modifier else Modifier.semantics { this.contentDescription = contentDescription }
    Canvas(
        modifier.then(spoken).layout { m, _ ->
            val s = integerScale(cardWidth.toPx(), CardArt.W)
            val pl = m.measure(Constraints.fixed(fan.image.width * s, fan.image.height * s))
            layout(pl.width, pl.height) { pl.place(0, 0) }
        },
    ) {
        val s = integerScale(size.width / fan.image.width * CardArt.W, CardArt.W)
        // Bob by whole art pixels only, so the art stays on its grid.
        val bob = (sin(t.value) * 1.4f).roundToInt() * s
        val at = IntOffset(0, bob)
        if (highlighted) drawPixelArt(fan.glow, at, s, alpha = pulse.value)
        drawPixelArt(fan.image, at, s)
    }
}
