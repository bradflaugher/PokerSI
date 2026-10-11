package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.game.GameSpeed
import com.bradflaugher.pokersi.ui.pixel.Mood
import com.bradflaugher.pokersi.ui.pixel.PanelStyle
import com.bradflaugher.pokersi.ui.pixel.PayRow
import com.bradflaugher.pokersi.ui.pixel.PayTable
import com.bradflaugher.pokersi.ui.pixel.PixelAvatar
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelCard
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.SpeechBubble

// ---------------------------------------------------------------- how to play

/**
 * One hand ranking for How to Play: its name, the rule in a few words, an
 * example hand, and how many of the example's cards (the first [making])
 * make the hand; the rest are kickers and show dimmed.
 */
internal class HandExample(val name: String, val rule: String, val cards: String, val making: Int)

internal val HandExamples = listOf(
    HandExample("ROYAL FLUSH", "A K Q J 10", "As Ks Qs Js Ts", 5),
    HandExample("STRAIGHT FLUSH", "5 IN A ROW, 1 SUIT", "9h 8h 7h 6h 5h", 5),
    HandExample("FOUR OF A KIND", "4 SAME RANK", "Qc Qd Qh Qs 7d", 4),
    HandExample("FULL HOUSE", "3 + 2", "Jh Jc Jd 4s 4h", 5),
    HandExample("FLUSH", "5 OF 1 SUIT", "Ad Jd 8d 6d 2d", 5),
    HandExample("STRAIGHT", "5 IN A ROW", "Ts 9d 8c 7h 6s", 5),
    HandExample("THREE OF A KIND", "3 SAME RANK", "7c 7d 7s Kh 2c", 3),
    HandExample("TWO PAIR", "2 + 2", "Ac Ad 9s 9h 4d", 4),
    HandExample("PAIR", "2 SAME RANK", "Kc Kd 8s 5h 2c", 2),
    HandExample("HIGH CARD", "NOTHING. OOF.", "Ah Jd 8c 5s 3h", 1),
)

/** Hand rankings (tap one to see an example), each game's rules, Run mode, and what the AI is. */
@Composable
fun HowToPlayScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    ArcadeScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar("HOW TO PLAY", onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                var picked by remember { mutableIntStateOf(0) }
                PayTable(
                    HandExamples.map { PayRow(it.name, it.rule) },
                    Modifier.readable(),
                    title = "HAND RANKINGS",
                    highlight = picked,
                    onRowClick = { picked = it },
                    detail = {
                        // The picked hand, right under its row: the cards that make it lit, kickers dimmed.
                        val example = HandExamples[picked]
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                            Card.parseList(example.cards).forEachIndexed { i, card ->
                                PixelCard(card, size = 44.dp, highlighted = i < example.making, dimmed = i >= example.making)
                            }
                        }
                    },
                )
                PixelText(
                    "TAP A HAND TO SEE IT",
                    style = PixelType.Label.copy(fontSize = 10.sp),
                    color = p.gray,
                    textAlign = TextAlign.Center,
                    shadowUnits = 0,
                )
                InfoPanel("THE BASICS") {
                    BodyText("Everyone gets cards and bets in turns. On your turn: FOLD (give up), CHECK (pass, when nobody has bet), CALL (match the bet), or RAISE (bet more). Bet so big nobody calls and the pot is yours. Otherwise the best five-card hand at the showdown wins.", small = true)
                }
                GameVariant.entries.forEach { v ->
                    InfoPanel(v.displayName.uppercase()) {
                        PixelText(v.tagline, style = PixelType.BodySmall, color = p.yellow)
                        VGap(4.dp)
                        BodyText(v.howToPlay, small = true)
                    }
                }
                InfoPanel("POKER TOUR") {
                    BodyText("Climb a ladder of poker tables with rising stakes. Your chips go with you from table to table. Win the chip target before you run out of hands (or bust everyone) to clear a table and collect its bonus chips, then pick your next table: low stakes, or high stakes with an extra opponent, bigger blinds and a bigger bonus. Bigger risk, bigger reward. Go broke and the tour is over. Clear the tenth table to win, then keep going in endless mode. Leaving in the middle of a hand folds it. The daily tour is the same deal for everyone, every day.", small = true)
                }
                InfoPanel("WHO AM I PLAYING?") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelAvatar(4242L, Mood.Smug, size = 56.dp)
                        SpeechBubble("I'm a language model. On your phone. Fear me.", Modifier.weight(1f))
                    }
                    VGap(6.dp)
                    BodyText("Each opponent is a made-up character played by a small AI language model running on your phone, offline. Every turn it reads the table (only what its seat may see), decides, and says something silly. Download a model from MODELS. With no model, the built-in House Bot plays every seat, and it steps in whenever the AI fumbles, so a hand never stalls.", small = true)
                }
                VGap(12.dp)
            }
        }
    }
}

@Composable
private fun InfoPanel(title: String, content: @Composable () -> Unit) {
    PixelPanel(Modifier.readable(), title = title, style = PanelStyle.Cabinet, paddingUnits = 2) { content() }
}

// ---------------------------------------------------------------- settings

/** The settings as the screen shows them. */
data class SettingsUi(
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val crt: Boolean = true,
    val cardSway: Boolean = true,
    val debugConsole: Boolean = false,
    val speed: GameSpeed = GameSpeed.NORMAL,
    val hasSavedRun: Boolean = false,
    /** BuildConfig.VERSION_NAME, for the ABOUT panel. */
    val version: String = "dev",
)

class SettingsCallbacks(
    val onSound: (Boolean) -> Unit = {},
    val onHaptics: (Boolean) -> Unit = {},
    val onCrt: (Boolean) -> Unit = {},
    val onCardSway: (Boolean) -> Unit = {},
    val onDebugConsole: (Boolean) -> Unit = {},
    val onSpeed: (GameSpeed) -> Unit = {},
    val onResetRun: () -> Unit = {},
    val onShowTips: () -> Unit = {},
    // The links (Links.kt) say whether something could open them.
    val onShare: () -> Boolean = { true },
    val onRate: () -> Boolean = { true },
    val onFeedback: () -> Boolean = { true },
    val onPrivacy: () -> Boolean = { true },
    val onSource: () -> Boolean = { true },
    val onBack: () -> Unit = {},
)

@Composable
fun SettingsScreen(settings: SettingsUi, callbacks: SettingsCallbacks, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    var confirmReset by remember { mutableStateOf(false) }
    ArcadeScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar("SETTINGS", callbacks.onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PixelPanel(Modifier.readable(), title = "CABINET", paddingUnits = 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleRow("SOUND", "Chiptune bleeps", settings.sound, callbacks.onSound)
                        ToggleRow("HAPTICS", "Buzz on buttons", settings.haptics, callbacks.onHaptics)
                        ToggleRow("CRT GLASS", "Scanlines and vignette", settings.crt, callbacks.onCrt)
                        ToggleRow("CARD SWAY", "Cards bob gently", settings.cardSway, callbacks.onCardSway)
                        SectionHeading("GAME SPEED")
                        ChoiceRow(GameSpeed.entries, settings.speed, { it.label }, callbacks.onSpeed)
                    }
                }
                PixelPanel(Modifier.readable(), title = "POKER TOUR", style = PanelStyle.Dark, paddingUnits = 2) {
                    if (!settings.hasSavedRun) {
                        BodyText("No tour in progress.", color = p.cardCream, small = true)
                    } else if (!confirmReset) {
                        PixelButton("ABANDON TOUR", { confirmReset = true }, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Deal, textStyle = PixelType.Small)
                    } else {
                        BodyText("Really throw away your tour? Chips and all?", color = p.yellow, small = true)
                        VGap(8.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("KEEP IT", { confirmReset = false }, Modifier.weight(1f))
                            SmallButton("YES, RESET", { confirmReset = false; callbacks.onResetRun() }, Modifier.weight(1f), variant = PixelButtonVariant.Deal)
                        }
                    }
                }
                PixelPanel(Modifier.readable(), title = "HELP", style = PanelStyle.Dark, paddingUnits = 2) {
                    SmallButton("SHOW TIPS", callbacks.onShowTips, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Hold)
                }
                PixelPanel(Modifier.readable(), title = "WORKSHOP", style = PanelStyle.Dark, paddingUnits = 2) {
                    ToggleRow("DEBUG CONSOLE", "Press ~ at the table to see the AI's prompts and replies", settings.debugConsole, callbacks.onDebugConsole)
                }
                AboutPanel(settings.version, callbacks)
                PixelText(
                    "NO ADS. NO ACCOUNTS. NO ANALYTICS.\nTHE NETWORK IS ONLY USED TO DOWNLOAD MODELS YOU PICK.",
                    style = PixelType.Label.copy(fontSize = 10.sp), color = p.gray, textAlign = TextAlign.Center, shadowUnits = 0,
                )
                VGap(12.dp)
            }
        }
    }
}

/**
 * Share, rate, feedback, privacy and source: passive links, only ever opened
 * from here. Never a prompt or the review API (see AGENTS.md).
 */
@Composable
private fun AboutPanel(version: String, callbacks: SettingsCallbacks) {
    val p = PixelTheme.palette
    var notice by remember { mutableStateOf<String?>(null) }
    fun open(link: () -> Boolean, missing: String) {
        notice = if (link()) null else missing
    }
    PixelPanel(Modifier.readable(), title = "ABOUT", paddingUnits = 2) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelText("POKER S.I.", Modifier.weight(1f), style = PixelType.Small, color = p.yellow)
                PixelText("VERSION ${version.uppercase()}", style = PixelType.Label.copy(fontSize = 11.sp), color = p.cardCream, shadowUnits = 0)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("SHARE", { open(callbacks.onShare, "NOTHING CAN SHARE") }, Modifier.weight(1f), variant = PixelButtonVariant.Pink)
                SmallButton("RATE ON PLAY", { open(callbacks.onRate, "NO STORE OR BROWSER") }, Modifier.weight(1f), variant = PixelButtonVariant.Bet)
            }
            SmallButton("FEEDBACK / REPORT A BUG", { open(callbacks.onFeedback, "NO BROWSER FOUND") }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("PRIVACY", { open(callbacks.onPrivacy, "NO BROWSER FOUND") }, Modifier.weight(1f))
                SmallButton("SOURCE", { open(callbacks.onSource, "NO BROWSER FOUND") }, Modifier.weight(1f))
            }
            notice?.let { PixelText(it, style = PixelType.Small.copy(fontSize = 9.sp), color = p.red) }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = PixelTheme.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            PixelText(title, style = PixelType.Small, color = p.yellow)
            PixelText(subtitle, style = PixelType.BodySmall, color = p.cardCream, shadowUnits = 0)
        }
        PixelButton(
            if (on) "ON" else "OFF", { onChange(!on) },
            Modifier.padding(0.dp),
            variant = if (on) PixelButtonVariant.Call else PixelButtonVariant.Gray,
            textStyle = PixelType.Small,
        )
    }
}
