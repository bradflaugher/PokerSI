package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlin.math.max

/**
 * A tiny hand-made bitmap drawn as rows of characters. `.` and space are
 * transparent; every other character is looked up in a color map when
 * stamped, so one pattern serves many color schemes.
 */
class PixelPattern(val rows: List<String>) {
    val width: Int = rows.maxOf { it.length }
    val height: Int = rows.size

    init {
        require(rows.all { it.length == width }) { "Ragged pixel pattern: $rows" }
    }

    operator fun get(x: Int, y: Int): Char = rows[y][x]

    fun flippedY(): PixelPattern = PixelPattern(rows.reversed())
    fun flippedX(): PixelPattern = PixelPattern(rows.map { it.reversed() })

    companion object {
        fun of(vararg rows: String) = PixelPattern(rows.toList())
    }
}

/**
 * An ARGB pixel buffer at the art's virtual resolution. Art is drawn here
 * once, turned into an [ImageBitmap] and cached, then blitted with
 * nearest-neighbour scaling at a whole-number scale.
 */
class PixelCanvas(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)

    fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height

    operator fun get(x: Int, y: Int): Int = if (inBounds(x, y)) pixels[y * width + x] else 0

    fun set(x: Int, y: Int, argb: Int) {
        if (inBounds(x, y)) pixels[y * width + x] = argb
    }

    fun set(x: Int, y: Int, color: Color) = set(x, y, color.toArgb())

    fun fill(x: Int, y: Int, w: Int, h: Int, color: Color) {
        val c = color.toArgb()
        for (yy in y until y + h) for (xx in x until x + w) set(xx, yy, c)
    }

    fun hline(x: Int, y: Int, w: Int, color: Color) = fill(x, y, w, 1, color)
    fun vline(x: Int, y: Int, h: Int, color: Color) = fill(x, y, 1, h, color)

    /** Draws a rectangle outline. */
    fun rect(x: Int, y: Int, w: Int, h: Int, color: Color) {
        hline(x, y, w, color); hline(x, y + h - 1, w, color)
        vline(x, y, h, color); vline(x + w - 1, y, h, color)
    }

    /** Stamps [pattern] at ([x],[y]); characters missing from [colors] stay untouched. */
    fun stamp(pattern: PixelPattern, x: Int, y: Int, colors: Map<Char, Color>) {
        val argb = colors.mapValues { it.value.toArgb() }
        for (py in 0 until pattern.height) for (px in 0 until pattern.width) {
            val c = pattern[px, py]
            if (c == '.' || c == ' ') continue
            argb[c]?.let { set(x + px, y + py, it) }
        }
    }

    /** Stamps [pattern] with every non-transparent character in one [color]. */
    fun stamp(pattern: PixelPattern, x: Int, y: Int, color: Color) {
        val c = color.toArgb()
        for (py in 0 until pattern.height) for (px in 0 until pattern.width) {
            val ch = pattern[px, py]
            if (ch != '.' && ch != ' ') set(x + px, y + py, c)
        }
    }

    /**
     * Surrounds every opaque pixel with a 1px [color] outline (4-connected,
     * or 8-connected when [diagonal]) on transparent neighbours.
     */
    fun outline(color: Color, diagonal: Boolean = false) {
        val c = color.toArgb()
        val src = pixels.copyOf()
        fun opaque(x: Int, y: Int) = x in 0 until width && y in 0 until height && (src[y * width + x] ushr 24) != 0
        for (y in 0 until height) for (x in 0 until width) {
            if (opaque(x, y)) continue
            val n = opaque(x - 1, y) || opaque(x + 1, y) || opaque(x, y - 1) || opaque(x, y + 1) ||
                (diagonal && (opaque(x - 1, y - 1) || opaque(x + 1, y - 1) || opaque(x - 1, y + 1) || opaque(x + 1, y + 1)))
            if (n) pixels[y * width + x] = c
        }
    }

    /** Copies [other] onto this canvas at ([x],[y]), skipping transparent pixels. */
    fun blit(other: PixelCanvas, x: Int, y: Int) {
        for (yy in 0 until other.height) for (xx in 0 until other.width) {
            val c = other.pixels[yy * other.width + xx]
            if ((c ushr 24) != 0) set(x + xx, y + yy, c)
        }
    }

    /** Draws [text] in [font] at ([x],[y]) with 1px letter spacing. Returns the width drawn. */
    fun text(text: String, x: Int, y: Int, color: Color, font: PixelFont = PixelFont.Regular): Int {
        var cx = x
        for (ch in text) {
            val g = font.glyph(ch)
            stamp(g, cx, y, color)
            cx += g.width + font.spacing
        }
        return max(0, cx - x - font.spacing)
    }

    fun toImageBitmap(): ImageBitmap {
        val bmp = createBitmap(width, height)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp.asImageBitmap()
    }
}

/** Bitmap fonts for text drawn inside pixel art (card ranks, tags, LED labels). */
class PixelFont(val height: Int, val spacing: Int, private val glyphs: Map<Char, PixelPattern>) {
    fun glyph(ch: Char): PixelPattern = glyphs[ch.uppercaseChar()] ?: glyphs['?']!!
    fun measure(text: String): Int = text.sumOf { glyph(it).width + spacing } - spacing

    companion object {
        private fun g(vararg rows: String) = PixelPattern(rows.toList())

        /** 5x7 caps and digits. "10" is a single narrow glyph mapped to 'T'. */
        val Regular: PixelFont = PixelFont(
            7, 1,
            mapOf(
                'A' to g(".XXX.", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"),
                'B' to g("XXXX.", "X...X", "X...X", "XXXX.", "X...X", "X...X", "XXXX."),
                'C' to g(".XXX.", "X...X", "X....", "X....", "X....", "X...X", ".XXX."),
                'D' to g("XXXX.", "X...X", "X...X", "X...X", "X...X", "X...X", "XXXX."),
                'E' to g("XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX"),
                'F' to g("XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "X...."),
                'G' to g(".XXX.", "X...X", "X....", "X.XXX", "X...X", "X...X", ".XXXX"),
                'H' to g("X...X", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"),
                'I' to g("XXX", ".X.", ".X.", ".X.", ".X.", ".X.", "XXX"),
                'J' to g("..XXX", "...X.", "...X.", "...X.", "...X.", "X..X.", ".XX.."),
                'K' to g("X...X", "X..X.", "X.X..", "XX...", "X.X..", "X..X.", "X...X"),
                'L' to g("X....", "X....", "X....", "X....", "X....", "X....", "XXXXX"),
                'M' to g("X...X", "XX.XX", "X.X.X", "X.X.X", "X...X", "X...X", "X...X"),
                'N' to g("X...X", "X...X", "XX..X", "X.X.X", "X..XX", "X...X", "X...X"),
                'O' to g(".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."),
                'P' to g("XXXX.", "X...X", "X...X", "XXXX.", "X....", "X....", "X...."),
                'Q' to g(".XXX.", "X...X", "X...X", "X...X", "X.X.X", "X..X.", ".XX.X"),
                'R' to g("XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"),
                'S' to g(".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."),
                'T' to g("XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "..X.."),
                'U' to g("X...X", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."),
                'V' to g("X...X", "X...X", "X...X", "X...X", "X...X", ".X.X.", "..X.."),
                'W' to g("X...X", "X...X", "X...X", "X.X.X", "X.X.X", "X.X.X", ".X.X."),
                'X' to g("X...X", "X...X", ".X.X.", "..X..", ".X.X.", "X...X", "X...X"),
                'Y' to g("X...X", "X...X", ".X.X.", "..X..", "..X..", "..X..", "..X.."),
                'Z' to g("XXXXX", "....X", "...X.", "..X..", ".X...", "X....", "XXXXX"),
                '0' to g(".XXX.", "X...X", "X..XX", "X.X.X", "XX..X", "X...X", ".XXX."),
                '1' to g("..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."),
                '2' to g(".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"),
                '3' to g("XXXXX", "...X.", "..X..", "...X.", "....X", "X...X", ".XXX."),
                '4' to g("...X.", "..XX.", ".X.X.", "X..X.", "XXXXX", "...X.", "...X."),
                '5' to g("XXXXX", "X....", "XXXX.", "....X", "....X", "X...X", ".XXX."),
                '6' to g("..XX.", ".X...", "X....", "XXXX.", "X...X", "X...X", ".XXX."),
                '7' to g("XXXXX", "....X", "...X.", "..X..", ".X...", ".X...", ".X..."),
                '8' to g(".XXX.", "X...X", "X...X", ".XXX.", "X...X", "X...X", ".XXX."),
                '9' to g(".XXX.", "X...X", "X...X", ".XXXX", "....X", "...X.", ".XX.."),
                '!' to g("X", "X", "X", "X", "X", ".", "X"),
                '?' to g(".XXX.", "X...X", "....X", "...X.", "..X..", ".....", "..X.."),
                '.' to g(".", ".", ".", ".", ".", ".", "X"),
                ',' to g("..", "..", "..", "..", "..", ".X", "X."),
                ':' to g(".", "X", ".", ".", ".", "X", "."),
                '-' to g("....", "....", "....", "XXXX", "....", "....", "...."),
                '+' to g(".....", "..X..", "..X..", "XXXXX", "..X..", "..X..", "....."),
                '$' to g("..X..", ".XXXX", "X.X..", ".XXX.", "..X.X", "XXXX.", "..X.."),
                '\'' to g("X", "X", ".", ".", ".", ".", "."),
                ' ' to g("...", "...", "...", "...", "...", "...", "..."),
            ),
        )

        /** Bold 6x8 rank glyphs for card corners (2px strokes read better small). */
        val Rank: PixelFont = PixelFont(
            8, 1,
            mapOf(
                'A' to g("..XX..", ".XXXX.", "XX..XX", "XX..XX", "XXXXXX", "XX..XX", "XX..XX", "XX..XX"),
                '2' to g(".XXXX.", "XX..XX", "....XX", "...XX.", "..XX..", ".XX...", "XX....", "XXXXXX"),
                '3' to g("XXXXX.", "....XX", "....XX", ".XXXX.", "....XX", "....XX", "XX..XX", ".XXXX."),
                '4' to g("...XX.", "..XXX.", ".XXXX.", "XX.XX.", "XXXXXX", "...XX.", "...XX.", "...XX."),
                '5' to g("XXXXXX", "XX....", "XXXXX.", "....XX", "....XX", "....XX", "XX..XX", ".XXXX."),
                '6' to g(".XXXX.", "XX....", "XX....", "XXXXX.", "XX..XX", "XX..XX", "XX..XX", ".XXXX."),
                '7' to g("XXXXXX", "....XX", "...XX.", "...XX.", "..XX..", "..XX..", "..XX..", "..XX.."),
                '8' to g(".XXXX.", "XX..XX", "XX..XX", ".XXXX.", "XX..XX", "XX..XX", "XX..XX", ".XXXX."),
                '9' to g(".XXXX.", "XX..XX", "XX..XX", "XX..XX", ".XXXXX", "....XX", "....XX", ".XXXX."),
                'T' to g(".XX..XXX.", "XXX.XX.XX", ".XX.XX.XX", ".XX.XX.XX", ".XX.XX.XX", ".XX.XX.XX", ".XX.XX.XX", ".XX..XXX."),
                'J' to g("..XXXX", "....XX", "....XX", "....XX", "....XX", "XX..XX", "XX..XX", ".XXXX."),
                'Q' to g(".XXXX.", "XX..XX", "XX..XX", "XX..XX", "XX..XX", "XX.XXX", "XX..XX", ".XXX.X"),
                'K' to g("XX..XX", "XX.XX.", "XXXX..", "XXX...", "XXXX..", "XX.XX.", "XX..XX", "XX..XX"),
                '?' to g("XXXXXX", "XXXXXX", "XXXXXX", "XXXXXX", "XXXXXX", "XXXXXX", "XXXXXX", "XXXXXX"),
            ),
        )

        /** 3x5 micro font for tiny labels ("SI" on the card back). */
        val Tiny: PixelFont = PixelFont(
            5, 1,
            mapOf(
                'S' to g("XXX", "X..", "XXX", "..X", "XXX"),
                'I' to g("XXX", ".X.", ".X.", ".X.", "XXX"),
                'P' to g("XXX", "X.X", "XXX", "X..", "X.."),
                'O' to g("XXX", "X.X", "X.X", "X.X", "XXX"),
                'K' to g("X.X", "X.X", "XX.", "X.X", "X.X"),
                'E' to g("XXX", "X..", "XX.", "X..", "XXX"),
                'R' to g("XX.", "X.X", "XX.", "X.X", "X.X"),
                '?' to g("XXX", "..X", ".X.", "...", ".X."),
                ' ' to g("..", "..", "..", "..", ".."),
            ),
        )
    }
}

/** The largest whole-number scale at which [virtualPx] art fits in [availablePx] (at least 1). */
fun integerScale(availablePx: Float, virtualPx: Int): Int = max(1, (availablePx / virtualPx).toInt())

/**
 * Blits pixel art at a whole-number [scale] with nearest-neighbour
 * filtering, so every art pixel is a crisp [scale] x [scale] block.
 */
fun DrawScope.drawPixelArt(
    image: ImageBitmap,
    topLeft: IntOffset,
    scale: Int,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = topLeft,
        dstSize = IntSize(image.width * scale, image.height * scale),
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = FilterQuality.None,
    )
}

/** Blits pixel art at a whole-number scale, centred in this draw scope. */
fun DrawScope.drawPixelArtCentered(image: ImageBitmap, scale: Int, alpha: Float = 1f, colorFilter: ColorFilter? = null) {
    val x = ((size.width - image.width * scale) / 2f).toInt()
    val y = ((size.height - image.height * scale) / 2f).toInt()
    drawPixelArt(image, IntOffset(x, y), scale, alpha, colorFilter)
}

/** Small bounded cache for generated art (keyed by anything with sane equals). */
internal class ArtCache<K : Any>(private val capacity: Int) {
    private val map = object : LinkedHashMap<K, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, ImageBitmap>?) = size > capacity
    }

    @Synchronized
    fun getOrPut(key: K, make: () -> ImageBitmap): ImageBitmap = map.getOrPut(key, make)
}
