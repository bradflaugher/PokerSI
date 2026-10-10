package com.bradflaugher.pokersi.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.bradflaugher.pokersi.ui.pixel.pixelUnitFor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.ui.pixel.CrtOverlay
import com.bradflaugher.pokersi.ui.pixel.LocalPixelPalette
import com.bradflaugher.pokersi.ui.pixel.LocalPixelUnit
import com.bradflaugher.pokersi.ui.pixel.LocalSoundPlayer
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelPalette
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.Sfx
import com.bradflaugher.pokersi.ui.pixel.FeltBackground
import com.bradflaugher.pokersi.ui.pixel.FeltColors
import com.bradflaugher.pokersi.ui.pixel.notchedRect
import com.bradflaugher.pokersi.ui.pixel.pxRect
import androidx.compose.ui.graphics.drawscope.DrawScope

/** Whether cards sway idly (a setting). */
val LocalCardSway = compositionLocalOf { true }

/**
 * Every screen's frame: green felt under CRT glass, content kept
 * clear of system bars (edge to edge).
 */
@Composable
fun ArcadeScreen(
    modifier: Modifier = Modifier,
    felt: FeltColors = FeltColors.Lobby,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = PixelTheme.palette
    CrtOverlay(modifier.fillMaxSize()) {
        FeltBackground(Modifier.fillMaxSize(), colors = felt) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                // Tablets: the phone layout, scaled up, reads far better than a sea of space.
                val scale = minOf(maxWidth / 411.dp, maxHeight / 731.dp, maxHeight / 411.dp).coerceIn(1f, 1.6f)
                val density = LocalDensity.current
                if (scale > 1.05f) {
                    val scaled = Density(density.density * scale, density.fontScale)
                    CompositionLocalProvider(LocalDensity provides scaled, LocalPixelUnit provides pixelUnitFor(scaled.density)) {
                        Box(Modifier.fillMaxSize(), content = content)
                    }
                } else {
                    Box(Modifier.fillMaxSize(), content = content)
                }
            }
        }
    }
}

/** A screen title strip with an optional BACK button. */
@Composable
fun ArcadeTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val p = PixelTheme.palette
    Row(
        modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            PixelButton("<", onBack, Modifier.size(48.dp, 46.dp).semantics { contentDescription = "Back" }, variant = PixelButtonVariant.Gray, textStyle = PixelType.Heading)
        }
        PixelText(title, Modifier.weight(1f), style = PixelType.Title.copy(fontSize = 18.sp), color = p.yellow, shadowUnits = 2, maxLines = 2)
        trailing()
    }
}

/** A small, compact cabinet button (44dp tall). */
@Composable
fun SmallButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PixelButtonVariant = PixelButtonVariant.Gray,
    enabled: Boolean = true,
    sound: Sfx? = Sfx.Blip,
) {
    PixelButton(text, onClick, modifier.height(44.dp), variant = variant, enabled = enabled, textStyle = PixelType.Small, sound = sound)
}

/** A pixel tag: colored notched pill with small arcade caps. */
@Composable
fun PixelTag(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = LocalPixelPalette.current.white, style: TextStyle = PixelType.Small.copy(fontSize = 8.sp)) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    Box(
        modifier
            .drawBehind {
                val w = size.width.toInt(); val h = size.height.toInt()
                pxBox(0, 0, w, h, unit, p.ink, color)
            }
            .padding(horizontal = 5.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        PixelText(text, style = style, color = textColor, shadowUnits = 0, maxLines = 1)
    }
}

/** Blinks [content] on and off like an arcade attract screen (always on when static). */
@Composable
fun Blinking(periodMillis: Int = 1100, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val alpha = if (PixelTheme.animated) {
        val t by rememberInfiniteTransition(label = "blink").animateFloat(0f, 1f, infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart), label = "b")
        if (t < 0.62f) 1f else 0f
    } else 1f
    Box(modifier.alpha(alpha)) { content() }
}

/** A pulsing 0..1 value (for "your turn" glows). Constant when static. */
@Composable
fun pulse(periodMillis: Int = 700): Float {
    if (!PixelTheme.animated) return remember { mutableFloatStateOf(1f) }.floatValue
    val t by rememberInfiniteTransition(label = "pulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    return t
}

/** A chunky ON/OFF style choice row used on the settings and picker screens. */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            PixelButton(
                label(o),
                { onSelect(o) },
                Modifier.weight(1f).height(44.dp),
                variant = if (o == selected) PixelButtonVariant.Bet else PixelButtonVariant.Gray,
                textStyle = PixelType.Small,
            )
        }
    }
}

/** A horizontal progress bar in pixel chrome (chip target, confidence). */
@Composable
fun PixelMeter(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    Canvas(modifier.height(height)) {
        val w = size.width.toInt(); val h = size.height.toInt()
        pxBox(0, 0, w, h, unit, p.ink, Color(0xFF0A0306))
        val inner = w - 2 * unit
        val fill = (inner * fraction.coerceIn(0f, 1f)).toInt()
        if (fill > 0) {
            pxRect(unit, unit, fill, h - 2 * unit, color)
            pxRect(unit, unit, fill, unit, lerp(color, Color.White, 0.45f))
        }
    }
}

/** Constrains content to a comfortable reading width on tablets. */
fun Modifier.readable(max: Dp = 560.dp): Modifier = this.widthIn(max = max).fillMaxWidth()

/** A text block in the terminal font. */
@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = LocalPixelPalette.current.white, align: TextAlign? = null, small: Boolean = false) {
    PixelText(text, modifier, style = if (small) PixelType.BodySmall else PixelType.Body, color = color, textAlign = align)
}

/** Click without ripple, with a blip. */
@Composable
fun Modifier.arcadeClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val sound = LocalSoundPlayer.current
    return this.clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button) {
        sound.play(Sfx.Blip)
        onClick()
    }
}

/** A labelled column section heading. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    PixelText(text, modifier, style = PixelType.Heading, color = PixelTheme.palette.yellow, shadowUnits = 1)
}

/** Spacer helpers. */
@Composable
fun VGap(h: Dp) = Box(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Box(Modifier.width(w))

/** A column that fills and centres, for menus. */
@Composable
fun MenuColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

/** An ink-outlined, notched box filled with [fill]. */
internal fun DrawScope.pxBox(x: Int, y: Int, w: Int, h: Int, u: Int, ink: Color, fill: Color) {
    notchedRect(x, y, w, h, u, 1, ink)
    pxRect(x + u, y + u, w - 2 * u, h - 2 * u, fill)
}
