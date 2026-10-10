package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bradflaugher.pokersi.ui.pixel.Mood
import com.bradflaugher.pokersi.ui.pixel.PixelAvatar
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.SpeechBubble

/**
 * PLACEHOLDER: the Models screen slot. The navigation calls this composable
 * for the MODELS menu item; the `llm/` work replaces its body with the real
 * model catalog, downloads and GGUF folder picker. Keep the signature.
 */
@Composable
fun ModelsScreen(brainLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    ArcadeScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar("MODELS", onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VGap(12.dp)
                PixelAvatar(90210L, Mood.Thinking, size = 96.dp)
                SpeechBubble("Models are coming soon. Until then, House Bot deals with you personally.", Modifier.readable(420.dp))
                PixelPanel(Modifier.readable(), title = "NOW PLAYING", paddingUnits = 2) {
                    PixelText(brainLabel.uppercase(), Modifier.fillMaxWidth(), style = PixelType.Small, color = p.ledGreen, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
