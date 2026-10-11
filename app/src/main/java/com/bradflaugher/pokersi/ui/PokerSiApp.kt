package com.bradflaugher.pokersi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bradflaugher.pokersi.game.AttractLines
import com.bradflaugher.pokersi.game.GameController
import com.bradflaugher.pokersi.game.TitleFacts
import androidx.compose.runtime.remember
import com.bradflaugher.pokersi.game.GameSpeed
import com.bradflaugher.pokersi.game.PauseReason
import com.bradflaugher.pokersi.game.RunScreen
import com.bradflaugher.pokersi.game.RunUiState
import com.bradflaugher.pokersi.ui.pixel.PixelButton
import com.bradflaugher.pokersi.ui.pixel.PixelButtonVariant
import com.bradflaugher.pokersi.ui.pixel.PixelPanel
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
import com.bradflaugher.pokersi.ui.pixel.rememberChiptuneSound
import com.bradflaugher.pokersi.ui.screens.BodyText
import com.bradflaugher.pokersi.ui.screens.ChoiceRow
import com.bradflaugher.pokersi.ui.screens.ChooseTableScreen
import com.bradflaugher.pokersi.ui.screens.DebugConsole
import com.bradflaugher.pokersi.debug.AiLog
import com.bradflaugher.pokersi.ui.screens.HowToPlayScreen
import com.bradflaugher.pokersi.ui.screens.LocalCardSway
import com.bradflaugher.pokersi.ui.models.ModelsScreen
import com.bradflaugher.pokersi.ui.screens.PickerCallbacks
import com.bradflaugher.pokersi.ui.screens.PickerScreen
import com.bradflaugher.pokersi.ui.screens.RunClearedScreen
import com.bradflaugher.pokersi.ui.screens.RunIntroScreen
import com.bradflaugher.pokersi.ui.screens.RunOverScreen
import com.bradflaugher.pokersi.ui.screens.SettingsCallbacks
import com.bradflaugher.pokersi.ui.screens.SettingsScreen
import com.bradflaugher.pokersi.ui.screens.SettingsUi
import com.bradflaugher.pokersi.ui.screens.TableCallbacks
import com.bradflaugher.pokersi.ui.screens.TableScreen
import com.bradflaugher.pokersi.ui.screens.TitleCallbacks
import com.bradflaugher.pokersi.ui.screens.TitleScreen
import com.bradflaugher.pokersi.ui.screens.StatsScreen
import com.bradflaugher.pokersi.ui.screens.TipsOverlay
import com.bradflaugher.pokersi.BuildConfig
import com.bradflaugher.pokersi.Links
import com.bradflaugher.pokersi.openWeb
import com.bradflaugher.pokersi.rateApp
import com.bradflaugher.pokersi.sendFeedback
import com.bradflaugher.pokersi.shareApp
import androidx.compose.ui.platform.LocalContext

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/** The whole app: theme from the settings, and state-based navigation over [PokerViewModel.backStack]. */
@Composable
fun PokerSiApp(vm: PokerViewModel) {
    val s = vm.settings
    val sound by s.sound.collectAsStateWithLifecycle()
    val haptics by s.haptics.collectAsStateWithLifecycle()
    val crt by s.crt.collectAsStateWithLifecycle()
    val sway by s.cardSway.collectAsStateWithLifecycle()
    val debug by s.debugConsole.collectAsStateWithLifecycle()
    val console by vm.console.collectAsStateWithLifecycle()
    val player = rememberChiptuneSound(sound)
    val haptic = LocalHapticFeedback.current
    // Bots (and their model) don't play on while nobody is looking.
    LifecycleStartEffect(vm.controller) {
        vm.controller.setPaused(PauseReason.Background, false)
        onStopOrDispose { vm.controller.setPaused(PauseReason.Background, true) }
    }
    PixelTheme(crtEnabled = crt, sound = player) {
        CompositionLocalProvider(
            LocalHapticFeedback provides if (haptics) haptic else NoHaptics,
            LocalCardSway provides sway,
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                when (vm.backStack.last()) {
                    Screen.Title -> TitleRoute(vm)
                    Screen.Picker -> PickerRoute(vm)
                    Screen.Table -> TableRoute(vm, run = null)
                    Screen.Run -> RunRoute(vm)
                    Screen.Models -> ModelsScreen(onBack = { vm.back() }, onConsole = if (debug) vm::toggleConsole else null)
                    Screen.HowTo -> HowToPlayScreen(onBack = { vm.back() })
                    Screen.Settings -> SettingsRoute(vm)
                    Screen.Stats -> StatsScreen(
                        vm.stats.stats.collectAsStateWithLifecycle().value,
                        onReset = vm.stats::reset,
                        onBack = { vm.back() },
                    )
                }
                BackHandler(enabled = vm.backStack.size > 1 && vm.backStack.last() !in setOf(Screen.Table, Screen.Run)) { vm.back() }
                if (debug) {
                    val entries by AiLog.entries.collectAsStateWithLifecycle()
                    DebugConsole(entries, console, onClose = vm::closeConsole, onClear = AiLog::clear)
                    // Composed last, so Back closes the console before anything under it.
                    BackHandler(enabled = console) { vm.closeConsole() }
                }
            }
        }
    }
}

@Composable
private fun TitleRoute(vm: PokerViewModel) {
    val label by vm.brains.label.collectAsStateWithLifecycle()
    val saved by vm.runSaver.saved.collectAsStateWithLifecycle()
    val daily by vm.daily.result.collectAsStateWithLifecycle()
    val tips by vm.tips.collectAsStateWithLifecycle()
    val stats by vm.stats.stats.collectAsStateWithLifecycle()
    // The saved tour is read once per visit (and when one is saved or cleared).
    val tour = remember(saved) { if (saved) vm.runSaver.load() else null }
    val attract = remember(tour, daily, stats, label) { AttractLines.lines(TitleFacts(tour, daily, stats, label)) }
    TitleScreen(
        brainLabel = label,
        hasSavedRun = saved,
        dailyLabel = daily.label,
        attract = attract,
        callbacks = TitleCallbacks(
            onQuickGame = { vm.go(Screen.Picker) },
            onRunMode = vm::newRun,
            onContinue = vm::continueRun,
            onDaily = vm::dailyRun,
            onModels = { vm.go(Screen.Models) },
            onHowTo = { vm.go(Screen.HowTo) },
            onSettings = { vm.go(Screen.Settings) },
            onStats = { vm.go(Screen.Stats) },
        ),
    )
    if (tips) TipsOverlay(onDismiss = vm::dismissTips)
}

@Composable
private fun PickerRoute(vm: PokerViewModel) {
    val setup by vm.picker.collectAsStateWithLifecycle()
    PickerScreen(
        setup,
        PickerCallbacks(
            onVariant = vm::setVariant,
            onOpponents = vm::setOpponents,
            onStack = vm::setStack,
            onShuffle = vm::shuffleOpponents,
            onDeal = vm::deal,
            onBack = { vm.back() },
        ),
    )
}

@Composable
private fun SettingsRoute(vm: PokerViewModel) {
    val s = vm.settings
    val ui = SettingsUi(
        sound = s.sound.collectAsStateWithLifecycle().value,
        haptics = s.haptics.collectAsStateWithLifecycle().value,
        crt = s.crt.collectAsStateWithLifecycle().value,
        cardSway = s.cardSway.collectAsStateWithLifecycle().value,
        debugConsole = s.debugConsole.collectAsStateWithLifecycle().value,
        speed = s.speed.collectAsStateWithLifecycle().value,
        hasSavedRun = vm.runSaver.saved.collectAsStateWithLifecycle().value,
        version = BuildConfig.VERSION_NAME,
    )
    val context = LocalContext.current
    SettingsScreen(
        ui,
        SettingsCallbacks(
            onSound = s::setSound,
            onHaptics = s::setHaptics,
            onCrt = s::setCrt,
            onCardSway = s::setCardSway,
            onDebugConsole = s::setDebugConsole,
            onSpeed = s::setSpeed,
            onResetRun = vm::resetRun,
            onShowTips = vm::showTips,
            onShare = context::shareApp,
            onRate = context::rateApp,
            onFeedback = context::sendFeedback,
            onPrivacy = { context.openWeb(Links.PRIVACY_POLICY_URL) },
            onSource = { context.openWeb(Links.SOURCE_URL) },
            onBack = { vm.back() },
        ),
    )
}

@Composable
private fun RunRoute(vm: PokerViewModel) {
    val c = vm.controller
    val run by c.run.collectAsStateWithLifecycle()
    val ui = run ?: return
    when (ui.screen) {
        RunScreen.Intro -> {
            BackHandler { vm.toTitle() }
            RunIntroScreen(ui, onPlay = c::beginTable, onBack = vm::toTitle)
        }
        RunScreen.Table -> TableRoute(vm, ui)
        RunScreen.Cleared -> {
            BackHandler { c.toChoose() }
            RunClearedScreen(ui, onNext = c::toChoose)
        }
        RunScreen.Choose -> {
            BackHandler { vm.toTitle() }
            ChooseTableScreen(ui, onChoose = { c.chooseTable(it) }, onBack = vm::toTitle)
        }
        RunScreen.Victory -> {
            BackHandler { c.continueEndless() }
            RunOverScreen(ui, victory = true, onNewRun = vm::newRun, onMenu = vm::toTitle, onContinue = c::continueEndless)
        }
        RunScreen.GameOver -> {
            BackHandler { vm.toTitle() }
            RunOverScreen(ui, victory = false, onNewRun = vm::newRun, onMenu = vm::toTitle)
        }
    }
}

@Composable
private fun TableRoute(vm: PokerViewModel, run: RunUiState?) {
    val c: GameController = vm.controller
    val table by c.table.collectAsStateWithLifecycle()
    val speed by vm.settings.speed.collectAsStateWithLifecycle()
    var menu by rememberSaveable { mutableStateOf(false) }
    // The menu really pauses the table: no bot acts behind it.
    DisposableEffect(c, menu) {
        c.setPaused(PauseReason.Menu, menu)
        onDispose { c.setPaused(PauseReason.Menu, false) }
    }
    KeepScreenOn()
    BackHandler { menu = !menu }
    Box(Modifier.fillMaxSize()) {
        TableScreen(
            table,
            TableCallbacks(
                onAct = c::act,
                onToggle = c::toggleSelect,
                onConfirmDiscard = c::confirmDiscard,
                onNext = c::next,
                onPlayAgain = vm::newQuickTable,
                onMenu = vm::toTitle,
                onPause = { menu = true },
                onConsole = if (vm.settings.debugConsole.collectAsStateWithLifecycle().value) vm::toggleConsole else null,
            ),
            run = run,
        )
        if (menu) {
            TableMenu(
                speed = speed,
                onSpeed = vm.settings::setSpeed,
                onResume = { menu = false },
                onQuit = { menu = false; vm.toTitle() },
                inRun = run != null,
            )
        }
    }
}

@Composable
private fun TableMenu(speed: GameSpeed, onSpeed: (GameSpeed) -> Unit, onResume: () -> Unit, onQuit: () -> Unit, inRun: Boolean) {
    Box(Modifier.fillMaxSize().background(Color(0xB0000000)), contentAlignment = Alignment.Center) {
        PixelPanel(Modifier.widthIn(max = 380.dp).fillMaxWidth().padding(20.dp), title = "PAUSED", paddingUnits = 3) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyText("Game speed")
                ChoiceRow(GameSpeed.entries, speed, { it.label }, onSpeed)
                PixelButton("RESUME", onResume, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Call)
                PixelButton("QUIT TO MENU", onQuit, Modifier.fillMaxWidth(), variant = PixelButtonVariant.Deal, textStyle = PixelType.Small)
                BodyText(if (inRun) "Your tour is saved. Quitting mid-hand folds the hand." else "Your chips at this table are lost.", small = true)
            }
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

