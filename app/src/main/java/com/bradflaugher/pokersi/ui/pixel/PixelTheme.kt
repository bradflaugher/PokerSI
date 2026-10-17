package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
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
    // Light enough for secondary text on felt and cabinet panels (see ContrastTest).
    val gray: Color = Color(0xFFBCB9D4),
    val grayDark: Color = Color(0xFF4A4763),
    // LEDs.
    val ledRed: Color = Color(0xFFFF2B2B),
    val ledAmber: Color = Color(0xFFFFB21E),
    val ledGreen: Color = Color(0xFF3DFF6E),
) {
    companion object {
        val Default = PixelPalette()
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
 */
@Composable
fun PixelTheme(
    palette: PixelPalette = PixelPalette.Default,
    renderMode: PixelRenderMode? = null,
    crtEnabled: Boolean = true,
    sound: SoundPlayer = LocalSoundPlayer.current,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val mode = renderMode
        ?: if (LocalInspectionMode.current) PixelRenderMode.Static else LocalPixelRenderMode.current
    CompositionLocalProvider(
        LocalPixelPalette provides palette,
        LocalPixelRenderMode provides mode,
        LocalPixelUnit provides pixelUnitFor(density),
        LocalCrtEnabled provides crtEnabled,
        LocalSoundPlayer provides sound,
        content = content,
    )
}
