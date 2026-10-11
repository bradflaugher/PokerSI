package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Rank
import com.bradflaugher.pokersi.engine.Suit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

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

    /** The face as an editable canvas (for art built from cards, like [CardFanArt]). */
    internal fun faceCanvas(card: Card, palette: PixelPalette): PixelCanvas = drawFace(card, palette)

    /** Renders every face and the back into the cache; call off the main thread. */
    fun prewarm(palette: PixelPalette = PixelPalette.Default) {
        back(palette)
        for (suit in Suit.entries) for (rank in Rank.entries) face(Card(rank, suit), palette)
    }

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

    /**
     * The ace: the big suit, bevelled and outlined, on a gold medallion
     * (lit rim, shaded rim, a field of alternating rays) with sparkles.
     */
    private fun drawAce(c: PixelCanvas, suit: Suit, p: PixelPalette) {
        val color = suitColor(suit, p)
        val cy = 31
        val field = lerp(p.cardCream, color, 0.08f)
        val ray = lerp(p.cardCream, p.gold, 0.45f)
        for (y in 0 until H) for (x in 0 until W) {
            val dx = x - CX.toDouble()
            val dy = y - cy.toDouble()
            val d = hypot(dx, dy)
            if (d > 14.5) continue
            val lit = -(dx + dy) / (d + 0.001)
            val col = when {
                d > 13.6 -> p.ink
                d > 12.2 -> if (lit > 0.35) p.yellow else if (lit < -0.35) p.goldDark else p.gold
                d > 11.3 -> p.goldDark
                else -> {
                    val a = atan2(dy, dx)
                    val seg = ((a + Math.PI) / (Math.PI * 2) * 16).toInt()
                    if (seg % 2 == 0 && d > 3) ray else field
                }
            }
            c.set(x, y, col)
        }
        // Rivets around the rim.
        for (i in 0 until 8) {
            val a = i * Math.PI / 4 + Math.PI / 8
            c.set(CX + (12.9 * cos(a)).roundToInt(), cy + (12.9 * sin(a)).roundToInt(), p.cardHighlight)
        }
        val big = SuitArt.big(suit)
        val art = PixelCanvas(big.width + 2, big.height + 2)
        stampBeveled(art, big, 1, 1, color, lerp(color, p.white, 0.5f), lerp(color, p.ink, 0.45f))
        art.outline(p.ink)
        c.blit(art, CX - art.width / 2, cy - art.height / 2)
        for ((sx, sy) in listOf(CX + 12 to 18, CX - 12 to 44)) sparkle(c, sx, sy, p)
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
    //
    // Hand-pixelled heads (crowns, faces, hair, beards) on procedural bodies
    // (robes with brocade, trim, collars and a held prop), in a gold frame
    // with a lamp-lit halo. Light comes from the top left, as everywhere.

    private class CourtColors(
        val bg: Color,
        val skin: Color,
        val skinShade: Color,
        val hair: Color,
        val hairDark: Color,
        val robe: Color,
        val robeDark: Color,
        val robeLight: Color,
        val accent: Color,
        val accentDark: Color,
        val jewel: Color,
    )

    private fun courtColors(suit: Suit, p: PixelPalette): CourtColors = when (suit) {
        Suit.HEARTS -> CourtColors(
            Color(0xFFFFE3E3), Color(0xFFFFCE9E), Color(0xFFE0915F), Color(0xFFC0471F), Color(0xFF7E2A12),
            p.suitRed, p.suitRedDark, Color(0xFFFF6E7F), p.gold, p.goldDark, p.teal,
        )
        Suit.DIAMONDS -> CourtColors(
            Color(0xFFFFEEC2), Color(0xFFE8A87C), Color(0xFFB8714A), Color(0xFFF2B33A), Color(0xFFA9701A),
            p.suitRed, p.suitRedDark, Color(0xFFFF6E7F), p.teal, Color(0xFF0E8F84), p.teal,
        )
        Suit.SPADES -> CourtColors(
            Color(0xFFDCE3FF), Color(0xFFFFDDB8), Color(0xFFD99A6C), Color(0xFF2B2240), Color(0xFF130F21),
            p.suitBlack, p.ink, p.suitBlackLight, p.hotPink, p.hotPinkDark, p.hotPink,
        )
        Suit.CLUBS -> CourtColors(
            Color(0xFFD6F5E6), Color(0xFFB9744A), Color(0xFF8A4F2C), Color(0xFF2A1A12), Color(0xFF130F21),
            p.suitBlack, p.ink, p.suitBlackLight, p.green, p.greenDark, p.hotPink,
        )
    }

    private val beard = Color(0xFFF2EFF8)
    private val beardShade = Color(0xFFB9B6CF)
    private val blush = Color(0xFFFF8A9A)
    private val steel = Color(0xFFD3DBF0)
    private val steelShade = Color(0xFF8D97BC)

    private val KingHead = PixelPattern.of(
        "...o....o....o...",
        "..ojo..ojo..ojo..",
        "..oyo..oyo..oGo..",
        ".oyygooyygooyGGo.",
        ".oyjggggjggggjGo.",
        ".oyggggggggggGGo.",
        ".oGGGGGGGGGGGGGo.",
        ".owwssssssssSWWo.",
        ".owwsWWsssWWSWWo.",
        ".owwssosssosSWWo.",
        ".owwssosssosSWWo.",
        ".owwsbssSssbSWWo.",
        ".owwswwwwwwwSWWo.",
        ".owwwwwmmmwwWWWo.",
        ".owwwWwwwwWwWWWo.",
        "..owwwwWwwwWWWo..",
        "...owwWwwwWWWo...",
        "....owwwwWWWo....",
        ".....owwWWWo.....",
        "......owWWo......",
        ".......ooo.......",
    )

    private val QueenHead = PixelPattern.of(
        "........o........",
        ".......ojo.......",
        "....o..oyo..o....",
        "...oyo.oyo.oGo...",
        "..oyjygyjyggjGo..",
        "..ohGGGGGGGGGHo..",
        ".ohhhhhhhhhhHHHo.",
        ".ohhhssssssshHHo.",
        "ohhhssssssssSHHHo",
        "ohhsosssssosSHHHo",
        "ohhsosssssosSHHHo",
        "ohhssssSssssSHHHo",
        "ohhsbsssssbsSHHHo",
        "ohhhsssmmmssSHHHo",
        "ohhhhsssssssHHHHo",
        "ohhhhhSsssSHHHHHo",
        "ohhhhhossSoHHHHHo",
        "ohhhhhossSoHHHHHo",
        "ohhhho.....oHHHHo",
        ".ohho.......oHHo.",
        "..oo.........oo..",
    )

    private val JackHead = PixelPattern.of(
        "...........aa....",
        "..........aAa....",
        ".........aAa.....",
        "....ooooaAaoo....",
        "...orrrrqarRRo...",
        "..orqrrrrrrrRRo..",
        ".orqrrrrrrrrrRRo.",
        "oRRRRRRRRRRRRRRRo",
        "..ohhssssssshHo..",
        "..ohssssssssSHo..",
        "..ohshhssshhsSo..",
        "..ohsosssssssSo..",
        "..ohsossssoosSo..",
        "..ohssssSsssSHo..",
        "..ohsbssssbsSHo..",
        "..ohssssssoSSHo..",
        "...ohssoooSSSo...",
        "....osssssSSo....",
        ".....ossssSo.....",
        "......ooooo......",
    )

    // The frame's inside, in card pixels.
    private const val FX = 12
    private const val FY = 10
    private const val FW = 21
    private const val FH = 43

    private fun drawCourt(c: PixelCanvas, card: Card, p: PixelPalette) {
        val cc = courtColors(card.suit, p)
        val frameColor = suitColor(card.suit, p)
        // Frame: suit-colored border, a bevelled gold line, the backdrop.
        c.fill(FX - 2, FY - 2, FW + 4, FH + 4, frameColor)
        c.fill(FX - 1, FY - 1, FW + 2, FH + 2, p.goldDark)
        c.hline(FX - 1, FY - 1, FW + 1, p.yellow)
        c.vline(FX - 1, FY - 1, FH + 1, p.gold)
        c.fill(FX, FY, FW, FH, cc.bg)
        // A lamp-lit halo behind the head, dithered out at its edge, and a
        // darker floor so the figure stands in light.
        val halo = lerp(cc.bg, p.white, 0.6f)
        val floor = lerp(cc.bg, p.ink, 0.08f)
        for (y in FY until FY + FH) for (x in FX until FX + FW) {
            val d = hypot(x - CX.toDouble(), (y - (FY + 11)).toDouble())
            when {
                d < 8.5 -> c.set(x, y, halo)
                d < 10.5 && (x + y) % 2 == 0 -> c.set(x, y, halo)
                y > FY + FH - 12 && (x + y) % 2 == 0 -> c.set(x, y, floor)
                y > FY + FH - 8 -> c.set(x, y, floor)
            }
        }
        // Gold corner studs.
        for ((x, y) in listOf(FX - 2 to FY - 2, FX + FW + 1 to FY - 2, FX - 2 to FY + FH + 1, FX + FW + 1 to FY + FH + 1)) {
            c.set(x, y, p.gold)
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
            if (x !in FX until FX + FW || y !in FY until FY + FH) fig.set(x, y, 0)
        }
        c.blit(fig, 0, 0)
    }

    private fun headColors(cc: CourtColors, p: PixelPalette) = mapOf(
        'o' to p.ink, 's' to cc.skin, 'S' to cc.skinShade, 'b' to blush,
        'w' to beard, 'W' to beardShade, 'g' to p.gold, 'G' to p.goldDark, 'y' to p.yellow,
        'j' to cc.jewel, 'm' to p.suitRed,
        'h' to cc.hair, 'H' to cc.hairDark,
        'r' to cc.robe, 'R' to cc.robeDark, 'q' to cc.robeLight,
        'a' to cc.accent, 'A' to cc.accentDark,
    )

    /**
     * The robe: shoulders sloping out to the frame from [top], lit on the
     * left and in shadow on the right, a brocade of accent dots, a gold
     * placket down the middle and a medallion bearing the suit.
     */
    private fun robe(fig: PixelCanvas, cc: CourtColors, p: PixelPalette, suit: Suit, top: Int) {
        val bottom = FY + FH
        for (y in top until bottom) {
            val h = minOf(11, 5 + (y - top) * 2)
            for (x in CX - h..CX + h) {
                val rel = x - CX
                val col = when {
                    rel == -h || rel == -h + 1 && y > top + 1 -> cc.robeLight
                    rel >= h - 2 -> cc.robeDark
                    (x + 2 * y) % 8 == 0 && y % 4 == 1 -> cc.accent
                    Math.floorMod(x - 2 * y, 8) == 0 && y % 4 == 3 -> cc.accentDark
                    else -> cc.robe
                }
                fig.set(x, y, col)
            }
            // A fold shadow down each flank.
            if (y > top + 5) { fig.set(CX - 6, y, cc.robeDark); fig.set(CX + 6, y, cc.robeDark) }
        }
        // Gold placket.
        for (y in top + 2 until bottom) {
            fig.set(CX - 1, y, p.gold)
            fig.set(CX, y, if (y % 3 == 0) p.yellow else p.gold)
            fig.set(CX + 1, y, p.goldDark)
        }
        // Medallion bearing the suit.
        val my = top + 12
        sym(fig, CX, my - 4, intArrayOf(2, 3, 4, 4, 4, 4, 4, 3, 2), p.ink)
        sym(fig, CX, my - 3, intArrayOf(2, 3, 3, 3, 3, 3, 2), p.gold)
        sym(fig, CX, my - 2, intArrayOf(2, 2, 2, 2, 2), p.cardCream)
        fig.set(CX - 2, my - 2, p.yellow); fig.set(CX - 3, my - 1, p.yellow)
        fig.set(CX + 2, my + 2, p.goldDark); fig.set(CX + 3, my + 1, p.goldDark)
        val pip = SuitArt.small(suit)
        fig.stamp(pip, CX - pip.width / 2, my - pip.height / 2, if (suit.isRed) p.suitRed else p.suitBlack)
    }

    /** Fills symmetric rows around [cx]: row i spans cx-half[i]..cx+half[i] (negative = skip). */
    private fun sym(c: PixelCanvas, cx: Int, y0: Int, half: IntArray, color: Color) {
        half.forEachIndexed { i, h -> if (h >= 0) c.hline(cx - h, y0 + i, h * 2 + 1, color) }
    }

    /** A hand: a 2x2 fist with a lit knuckle. */
    private fun hand(fig: PixelCanvas, x: Int, y: Int, cc: CourtColors) {
        fig.fill(x, y, 2, 2, cc.skin)
        fig.set(x + 1, y + 1, cc.skinShade)
        fig.rect(x - 1, y - 1, 4, 4, Color(0xFF130F21))
    }

    private fun king(fig: PixelCanvas, cc: CourtColors, p: PixelPalette, suit: Suit) {
        val top = FY + 17
        robe(fig, cc, p, suit, top)
        // Ermine cape over the shoulders: white fur with black tails.
        for (y in top - 1..top + 3) {
            val h = minOf(11, 6 + (y - top + 1) * 2)
            fig.hline(CX - h, y, h * 2 + 1, beard)
            fig.set(CX + h, y, beardShade); fig.set(CX + h - 1, y, beardShade)
        }
        fig.hline(CX - 11, top + 4, 23, beardShade)
        for ((dx, dy) in listOf(-9 to 1, -5 to 3, 5 to 3, 9 to 1, -10 to 4, 10 to 4)) fig.set(CX + dx, top + dy, p.ink)
        // Sceptre in the right hand (viewer's left): gold rod, jewelled orb.
        val sx = CX - 8
        for (y in FY + 9 until FY + FH) { fig.set(sx, y, p.gold); fig.set(sx + 1, y, p.goldDark) }
        fig.rect(sx - 1, FY + 9, 4, FH - 9, p.ink)
        sym(fig, sx, FY + 5, intArrayOf(1, 2, 2, 1), p.ink)
        fig.set(sx, FY + 6, cc.jewel); fig.set(sx - 1, FY + 6, lerp(cc.jewel, p.white, 0.6f)); fig.set(sx + 1, FY + 6, cc.jewel)
        fig.set(sx, FY + 7, cc.jewel); fig.set(sx - 1, FY + 7, cc.jewel); fig.set(sx + 1, FY + 7, lerp(cc.jewel, p.ink, 0.4f))
        fig.set(sx, FY + 4, p.gold); fig.set(sx, FY + 3, p.yellow)
        fig.hline(sx - 1, FY + 8, 3, p.gold)
        hand(fig, sx - 1, top + 9, cc)
        fig.stamp(KingHead, CX - 8, FY + 1, headColors(cc, p))
    }

    private fun queen(fig: PixelCanvas, cc: CourtColors, p: PixelPalette, suit: Suit) {
        val top = FY + 18
        robe(fig, cc, p, suit, top)
        // A square neckline with a gold necklace and a pendant.
        fig.fill(CX - 3, top - 1, 7, 4, cc.skin)
        fig.vline(CX + 3, top - 1, 4, cc.skinShade)
        fig.hline(CX - 3, top + 3, 7, p.gold)
        fig.hline(CX - 2, top + 1, 5, p.gold)
        fig.set(CX, top + 2, cc.jewel)
        // A rose held up in the left hand (viewer's right).
        val rx = CX + 6
        val ry = top + 5
        fig.vline(rx, ry, 9, p.greenDark)
        fig.set(rx + 1, ry + 4, p.green); fig.set(rx - 1, ry + 6, p.green)
        sym(fig, rx, ry - 3, intArrayOf(1, 2, 2, 1), p.hotPink)
        fig.set(rx - 1, ry - 2, lerp(p.hotPink, p.white, 0.55f))
        fig.set(rx + 1, ry - 1, p.hotPinkDark); fig.set(rx, ry - 1, p.hotPinkDark)
        hand(fig, rx - 1, ry + 6, cc)
        fig.stamp(QueenHead, CX - 8, FY + 1, headColors(cc, p))
    }

    private fun jack(fig: PixelCanvas, cc: CourtColors, p: PixelPalette, suit: Suit) {
        val top = FY + 19
        robe(fig, cc, p, suit, top)
        // Ruff collar.
        sym(fig, CX, top - 1, intArrayOf(7, 8), p.cardHighlight)
        for (x in CX - 8..CX + 8) if ((x + CX) % 2 == 0) fig.set(x, top + 1, p.cardHighlight)
        for (x in CX - 7..CX + 7 step 2) fig.set(x, top, beardShade)
        // A sword held upright on the left (viewer's right).
        val bx = CX + 8
        for (y in FY + 4 until top + 6) { fig.set(bx, y, steel); fig.set(bx + 1, y, steelShade) }
        fig.set(bx, FY + 3, steel)
        fig.vline(bx, FY + 5, 6, p.white)
        fig.hline(bx - 2, top + 6, 6, p.gold)
        fig.set(bx + 3, top + 6, p.goldDark)
        hand(fig, bx, top + 8, cc)
        fig.stamp(JackHead, CX - 8, FY + 1, headColors(cc, p))
    }

    // ---- Back ------------------------------------------------------------

    /**
     * The PokerSI back: a cabinet-blue panel with a fine diamond lattice,
     * a gold double frame with the four suits in its corners, and the SI
     * robot on a red casino chip (the launcher icon's emblem) in the middle.
     */
    private fun drawBack(p: PixelPalette): PixelCanvas {
        val c = PixelCanvas(W, H)
        body(c, p.cardCream, p.cardHighlight, p.cardShade, p.ink)
        val field = p.cabinetDeep
        val line = p.cabinet
        // Panel with a lattice of diagonal lines and pink studs where they cross.
        for (y in 5 until H - 5) for (x in 5 until W - 5) {
            val u = Math.floorMod(x + y, 6)
            val v = Math.floorMod(x - y, 6)
            val col = when {
                u == 0 && v == 0 && Math.floorMod(x + y, 12) == 0 -> p.hotPink
                u == 0 || v == 0 -> line
                else -> field
            }
            c.set(x, y, col)
        }
        // A soft glow behind the chip.
        for (y in 5 until H - 5) for (x in 5 until W - 5) {
            val d = hypot(x - CX.toDouble(), y - 31.0)
            if (d < 17.5 && (d < 15.5 || (x + y) % 2 == 0) && c[x, y] == field.toArgb()) c.set(x, y, p.purpleDark)
        }
        // Double frame: gold, ink, then a thin gold pinline.
        c.rect(3, 3, W - 6, H - 6, p.gold)
        c.hline(4, 3, W - 9, p.yellow); c.vline(3, 4, H - 9, p.yellow)
        c.rect(4, 4, W - 8, H - 8, p.ink)
        c.rect(6, 6, W - 12, H - 12, p.goldDark)
        // The four suits in the corners.
        val pips = listOf(Suit.SPADES to (8 to 8), Suit.HEARTS to (W - 13 to 8), Suit.CLUBS to (W - 13 to H - 14), Suit.DIAMONDS to (8 to H - 14))
        for ((suit, at) in pips) {
            val pat = SuitArt.small(suit)
            c.fill(at.first - 1, at.second - 1, pat.width + 2, pat.height + 2, field)
            c.stamp(pat, at.first, at.second, if (suit.isRed) p.hotPink else p.gold)
        }
        RobotChip.draw(c, CX + 0.5, 31.5, p)
        return c
    }
}

/**
 * The PokerSI emblem: a red casino chip with the SI robot's head on its
 * inlay (a heart on its antenna, two LED eyes, a smile). The launcher icon is
 * the same design (tools/icon/gen_icon.py); this is its card-back size.
 */
internal object RobotChip {
    private const val R = 12.6

    private val Head = PixelPattern.of(
        ".....oo.oo.....",
        "....oppopPo....",
        "....oMpppPo....",
        ".....oppPo.....",
        "......oPo......",
        "......omo......",
        "..ooooomoooooo.",
        ".oMMMMMMMMMMMno",
        ".oMvvvvvvvvvmno",
        "noMvyyvvvvyyvno",
        "noMvYYvvvvYYvno",
        ".oMmvvvvvvvvmno",
        ".oMpmommmmompno",
        ".oMmmmoooommmno",
        ".onnnnnnnnnnnno",
        "..ooooooooooooo",
    )

    fun draw(c: PixelCanvas, cx: Double, cy: Double, p: PixelPalette) {
        val red = p.suitRed
        val redDark = p.suitRedDark
        val redLight = Color(0xFFFF6A6A)
        val groove = R * 0.74
        val ring = groove - 1.0
        val inlay = ring - 1.2
        val x0 = (cx - R - 2).toInt()
        val y0 = (cy - R - 2).toInt()
        val chip = PixelCanvas(c.width, c.height)
        for (y in y0..(cy + R + 2).toInt()) for (x in x0..(cx + R + 2).toInt()) {
            val dx = x + 0.5 - cx
            val dy = y + 0.5 - cy
            val d = hypot(dx, dy)
            if (d > R) continue
            val lit = -(dx + dy) / (1.4142 * (d + 0.001))
            val ang = Math.floorMod((Math.toDegrees(atan2(dy, dx)) + 360 + 8 - 22.5).toInt(), 45)
            val outer = d > R - 1.6
            val col = when {
                d > groove && ang < 16 -> if (lit > 0.5 && outer) p.cardHighlight else if (lit < -0.5 && outer) p.cardShade else p.cardCream
                d > groove -> if (lit > 0.55 && outer) redLight else if (lit < -0.4 && outer) redDark else red
                d > ring -> redDark
                d > inlay -> if (lit > -0.2) p.gold else p.goldDark
                (dx + dy) > 1.5 && d > inlay - 1.2 -> p.cardShade
                else -> p.cardCream
            }
            chip.set(x, y, col)
        }
        val colors = mapOf(
            'o' to p.ink, 'M' to Color(0xFFFFFFFF), 'm' to Color(0xFFD3DBF0), 'n' to Color(0xFF8D97BC),
            'v' to Color(0xFF141834), 'y' to p.yellow, 'Y' to p.ledAmber, 'p' to p.hotPink, 'P' to p.hotPinkDark,
        )
        chip.stamp(Head, (cx - Head.width / 2.0).toInt(), (cy - Head.height / 2.0 - 1).toInt(), colors)
        chip.outline(p.ink)
        c.blit(chip, 0, 0)
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
