package com.bradflaugher.pokersi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bradflaugher.pokersi.ui.pixel.LocalSoundPlayer
import com.bradflaugher.pokersi.ui.pixel.Mood
import com.bradflaugher.pokersi.ui.pixel.PixelAvatar
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.Sfx

/**
 * What sits under an [open] overlay (a [ConfirmDialog], the pause menu, the
 * tour) is hidden from TalkBack, so swiping can't reach a control behind it.
 */
fun Modifier.modalBehind(open: Boolean): Modifier = if (open) clearAndSetSemantics { } else this

/**
 * The cabinet's "are you sure?", for the one tap that would lose something:
 * leaving the app, a quick table's chips, a saved tour, the records. A
 * panel headed HOLD IT! over a dark scrim, a worried face, the [question]
 * and what is at stake ([message]).
 *
 * The safe choice is the big green [stayLabel] button on top (it takes
 * focus, for keyboards); it, Back and a tap on the scrim all call
 * [onStay]. Only the red [confirmLabel] button below calls [onConfirm].
 */
@Composable
fun ConfirmDialog(
    question: String,
    message: String,
    confirmLabel: String,
    stayLabel: String,
    onConfirm: () -> Unit,
    onStay: () -> Unit,
    modifier: Modifier = Modifier,
    mood: Mood = Mood.Shocked,
) {
    val p = PixelTheme.palette
    val sound = LocalSoundPlayer.current
    val stay = remember { FocusRequester() }
    // Pops up with a flip, a quick step from a little small to full size.
    val animated = PixelTheme.animated
    val pop = remember { Animatable(if (animated) 0.88f else 1f) }
    LaunchedEffect(Unit) {
        sound.play(Sfx.Flip)
        stay.requestFocus()
        if (animated) pop.animateTo(1f, tween(120))
    }
    BackHandler(onBack = onStay)
    // The scrim is a sibling behind the panel, not its parent, so TalkBack reads the panel's parts.
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xD0000000))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = stayLabel.lowercase(), onClick = onStay),
        )
        TabletScaled(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PixelPanel(
                    Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .padding(20.dp)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                        // A tap on the panel's face is not a tap on the scrim.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .semantics {
                            paneTitle = question
                            isTraversalGroup = true
                        },
                    title = "HOLD IT!",
                    paddingUnits = 3,
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PixelAvatar(4242L, mood, size = 48.dp)
                            PixelText(question, Modifier.weight(1f).semantics { heading() }, style = PixelType.Small, color = p.yellow)
                        }
                        BodyText(message, color = p.cardCream, small = true)
                        PixelButton(stayLabel, onStay, Modifier.fillMaxWidth().focusRequester(stay), variant = PixelButtonVariant.Call)
                        PixelButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Deal, textStyle = PixelType.Small)
                    }
                }
            }
        }
    }
}
