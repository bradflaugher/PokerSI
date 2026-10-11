package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.ui.pixel.LocalPixelUnit
import com.bradflaugher.pokersi.ui.pixel.MarqueeFrame
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelCardFan
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.Sfx
import com.bradflaugher.pokersi.ui.pixel.pxRect

/** What the title screen can open. */
class TitleCallbacks(
    val onQuickGame: () -> Unit = {},
    val onRunMode: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onDaily: () -> Unit = {},
    val onModels: () -> Unit = {},
    val onHowTo: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onStats: () -> Unit = {},
)

/**
 * The attract screen: the POKER S.I. marquee, a royal flush fanned out,
 * the cabinet's attract line cycling through [attract] (credits, tour, daily,
 * records: see [com.bradflaugher.pokersi.game.AttractLines]), the menu and a
 * status strip saying who plays the opponents.
 */
@Composable
fun TitleScreen(
    brainLabel: String,
    hasSavedRun: Boolean,
    callbacks: TitleCallbacks,
    modifier: Modifier = Modifier,
    dailyLabel: String = "DAILY TOUR",
    attract: List<String> = listOf("INSERT COIN  -  PRESS START"),
) {
    val p = PixelTheme.palette
    ArcadeScreen(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                if (wide) {
                    Row(Modifier.weight(1f).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            TitleMarquee()
                            CardFan()
                            AttractLine(attract)
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TitleMenu(hasSavedRun, callbacks, dailyLabel)
                        }
                    }
                } else {
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val tall = maxHeight
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).heightIn(min = tall).padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            TitleMarquee(Modifier.readable(520.dp))
                            CardFan(if (tall > 640.dp) 72.dp else 56.dp)
                            AttractLine(attract)
                            Column(Modifier.readable(460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TitleMenu(hasSavedRun, callbacks, dailyLabel)
                            }
                        }
                    }
                }
                StatusLine(brainLabel)
            }
        }
    }
}

@Composable
private fun TitleMarquee(modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    MarqueeFrame(modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelText("POKER", style = PixelType.Banner, color = p.yellow, shadowUnits = 2, fontSize = 40.sp)
            PixelText("S.I.", style = PixelType.Banner, color = p.hotPink, shadowUnits = 2, fontSize = 40.sp)
            PixelText("PLAY POKER WITH A SUPER INTELLIGENCE", style = PixelType.Small.copy(fontSize = 8.sp), color = p.white, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CardFan(size: androidx.compose.ui.unit.Dp = 54.dp) {
    val cards = remember { Card.parseList("Ts Js Qs Ks As") }
    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
        PixelCardFan(cards, cardWidth = size, contentDescription = "A royal flush in spades")
    }
}

/**
 * The attract line: each message types itself out like a cabinet's LED
 * scroller, holds, blinks twice and gives way to the next. Static renders
 * show the first message, whole and still.
 */
@Composable
private fun AttractLine(lines: List<String>) {
    val p = PixelTheme.palette
    val messages = lines.ifEmpty { listOf("FREE PLAY") }
    var index by remember(messages) { mutableIntStateOf(0) }
    var typed by remember(messages) { mutableIntStateOf(Int.MAX_VALUE) }
    var lit by remember { mutableStateOf(true) }
    if (PixelTheme.animated) {
        LaunchedEffect(messages) {
            while (true) {
                val text = messages[index % messages.size]
                lit = true
                for (n in 0..text.length) {
                    typed = n
                    delay(28)
                }
                delay(2_400)
                repeat(2) {
                    lit = false
                    delay(160)
                    lit = true
                    delay(160)
                }
                index = (index + 1) % messages.size
            }
        }
    }
    val text = messages[index % messages.size]
    val shown = text.take(typed)
    // Reserves two lines so the menu below never jumps; TalkBack reads the whole message.
    Box(
        Modifier.fillMaxWidth().heightIn(min = 34.dp).clearAndSetSemantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        PixelText(
            if (lit) shown + if (shown.length < text.length) "_" else "" else "",
            style = PixelType.Small,
            color = if (index == 0) p.yellow else p.ledAmber,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun TitleMenu(hasSavedRun: Boolean, callbacks: TitleCallbacks, dailyLabel: String) {
    if (hasSavedRun) {
        PixelButton("CONTINUE TOUR", callbacks.onContinue, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Call, sound = Sfx.Deal)
    }
    PixelButton("QUICK GAME", callbacks.onQuickGame, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Deal, sound = Sfx.Deal)
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PixelButton(if (hasSavedRun) "NEW TOUR" else "POKER TOUR", callbacks.onRunMode, Modifier.weight(1f), variant = PixelButtonVariant.Pink, textStyle = PixelType.Small)
        PixelButton(dailyLabel, callbacks.onDaily, Modifier.weight(1f), variant = PixelButtonVariant.Bet, textStyle = PixelType.Small)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val small = PixelType.Small.copy(fontSize = 9.sp)
        PixelButton("MODELS", callbacks.onModels, Modifier.weight(1.1f), variant = PixelButtonVariant.Hold, textStyle = small)
        PixelButton("STATS", callbacks.onStats, Modifier.weight(1f), variant = PixelButtonVariant.Gray, textStyle = small)
        PixelButton("HOW TO", callbacks.onHowTo, Modifier.weight(1f), variant = PixelButtonVariant.Gray, textStyle = small)
        PixelButton("SETUP", callbacks.onSettings, Modifier.weight(1f), variant = PixelButtonVariant.Gray, textStyle = small)
    }
}

/** The cabinet's bottom status strip: who is playing the opponents. */
@Composable
private fun StatusLine(brainLabel: String) {
    val p = PixelTheme.palette
    val unit = LocalPixelUnit.current
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                pxRect(0, 0, size.width.toInt(), unit, p.gold)
                pxRect(0, unit, size.width.toInt(), size.height.toInt() - unit, Color(0xEE05071C))
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PixelText("OPPONENTS:", style = PixelType.Label.copy(fontSize = 11.sp), color = p.gray, shadowUnits = 0)
        PixelText(brainLabel.uppercase(), Modifier.weight(1f), style = PixelType.LabelBold.copy(fontSize = 11.sp), color = p.ledGreen, maxLines = 1)
        PixelText("FREE PLAY", style = PixelType.Label.copy(fontSize = 11.sp), color = p.ledAmber, shadowUnits = 0)
    }
}
