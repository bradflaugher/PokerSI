package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Rank
import com.bradflaugher.pokersi.engine.Suit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Playing card pixel art, drawn procedurally at [W] x [H] virtual pixels
 * and cached as tiny [ImageBitmap]s. [PixelCard] scales them up by a whole
 * number with nearest-neighbour filtering.
 *
 * All art here is original: hand-made glyphs and pips, procedural court
 * portraits and the PokerSI robot card back.
 */
object CardArt {
    const val W = 45
    const val H = 63
    private const val CX = W / 2 // 22: centre column

    private val cache = ArtCache<Any>(160)

    /** Face art for [card]. */
    fun face(card: Card, palette: PixelPalette = PixelPalette.Default): ImageBitmap =
        cache.getOrPut(FaceKey(card, palette)) { drawFace(card, palette).toImageBitmap() }

    /** The PokerSI card back. */
    fun back(palette: PixelPalette = PixelPalette.Default): ImageBitmap =
        cache.getOrPut(BackKey(palette)) { drawBack(palette).toImageBitmap() }

    /** The card's silhouette in one color (drop shadows). */
    fun silhouette(color: Color = Color.Black): ImageBitmap =
        cache.getOrPut(SilhouetteKey(color)) {
            PixelCanvas(W, H).also { c -> forEachCardPixel { x, y, _ -> c.set(x, y, color) } }.toImageBitmap()
        }

    /** A [margin]-pixel glow ring around the card, [W]+2m x [H]+2m. */
    fun glow(color: Color, margin: Int = 2): ImageBitmap =
        cache.getOrPut(GlowKey(color, margin)) {
            val c = PixelCanvas(W + margin * 2, H + margin * 2)
            forEachCardPixel { x, y, _ -> c.set(x + margin, y + margin, color) }
            repeat(margin) { c.outline(color, diagonal = it > 0) }
            c.toImageBitmap()
        }

    /** The video poker "HELD" tag. */
    fun heldTag(palette: PixelPalette = PixelPalette.Default): ImageBitmap =
        cache.getOrPut(HeldKey(palette)) {
            val font = PixelFont.Regular
            val tw = font.measure("HELD")
            val c = PixelCanvas(tw + 6, font.height + 6)
            c.fill(1, 1, c.width - 2, c.height - 2, palette.red)
            c.hline(2, 1, c.width - 4, lerp(palette.red, palette.white, 0.45f))
            c.hline(1, c.height - 2, c.width - 2, palette.redDark)
            c.rect(0, 0, c.width, c.height, palette.ink)
            // Knock the corners off.
            c.set(0, 0, 0); c.set(c.width - 1, 0, 0); c.set(0, c.height - 1, 0); c.set(c.width - 1, c.height - 1, 0)
            c.text("HELD", 3, 4, palette.ink)
            c.text("HELD", 3, 3, palette.yellow)
            c.toImageBitmap()
        }

    private data class FaceKey(val card: Card, val palette: PixelPalette)
    private data class BackKey(val palette: PixelPalette)
    private data class SilhouetteKey(val color: Color)
    private data class GlowKey(val color: Color, val margin: Int)
    private data class HeldKey(val palette: PixelPalette)

    // ---- Card body -------------------------------------------------------

    /** Calls [f] for every pixel inside the card, with whether it is on the outline. */
    private inline fun forEachCardPixel(f: (x: Int, y: Int, edge: Boolean) -> Unit) {
        for (y in 0 until H) for (x in 0 until W) {
            if (!insideCard(x, y)) continue
            val edge = !insideCard(x - 1, y) || !insideCard(x + 1, y) || !insideCard(x, y - 1) || !insideCard(x, y + 1)
            f(x, y, edge)
        }
    }

    /** Card shape: a rectangle with 3-step rounded pixel corners. */
    private fun insideCard(x: Int, y: Int): Boolean {
        if (x !in 0 until W || y !in 0 until H) return false
        val dx = if (x < 2) 2 - x else if (x > W - 3) x - (W - 3) else 0
        val dy = if (y < 2) 2 - y else if (y > H - 3) y - (H - 3) else 0
        return dx + dy <= 2
    }

    private fun body(c: PixelCanvas, fill: Color, light: Color, shade: Color, ink: Color) {
        forEachCardPixel { x, y, edge ->
            c.set(x, y, if (edge) ink else fill)
        }
        // Bevel: light top/left inner edge, shaded bottom/right inner edge.
        for (x in 0 until W) for (y in 0 until H) {
            if (!insideCard(x, y) || isEdge(x, y)) continue
            if (isEdge(x, y + 1) || isEdge(x + 1, y)) c.set(x, y, shade)
            else if (isEdge(x, y - 1) || isEdge(x - 1, y)) c.set(x, y, light)
        }
    }

    private fun isEdge(x: Int, y: Int): Boolean =
        insideCard(x, y) && (!insideCard(x - 1, y) || !insideCard(x + 1, y) || !insideCard(x, y - 1) || !insideCard(x, y + 1))

    // ---- Faces -----------------------------------------------------------

    private fun suitColor(s: Suit, p: PixelPalette) = if (s.isRed) p.suitRed else p.suitBlack

    private fun drawFace(card: Card, p: PixelPalette): PixelCanvas {
        val c = PixelCanvas(W, H)
        body(c, p.cardCream, p.cardHighlight, p.cardShade, p.ink)
        val color = suitColor(card.suit, p)
        // Corner indices: rank above a small suit, top-left and rotated bottom-right.
        val rankGlyph = PixelFont.Rank.glyph(card.rank.letter)
        val small = SuitArt.small(card.suit)
        val rx = if (rankGlyph.width > 6) 2 else 3
        c.stamp(rankGlyph, rx, 3, color)
        c.stamp(small, 3 + (6 - small.width) / 2, 13, color)
        c.stamp(rankGlyph.flippedX().flippedY(), W - rx - rankGlyph.width, H - 3 - rankGlyph.height, color)
        c.stamp(small.flippedX().flippedY(), W - 3 - small.width - (6 - small.width) / 2, H - 13 - small.height, color)

        when (card.rank) {
            Rank.ACE -> drawAce(c, card.suit, p)
            Rank.JACK, Rank.QUEEN, Rank.KING -> drawCourt(c, card, p)
            else -> drawPips(c, card, color, p)
        }
        return c
    }

    // Pip columns and rows (centres).
    private const val L = 15
    private const val R = 29
    private val pipLayouts: Map<Int, List<Pair<Int, Int>>> = run {
        val top = 15; val upper = 26; val lower = 37; val bottom = 48; val mid = 31
        mapOf(
            2 to listOf(CX to top, CX to bottom),
            3 to listOf(CX to top, CX to mid, CX to bottom),
            4 to listOf(L to top, R to top, L to bottom, R to bottom),
            5 to listOf(L to top, R to top, CX to mid, L to bottom, R to bottom),
            6 to listOf(L to top, R to top, L to mid, R to mid, L to bottom, R to bottom),
            7 to listOf(L to top, R to top, CX to 23, L to mid, R to mid, L to bottom, R to bottom),
            8 to listOf(L to top, R to top, CX to 23, L to mid, R to mid, CX to 39, L to bottom, R to bottom),
            9 to listOf(L to top, R to top, L to upper, R to upper, CX to mid, L to lower, R to lower, L to bottom, R to bottom),
            10 to listOf(L to top, R to top, CX to 20, L to upper, R to upper, L to lower, R to lower, CX to 42, L to bottom, R to bottom),
        )
    }

    private fun drawPips(c: PixelCanvas, card: Card, color: Color, p: PixelPalette) {
        val pip = SuitArt.pip(card.suit)
        val light = lerp(color, p.white, 0.45f)
        for ((x, y) in pipLayouts.getValue(card.rank.value)) {
            val flip = y > 31
            val pat = if (flip) pip.flippedY() else pip
            val px = x - pat.width / 2
            val py = y - pat.height / 2
            c.stamp(pat, px, py, color)
            // A single glint pixel makes the pips feel inked rather than flat.
            val gx = px + if (card.suit == Suit.HEARTS) 1 else pat.width / 2
            val gy = py + if (flip) pat.height - 2 else 1
            if (pat[gx - px, gy - py] != '.') c.set(gx, gy, light)
        }
    }

    private fun drawAce(c: PixelCanvas, suit: Suit, p: PixelPalette) {
        val color = suitColor(suit, p)
        val big = SuitArt.big(suit)
        val art = PixelCanvas(big.width + 2, big.height + 2)
        stampBeveled(art, big, 1, 1, color, lerp(color, p.white, 0.5f), lerp(color, p.ink, 0.45f))
        art.outline(p.ink)
        val ax = CX - art.width / 2
        val ay = 31 - art.height / 2
        // Ornate gold sunburst ring behind the big suit.
        val ring = PixelCanvas(W, H)
        for (y in 0 until H) for (x in 0 until W) {
            val d = hypot(x - CX.toDouble(), y - 31.0)
            if (d in 13.2..14.4) {
                val a = atan2(y - 31.0, x - CX.toDouble())
                val seg = ((a + Math.PI) / (Math.PI * 2) * 24).toInt()
                ring.set(x, y, if (seg % 2 == 0) p.gold else p.goldDark)
            }
        }
        c.blit(ring, 0, 0)
        c.blit(art, ax, ay)
        for ((sx, sy) in listOf(CX - 12 to 18, CX + 12 to 44, CX + 12 to 18, CX - 12 to 44)) {
            sparkle(c, sx, sy, p)
        }
    }

    private fun sparkle(c: PixelCanvas, x: Int, y: Int, p: PixelPalette) {
        c.set(x, y - 2, p.gold); c.set(x, y + 2, p.gold); c.set(x - 2, y, p.gold); c.set(x + 2, y, p.gold)
        c.set(x, y - 1, p.yellow); c.set(x, y + 1, p.yellow); c.set(x - 1, y, p.yellow); c.set(x + 1, y, p.yellow)
        c.set(x, y, p.white)
    }

    /** Stamps [pat] with an automatic bevel: lit top/left edges, shaded bottom/right. */
    private fun stampBeveled(c: PixelCanvas, pat: PixelPattern, x0: Int, y0: Int, base: Color, light: Color, dark: Color) {
        fun on(x: Int, y: Int) = x in 0 until pat.width && y in 0 until pat.height && pat[x, y] != '.'
        for (y in 0 until pat.height) for (x in 0 until pat.width) {
            if (!on(x, y)) continue
            val col = when {
                !on(x, y + 1) || !on(x + 1, y) && !on(x + 1, y + 1) -> dark
                !on(x, y - 1) || !on(x - 1, y) -> light
                else -> base
            }
            c.set(x0 + x, y0 + y, col)
        }
    }

    // ---- Court cards -----------------------------------------------------

    private class CourtColors(val bg: Color, val hair: Color, val hairDark: Color, val robe: Color, val robeDark: Color, val robeLight: Color)

    private fun courtColors(suit: Suit, p: PixelPalette): CourtColors = when (suit) {
        Suit.HEARTS -> CourtColors(Color(0xFFFFD9DF), Color(0xFFC0471F), Color(0xFF7E2A12), p.suitRed, p.suitRedDark, Color(0xFFFF6E7F))
        Suit.DIAMONDS -> CourtColors(Color(0xFFFFE9B0), Color(0xFFF2B33A), Color(0xFFA9701A), p.suitRed, p.suitRedDark, Color(0xFFFF6E7F))
        Suit.SPADES -> CourtColors(Color(0xFFD5DEFF), Color(0xFF2B2240), Color(0xFF130F21), p.suitBlack, p.ink, p.suitBlackLight)
        Suit.CLUBS -> CourtColors(Color(0xFFCFF3E2), Color(0xFF6B3E1E), Color(0xFF3E220E), p.suitBlack, p.ink, p.suitBlackLight)
    }

    private val skin = Color(0xFFFFCE9E)
    private val skinShade = Color(0xFFE89A6A)
    private val blush = Color(0xFFFF8A9A)
    private val beard = Color(0xFFF2EFF8)
    private val beardShade = Color(0xFFB9B6CF)

    /** Fills symmetric rows around [cx]: row i spans cx-half[i]..cx+half[i] (negative = skip). */
    private fun sym(c: PixelCanvas, cx: Int, y0: Int, half: IntArray, color: Color) {
        half.forEachIndexed { i, h -> if (h >= 0) c.hline(cx - h, y0 + i, h * 2 + 1, color) }
    }

    private fun drawCourt(c: PixelCanvas, card: Card, p: PixelPalette) {
        val cc = courtColors(card.suit, p)
        val frameColor = suitColor(card.suit, p)
        // Frame: suit-colored border, gold inner line, tinted backdrop.
        c.fill(10, 8, 25, 47, frameColor)
        c.fill(11, 9, 23, 45, p.gold)
        c.fill(12, 10, 21, 43, cc.bg)
        // Backdrop diagonal stripes for texture.
        for (y in 10 until 53) for (x in 12 until 33) {
            if ((x + y) % 6 == 0) c.set(x, y, lerp(cc.bg, p.white, 0.55f))
        }

        val fig = PixelCanvas(W, H)
        when (card.rank) {
            Rank.KING -> king(fig, cc, p, card.suit)
            Rank.QUEEN -> queen(fig, cc, p, card.suit)
            else -> jack(fig, cc, p, card.suit)
        }
        fig.outline(p.ink)
        // Keep the figure inside the frame.
        for (y in 0 until H) for (x in 0 until W) {
            if (x !in 12..32 || y !in 10..52) fig.set(x, y, 0)
        }
        c.blit(fig, 0, 0)
    }

    private fun robe(fig: PixelCanvas, cc: CourtColors, p: PixelPalette, suit: Suit, top: Int) {
        // Shoulders widening down to the frame.
        for (y in top until 53) {
            val h = minOf(10, 6 + (y - top) * 2)
            fig.hline(CX - h, y, h * 2 + 1, cc.robe)
            fig.set(CX - h, y, cc.robeLight)
            fig.set(CX + h, y, cc.robeDark)
        }
        // Gold trim down the middle and an emblem with the suit.
        for (y in top + 1 until 53) { fig.set(CX - 3, y, p.gold); fig.set(CX + 3, y, p.goldDark) }
        val emblemY = top + 9
        sym(fig, CX, emblemY - 4, intArrayOf(2, 3, 4, 4, 4, 4, 4, 3, 2), p.gold)
        sym(fig, CX, emblemY - 3, intArrayOf(2, 3, 3, 3, 3, 3, 2), p.cardCream)
        val pip = SuitArt.small(suit)
        fig.stamp(pip, CX - pip.width / 2, emblemY - pip.height / 2, if (suit.isRed) p.suitRed else p.suitBlack)
    }

    private fun eyes(fig: PixelCanvas, y: Int, spread: Int, p: PixelPalette) {
        fig.vline(CX - spread, y, 2, p.ink)
        fig.vline(CX + spread, y, 2, p.ink)
    }

    private fun king(fig: PixelCanvas, cc: CourtColors, p: PixelPalette, suit: Suit) {
        robe(fig, cc, p, suit, 27)
        // Ermine collar.
        sym(fig, CX, 26, intArrayOf(8, 9), beard)
        for (x in CX - 8..CX + 8 step 3) fig.set(x, 27, p.ink)
        // Crown.
        val top = 11
        for (dx in listOf(-6, 0, 6)) { fig.set(CX + dx, top, p.gold) }
        for (dx in listOf(-6, -5, -1, 0, 1, 5, 6)) fig.set(CX + dx, top + 1, p.gold)
        sym(fig, CX, top + 2, intArrayOf(6, 6), p.gold)
        sym(fig, CX, top + 4, intArrayOf(6), p.goldDark)
        fig.set(CX, top + 2, p.cardHighlight)
        for (dx in listOf(-4, 0, 4)) fig.set(CX + dx, top + 4, if (suit.isRed) p.teal else p.hotPink)
        // Hair, face, beard.
        sym(fig, CX, top + 5, intArrayOf(6), beard)
        sym(fig, CX, top + 6, intArrayOf(6, 6, 6, 6, 6, 6), skin)
        fig.set(CX - 6, top + 6, beard); fig.set(CX + 6, top + 6, beard)
        fig.set(CX - 6, top + 7, beard); fig.set(CX + 6, top + 7, beard)
        eyes(fig, top + 8, 3, p)
        fig.set(CX, top + 9, skinShade); fig.set(CX, top + 10, skinShade)
        fig.set(CX - 4, top + 10, blush); fig.set(CX + 4, top + 10, blush)
        // Big curly mustache and beard.
        sym(fig, CX, top + 11, intArrayOf(4), beard)
        fig.set(CX - 5, top + 10, beard); fig.set(CX + 5, top + 10, beard)
        sym(fig, CX, top + 12, intArrayOf(6, 6, 5, 4, 3, 1), beard)
        fig.hline(CX - 1, top + 12, 3, p.suitRed)
        for (i in 0..3) fig.set(CX - 4 + i * 3 - (i % 2), top + 14 + (i % 2), beardShade)
    }

    private fun queen(fig: PixelCanvas, cc: CourtColors, p: PixelPalette, suit: Suit) {
        val top = 11
        // Long hair behind everything.
        sym(fig, CX, top + 3, intArrayOf(5, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 6), cc.hair)
        robe(fig, cc, p, suit, 26)
        // Hair falls over the shoulders.
        for (y in top + 14 until top + 20) { fig.hline(CX - 7, y, 2, cc.hair); fig.hline(CX + 6, y, 2, cc.hair) }
        for (y in top + 14 until top + 20) { fig.set(CX - 7, y, cc.hairDark); fig.set(CX + 7, y, cc.hairDark) }
        // Necklace.
        sym(fig, CX, 26, intArrayOf(5), p.gold)
        fig.set(CX, 27, p.teal)
        // Tiara.
        fig.set(CX, top, p.hotPink)
        sym(fig, CX, top + 1, intArrayOf(1, 3), p.gold)
        fig.set(CX - 3, top + 1, p.gold); fig.set(CX + 3, top + 1, p.gold)
        // Face.
        sym(fig, CX, top + 4, intArrayOf(4, 5, 5, 5, 5, 4, 3, 2), skin)
        // Bangs.
        fig.hline(CX - 5, top + 4, 5, cc.hair); fig.hline(CX + 2, top + 4, 3, cc.hair)
        fig.hline(CX - 5, top + 5, 3, cc.hair)
        fig.set(CX - 2, top + 3, cc.hairDark); fig.set(CX + 3, top + 3, cc.hairDark)
        // Eyes with lashes.
        eyes(fig, top + 7, 2, p)
        fig.set(CX - 3, top + 6, p.ink); fig.set(CX + 3, top + 6, p.ink)
        fig.set(CX - 4, top + 9, blush); fig.set(CX + 4, top + 9, blush)
        // Lips.
        fig.hline(CX - 1, top + 10, 3, p.suitRed)
        fig.set(CX, top + 11, p.suitRedDark)
        // A flower held up.
        sym(fig, CX + 6, 34, intArrayOf(1, 2, 1), p.hotPink)
        fig.set(CX + 6, 35, p.yellow)
        fig.vline(CX + 6, 37, 5, p.green)
    }

    private fun jack(fig: PixelCanvas, cc: CourtColors, p: PixelPalette, suit: Suit) {
        val top = 12
        robe(fig, cc, p, suit, 26)
        // Ruff collar.
        sym(fig, CX, 25, intArrayOf(7), p.cardHighlight)
        for (x in CX - 7..CX + 7 step 2) fig.set(x, 26, p.cardHighlight)
        // Feather (sticks up and right).
        val feather = if (suit.isRed) p.teal else p.hotPink
        for (i in 0..5) { fig.set(CX + 4 + i, top - 1 - i / 2 * 1 - (i % 2), feather) }
        for (i in 0..4) { fig.set(CX + 4 + i, top - i / 2, lerp(feather, p.white, 0.4f)) }
        // Floppy cap.
        sym(fig, CX - 1, top, intArrayOf(5, 6, 7), cc.robe)
        fig.hline(CX - 7, top + 2, 15, cc.robeDark)
        fig.set(CX - 4, top, cc.robeLight); fig.set(CX - 5, top + 1, cc.robeLight)
        // Hair peeking out and face.
        sym(fig, CX, top + 3, intArrayOf(5, 5, 5, 5, 5, 4, 3), skin)
        fig.hline(CX - 5, top + 3, 3, cc.hair); fig.hline(CX + 3, top + 3, 3, cc.hair)
        fig.vline(CX - 5, top + 4, 3, cc.hair); fig.vline(CX + 5, top + 4, 3, cc.hair)
        fig.set(CX - 5, top + 6, cc.hairDark); fig.set(CX + 5, top + 6, cc.hairDark)
        // A wink and a smirk.
        fig.vline(CX - 2, top + 5, 2, p.ink)
        fig.hline(CX + 1, top + 6, 3, p.ink)
        fig.set(CX - 3, top + 7, blush); fig.set(CX + 4, top + 7, blush)
        fig.set(CX - 1, top + 8, p.ink); fig.hline(CX, top + 9, 2, p.ink); fig.set(CX + 2, top + 8, p.ink)
    }

    // ---- Back ------------------------------------------------------------

    private fun drawBack(p: PixelPalette): PixelCanvas {
        val c = PixelCanvas(W, H)
        body(c, p.cardCream, p.cardHighlight, p.cardShade, p.ink)
        val a = p.purple
        val b = p.cabinet
        // Diamond lattice.
        for (y in 4 until H - 4) for (x in 4 until W - 4) {
            val u = Math.floorDiv(x + y, 5)
            val v = Math.floorDiv(x - y + 200, 5)
            var col = if ((u + v) % 2 == 0) a else b
            if ((x + y) % 5 == 0 && (x - y + 200) % 5 == 0) col = p.hotPink
            c.set(x, y, col)
        }
        c.rect(3, 3, W - 6, H - 6, p.gold)
        c.rect(4, 4, W - 8, H - 8, p.ink)

        // The SI robot emblem.
        val e = PixelCanvas(W, H)
        val metal = Color(0xFFD3DBF0)
        val metalShade = Color(0xFF8D97BC)
        val visor = Color(0xFF141834)
        // Antenna.
        e.vline(CX, 17, 3, metalShade)
        sym(e, CX, 14, intArrayOf(1, 1, 1), p.hotPink)
        e.set(CX - 1, 14, lerp(p.hotPink, p.white, 0.6f))
        // Head.
        sym(e, CX, 20, intArrayOf(7, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 7), metal)
        e.hline(CX - 7, 33, 15, metalShade)
        e.vline(CX + 8, 21, 12, metalShade)
        // Ear bolts.
        e.fill(CX - 10, 24, 2, 5, metalShade); e.fill(CX + 9, 24, 2, 5, metalShade)
        // Visor with LED eyes.
        e.fill(CX - 6, 22, 13, 7, visor)
        e.fill(CX - 4, 24, 3, 2, p.yellow); e.fill(CX + 2, 24, 3, 2, p.yellow)
        e.set(CX - 4, 24, p.white); e.set(CX + 2, 24, p.white)
        // Grin.
        e.set(CX - 4, 30, p.ink); e.set(CX + 4, 30, p.ink)
        e.hline(CX - 3, 31, 7, p.ink)
        e.set(CX - 6, 30, blush); e.set(CX + 6, 30, blush)
        // "SI" plaque.
        e.fill(CX - 6, 36, 13, 9, p.ink)
        e.rect(CX - 5, 37, 11, 7, p.gold)
        e.text("SI", CX - 3, 38, p.yellow, PixelFont.Tiny)
        e.outline(p.ink)
        e.outline(p.cardCream)
        c.blit(e, 0, 0)
        return c
    }
}

/** Hand-made suit bitmaps at three sizes. */
object SuitArt {
    private fun g(vararg rows: String) = PixelPattern(rows.toList())

    private val small = mapOf(
        Suit.HEARTS to g("XX.XX", "XXXXX", "XXXXX", ".XXX.", "..X.."),
        Suit.DIAMONDS to g("..X..", ".XXX.", "XXXXX", ".XXX.", "..X.."),
        Suit.SPADES to g("..X..", ".XXX.", "XXXXX", "XXXXX", "..X..", ".XXX."),
        Suit.CLUBS to g(".XXX.", ".XXX.", "XXXXX", "XXXXX", "..X..", ".XXX."),
    )

    private val pip = mapOf(
        Suit.HEARTS to g(".XX.XX.", "XXXXXXX", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."),
        Suit.DIAMONDS to g("...X...", "..XXX..", ".XXXXX.", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."),
        Suit.SPADES to g("...X...", "..XXX..", ".XXXXX.", "XXXXXXX", "XXXXXXX", "XX.X.XX", "...X...", "..XXX.."),
        Suit.CLUBS to g("..XXX..", "..XXX..", "XX.X.XX", "XXXXXXX", "XX.X.XX", "...X...", "..XXX.."),
    )

    private val big = mapOf(
        Suit.HEARTS to g(
            "...XXX...XXX...",
            ".XXXXXX.XXXXXX.",
            ".XXXXXXXXXXXXX.",
            "XXXXXXXXXXXXXXX",
            "XXXXXXXXXXXXXXX",
            "XXXXXXXXXXXXXXX",
            ".XXXXXXXXXXXXX.",
            ".XXXXXXXXXXXXX.",
            "..XXXXXXXXXXX..",
            "...XXXXXXXXX...",
            "....XXXXXXX....",
            ".....XXXXX.....",
            "......XXX......",
            ".......X.......",
        ),
        Suit.SPADES to g(
            ".......X.......",
            "......XXX......",
            ".....XXXXX.....",
            "....XXXXXXX....",
            "...XXXXXXXXX...",
            "..XXXXXXXXXXX..",
            ".XXXXXXXXXXXXX.",
            "XXXXXXXXXXXXXXX",
            "XXXXXXXXXXXXXXX",
            "XXXXXXXXXXXXXXX",
            ".XXXXXXXXXXXXX.",
            "..XXXX.X.XXXX..",
            ".......X.......",
            "......XXX......",
            ".....XXXXX.....",
        ),
        Suit.DIAMONDS to g(
            "......X......",
            ".....XXX.....",
            ".....XXX.....",
            "....XXXXX....",
            "...XXXXXXX...",
            "..XXXXXXXXX..",
            ".XXXXXXXXXXX.",
            "XXXXXXXXXXXXX",
            ".XXXXXXXXXXX.",
            "..XXXXXXXXX..",
            "...XXXXXXX...",
            "....XXXXX....",
            ".....XXX.....",
            ".....XXX.....",
            "......X......",
        ),
        Suit.CLUBS to clubShape(),
    )

    /** Club rasterised from three circles and a flared stem. */
    private fun clubShape(): PixelPattern {
        val w = 15; val h = 15
        val rows = (0 until h).map { y ->
            buildString {
                for (x in 0 until w) {
                    val px = x + 0.5; val py = y + 0.5
                    fun circ(cx: Double, cy: Double, r: Double) = hypot(px - cx, py - cy) <= r
                    val on = circ(7.5, 3.9, 3.7) || circ(3.7, 8.4, 3.7) || circ(11.3, 8.4, 3.7) ||
                        (abs(px - 7.5) <= 0.6 && py in 6.0..13.0) ||
                        (py >= 12.0 && abs(px - 7.5) <= (py - 11.0) * 1.1) ||
                        circ(7.5, 7.6, 2.0)
                    append(if (on) 'X' else '.')
                }
            }
        }
        return PixelPattern(rows)
    }

    fun small(s: Suit): PixelPattern = small.getValue(s)
    fun pip(s: Suit): PixelPattern = pip.getValue(s)
    fun big(s: Suit): PixelPattern = big.getValue(s)
}
