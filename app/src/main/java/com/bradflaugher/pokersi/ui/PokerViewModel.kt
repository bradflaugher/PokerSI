package com.bradflaugher.pokersi.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
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
enum class Screen { Title, Picker, Table, Run, Models, HowTo, Settings }

/** Holds settings, the saved run, the game picker and the one [GameController]. */
class PokerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("pokersi", Context.MODE_PRIVATE)
    val settings = Settings(prefs)
    val runSaver = PrefsRunSaver(prefs)
    val daily = DailyRecord(prefs)
    val brains = AppServices.brains(app)
    val controller = GameController(viewModelScope, brains, Pacing(), settings.speed, runSaver = runSaver)

    /** The back stack; the last entry is showing. */
    val backStack = mutableStateListOf(Screen.Title)

    private val _picker = MutableStateFlow(QuickGameSetup(seed = Random.nextLong()))
    val picker: StateFlow<QuickGameSetup> = _picker.asStateFlow()

    init {
        // Today's daily tour: played, finished, best result (for the title screen).
        viewModelScope.launch {
            controller.run.collect { ui -> ui?.state?.let(daily::update) }
        }
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

    fun newRun() = openRun(RunState.new(Random.nextLong()))

    /** Today's daily tour: picks up a saved one in progress instead of dealing it again. */
    fun dailyRun() {
        val today = LocalDate.now()
        val saved = runSaver.load()
        if (saved != null && saved.daily && saved.seed == RunState.dailySeed(today) && saved.phase != RunPhase.GAME_OVER) {
            openRun(saved)
        } else {
            openRun(RunState.daily(today))
        }
    }

    fun continueRun() {
        val saved = runSaver.load() ?: return newRun()
        openRun(saved)
    }

    private fun openRun(state: RunState) {
        controller.startRun(state)
        backStack.removeAll { it == Screen.Run }
        go(Screen.Run)
    }

    fun resetRun() {
        runSaver.clear()
    }

    override fun onCleared() {
        controller.stop()
    }
}
