package com.bradflaugher.pokersi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.ui.pixel.Mood
import com.bradflaugher.pokersi.ui.pixel.PixelAvatar
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PixelPalette
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType

private class Tip(val key: String, val color: (PixelPalette) -> Color, val text: String)

private val TIPS = listOf(
    Tip("AI", { it.teal }, "Your opponents are AI characters running right on this phone. Offline."),
    Tip("MODELS", { it.cabinetLight }, "Download a brain in MODELS. Until then, the House Bot plays every seat."),
    Tip("QUICK", { it.suitRed }, "QUICK GAME is one table, any of six games. POKER TOUR climbs ten tables of rising stakes."),
    Tip("HOLD", { it.gold }, "Draw games: tap cards to HOLD the keepers, then DRAW the rest."),
    Tip("PAUSE", { it.gray }, "The pause button (or Back) pauses a hand: change the speed or quit."),
)

/**
 * The first-run tips over the title screen: five lines on what nobody would
 * guess. Never blocks: one tap anywhere (the panel included) or Back
 * dismisses, and SETTINGS > SHOW TIPS brings them back.
 */
@Composable
fun TipsOverlay(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    BackHandler(onBack = onDismiss)
    // The scrim is a sibling behind the panel, not its parent: a clickable parent would
    // merge the whole panel into one "dismiss" node for TalkBack.
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xC8000000))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Dismiss tips", onClick = onDismiss),
        )
        TabletScaled(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PixelPanel(
                    Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .padding(16.dp)
                        // A tap on the panel dismisses too (GOT IT handles its own).
                        .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
                        .semantics {
                            paneTitle = "Tips"
                            isTraversalGroup = true
                        },
                    title = "HOW IT WORKS",
                    paddingUnits = 3,
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PixelAvatar(4242L, Mood.Happy, size = 48.dp)
                            PixelText(
                                "WELCOME TO THE TABLE!",
                                Modifier.weight(1f).semantics { heading() },
                                style = PixelType.Small,
                                color = p.yellow,
                            )
                        }
                        TIPS.forEach { tip ->
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.width(66.dp).padding(top = 2.dp), contentAlignment = Alignment.CenterStart) {
                                    PixelTag(
                                        tip.key,
                                        tip.color(p),
                                        textColor = if (tip.key == "HOLD" || tip.key == "PAUSE") p.ink else p.white,
                                        style = PixelType.Small.copy(fontSize = 9.sp),
                                    )
                                }
                                BodyText(tip.text, Modifier.weight(1f), color = p.cardCream, small = true)
                            }
                        }
                        PixelButton("GOT IT", onDismiss, Modifier.fillMaxWidth().padding(top = 4.dp), variant = PixelButtonVariant.Call)
                        PixelText(
                            "OR TAP ANYWHERE",
                            Modifier.fillMaxWidth(),
                            style = PixelType.Label.copy(fontSize = 10.sp),
                            color = p.gray,
                            textAlign = TextAlign.Center,
                            shadowUnits = 0,
                        )
                    }
                }
            }
        }
    }
}
