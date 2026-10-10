package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.ui.pixel.AnimatedPixelCard
import com.bradflaugher.pokersi.ui.pixel.LocalPixelUnit
import com.bradflaugher.pokersi.ui.pixel.MarqueeFrame
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
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
)

/**
 * The attract screen: the POKER S.I. marquee, a royal flush fanned out,
 * blinking INSERT COIN, the menu and a cabinet status line saying who plays
 * the opponents.
 */
@Composable
fun TitleScreen(
    brainLabel: String,
    hasSavedRun: Boolean,
    callbacks: TitleCallbacks,
    modifier: Modifier = Modifier,
    dailyLabel: String = "DAILY RUN",
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
                            InsertCoin()
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
                            CardFan(if (tall > 640.dp) 66.dp else 54.dp)
                            InsertCoin()
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
    val cards = Card.parseList("Ts Js Qs Ks As")
    Box(Modifier.fillMaxWidth().height(size * 1.75f).padding(top = 6.dp), contentAlignment = Alignment.Center) {
        cards.forEachIndexed { i, c ->
            val k = i - 2
            AnimatedPixelCard(
                c, faceUp = true, size = size,
                highlighted = true,
                sway = LocalCardSway.current, swayPhase = i * 0.8f,
                modifier = Modifier.offset(x = size * 0.74f * k, y = (k * k * 3).dp).rotate(k * 7f),
            )
        }
    }
}

@Composable
private fun InsertCoin() {
    val p = PixelTheme.palette
    Blinking(1200) {
        PixelText("INSERT COIN  -  PRESS START", style = PixelType.Small, color = p.yellow, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TitleMenu(hasSavedRun: Boolean, callbacks: TitleCallbacks, dailyLabel: String) {
    if (hasSavedRun) {
        PixelButton("CONTINUE RUN", callbacks.onContinue, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Call, sound = Sfx.Deal)
    }
    PixelButton("QUICK GAME", callbacks.onQuickGame, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Deal, sound = Sfx.Deal)
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PixelButton(if (hasSavedRun) "NEW RUN" else "RUN MODE", callbacks.onRunMode, Modifier.weight(1f), variant = PixelButtonVariant.Pink, textStyle = PixelType.Small)
        PixelButton(dailyLabel, callbacks.onDaily, Modifier.weight(1f), variant = PixelButtonVariant.Bet, textStyle = PixelType.Small)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        PixelButton("MODELS", callbacks.onModels, Modifier.weight(1f), variant = PixelButtonVariant.Hold, textStyle = PixelType.Small)
        PixelButton("HOW TO", callbacks.onHowTo, Modifier.weight(1f), variant = PixelButtonVariant.Gray, textStyle = PixelType.Small)
        PixelButton("SETUP", callbacks.onSettings, Modifier.weight(1f), variant = PixelButtonVariant.Gray, textStyle = PixelType.Small)
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
