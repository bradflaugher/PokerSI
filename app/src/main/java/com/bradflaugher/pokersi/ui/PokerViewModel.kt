package com.bradflaugher.pokersi.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bradflaugher.pokersi.AppServices
import com.bradflaugher.pokersi.engine.GameVariant
import com.bradflaugher.pokersi.game.DailyRecord
import com.bradflaugher.pokersi.game.GameController
import com.bradflaugher.pokersi.game.Pacing
import com.bradflaugher.pokersi.game.PrefsRunSaver
import com.bradflaugher.pokersi.game.QuickGameSetup
import com.bradflaugher.pokersi.game.Settings
import com.bradflaugher.pokersi.game.Stats
import com.bradflaugher.pokersi.run.RunPhase
import com.bradflaugher.pokersi.run.RunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/** The app's screens. */
enum class Screen { Title, Picker, Table, Run, Models, HowTo, Settings, Stats }

/** Holds settings, the saved run, the game picker and the one [GameController]. */
class PokerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("pokersi", Context.MODE_PRIVATE)
    val settings = Settings(prefs)
    val runSaver = PrefsRunSaver(prefs)
    val daily = DailyRecord(prefs)
    val stats = Stats(prefs)
    val brains = AppServices.brains(app)
    val controller = GameController(viewModelScope, brains, Pacing(), settings.speed, runSaver = runSaver, recorder = stats)

    private val _tips = MutableStateFlow(!prefs.getBoolean(KEY_TIPS_SEEN, false))

    /** The first-run tips are showing over the title screen. */
    val tips: StateFlow<Boolean> = _tips.asStateFlow()

    /** The back stack; the last entry is showing. */
    val backStack = mutableStateListOf(Screen.Title)

    private val _picker = MutableStateFlow(QuickGameSetup(seed = Random.nextLong()))
    val picker: StateFlow<QuickGameSetup> = _picker.asStateFlow()

    init {
        // Today's daily tour: played, finished, best result (for the title screen).
        viewModelScope.launch {
            controller.run.collect { ui ->
                ui?.state?.let {
                    daily.update(it)
                    stats.runChanged(it)
                }
            }
        }
    }

    // ---------------------------------------------------------------- tips

    /** SETTINGS > SHOW TIPS: back to the title screen, tips up. */
    fun showTips() {
        toTitle()
        _tips.value = true
    }

    fun dismissTips() {
        _tips.value = false
        prefs.edit { putBoolean(KEY_TIPS_SEEN, true) }
    }

    /** Screenshot and test launches start without the tips, and still see them next launch. */
    fun skipTips() {
        _tips.value = false
    }

    fun go(screen: Screen) {
        if (screen == Screen.Title) daily.refresh()
        backStack += screen
    }

    /** Pops one screen; false at the title (let the system handle Back). */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        val leaving = backStack.removeAt(backStack.lastIndex)
        if (leaving == Screen.Table || leaving == Screen.Run) controller.stop()
        return true
    }

    fun toTitle() {
        controller.stop()
        daily.refresh()
        backStack.clear()
        backStack += Screen.Title
    }

    // ---------------------------------------------------------------- quick game

    fun setVariant(v: GameVariant) = _picker.update { it.copy(variant = v) }
    fun setOpponents(n: Int) = _picker.update { it.copy(opponentCount = n.coerceIn(1, 5)) }
    fun setStack(n: Int) = _picker.update { it.copy(stack = n) }
    fun shuffleOpponents() = _picker.update { it.copy(seed = Random.nextLong()) }

    fun deal() {
        controller.startQuick(_picker.value.config().copy(seed = Random.nextLong()))
        go(Screen.Table)
    }

    fun newQuickTable() = controller.playAgain()

    // ---------------------------------------------------------------- run

    fun newRun() = openRun(RunState.new(Random.nextLong()), started = true)

    /** Today's daily tour: picks up a saved one in progress instead of dealing it again. */
    fun dailyRun() {
        val today = LocalDate.now()
        val saved = runSaver.load(daily = true)
        if (saved != null && saved.seed == RunState.dailySeed(today) && saved.phase != RunPhase.GAME_OVER) {
            openRun(saved)
        } else {
            openRun(RunState.daily(today), started = true)
        }
    }

    fun continueRun() {
        val saved = runSaver.load() ?: return newRun()
        openRun(saved)
    }

    private fun openRun(state: RunState, started: Boolean = false) {
        if (started) stats.tourStarted()
        controller.startRun(state)
        backStack.removeAll { it == Screen.Run }
        go(Screen.Run)
    }

    fun resetRun() {
        runSaver.clear()
        runSaver.clear(daily = true)
    }

    override fun onCleared() {
        controller.stop()
    }

    private companion object {
        const val KEY_TIPS_SEEN = "tips_seen"
    }
}
