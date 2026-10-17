package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class PanelStyle { Cabinet, Felt, Dark, Paper }

private class PanelColors(val border: Color, val borderLight: Color, val borderDark: Color, val fill: Color, val fillLight: Color, val header: Color)

@Composable
private fun panelColors(style: PanelStyle): PanelColors {
    val p = LocalPixelPalette.current
    return when (style) {
        PanelStyle.Cabinet -> PanelColors(p.gold, p.yellow, p.goldDark, p.cabinetDeep, p.cabinet, p.signRed)
        PanelStyle.Felt -> PanelColors(p.gold, p.yellow, p.goldDark, p.feltDark, p.felt, p.purple)
        PanelStyle.Dark -> PanelColors(p.gray, Color(0xFFDEDBEE), p.grayDark, p.cabinetBlack, p.inkSoft, p.hotPinkDark)
        PanelStyle.Paper -> PanelColors(p.ink, p.inkSoft, p.ink, p.cardCream, p.cardHighlight, p.signRed)
    }
}

/**
 * A bevelled cabinet panel (the pay table look): ink outline, a gold
 * bevelled border and a deep fill. [title] adds a header strip.
 */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    style: PanelStyle = PanelStyle.Cabinet,
    paddingUnits: Int = 3,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPixelPalette.current
    val unit = LocalPixelUnit.current
    val c = panelColors(style)
    val density = LocalDensity.current
    val border = unit * 4
    Column(
        modifier
            .drawBehind {
                val w = size.width.toInt(); val h = size.height.toInt()
                // Shadow, outline, bevelled border, inner edge, fill.
                notchedRect(unit, unit, w - unit, h - unit, unit, 2, p.ink.copy(alpha = 0.45f))
                bevelBox(0, 0, w - unit, h - unit, unit, c.border, c.borderLight, c.borderDark, p.ink, cut = 2)
                pxRect(border - unit, border - unit, w - 2 * border + unit, h - 2 * border + unit, p.ink)
                pxRect(border, border, w - 2 * border - unit, h - 2 * border - unit, c.fill)
                // A lighter inner top band, like glass catching light.
                pxRect(border, border, w - 2 * border - unit, unit, c.fillLight)
            }
            .padding(with(density) { (border + unit * paddingUnits).toDp() }),
    ) {
        if (title != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = with(density) { (unit * 3).toDp() })
                    .drawBehind {
                        val w = size.width.toInt(); val h = size.height.toInt()
                        notchedRect(0, 0, w, h, unit, 1, p.ink)
                        pxRect(unit, unit, w - 2 * unit, h - 2 * unit, c.header)
                        pxRect(unit, unit, w - 2 * unit, unit, lerp(c.header, Color.White, 0.3f))
                    }
                    .padding(with(density) { (unit * 3).toDp() }),
                contentAlignment = Alignment.Center,
            ) {
                PixelText(title, style = PixelType.Heading, color = p.yellow, textAlign = TextAlign.Center)
            }
        }
        content()
    }
}

/** One pay table row: the hand and what it pays. */
data class PayRow(val name: String, val pays: String)

/**
 * A video poker pay table: yellow rows on cabinet blue, with the
 * [highlight]ed row (the hand just made, or the one picked) flashing red.
 * With [onRowClick] the rows can be picked: each is a selectable row (at
 * least 48dp, a comfortable touch target) with the cabinet's blip and a
 * haptic tick, and [detail] is shown right under the highlighted row.
 */
@Composable
fun PayTable(
    rows: List<PayRow>,
    modifier: Modifier = Modifier,
    title: String? = null,
    highlight: Int? = null,
    onRowClick: ((Int) -> Unit)? = null,
    detail: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val player = LocalSoundPlayer.current
    val p = LocalPixelPalette.current
    val unit = LocalPixelUnit.current
    val density = LocalDensity.current
    val flash = if (highlight != null && PixelTheme.animated) {
        rememberInfiniteTransition(label = "pay").animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "f")
    } else remember { mutableFloatStateOf(0.2f) }
    PixelPanel(modifier, title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(with(density) { unit.toDp() })) {
            rows.forEachIndexed { i, row ->
                val hot = i == highlight
                val on = hot && flash.value < 0.6f
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (onRowClick == null) Modifier
                            else Modifier.heightIn(min = 48.dp).selectable(selected = hot, role = Role.RadioButton) {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                player.play(Sfx.Blip)
                                onRowClick(i)
                            },
                        )
                        .drawBehind { if (hot) notchedRect(0, 0, size.width.toInt(), size.height.toInt(), unit, 1, if (on) p.signRed else p.redDark) }
                        .padding(horizontal = with(density) { (unit * 2).toDp() }, vertical = with(density) { unit.toDp() }),
                    verticalAlignment = Alignment.CenterVertically,
                    // Never touching, even with LARGER TEXT: a long name wraps instead.
                    horizontalArrangement = Arrangement.spacedBy(with(density) { (unit * 4).toDp() }),
                ) {
                    val color = if (hot) p.white else p.yellow
                    PixelText(row.name, Modifier.weight(1f), style = PixelType.LabelBold, color = color)
                    PixelText(row.pays, style = PixelType.LabelBold, color = color, textAlign = TextAlign.End)
                }
                if (hot && detail != null) detail()
            }
        }
    }
}
