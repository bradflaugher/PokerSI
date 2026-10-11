package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.core.graphics.createBitmap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/** Five felt tones, darkest (the table's edge) to lightest (under the lamp). */
@Immutable
data class FeltColors(val deep: Color, val dark: Color, val mid: Color, val light: Color, val lamp: Color) {
    internal val tones: List<Color> get() = listOf(deep, dark, mid, light, lamp)

    companion object {
        /** The card table: classic casino green. */
        val Table = FeltColors(
            deep = Color(0xFF021C15),
            dark = Color(0xFF053324),
            mid = Color(0xFF0A4C34),
            light = Color(0xFF106444),
            lamp = Color(0xFF187C53),
        )

        /** The lobby (title, menus): a deeper green, so buttons and the marquee pop. */
        val Lobby = FeltColors(
            deep = Color(0xFF01140F),
            dark = Color(0xFF03261B),
            mid = Color(0xFF073A29),
            light = Color(0xFF0C4E37),
            lamp = Color(0xFF136346),
        )
    }
}

/**
 * The green baize every screen sits on, as pixel art: a lamp-lit centre
 * falling off to a shadowed rail on every edge, with the odd fleck of
 * lint, ordered-dithered into five tones with a twill matrix (so the
 * gradient shows the cloth's diagonal nap) and drawn at chrome-pixel resolution with
 * no smoothing. It's rendered once per size and cached, so it costs nothing
 * per frame.
 *
 * @param pixelSize size of one felt pixel; defaults to 2 chrome pixels.
 */
@Composable
fun FeltBackground(
    modifier: Modifier = Modifier,
    colors: FeltColors = FeltColors.Table,
    pixelSize: Dp? = null,
    content: @Composable () -> Unit = {},
) {
    val unit = LocalPixelUnit.current
    Box(
        modifier.drawWithCache {
            val block = max(1f, pixelSize?.toPx()?.let { floor(it) } ?: (unit * 2f))
            val w = ceil(size.width / block).toInt().coerceAtLeast(1)
            val h = ceil(size.height / block).toInt().coerceAtLeast(1)
            val image = FeltArt.render(w, h, colors)
            onDrawBehind { drawPixelArt(image, IntOffset.Zero, block.toInt()) }
        },
    ) { content() }
}

/** The felt texture, one pixel per felt block. */
object FeltArt {
    private val cache = ArtCache<Any>(6)

    /**
     * A 4x4 "twill" dither: thresholds climb diagonal by diagonal (and in
     * a Bayer-like order along each one), so the lamp's gradient resolves
     * into fine diagonal ribs like the nap of baize instead of a
     * cross-hatch, in 16 levels so there are no contour rings.
     */
    private val TWILL = FloatArray(16) { i ->
        val x = i % 4
        val y = i / 4
        val t = 4 * ((x + y) % 4) + intArrayOf(0, 2, 1, 3)[x]
        (t + 0.5f) / 16f
    }

    /** Felt tone (0..1) at block (x, y) of a w x h field, before dithering. */
    fun shade(x: Int, y: Int, w: Int, h: Int): Float {
        // Lamp: an ellipse of light a little above centre, like a lamp over the table.
        val dx = (x + 0.5f) / w - 0.5f
        val dy = ((y + 0.5f) / h - 0.42f) * (h.toFloat() / w).coerceAtMost(1.8f) * 0.75f
        val d = sqrt(dx * dx + dy * dy) / 0.75f
        val lamp = (1f - d * d).coerceIn(0f, 1f)
        // The rail: every edge falls off into shadow over the last few percent.
        val ex = minOf(x + 0.5f, w - x - 0.5f) / w
        val ey = minOf(y + 0.5f, h - y - 0.5f) / w
        val edge = (minOf(ex, ey) / 0.09f).coerceAtMost(1f)
        var v = (0.10f + lamp * 0.80f) * (0.55f + 0.45f * edge)
        // A rare fleck of lint.
        if (hash(x, y) > 0.985f) v += 0.12f
        return v.coerceIn(0f, 1f)
    }

    /** The texture as a bitmap, w x h felt blocks. */
    fun render(w: Int, h: Int, colors: FeltColors): ImageBitmap = cache.getOrPut(listOf(w, h, colors)) {
        // The five tones plus the midpoints between them: smaller steps keep
        // the twill a soft nap rather than a hard stripe.
        val base5 = colors.tones
        val tones = base5.flatMapIndexed { i, c -> if (i == 0) listOf(c) else listOf(lerp(base5[i - 1], c, 0.5f), c) }.map { it.toArgb() }
        val steps = tones.size - 1
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val v = shade(x, y, w, h) * steps
            val base = floor(v).toInt()
            val frac = v - base
            val k = if (frac > TWILL[(y and 3) * 4 + (x and 3)]) base + 1 else base
            px[y * w + x] = tones[k.coerceIn(0, steps)]
        }
        createBitmap(w, h).also { it.setPixels(px, 0, w, 0, 0, w, h) }.asImageBitmap()
    }

    /** A cheap deterministic hash to [0, 1). */
    private fun hash(x: Int, y: Int): Float {
        var n = x * 374761393 + y * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65536f
    }
}
