package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Cabinet button colors. */
enum class PixelButtonVariant { Deal, Bet, Hold, Call, Pink, Gray }

private class ButtonColors(val face: Color, val light: Color, val dark: Color, val side: Color, val text: Color, val shadow: Color)

@Composable
private fun colorsFor(variant: PixelButtonVariant, enabled: Boolean): ButtonColors {
    val p = LocalPixelPalette.current
    if (!enabled) return ButtonColors(p.grayDark, lerp(p.grayDark, p.gray, 0.5f), lerp(p.grayDark, p.ink, 0.4f), p.inkSoft, p.gray, p.ink)
    return when (variant) {
        PixelButtonVariant.Deal -> ButtonColors(p.suitRed, Color(0xFFFF7D8A), Color(0xFFB4152C), p.suitRedDark, p.yellow, p.ink)
        PixelButtonVariant.Bet -> ButtonColors(p.gold, p.yellow, p.goldDark, Color(0xFF7A4A06), p.ink, Color(0x66FFFFFF))
        PixelButtonVariant.Hold -> ButtonColors(p.cabinetLight, p.cabinetGlow, p.cabinet, p.cabinetDeep, p.white, p.ink)
        PixelButtonVariant.Call -> ButtonColors(p.green, Color(0xFF8CF5A6), p.greenDark, Color(0xFF0B4A20), p.white, p.ink)
        PixelButtonVariant.Pink -> ButtonColors(p.hotPink, Color(0xFFFF9CCB), p.hotPinkDark, Color(0xFF5E0A37), p.white, p.ink)
        PixelButtonVariant.Gray -> ButtonColors(p.gray, Color(0xFFC9C6DD), p.grayDark, p.inkSoft, p.ink, Color(0x55FFFFFF))
    }
}

/**
 * A big rectangular video poker cabinet button: ink outline, bevelled face,
 * and a raised side that disappears when pressed (the face drops by two
 * chrome pixels). Plays [sound] and a haptic tick on click. With [icon]
 * the face shows that glyph instead of [text]; give the button a content
 * description then.
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PixelButtonVariant = PixelButtonVariant.Deal,
    enabled: Boolean = true,
    textStyle: TextStyle = PixelType.Heading,
    sound: Sfx? = Sfx.Blip,
    haptics: Boolean = true,
    subtitle: String? = null,
    icon: PixelGlyph? = null,
) {
    val unit = LocalPixelUnit.current
    val colors = colorsFor(variant, enabled)
    val p = LocalPixelPalette.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val player = LocalSoundPlayer.current
    val depth = unit * 2
    val down = pressed && enabled
    val density = LocalDensity.current

    Box(
        modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 52.dp)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                sound?.let(player::play)
                onClick()
            }
            .drawBehind {
                val w = size.width.toInt()
                val h = size.height.toInt()
                val u = unit
                // Drop shadow on the cabinet.
                notchedRect(u, u, w - u, h - u, u, 1, p.ink.copy(alpha = 0.45f))
                // The raised side (visible below the face when not pressed).
                notchedRect(0, depth, w - u, h - depth - u, u, 1, p.ink)
                notchedRect(u, depth + u, w - 3 * u, h - depth - 3 * u, u, 0, colors.side)
                val top = if (down) depth else 0
                bevelBox(0, top, w - u, h - depth - u, u, colors.face, colors.light, colors.dark, p.ink, cut = 1)
                // A glossy glint on the top-left.
                pxRect(2 * u, top + 2 * u, 2 * u, u, lerp(colors.light, Color.White, 0.6f))
            }
            .padding(
                start = with(density) { (unit * 4).toDp() },
                end = with(density) { (unit * 5).toDp() },
                top = with(density) { (unit * 3).toDp() },
                bottom = with(density) { (unit * 4 + depth).toDp() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.offset { IntOffset(0, if (down) depth else 0) }, contentAlignment = Alignment.Center) {
            if (icon != null) {
                PixelGlyphBox(icon, colors.text, if (enabled) colors.shadow else null)
            } else if (subtitle == null) {
                PixelText(text, style = textStyle, color = colors.text, shadowColor = colors.shadow, shadowUnits = if (enabled) 1 else 0, textAlign = TextAlign.Center)
            } else {
                androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelText(text, style = textStyle, color = colors.text, shadowColor = colors.shadow, shadowUnits = if (enabled) 1 else 0, textAlign = TextAlign.Center)
                    PixelText(subtitle, style = PixelType.BodySmall, color = colors.text.copy(alpha = 0.85f), shadowUnits = 0, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
