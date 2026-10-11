package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset

/** Chip denominations, largest first, and their colors. */
object Chips {
    data class Denomination(val value: Int, val color: Color, val stripe: Color)

    val Denominations = listOf(
        Denomination(1000, Color(0xFFFFC22E), Color(0xFF130F21)),
        Denomination(500, Color(0xFF7A2CF2), Color(0xFFFFF3D6)),
        Denomination(100, Color(0xFF2C2445), Color(0xFFFFC22E)),
        Denomination(25, Color(0xFF2FD15A), Color(0xFFFFF3D6)),
        Denomination(5, Color(0xFFE8263E), Color(0xFFFFF3D6)),
        Denomination(1, Color(0xFFE9E4F5), Color(0xFF1F3BD1)),
    )

    /** Greedy breakdown of [amount] into (denomination, count), largest first. */
    fun breakdown(amount: Int): List<Pair<Denomination, Int>> {
        var left = amount.coerceAtLeast(0)
        return Denominations.mapNotNull { d ->
            val n = left / d.value
            left -= n * d.value
            if (n > 0) d to n else null
        }
    }
}

private const val CHIP_W = 12
private const val MAX_PER_STACK = 10
private const val MAX_STACKS = 4

/** Pixel art for a stack of chips worth [amount] (cached). */
internal object ChipArt {
    private val cache = ArtCache<Any>(64)

    fun image(amount: Int, ink: Color): ImageBitmap {
        val stacks = Chips.breakdown(amount).take(MAX_STACKS).map { (d, n) -> d to n.coerceAtMost(MAX_PER_STACK) }
        return cache.getOrPut(stacks.map { it.first.value to it.second } + ink) { draw(stacks, ink).toImageBitmap() }
    }

    private fun draw(stacks: List<Pair<Chips.Denomination, Int>>, ink: Color): PixelCanvas {
        val cols = stacks.size.coerceAtLeast(1)
        val w = cols * (CHIP_W + 1) + 3
        val h = MAX_PER_STACK * 2 + 4 + 3
        val c = PixelCanvas(w, h)
        // Stacks lean slightly for character: alternate one pixel offsets.
        fun lean(k: Int) = (if (k % 3 == 1) 1 else 0) - (if (k % 5 == 3) 1 else 0)
        stacks.forEachIndexed { i, (d, n) ->
            val x0 = 1 + i * (CHIP_W + 1)
            val base = h - 2
            val dark = lerp(d.color, ink, 0.4f)
            val light = lerp(d.color, Color.White, 0.35f)
            val stripeDark = lerp(d.stripe, ink, 0.3f)
            // Edges: a lit band over a shaded one, with the edge spots
            // stepping round from chip to chip so the stack looks stacked.
            for (k in 0 until n) {
                val y = base - 2 - k * 2
                val x = x0 + lean(k)
                c.hline(x, y, CHIP_W, d.color)
                c.hline(x, y + 1, CHIP_W, dark)
                c.set(x, y, light)
                c.set(x + CHIP_W - 1, y, dark)
                for (s in intArrayOf(1, 5, 9)) {
                    val sx = x + s + (k % 2) * 2
                    if (sx < x + CHIP_W - 1) { c.set(sx, y, d.stripe); c.set(sx, y + 1, stripeDark) }
                }
            }
            // Top face, seen low across the table: a flat ellipse flush on
            // the edge band below it.
            val ty = base - n * 2 - 3
            val tx = x0 + lean(n - 1)
            c.hline(tx + 2, ty, 8, d.color)
            c.hline(tx, ty + 1, 12, d.color)
            c.hline(tx, ty + 2, 12, d.color)
            // Edge spots round the rim, an inlay ring and a glint.
            for ((sx, sy) in listOf(2 to 0, 9 to 0, 0 to 2, 11 to 2, 5 to 2, 6 to 2)) c.set(tx + sx, ty + sy, d.stripe)
            c.hline(tx + 4, ty, 4, light)
            c.hline(tx + 3, ty + 1, 6, lerp(d.color, Color.White, 0.18f))
            c.set(tx + 1, ty + 1, light)
            c.set(tx + 4, ty, lerp(d.color, Color.White, 0.65f))
        }
        c.outline(ink)
        return c
    }
}

/**
 * A pile of pixel poker chips worth [amount], one stack per denomination,
 * with the amount underneath in chunky numbers when [showAmount].
 */
@Composable
fun ChipStack(
    amount: Int,
    modifier: Modifier = Modifier,
    showAmount: Boolean = true,
    scaleUnits: Int = 1,
) {
    val p = LocalPixelPalette.current
    val unit = LocalPixelUnit.current
    val density = LocalDensity.current
    val art = ChipArt.image(amount, p.ink)
    val scale = unit * scaleUnits
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(with(density) { unit.toDp() })) {
        Canvas(Modifier.size(with(density) { (art.width * scale).toDp() }, with(density) { (art.height * scale).toDp() })) {
            drawPixelArt(art, IntOffset.Zero, scale)
        }
        if (showAmount) PixelText(formatChips(amount), style = PixelType.Heading, color = p.yellow)
    }
}

/** "1,250" style grouping for chip counts. */
fun formatChips(amount: Int): String = String.format(java.util.Locale.US, "%,d", amount)
