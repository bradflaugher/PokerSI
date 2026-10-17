package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The PokerSI palette: a 90s video poker cabinet (royal blue, black,
 * yellow/red/white text) crossed with a modern pixel card game (cream cards, hot accents).
 * Every color in the design system comes from here.
 */
@Immutable
data class PixelPalette(
    // Ink: outlines and text shadows. A purple-black reads warmer than pure black.
    val ink: Color = Color(0xFF130F21),
    val inkSoft: Color = Color(0xFF2C2445),
    // Cabinet: deep royal blue to black.
    val cabinetBlack: Color = Color(0xFF05071C),
    val cabinetDeep: Color = Color(0xFF0A1250),
    val cabinet: Color = Color(0xFF1530A8),
    val cabinetLight: Color = Color(0xFF3A63F0),
    val cabinetGlow: Color = Color(0xFF8FB0FF),
    // Felt.
    val feltDark: Color = Color(0xFF07403A),
    val felt: Color = Color(0xFF0E6E5E),
    val feltLight: Color = Color(0xFF22A88B),
    // Cards.
    val cardCream: Color = Color(0xFFFFF3D6),
    val cardShade: Color = Color(0xFFE6D2A8),
    val cardHighlight: Color = Color(0xFFFFFFF4),
    val suitRed: Color = Color(0xFFE8263E),
    val suitRedDark: Color = Color(0xFF9E1230),
    val suitBlack: Color = Color(0xFF1D2150),
    val suitBlackLight: Color = Color(0xFF3B4290),
    // Text and highlights.
    val gold: Color = Color(0xFFFFC22E),
    val goldDark: Color = Color(0xFFB9780C),
    val yellow: Color = Color(0xFFFFF04A),
    val white: Color = Color(0xFFFFFFFF),
    val red: Color = Color(0xFFFF3B30),
    val redDark: Color = Color(0xFFA3101C),
    // Accents.
    val hotPink: Color = Color(0xFFFF3D9A),
    val hotPinkDark: Color = Color(0xFFA81563),
    val purple: Color = Color(0xFF7A2CF2),
    val purpleDark: Color = Color(0xFF3B137E),
    val teal: Color = Color(0xFF1FD6C4),
    val green: Color = Color(0xFF2FD15A),
    val greenDark: Color = Color(0xFF137A33),
    val gray: Color = Color(0xFF8D8AA6),
    val grayDark: Color = Color(0xFF4A4763),
    // LEDs.
    val ledRed: Color = Color(0xFFFF2B2B),
    val ledAmber: Color = Color(0xFFFFB21E),
    val ledGreen: Color = Color(0xFF3DFF6E),
    /**
     * SETTINGS > HIGH CONTRAST: decoration turns down too (fainter scanlines
     * and vignette in [CrtOverlay], a darker, flatter felt in [FeltBackground]).
     */
    val highContrast: Boolean = false,
) {
    companion object {
        val Default = PixelPalette()

        /**
         * The same cabinet, stronger: secondary grays brighten from mid to
         * light (on felt and in panel borders), outlines go pure black, and
         * the cream and glow tones lift a step. Accents keep their hue.
         */
        val HighContrast = PixelPalette(
            ink = Color(0xFF000000),
            inkSoft = Color(0xFF1A1630),
            cabinetGlow = Color(0xFFB8CCFF),
            cardCream = Color(0xFFFFF9EA),
            gray = Color(0xFFC8C5DE),
            grayDark = Color(0xFF6A6688),
            highContrast = true,
        )
    }
}

/**
 * How the design system animates. [Static] freezes idle animations
 * (sway, marquee chase, blinking): screenshot tests and `@Preview`s use it so every render is
 * deterministic.
 */
enum class PixelRenderMode { Animated, Static }

val LocalPixelPalette = staticCompositionLocalOf { PixelPalette.Default }

/** Defaults to [PixelRenderMode.Animated]; [PixelTheme] switches to Static in inspection mode. */
val LocalPixelRenderMode = compositionLocalOf { PixelRenderMode.Animated }

/** Whether the CRT overlay (scanlines + vignette) is drawn. */
val LocalCrtEnabled = compositionLocalOf { true }

/**
 * Size of one "chrome" pixel in device pixels (borders, bevels, bubbles,
 * marquee bulbs). Always a whole number so UI pixel art stays crisp.
 */
val LocalPixelUnit = compositionLocalOf { 4 }

/** How much SETTINGS > LARGER TEXT scales text, on top of the system font size. */
const val LARGER_TEXT_SCALE = 1.2f

/** Whole device pixels per chrome pixel for a display density (about 1.5dp, at least 1px). */
fun pixelUnitFor(density: Float): Int = max(1, (density * 1.5f).roundToInt())

/** The design system's palette, render mode and pixel unit. */
object PixelTheme {
    val palette: PixelPalette @Composable get() = LocalPixelPalette.current
    val renderMode: PixelRenderMode @Composable get() = LocalPixelRenderMode.current
    val unit: Int @Composable get() = LocalPixelUnit.current
    val animated: Boolean @Composable get() = LocalPixelRenderMode.current == PixelRenderMode.Animated
}

/**
 * Root of every PokerSI screen: provides palette, pixel unit, render mode and
 * sound. Inside `@Preview`/inspection mode the render mode is forced to Static.
 * [textScale] multiplies the system font scale, so every `sp` in the app
 * (all of [PixelType]) grows with it while layout in `dp` stays put.
 */
@Composable
fun PixelTheme(
    palette: PixelPalette = PixelPalette.Default,
    renderMode: PixelRenderMode? = null,
    crtEnabled: Boolean = true,
    sound: SoundPlayer = LocalSoundPlayer.current,
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val outer = LocalDensity.current
    val density = if (textScale == 1f) outer else Density(outer.density, outer.fontScale * textScale)
    val mode = renderMode
        ?: if (LocalInspectionMode.current) PixelRenderMode.Static else LocalPixelRenderMode.current
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalPixelPalette provides palette,
        LocalPixelRenderMode provides mode,
        LocalPixelUnit provides pixelUnitFor(density.density),
        LocalCrtEnabled provides crtEnabled,
        LocalSoundPlayer provides sound,
        content = content,
    )
}
