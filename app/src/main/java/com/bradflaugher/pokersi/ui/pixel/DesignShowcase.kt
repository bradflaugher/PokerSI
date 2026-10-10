package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bradflaugher.pokersi.engine.Card

private fun cards(s: String) = Card.parseList(s)

/**
 * Every piece of the pixel design system on one scrolling screen, on the
 * swirl under CRT glass. Used by the screenshot tests and handy as a debug
 * screen. Interactive bits (HOLD toggles, DEAL, BET) work.
 */
@Composable
fun DesignShowcase(modifier: Modifier = Modifier, scrollable: Boolean = true) {
    PixelTheme {
        CrtOverlay(modifier.fillMaxSize()) {
            SwirlBackground(Modifier.fillMaxSize()) {
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxSize()
                        .then(if (scrollable) Modifier.verticalScroll(scroll) else Modifier)
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ShowcaseTitle()
                    ShowcaseTable()
                    ShowcaseCards()
                    ShowcaseButtons()
                    ShowcaseCounters()
                    ShowcaseAvatars()
                    ShowcaseBubbles()
                    ShowcasePayTable()
                    ShowcaseBanner()
                }
            }
        }
    }
}

@Composable
fun ShowcaseTitle(modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    MarqueeFrame(modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelText("POKER", style = PixelType.Banner, color = p.yellow, shadowUnits = 2, fontSize = PixelType.Banner.fontSize * 1.4f)
            PixelText("S.I.", style = PixelType.Banner, color = p.hotPink, shadowUnits = 2, fontSize = PixelType.Banner.fontSize * 1.4f)
            PixelText("SILICON INTELLIGENCE CASINO", style = PixelType.Small, color = p.white, textAlign = TextAlign.Center)
        }
    }
}

/** A mock table: opponent with a speech bubble, the board, your hand, counters and buttons. */
@Composable
fun ShowcaseTable(modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    var held by remember { mutableStateOf(setOf(0, 1, 2)) }
    var credits by remember { mutableIntStateOf(1250) }
    var faceUp by remember { mutableStateOf(true) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelAvatar(seed = 42L, mood = Mood.Smug, size = 72.dp)
                PixelText("BYTE-SIZED BOB", style = PixelType.Small, color = p.yellow)
            }
            SpeechBubble("All in? You don't have the chips for my bluffs, meatbag.", Modifier.weight(1f).padding(bottom = 20.dp), streaming = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cards("Kh Kd 7c").forEach { PixelCard(it, size = 56.dp) }
            PixelCard(null, faceUp = false, size = 56.dp)
            PixelCard(null, faceUp = false, size = 56.dp)
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cards("Ks Kc 7h 2d 9s").forEachIndexed { i, c ->
                AnimatedPixelCard(
                    c, faceUp = faceUp, size = 70.dp, held = i in held, highlighted = i < 2, swayPhase = i * 1.1f,
                    onClick = { held = if (i in held) held - i else held + i },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            LedCounter(credits, label = "CREDIT", minDigits = 5)
            LedCounter(50, label = "BET", minDigits = 3, color = LedColor.Amber)
            ChipStack(credits, showAmount = false, scaleUnits = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton("BET", { credits -= 5 }, variant = PixelButtonVariant.Bet, sound = Sfx.Chip)
            PixelButton("HOLD", { held = if (held.size == 5) emptySet() else (0..4).toSet() }, variant = PixelButtonVariant.Hold)
            PixelButton("DEAL", { faceUp = !faceUp; credits += 120 }, variant = PixelButtonVariant.Deal, sound = Sfx.Deal)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShowcaseCards(modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel("THE DECK")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards("As 2h 3d 4c 5s 6h 7d 8c 9s Th").forEach { PixelCard(it, size = 56.dp) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards("Jh Qh Kh Jc Qc Kc Ad Ah Ac").forEach { PixelCard(it, size = 56.dp) }
            PixelCard(null, faceUp = false, size = 56.dp)
        }
        SectionLabel("STATES")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            LabeledCard("HELD") { PixelCard(Card.parse("Qs"), held = true, size = 56.dp) }
            LabeledCard("WIN") { PixelCard(Card.parse("Ts"), highlighted = true, size = 56.dp) }
            LabeledCard("FOLD") { PixelCard(Card.parse("Kd"), dimmed = true, size = 56.dp) }
            LabeledCard("BACK") { PixelCard(null, faceUp = false, dimmed = true, size = 56.dp) }
        }
        SectionLabel("SIZES")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            PixelCard(Card.parse("Ks"), size = 24.dp)
            PixelCard(Card.parse("Ks"), size = 36.dp)
            PixelCard(Card.parse("Ks"), size = 48.dp)
            PixelCard(Card.parse("Ks"), size = 72.dp)
            PixelCard(Card.parse("Ks"), size = 96.dp)
        }
        Spacer(Modifier.height(2.dp))
        PixelText("Tap a card to hold it.", style = PixelType.BodySmall, color = p.cabinetGlow)
    }
}

@Composable
private fun LabeledCard(label: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        content()
        PixelText(label, style = PixelType.Small, color = PixelTheme.palette.white)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    PixelText(text, modifier, style = PixelType.Heading, color = PixelTheme.palette.yellow, shadowUnits = 1)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShowcaseButtons(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel("BUTTONS")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelButton("DEAL", {}, variant = PixelButtonVariant.Deal)
            PixelButton("BET", {}, variant = PixelButtonVariant.Bet)
            PixelButton("HOLD", {}, variant = PixelButtonVariant.Hold)
            PixelButton("CALL", {}, variant = PixelButtonVariant.Call)
            PixelButton("RAISE", {}, variant = PixelButtonVariant.Pink)
            PixelButton("FOLD", {}, variant = PixelButtonVariant.Gray)
            PixelButton("DRAW", {}, enabled = false)
        }
        PixelButton("ALL IN", {}, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Deal, subtitle = "1,250 chips", textStyle = PixelType.Title)
    }
}

@Composable
fun ShowcaseCounters(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel("COUNTERS & CHIPS")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            LedCounter(8675, label = "POT", minDigits = 6)
            LedCounter(42, label = "WIN", minDigits = 4, color = LedColor.Green)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            ChipStack(7)
            ChipStack(135)
            ChipStack(2860)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShowcaseAvatars(modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionLabel("OPPONENTS")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1L..15L).forEach { PixelAvatar(seed = it * 7919L, size = 60.dp) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Mood.entries.forEach { m ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelAvatar(seed = 12L, mood = m, size = 60.dp)
                    PixelText(m.name.uppercase(), style = PixelType.Label, color = p.white, fontSize = PixelType.Label.fontSize * 0.8f)
                }
            }
        }
    }
}

@Composable
fun ShowcaseBubbles(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("TABLE TALK", Modifier.align(Alignment.CenterHorizontally))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelAvatar(seed = 11L, mood = Mood.Thinking, size = 56.dp)
            SpeechBubble("", Modifier.padding(bottom = 16.dp), thinking = true)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PixelAvatar(seed = 5L, mood = Mood.Happy, size = 56.dp)
            SpeechBubble("Nice hand! Shame about the river. Beep boop.", Modifier.weight(1f).padding(bottom = 16.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            SpeechBubble("I fold. I always fold.", Modifier.padding(bottom = 16.dp), tail = BubbleTail.BottomEnd)
            PixelAvatar(seed = 23L, mood = Mood.Sad, size = 56.dp)
        }
    }
}

@Composable
fun ShowcasePayTable(modifier: Modifier = Modifier) {
    PayTable(
        rows = listOf(
            PayRow("ROYAL FLUSH", "800"),
            PayRow("STRAIGHT FLUSH", "50"),
            PayRow("FOUR OF A KIND", "25"),
            PayRow("FULL HOUSE", "9"),
            PayRow("FLUSH", "6"),
            PayRow("STRAIGHT", "4"),
            PayRow("THREE OF A KIND", "3"),
            PayRow("TWO PAIR", "2"),
            PayRow("JACKS OR BETTER", "1"),
        ),
        modifier = modifier.fillMaxWidth(),
        title = "PAY TABLE",
        highlight = 3,
    )
}

@Composable
fun ShowcaseBanner(modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    Box(modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        ConfettiBurst(trigger = 1, modifier = Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BigBanner("FULL HOUSE!")
            BigBanner("+1,250", color = p.white, ribbon = p.cabinetLight, style = PixelType.Score)
        }
    }
    Spacer(Modifier.width(1.dp))
}

@Preview(widthDp = 411, heightDp = 2600)
@Composable
private fun DesignShowcasePreview() {
    DesignShowcase(scrollable = false)
}
