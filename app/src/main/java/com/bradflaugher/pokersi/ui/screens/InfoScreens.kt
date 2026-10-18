package com.bradflaugher.pokersi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.bradflaugher.pokersi.llm.ModelCatalog
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.game.GameSpeed
import com.bradflaugher.pokersi.game.SoundVolume
import com.bradflaugher.pokersi.ui.pixel.LocalSoundPlayer
import com.bradflaugher.pokersi.ui.pixel.Sfx
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

/** A question for HOW TO's QUESTIONS panel, and its answer. Kept true to the app. */
internal class Faq(val question: String, val answer: String)

internal val Faqs = listOf(
    Faq(
        "DO I NEED THE INTERNET?",
        "Only to download a model, straight from Hugging Face. After that everything runs on your phone, offline: the cards, the AI, all of it.",
    ),
    Faq(
        "WHY ARE OPPONENTS SLOW TO ACT?",
        "Each one really thinks, on your phone's own chip. A smaller model answers quicker, and GAME SPEED: FAST (in SETUP or the pause menu) cuts the waits. With FAST FOLD on, once you fold the rest of the hand plays out at once.",
    ),
    Faq(
        "WHICH MODEL SHOULD I PICK?",
        "Start with ${ModelCatalog.recommended.name}: built for phones, quick and sensible. Bigger models play sharper but need more memory and think slower. MODELS can also load GGUF files you already have.",
    ),
    Faq(
        "MY PHONE IS OLD. WILL IT WORK?",
        "Yes. With no model loaded, House Bot plays every seat: every game and the whole tour work without a download. It also steps in whenever a model fumbles or takes too long, so a hand never stalls.",
    ),
    Faq(
        "WHY IS SOME TABLE TALK BLURRED?",
        "Small models love to blurt out their own cards. HAND BLUR (in SETUP, on by default) hides the words that give a hand away, so you see that they blabbed but not what. Switch it off to test how leaky your model is, or switch TABLE TALK off for quiet, quicker turns.",
    ),
    Faq(
        "WHAT IS THE POKER TOUR?",
        "Ten tables of rising stakes, your chips carried from one to the next. Every tour has a seed (it's shown when the tour ends), and the DAILY TOUR deals everyone the same seed that day.",
    ),
    Faq(
        "IS THERE REAL MONEY?",
        "No. Chips are just for fun: nothing to buy, nothing to cash out, no ads.",
    ),
    Faq(
        "IS ANYTHING SENT ANYWHERE?",
        "No. No account, no ads, no analytics: games, stats and settings stay on this phone. The only network use is a model download you pick. The privacy policy is in SETUP, under ABOUT.",
    ),
    Faq(
        "HOW DO I REPORT A BUG?",
        "SETUP > ABOUT > FEEDBACK / REPORT A BUG opens a new GitHub issue in your browser. Thanks for helping!",
    ),
    Faq(
        "HOW DO I SEE THE TOUR AGAIN?",
        "Tap TAKE THE TOUR below, or REPLAY TOUR in SETUP. Replaying also brings back the one-time tips at the table.",
    ),
)

/** HOW TO's sections, for the jump buttons at the top. */
private enum class HowToSection(val label: String) { Hands("HANDS"), Games("GAMES"), Tour("TOUR"), Ai("AI"), Faq("FAQ") }

/**
 * Hand rankings (tap one to see an example), each game's rules, Run mode,
 * what the AI is, and QUESTIONS (tap one to open it; [openFaq] starts there
 * with that one open), with jump buttons to each section and TAKE THE TOUR
 * ([onTour]).
 */
@Composable
fun HowToPlayScreen(onBack: () -> Unit, modifier: Modifier = Modifier, onTour: () -> Unit = {}, openFaq: Int? = null) {
    val p = PixelTheme.palette
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val tops = remember { mutableStateMapOf<HowToSection, Int>() }
    fun Modifier.section(s: HowToSection) = onPlaced { tops[s] = it.positionInParent().y.toInt() }
    ArcadeScreen(modifier) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar("HOW TO PLAY", onBack)
            Row(Modifier.readable().align(Alignment.CenterHorizontally).padding(horizontal = 10.dp).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                HowToSection.entries.forEach { s ->
                    PixelButton(
                        s.label,
                        { scope.launch { tops[s]?.let { scroll.animateScrollTo(it) } } },
                        Modifier.weight(if (s.label.length > 3) 1.25f else 1f).height(48.dp).semantics { contentDescription = "Jump to ${s.name.lowercase()}" },
                        variant = if (s == HowToSection.Faq) PixelButtonVariant.Bet else PixelButtonVariant.Hold,
                        textStyle = PixelType.Small.copy(fontSize = 9.sp),
                    )
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                var picked by remember { mutableIntStateOf(0) }
                PayTable(
                    HandExamples.map { PayRow(it.name, it.rule) },
                    Modifier.readable().section(HowToSection.Hands),
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
                InfoPanel("THE BASICS", Modifier.section(HowToSection.Games)) {
                    BodyText("Everyone gets cards and bets in turns. On your turn: FOLD (give up), CHECK (pass, when nobody has bet), CALL (match the bet), or RAISE (bet more). Bet so big nobody calls and the pot is yours. Otherwise the best five-card hand at the showdown wins.", small = true)
                }
                GameVariant.entries.forEach { v ->
                    InfoPanel(v.displayName.uppercase()) {
                        PixelText(v.tagline, style = PixelType.BodySmall, color = p.yellow)
                        VGap(4.dp)
                        BodyText(v.howToPlay, small = true)
                    }
                }
                InfoPanel("POKER TOUR", Modifier.section(HowToSection.Tour)) {
                    BodyText("Climb a ladder of poker tables with rising stakes. Your chips go with you from table to table. Win the chip target before you run out of hands (or bust everyone) to clear a table and collect its bonus chips, then pick your next table: low stakes, or high stakes with an extra opponent, bigger blinds and a bigger bonus. Bigger risk, bigger reward. Go broke and the tour is over. Clear the tenth table to win, then keep going in endless mode. Leaving in the middle of a hand folds it. The daily tour is the same deal for everyone, every day.", small = true)
                }
                InfoPanel("WHO AM I PLAYING?", Modifier.section(HowToSection.Ai)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PixelAvatar(4242L, Mood.Smug, size = 56.dp)
                        SpeechBubble("I'm a language model. On your phone. Fear me.", Modifier.weight(1f))
                    }
                    VGap(6.dp)
                    BodyText("Each opponent is a made-up character played by a small AI language model running on your phone, offline. Every turn it reads the table (only what its seat may see), decides, and says something silly. Download a model from MODELS. With no model, the built-in House Bot plays every seat, and it steps in whenever the AI fumbles, so a hand never stalls.", small = true)
                }
                InfoPanel("QUESTIONS", Modifier.section(HowToSection.Faq)) {
                    var open by remember { mutableStateOf(openFaq) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Faqs.forEachIndexed { i, faq ->
                            FaqRow(faq, open == i, onToggle = { open = if (open == i) null else i })
                        }
                        VGap(6.dp)
                        PixelButton("TAKE THE TOUR", onTour, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Call, textStyle = PixelType.Small)
                    }
                }
                VGap(12.dp)
            }
        }
    }
    LaunchedEffect(openFaq, tops[HowToSection.Faq]) {
        if (openFaq != null) tops[HowToSection.Faq]?.let { scroll.scrollTo(it) }
    }
}

@Composable
private fun InfoPanel(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    PixelPanel(modifier.readable(), title = title, style = PanelStyle.Cabinet, paddingUnits = 2) { content() }
}

/** One question: a row to tap (48dp tall), and its answer under it while open. */
@Composable
private fun FaqRow(faq: Faq, open: Boolean, onToggle: () -> Unit) {
    val p = PixelTheme.palette
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .arcadeClick(onClick = onToggle)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PixelTag(if (open) "-" else "+", if (open) p.gold else p.cabinetLight, textColor = if (open) p.ink else p.white, style = PixelType.Small.copy(fontSize = 10.sp))
            PixelText(faq.question, Modifier.weight(1f), style = PixelType.Small.copy(fontSize = 9.sp), color = if (open) p.yellow else p.white)
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            BodyText(faq.answer, Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp), color = p.cardCream, small = true)
        }
    }
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
    val fastFold: Boolean = true,
    val tableTalk: Boolean = true,
    val handBlur: Boolean = true,
    val volume: SoundVolume = SoundVolume.HIGH,
    val confirmExit: Boolean = true,
    val largerText: Boolean = false,
    val highContrast: Boolean = false,
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
    val onFastFold: (Boolean) -> Unit = {},
    val onTableTalk: (Boolean) -> Unit = {},
    val onHandBlur: (Boolean) -> Unit = {},
    val onVolume: (SoundVolume) -> Unit = {},
    val onConfirmExit: (Boolean) -> Unit = {},
    val onLargerText: (Boolean) -> Unit = {},
    val onHighContrast: (Boolean) -> Unit = {},
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

/** The settings, in panels: game, sound and feel, accessibility, the tour, help, the workshop, about. */
@Composable
fun SettingsScreen(settings: SettingsUi, callbacks: SettingsCallbacks, modifier: Modifier = Modifier) {
    val p = PixelTheme.palette
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    // A new volume plays a chip at that volume (its button stays quiet, so the chip is all you hear).
    val sound = LocalSoundPlayer.current
    var heardVolume by remember { mutableStateOf(settings.volume) }
    LaunchedEffect(settings.volume) {
        if (settings.volume != heardVolume) sound.play(Sfx.Chip)
        heardVolume = settings.volume
    }
    ArcadeScreen(modifier.modalBehind(confirmReset && settings.hasSavedRun)) {
        Column(Modifier.fillMaxSize()) {
            ArcadeTopBar("SETTINGS", callbacks.onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PixelPanel(Modifier.readable(), title = "GAME", paddingUnits = 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ChoiceSetting("GAME SPEED", "How fast the table moves") {
                            ChoiceRow(GameSpeed.entries, settings.speed, { it.label }, callbacks.onSpeed)
                        }
                        ToggleRow("FAST FOLD", "Once you fold, the rest of the hand plays out at once", settings.fastFold, callbacks.onFastFold)
                        ToggleRow("TABLE TALK", "Opponents chat in speech bubbles", settings.tableTalk, callbacks.onTableTalk)
                        if (settings.tableTalk) {
                            ToggleRow("HAND BLUR", "Blurs the words that give an opponent's hand away. Off: watch small models blurt their cards", settings.handBlur, callbacks.onHandBlur)
                        }
                        ToggleRow("CONFIRM EXIT", "Back on the title screen asks before leaving", settings.confirmExit, callbacks.onConfirmExit)
                    }
                }
                PixelPanel(Modifier.readable(), title = "SOUND & FEEL", paddingUnits = 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleRow("SOUND", "Chiptune bleeps", settings.sound, callbacks.onSound)
                        ChoiceSetting("VOLUME", if (settings.sound) "How loud the bleeps are" else "Switch SOUND on to choose") {
                            ChoiceRow(SoundVolume.entries, settings.volume, { it.label }, callbacks.onVolume, enabled = settings.sound, sound = null)
                        }
                        ToggleRow("HAPTICS", "Buzz on buttons", settings.haptics, callbacks.onHaptics)
                        ToggleRow("CRT GLASS", "Scanlines and vignette", settings.crt, callbacks.onCrt)
                        ToggleRow("CARD SWAY", "Cards bob gently", settings.cardSway, callbacks.onCardSway)
                    }
                }
                PixelPanel(Modifier.readable(), title = "ACCESSIBILITY", paddingUnits = 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ToggleRow("LARGER TEXT", "All text a size up, on top of your phone's font size", settings.largerText, callbacks.onLargerText)
                        ToggleRow("HIGH CONTRAST", "Brighter text and outlines, quieter felt and glass", settings.highContrast, callbacks.onHighContrast)
                    }
                }
                PixelPanel(Modifier.readable(), title = "POKER TOUR", style = PanelStyle.Dark, paddingUnits = 2) {
                    if (!settings.hasSavedRun) {
                        BodyText("No tour in progress.", color = p.cardCream, small = true)
                    } else {
                        PixelButton("ABANDON TOUR", { confirmReset = true }, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Deal, textStyle = PixelType.Small)
                    }
                }
                PixelPanel(Modifier.readable(), title = "HELP", style = PanelStyle.Dark, paddingUnits = 2) {
                    SmallButton("REPLAY TOUR", callbacks.onShowTips, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Hold)
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
    if (confirmReset && settings.hasSavedRun) {
        ConfirmDialog(
            question = "ABANDON YOUR TOUR?",
            message = "Your saved tour and today's daily tour are thrown away, chips and all. Your records stay.",
            confirmLabel = "ABANDON",
            stayLabel = "KEEP IT",
            onConfirm = { confirmReset = false; callbacks.onResetRun() },
            onStay = { confirmReset = false },
            mood = Mood.Sad,
        )
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

/** A setting with a few choices: a title and subtitle like [ToggleRow]'s, the choices under them. */
@Composable
private fun ChoiceSetting(title: String, subtitle: String, choices: @Composable () -> Unit) {
    val p = PixelTheme.palette
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        PixelText(title, Modifier.semantics { heading() }, style = PixelType.Small, color = p.yellow)
        PixelText(subtitle, style = PixelType.BodySmall, color = p.cardCream, shadowUnits = 0)
        VGap(4.dp)
        choices()
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
            // TalkBack: "Sound, on, button" rather than a bare "on".
            Modifier.semantics {
                contentDescription = title.lowercase().replaceFirstChar(Char::uppercase)
                stateDescription = if (on) "On" else "Off"
            },
            variant = if (on) PixelButtonVariant.Call else PixelButtonVariant.Gray,
            textStyle = PixelType.Small,
        )
    }
}
