package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.random.Random

/** Facial expressions for [PixelAvatar]. */
enum class Mood { Neutral, Happy, Smug, Sad, Thinking, Angry, Shocked }

/**
 * A procedural pixel-art face on a 24x24 grid: species, head shape, hair,
 * hat, glasses, facial hair and colors all come from [seed], so every
 * random opponent gets a stable, unique face. [mood] changes the eyes,
 * brows and mouth. Blinks now and then when animated.
 */
@Suppress("ModifierParameter") // seed/mood first reads best at call sites.
@Composable
fun PixelAvatar(
    seed: Long,
    mood: Mood = Mood.Neutral,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    background: Boolean = true,
    blink: Boolean = true,
    /** What TalkBack says ("Lady Dot, rock"); null leaves the face to its caption. */
    contentDescription: String? = null,
) {
    val palette = LocalPixelPalette.current
    var closed by remember { mutableStateOf(false) }
    if (blink && PixelTheme.animated) {
        LaunchedEffect(seed) {
            val r = Random(seed)
            while (true) {
                delay(2200L + r.nextLong(2600))
                closed = true
                delay(120)
                closed = false
            }
        }
    }
    val art = AvatarArt.image(seed, mood, background, closed && mood != Mood.Shocked, palette)
    val spoken = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Image
    }
    Canvas(
        modifier.then(spoken).layout { m, _ ->
            val px = integerScale(size.toPx(), AvatarArt.SIZE) * AvatarArt.SIZE
            val pl = m.measure(Constraints.fixed(px, px))
            layout(px, px) { pl.place(0, 0) }
        },
    ) {
        drawPixelArtCentered(art, integerScale(this.size.minDimension, AvatarArt.SIZE))
    }
}

/** Everything the seed decides about a face. */
data class AvatarLook(
    val species: Species,
    val head: Int,
    val skin: Color,
    val hair: Hair,
    val hairColor: Color,
    val hat: Hat,
    val hatColor: Color,
    val glasses: Glasses,
    val facialHair: FacialHair,
    val shirt: Color,
    val background: Color,
    val blush: Boolean,
    val freckles: Boolean,
    val earring: Boolean,
) {
    enum class Species { Human, Robot, Alien, Monster }
    enum class Hair { Bald, Short, Spiky, Long, Afro, Mohawk, Bun, Bob }
    enum class Hat { None, Cap, TopHat, Beanie, Cowboy, Crown, Propeller, Party }
    enum class Glasses { None, Round, Shades, Monocle, Visor }
    enum class FacialHair { None, Mustache, Beard, Goatee }

    /** A short description, e.g. for an LLM persona prompt: "a robot with a top hat and shades". */
    fun describe(): String {
        val who = when (species) {
            Species.Human -> "a person"
            Species.Robot -> "a robot"
            Species.Alien -> "a little green alien"
            Species.Monster -> "a fuzzy monster"
        }
        val extras = buildList {
            if (hat != Hat.None) add("a " + hat.name.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase())
            when (glasses) {
                Glasses.Round -> add("round glasses")
                Glasses.Shades -> add("shades")
                Glasses.Monocle -> add("a monocle")
                Glasses.Visor -> add("a neon visor")
                Glasses.None -> {}
            }
            if (facialHair != FacialHair.None) add("a " + facialHair.name.lowercase())
        }
        return if (extras.isEmpty()) who else who + " with " + extras.joinToString(", ").replace(Regex(", (?=[^,]*$)"), " and ")
    }

    companion object {
        private val skins = listOf(0xFFFFDBB5, 0xFFF2C08F, 0xFFD99A6C, 0xFFA8693F, 0xFF6E4126, 0xFFFFE6D0).map { Color(it) }
        private val hairs = listOf(0xFF2A1A12, 0xFF5B3418, 0xFFA65A21, 0xFFE8C35A, 0xFFE24B2D, 0xFFB9B6CF, 0xFF1C1C2E, 0xFFFF5FB0, 0xFF3AD1E8, 0xFF8D5CF6).map { Color(it) }
        private val shirts = listOf(0xFFE8263E, 0xFF1F3BD1, 0xFF2FD15A, 0xFFFFC22E, 0xFF7A2CF2, 0xFFFF3D9A, 0xFF1FD6C4, 0xFFF26B1D, 0xFF3B4290).map { Color(it) }
        private val backgrounds = listOf(0xFF2A47C9, 0xFF7A2CF2, 0xFF0E6E5E, 0xFFB01F5E, 0xFFC97A12, 0xFF146B9E, 0xFF5B2A86, 0xFF1F7A3A).map { Color(it) }

        fun from(seed: Long): AvatarLook {
            val r = Random(seed * 0x9E3779B97F4A7C15uL.toLong() + 17)
            val species = r.pick(Species.entries, intArrayOf(70, 12, 9, 9))
            val skin = when (species) {
                Species.Human -> skins.random(r)
                Species.Robot -> listOf(Color(0xFFB8C4DE), Color(0xFFD9A441), Color(0xFF9AD4C8)).random(r)
                Species.Alien -> listOf(Color(0xFF7EE06A), Color(0xFFA6F0D4)).random(r)
                Species.Monster -> listOf(Color(0xFFB07CFF), Color(0xFFFF8FB8), Color(0xFF6FC7FF), Color(0xFFFFB347)).random(r)
            }
            val hair = when (species) {
                Species.Human -> r.pick(Hair.entries, intArrayOf(8, 22, 12, 16, 10, 6, 8, 14))
                Species.Monster -> r.pick(listOf(Hair.Spiky, Hair.Mohawk, Hair.Afro, Hair.Bald), intArrayOf(3, 3, 2, 2))
                else -> Hair.Bald
            }
            val hat = if (r.nextFloat() < 0.38f) r.pick(Hat.entries.drop(1), intArrayOf(16, 12, 14, 10, 6, 8, 8)) else Hat.None
            val glasses = if (r.nextFloat() < 0.32f) r.pick(Glasses.entries.drop(1), intArrayOf(10, 10, 4, if (species == Species.Robot) 8 else 3)) else Glasses.None
            val facial = if (species == Species.Human && r.nextFloat() < 0.3f) r.pick(FacialHair.entries.drop(1), intArrayOf(10, 8, 6)) else FacialHair.None
            return AvatarLook(
                species = species,
                head = r.nextInt(4),
                skin = skin,
                hair = hair,
                hairColor = hairs.random(r),
                hat = hat,
                hatColor = shirts.random(r),
                glasses = glasses,
                facialHair = facial,
                shirt = shirts.random(r),
                background = backgrounds.random(r),
                blush = r.nextFloat() < 0.5f,
                freckles = species == Species.Human && r.nextFloat() < 0.18f,
                earring = species == Species.Human && r.nextFloat() < 0.2f,
            )
        }

        private fun <T> Random.pick(items: List<T>, weights: IntArray): T {
            var n = nextInt(weights.sum())
            items.forEachIndexed { i, it -> n -= weights[i]; if (n < 0) return it }
            return items.last()
        }
    }
}

/** Draws and caches avatar bitmaps. */
object AvatarArt {
    const val SIZE = 24
    private val cache = ArtCache<Any>(96)

    fun image(seed: Long, mood: Mood, background: Boolean, eyesClosed: Boolean = false, palette: PixelPalette = PixelPalette.Default): ImageBitmap =
        cache.getOrPut(listOf(seed, mood, background, eyesClosed, palette)) { draw(AvatarLook.from(seed), mood, background, eyesClosed, palette).toImageBitmap() }

    private val headShapes = listOf(
        intArrayOf(6, 8, 10, 10, 12, 12, 12, 12, 12, 12, 10, 10, 8, 6), // round
        intArrayOf(10, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 10, 8), // square
        intArrayOf(6, 8, 8, 10, 10, 10, 10, 10, 10, 10, 10, 10, 8, 8, 6), // long
        intArrayOf(8, 12, 14, 14, 14, 14, 14, 14, 14, 12, 12, 10), // wide
    )

    /** Fills a row of even [width] centred on the grid (between columns 11 and 12). */
    private fun PixelCanvas.row(y: Int, width: Int, color: Color) = hline(12 - width / 2, y, width, color)

    fun draw(look: AvatarLook, mood: Mood, background: Boolean, eyesClosed: Boolean, p: PixelPalette): PixelCanvas {
        val c = PixelCanvas(SIZE, SIZE)
        if (background) {
            // A backdrop with a spotlight behind the head and shadowed
            // corners: the same lamp as the table.
            c.fill(1, 0, SIZE - 2, SIZE, look.background)
            c.fill(0, 1, SIZE, SIZE - 2, look.background)
            val light = lerp(look.background, Color.White, 0.2f)
            val dark = lerp(look.background, p.ink, 0.3f)
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                if (c[x, y] == 0) continue
                val d = hypot(x - 11.5, y - 8.5)
                when {
                    d < 9.6 -> c.set(x, y, light)
                    d > 14.2 -> c.set(x, y, dark)
                }
            }
        }

        val f = PixelCanvas(SIZE, SIZE)
        val shape = headShapes[look.head]
        val top = 18 - shape.size + 1 + if (look.head == 3) -1 else 0
        val bottom = top + shape.size - 1
        val skinShade = lerp(look.skin, p.ink, 0.22f)
        val hairDark = lerp(look.hairColor, p.ink, 0.35f)

        // Behind the head: long hair and afros, shaded on the right.
        fun backHair(y: Int, w: Int) {
            f.row(y, w, look.hairColor)
            f.hline(12 + w / 2 - 2, y, 2, hairDark)
        }
        when (look.hair) {
            AvatarLook.Hair.Afro -> for (y in top - 3..top + 9) {
                backHair(y, listOf(10, 14, 16, 18, 18, 20, 20, 20, 20, 18, 18, 16, 12)[y - (top - 3)])
            }
            AvatarLook.Hair.Long -> for (y in top..bottom + 3) backHair(y, shape.getOrElse(y - top) { 12 } + 4)
            AvatarLook.Hair.Bob -> for (y in top..top + 10) backHair(y, shape.getOrElse(y - top) { 12 } + 2)
            else -> {}
        }
        if (look.hair == AvatarLook.Hair.Afro) {
            f.hline(12 - 7, top - 1, 3, lerp(look.hairColor, Color.White, 0.3f))
            f.hline(12 - 8, top, 2, lerp(look.hairColor, Color.White, 0.3f))
        }

        // Shoulders and neck: the shirt is lit on the left, shaded on the
        // right, with a little V of neck at the collar.
        val shirtDark = lerp(look.shirt, p.ink, 0.3f)
        val shirtLight = lerp(look.shirt, Color.White, 0.3f)
        f.row(19, 4, skinShade)
        for (y in 20 until SIZE) {
            val w = minOf(22, 14 + (y - 20) * 3)
            f.row(y, w, look.shirt)
            f.set(12 - w / 2, y, shirtLight)
            f.hline(12 + w / 2 - 3, y, 3, shirtDark)
        }
        f.row(20, 6, shirtLight)
        f.row(20, 2, skinShade)
        f.row(21, 2, shirtDark)

        // Head.
        shape.forEachIndexed { i, w -> f.row(top + i, w, look.skin) }
        // Cheek shading on the right edge, a rim of light on the left.
        val skinLight = lerp(look.skin, Color.White, 0.3f)
        shape.forEachIndexed { i, w ->
            if (i > 2) f.set(12 + w / 2 - 1, top + i, skinShade)
            if (i in 1 until shape.size - 2) f.set(12 - w / 2, top + i, skinLight)
        }
        // A shadow under the chin.
        f.row(bottom, (shape.last() - 2).coerceAtLeast(2), skinShade)
        val half = shape.max() / 2

        // Ears (humans/monsters), bolts (robots).
        val earY = top + shape.size / 2 - 1
        when (look.species) {
            AvatarLook.Species.Robot -> {
                f.fill(12 - half - 1, earY, 1, 3, skinShade); f.fill(11 + half + 1, earY, 1, 3, skinShade)
            }
            AvatarLook.Species.Alien -> {}
            else -> {
                f.fill(12 - half - 1, earY, 1, 2, look.skin); f.fill(11 + half + 1, earY, 1, 2, skinShade)
                if (look.earring) f.set(11 + half + 1, earY + 2, p.gold)
            }
        }

        val eyeY = top + (shape.size * 0.42f).toInt()
        val mouthY = top + (shape.size * 0.74f).toInt()
        val lx = 9; val rx = 13 // eyes are 2 wide: 9-10 and 13-14

        // Hair on top of the head.
        when (look.hair) {
            AvatarLook.Hair.Short, AvatarLook.Hair.Long, AvatarLook.Hair.Bob, AvatarLook.Hair.Bun, AvatarLook.Hair.Spiky -> {
                f.row(top - 1, shape[0] - 2, look.hairColor)
                f.row(top, shape[0] + 2, look.hairColor)
                f.row(top + 1, shape[1], look.hairColor)
                f.row(top + 2, shape[2], look.hairColor)
                // Fringe: a side part.
                f.hline(12 - shape[3] / 2, top + 3, shape[3] / 2 + 1, look.hairColor)
                f.set(12 - shape[3] / 2, top + 4, look.hairColor)
                // Sheen on the lit side, shadow on the far side.
                f.hline(12 - shape[0] / 2, top, 3, lerp(look.hairColor, Color.White, 0.35f))
                f.set(12 - shape[1] / 2 + 1, top + 1, lerp(look.hairColor, Color.White, 0.2f))
                f.hline(12 + shape[1] / 2 - 2, top + 1, 2, hairDark)
                if (look.hair == AvatarLook.Hair.Spiky) {
                    for (i in 0 until 4) { f.set(7 + i * 3, top - 2, look.hairColor); f.set(8 + i * 3, top - 3, look.hairColor) }
                    f.row(top - 2, shape[0], look.hairColor)
                }
                if (look.hair == AvatarLook.Hair.Bun) { f.row(top - 3, 4, look.hairColor); f.row(top - 2, 4, look.hairColor) }
            }
            AvatarLook.Hair.Mohawk -> for (y in top - 3..top + 2) f.row(y, 2, look.hairColor)
            AvatarLook.Hair.Afro -> f.row(top, shape[0], look.hairColor)
            AvatarLook.Hair.Bald -> if (look.species == AvatarLook.Species.Human) f.set(9, top + 1, lerp(look.skin, Color.White, 0.6f))
        }

        // Species extras.
        when (look.species) {
            AvatarLook.Species.Robot -> if (look.hat == AvatarLook.Hat.None) {
                f.vline(12, top - 3, 3, skinShade); f.fill(11, top - 5, 2, 2, p.red)
                f.set(11, top - 5, lerp(p.red, Color.White, 0.6f))
            }
            AvatarLook.Species.Alien -> if (look.hat == AvatarLook.Hat.None) {
                f.set(8, top - 1, look.skin); f.set(7, top - 2, look.skin); f.set(7, top - 3, p.hotPink)
                f.set(15, top - 1, look.skin); f.set(16, top - 2, look.skin); f.set(16, top - 3, p.hotPink)
            }
            AvatarLook.Species.Monster -> {
                f.set(8, top - 1, p.cardCream); f.set(8, top - 2, p.cardCream)
                f.set(15, top - 1, p.cardCream); f.set(15, top - 2, p.cardCream)
            }
            else -> {}
        }

        // Face details.
        if (look.blush || mood == Mood.Happy) { f.set(lx - 1, eyeY + 3, p.hotPink.copy(alpha = 1f)); f.set(rx + 2, eyeY + 3, p.hotPink) }
        if (look.freckles) { f.set(lx, eyeY + 3, skinShade); f.set(lx + 1, eyeY + 4, skinShade); f.set(rx, eyeY + 4, skinShade); f.set(rx + 1, eyeY + 3, skinShade) }
        drawEyes(f, look, mood, eyesClosed, lx, rx, eyeY, p)
        if (look.facialHair == AvatarLook.FacialHair.Beard) {
            for (y in mouthY - 1..bottom + 1) f.row(y, (shape.getOrElse(y - top) { 6 }).coerceAtLeast(6), look.hairColor)
            f.row(mouthY, 6, look.skin)
        }
        drawMouth(f, look, mood, mouthY, p)
        when (look.facialHair) {
            AvatarLook.FacialHair.Mustache -> { f.row(mouthY - 1, 6, look.hairColor); f.set(8, mouthY, look.hairColor); f.set(15, mouthY, look.hairColor) }
            AvatarLook.FacialHair.Goatee -> f.row(mouthY + 2, 2, look.hairColor)
            AvatarLook.FacialHair.Beard -> f.row(mouthY - 1, 6, hairDark)
            AvatarLook.FacialHair.None -> {}
        }
        drawGlasses(f, look, lx, rx, eyeY, p)
        drawHat(f, look, top, shape, p)

        f.outline(p.ink)
        c.blit(f, 0, 0)
        return c
    }

    private fun drawEyes(f: PixelCanvas, look: AvatarLook, mood: Mood, closed: Boolean, lx: Int, rx: Int, y: Int, p: PixelPalette) {
        val ink = p.ink
        val robot = look.species == AvatarLook.Species.Robot
        val alien = look.species == AvatarLook.Species.Alien
        if (closed || mood == Mood.Happy) {
            // Closed: a line. Happy: upturned arcs.
            for (x in listOf(lx, rx)) {
                if (mood == Mood.Happy) { f.set(x - 1, y + 1, ink); f.hline(x, y, 2, ink); f.set(x + 2, y + 1, ink) } else f.hline(x, y + 1, 2, ink)
            }
        } else {
            for (x in listOf(lx, rx)) {
                when {
                    robot -> { f.fill(x, y, 2, 2, p.yellow); f.set(x, y, Color.White) }
                    alien -> { f.fill(x - 1, y - 1, 3, 3, ink); f.set(x - 1, y - 1, Color.White) }
                    mood == Mood.Shocked -> { f.fill(x, y - 1, 2, 3, Color.White); f.set(x + (if (x == lx) 1 else 0), y, ink) }
                    mood == Mood.Thinking -> { f.fill(x, y, 2, 2, Color.White); f.set(x + 1, y, ink) }
                    mood == Mood.Smug -> { f.hline(x, y, 2, lerp(look.skin, ink, 0.35f)); f.hline(x, y + 1, 2, ink) }
                    else -> { f.fill(x, y, 2, 2, ink); f.set(x, y, Color.White) }
                }
            }
        }
        // Brows.
        val brow = if (look.species == AvatarLook.Species.Human) lerp(look.hairColor, ink, 0.3f) else ink
        when (mood) {
            Mood.Sad -> { f.set(lx, y - 1, brow); f.set(lx + 1, y - 2, brow); f.set(rx, y - 2, brow); f.set(rx + 1, y - 1, brow) }
            Mood.Angry -> { f.set(lx, y - 2, brow); f.set(lx + 1, y - 1, brow); f.set(rx, y - 1, brow); f.set(rx + 1, y - 2, brow) }
            Mood.Smug -> { f.hline(lx, y - 1, 2, brow); f.hline(rx, y - 2, 2, brow) }
            Mood.Thinking -> { f.hline(lx, y - 1, 2, brow); f.hline(rx, y - 3, 2, brow) }
            Mood.Shocked -> { f.hline(lx, y - 3, 2, brow); f.hline(rx, y - 3, 2, brow) }
            else -> {}
        }
        if (mood == Mood.Sad && !closed) f.set(rx + 1, y + 2, p.teal)
    }

    private fun drawMouth(f: PixelCanvas, look: AvatarLook, mood: Mood, y: Int, p: PixelPalette) {
        val ink = p.ink
        if (look.species == AvatarLook.Species.Robot && mood != Mood.Shocked) {
            f.fill(9, y, 6, 2, lerp(look.skin, ink, 0.6f))
            for (x in 10..14 step 2) f.vline(x, y, 2, lerp(look.skin, Color.White, 0.3f))
            if (mood == Mood.Happy) { f.set(8, y - 1, ink); f.set(15, y - 1, ink) }
            if (mood == Mood.Sad) { f.set(8, y + 2, ink); f.set(15, y + 2, ink) }
            return
        }
        when (mood) {
            Mood.Neutral -> f.hline(10, y, 4, ink)
            Mood.Happy -> {
                f.set(8, y - 1, ink); f.set(15, y - 1, ink)
                f.hline(9, y, 6, ink)
                f.hline(10, y + 1, 4, p.suitRed); f.set(9, y + 1, ink); f.set(14, y + 1, ink)
                f.hline(10, y + 2, 4, ink)
                f.hline(10, y, 4, Color.White)
            }
            Mood.Smug -> { f.hline(9, y, 4, ink); f.set(13, y - 1, ink); f.set(14, y - 1, ink) }
            Mood.Sad -> { f.hline(10, y, 4, ink); f.set(9, y + 1, ink); f.set(14, y + 1, ink) }
            Mood.Thinking -> { f.hline(12, y, 2, ink); f.set(11, y + 1, ink) }
            Mood.Angry -> { f.fill(9, y, 6, 2, Color.White); f.rect(8, y - 1, 8, 4, ink); f.vline(11, y, 2, ink); f.vline(13, y, 2, ink) }
            Mood.Shocked -> { f.fill(10, y - 1, 4, 3, p.suitRedDark); f.rect(10, y - 1, 4, 3, ink); f.set(10, y - 1, 0); f.set(13, y - 1, 0); f.row(y - 2, 2, ink) }
        }
        if (look.species == AvatarLook.Species.Monster && mood != Mood.Shocked) { f.set(10, y + 1, Color.White); f.set(13, y + 1, Color.White) }
    }

    private fun drawGlasses(f: PixelCanvas, look: AvatarLook, lx: Int, rx: Int, y: Int, p: PixelPalette) {
        when (look.glasses) {
            AvatarLook.Glasses.None -> {}
            AvatarLook.Glasses.Round -> {
                for (x in listOf(lx, rx)) { f.rect(x - 1, y - 1, 4, 4, p.ink) }
                f.hline(lx + 3, y, 1, p.ink); f.hline(rx - 1, y, 1, p.ink)
            }
            AvatarLook.Glasses.Shades -> {
                f.fill(lx - 1, y - 1, 4, 3, p.ink); f.fill(rx - 1, y - 1, 4, 3, p.ink)
                f.hline(lx + 3, y - 1, 2, p.ink)
                f.set(lx, y - 1, Color.White); f.set(rx, y - 1, Color.White)
            }
            AvatarLook.Glasses.Monocle -> { f.rect(rx - 1, y - 1, 4, 4, p.gold); f.vline(rx + 2, y + 3, 4, p.gold) }
            AvatarLook.Glasses.Visor -> {
                f.fill(lx - 2, y - 1, 12, 3, p.hotPink)
                f.hline(lx - 2, y - 1, 12, lerp(p.hotPink, Color.White, 0.5f))
            }
        }
    }

    private fun drawHat(f: PixelCanvas, look: AvatarLook, top: Int, shape: IntArray, p: PixelPalette) {
        val hc = look.hatColor
        val dark = lerp(hc, p.ink, 0.35f)
        val light = lerp(hc, Color.White, 0.35f)
        val w = shape[1] + 2
        when (look.hat) {
            AvatarLook.Hat.None -> {}
            AvatarLook.Hat.Cap -> {
                for (y in top - 3..top + 1) f.row(y, minOf(w, 8 + (y - top + 3) * 2), hc)
                f.hline(12, top + 1, 9, dark)
                f.set(11, top - 3, light)
            }
            AvatarLook.Hat.TopHat -> {
                for (y in top - 5..top) f.row(y, 10, p.inkSoft)
                f.row(top - 1, 10, hc)
                f.row(top + 1, 16, p.inkSoft)
                f.vline(8, top - 5, 4, lerp(p.inkSoft, Color.White, 0.25f))
            }
            AvatarLook.Hat.Beanie, AvatarLook.Hat.Propeller -> {
                for (y in top - 3..top + 1) f.row(y, minOf(w, 8 + (y - top + 3) * 2), hc)
                f.row(top + 1, w, dark)
                for (x in 12 - w / 2 until 12 + w / 2 step 2) f.set(x, top + 1, light)
                if (look.hat == AvatarLook.Hat.Beanie) { f.row(top - 4, 2, light); f.row(top - 5, 2, light) } else {
                    f.vline(12, top - 5, 2, p.gray)
                    f.hline(8, top - 6, 4, p.red); f.hline(12, top - 6, 4, p.cabinetLight)
                }
            }
            AvatarLook.Hat.Cowboy -> {
                for (y in top - 4..top) f.row(y, if (y == top - 4) 6 else 10, hc)
                f.row(top - 4, 2, Color.Transparent)
                f.row(top - 1, 10, dark)
                f.row(top + 1, 20, hc); f.row(top, 20, hc)
                f.set(1, top - 1, hc); f.set(22, top - 1, hc)
            }
            AvatarLook.Hat.Crown -> {
                f.row(top - 1, 12, p.gold); f.row(top, 12, p.goldDark)
                for (x in listOf(6, 9, 14, 17)) { f.set(x, top - 2, p.gold); f.set(x, top - 3, p.gold) }
                f.set(11, top - 2, p.gold); f.set(12, top - 2, p.gold); f.set(11, top - 3, p.red); f.set(12, top - 3, p.red)
                f.set(9, top, p.teal); f.set(14, top, p.hotPink)
            }
            AvatarLook.Hat.Party -> {
                for (i in 0 until 7) f.row(top - 6 + i, (i + 1) / 2 * 2 + 2, if (i % 2 == 0) hc else light)
                f.row(top - 7, 2, p.yellow)
            }
        }
    }
}
