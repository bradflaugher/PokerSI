package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bradflaugher.pokersi.engine.Card
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object PixelCardDefaults {
    /** A comfortable card width on a phone (hand cards). */
    val Width: Dp = 64.dp

    /** Card height for a given width (the art is 45 x 63 pixels). */
    fun heightFor(width: Dp): Dp = width * (CardArt.H.toFloat() / CardArt.W)

    /** Greys and darkens folded or dead cards. */
    val DimFilter: ColorFilter = ColorFilter.colorMatrix(
        ColorMatrix().apply { setToSaturation(0.15f) }.also {
            it.timesAssign(ColorMatrix(floatArrayOf(0.55f, 0f, 0f, 0f, 0f, 0f, 0.55f, 0f, 0f, 0f, 0f, 0f, 0.6f, 0f, 8f, 0f, 0f, 0f, 1f, 0f)))
        },
    )
}

/**
 * A playing card drawn as genuine pixel art: the card's 45x63 bitmap is
 * blitted at the largest whole-number scale that fits [size] (the card's
 * width), with nearest-neighbour filtering, a hard drop shadow, and the
 * video poker "HELD" tag above it when [held].
 *
 * [card] null or [faceUp] false shows the PokerSI back. [highlighted] adds
 * a pulsing gold glow (winning cards), [dimmed] greys it out (folded).
 */
@Suppress("ModifierParameter") // card/faceUp first reads best at call sites.
@Composable
fun PixelCard(
    card: Card?,
    faceUp: Boolean = true,
    modifier: Modifier = Modifier,
    held: Boolean = false,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    size: Dp = PixelCardDefaults.Width,
) {
    val palette = LocalPixelPalette.current
    val glowPulse = glowPulse(highlighted)
    val spoken = cardDescription(card, faceUp, held)
    Canvas(modifier.pixelCardSize(size).semantics { contentDescription = spoken }) {
        drawPixelCard(card, faceUp, palette, held, highlighted, glowPulse.value, dimmed, mirrored = false)
    }
}

/** What TalkBack says for a card: "Ace of hearts", "face-down card", "Queen of spades, held". */
fun cardDescription(card: Card?, faceUp: Boolean, held: Boolean = false): String {
    val base = if (faceUp && card != null) "${card.rank.label} of ${card.suit.label}" else "face-down card"
    return if (held) "$base, held" else base
}

@Composable
private fun glowPulse(highlighted: Boolean): State<Float> {
    if (!highlighted || !PixelTheme.animated) return remember { mutableFloatStateOf(1f) }
    val t = rememberInfiniteTransition(label = "glow")
    return t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "glow")
}

/**
 * Sizes a card to exactly a whole-number multiple of the 45x63 art: the
 * largest scale whose width fits in [maxWidth]. Layouts stay tight and every
 * art pixel is a square block of device pixels.
 */
fun Modifier.pixelCardSize(maxWidth: Dp): Modifier = layout { measurable, _ ->
    val scale = integerScale(maxWidth.toPx(), CardArt.W)
    val w = scale * CardArt.W
    val h = scale * CardArt.H
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(w, h) { placeable.place(0, 0) }
}

/** The scale [PixelCard] uses for a draw area [widthPx] wide. */
fun cardScaleFor(widthPx: Float): Int = integerScale(widthPx, CardArt.W)

/**
 * Draws a card into this scope (centred, whole-number scale). Shared by
 * [PixelCard] and [AnimatedPixelCard]; useful for custom table layouts.
 */
fun DrawScope.drawPixelCard(
    card: Card?,
    faceUp: Boolean,
    palette: PixelPalette,
    held: Boolean = false,
    highlighted: Boolean = false,
    glowAlpha: Float = 1f,
    dimmed: Boolean = false,
    mirrored: Boolean = false,
    shadowAlpha: Float = 0.55f,
) {
    val scale = cardScaleFor(size.width)
    val w = CardArt.W * scale
    val h = CardArt.H * scale
    val x = ((size.width - w) / 2f).toInt()
    val y = ((size.height - h) / 2f).toInt()
    val art = if (faceUp && card != null) CardArt.face(card, palette) else CardArt.back(palette)
    val filter = if (dimmed) PixelCardDefaults.DimFilter else null

    drawPixelArt(CardArt.silhouette(palette.ink), IntOffset(x + scale, y + scale * 2), scale, alpha = if (dimmed) shadowAlpha * 0.6f else shadowAlpha)
    if (highlighted) {
        drawPixelArt(CardArt.glow(palette.gold, 3), IntOffset(x - 3 * scale, y - 3 * scale), scale, alpha = glowAlpha * 0.45f)
        drawPixelArt(CardArt.glow(palette.yellow, 1), IntOffset(x - scale, y - scale), scale, alpha = glowAlpha)
    }
    if (mirrored) {
        scale(-1f, 1f) { drawPixelArt(art, IntOffset(x, y), scale, colorFilter = filter) }
    } else {
        drawPixelArt(art, IntOffset(x, y), scale, colorFilter = filter)
    }
    if (held) {
        val tag = CardArt.heldTag(palette)
        drawPixelArt(tag, IntOffset(x + (w - tag.width * scale) / 2, y - (tag.height - 2) * scale), scale)
    }
}

/**
 * [PixelCard] with arcade juice:
 * - **flip**: changing [faceUp] turns the card over in 3D (rotationY), with a
 *   little scale pop at the halfway point;
 * - **deal**: on first appearance it slides in from [dealFrom] (an offset
 *   from its resting place, e.g. the deck) after [dealDelayMillis], spinning
 *   flat; with [flipOnDeal] it lands face down and then flips up;
 * - **idle sway**: a gentle tilt and bob ([sway]; [swayPhase] desyncs a hand);
 * - **held** cards lift up; taps squash and bounce.
 * In [PixelRenderMode.Static] everything rests at its final state.
 */
@Composable
fun AnimatedPixelCard(
    card: Card?,
    faceUp: Boolean,
    modifier: Modifier = Modifier,
    held: Boolean = false,
    highlighted: Boolean = false,
    dimmed: Boolean = false,
    size: Dp = PixelCardDefaults.Width,
    sway: Boolean = true,
    swayPhase: Float = 0f,
    dealFrom: DpOffset? = null,
    dealDelayMillis: Int = 0,
    flipOnDeal: Boolean = false,
    onDealt: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalPixelPalette.current
    val animated = PixelTheme.animated
    val sound = LocalSoundPlayer.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Deal: 0 = at dealFrom, 1 = resting.
    val deal = remember { Animatable(if (dealFrom != null && animated) 0f else 1f) }
    val landed = remember { mutableStateOf(dealFrom == null || !animated || !flipOnDeal) }
    LaunchedEffect(Unit) {
        if (deal.value < 1f) {
            delay(dealDelayMillis.toLong())
            sound.play(Sfx.Deal)
            deal.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            landed.value = true
            onDealt()
        }
    }
    val showFaceUp = faceUp && landed.value

    val flip by animateFloatAsState(
        targetValue = if (showFaceUp) 0f else 180f,
        animationSpec = if (animated) tween(360, easing = FastOutSlowInEasing) else snapSpec(),
        label = "flip",
    )
    val lift by animateFloatAsState(
        targetValue = if (held) 1f else 0f,
        animationSpec = if (animated) spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium) else snapSpec(),
        label = "lift",
    )
    val squash = remember { Animatable(1f) }

    val swayOn = sway && animated
    val time = if (swayOn) {
        rememberInfiniteTransition(label = "sway").animateFloat(
            0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "t",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val glow = glowPulse(highlighted)

    val widthDp = size
    val heightDp = PixelCardDefaults.heightFor(size)
    val interaction = remember { MutableInteractionSource() }
    val click = if (onClick != null) {
        Modifier.clickable(interaction, indication = null) {
            sound.play(Sfx.Flip)
            scope.launch {
                squash.snapTo(0.9f)
                squash.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
            }
            onClick()
        }
    } else Modifier

    val spoken = cardDescription(card, faceUp, held)
    Box(
        modifier
            .pixelCardSize(widthDp)
            .then(click)
            .semantics { contentDescription = spoken }
            .graphicsLayer {
                val d = deal.value
                val from = dealFrom ?: DpOffset.Zero
                val t = time.value + swayPhase
                val mid = sin(PI * flip / 180.0).toFloat()
                translationX = with(density) { from.x.toPx() } * (1 - d)
                translationY = with(density) { from.y.toPx() } * (1 - d) -
                    lift * widthDp.toPx() * 0.08f +
                    (if (swayOn) sin(t * 1.0f) * 2.dp.toPx() else 0f)
                rotationZ = (1 - d) * -24f + (if (swayOn) sin(t + 1.3f) * 1.6f else 0f)
                rotationX = if (swayOn) sin(t * 2f) * 3f else 0f
                rotationY = flip
                val pop = 1f + 0.10f * abs(mid)
                scaleX = squash.value * pop
                scaleY = (2f - squash.value) * pop
                cameraDistance = 16f
                transformOrigin = TransformOrigin.Center
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val showingBack = flip > 90f
            drawPixelCard(
                card = card,
                faceUp = !showingBack,
                palette = palette,
                held = held && !showingBack,
                highlighted = highlighted,
                glowAlpha = glow.value,
                dimmed = dimmed,
                mirrored = showingBack,
                shadowAlpha = 0.55f - lift * 0.15f,
            )
        }
    }
}

private fun <T> snapSpec() = androidx.compose.animation.core.snap<T>()
