package com.bradflaugher.pokersi.game

import android.content.SharedPreferences
import androidx.core.content.edit
import com.bradflaugher.pokersi.run.RunPhase
import com.bradflaugher.pokersi.run.RunState
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Player preferences, persisted in [prefs] and observable as StateFlows. */
class Settings(private val prefs: SharedPreferences) {
    private val _sound = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    private val _haptics = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    private val _crt = MutableStateFlow(prefs.getBoolean(KEY_CRT, true))
    private val _cardSway = MutableStateFlow(prefs.getBoolean(KEY_SWAY, true))
    private val _debugConsole = MutableStateFlow(prefs.getBoolean(KEY_DEBUG_CONSOLE, false))
    private val _fastFold = MutableStateFlow(prefs.getBoolean(KEY_FAST_FOLD, true))
    private val _speed = MutableStateFlow(
        prefs.getString(KEY_SPEED, null)?.let { s -> GameSpeed.entries.firstOrNull { it.name == s } } ?: GameSpeed.NORMAL,
    )

    val sound: StateFlow<Boolean> = _sound.asStateFlow()
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()
    val crt: StateFlow<Boolean> = _crt.asStateFlow()
    val cardSway: StateFlow<Boolean> = _cardSway.asStateFlow()

    /** SETTINGS > DEBUG CONSOLE: the "~" console of AI prompts and replies. Off by default. */
    val debugConsole: StateFlow<Boolean> = _debugConsole.asStateFlow()
    val speed: StateFlow<GameSpeed> = _speed.asStateFlow()

    /** SETTINGS > FAST FOLD: once you fold, the rest of the hand plays out at once. On by default. */
    val fastFold: StateFlow<Boolean> = _fastFold.asStateFlow()

    fun setSound(on: Boolean) = put(KEY_SOUND, on, _sound)
    fun setHaptics(on: Boolean) = put(KEY_HAPTICS, on, _haptics)
    fun setCrt(on: Boolean) = put(KEY_CRT, on, _crt)
    fun setCardSway(on: Boolean) = put(KEY_SWAY, on, _cardSway)
    fun setDebugConsole(on: Boolean) = put(KEY_DEBUG_CONSOLE, on, _debugConsole)
    fun setFastFold(on: Boolean) = put(KEY_FAST_FOLD, on, _fastFold)

    fun setSpeed(speed: GameSpeed) {
        _speed.value = speed
        prefs.edit { putString(KEY_SPEED, speed.name) }
    }

    private fun put(key: String, value: Boolean, flow: MutableStateFlow<Boolean>) {
        flow.value = value
        prefs.edit { putBoolean(key, value) }
    }

    private companion object {
        const val KEY_SOUND = "sound"
        const val KEY_HAPTICS = "haptics"
        const val KEY_CRT = "crt"
        const val KEY_SWAY = "card_sway"
        const val KEY_DEBUG_CONSOLE = "debug_console"
        const val KEY_SPEED = "speed"
        const val KEY_FAST_FOLD = "fast_fold"
    }
}

/**
 * Keeps the run in SharedPreferences as [RunState.toJson]; [saved] tells the title
 * screen there's one to CONTINUE. A save from an older format (the Joker-shop
 * runs, the tours with rule-twist tables) is dropped instead of crashing.
 */
class PrefsRunSaver(private val prefs: SharedPreferences) : RunSaver {
    init {
        val old = OLD_KEYS.filter(prefs::contains)
        if (old.isNotEmpty()) prefs.edit { old.forEach(::remove) }
    }

    private val _saved = MutableStateFlow(load() != null)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    override fun save(state: RunState) {
        prefs.edit { putString(key(state.daily), state.toJson()) }
        if (!state.daily) _saved.value = true
    }

    override fun load(daily: Boolean): RunState? = prefs.getString(key(daily), null)?.let { json ->
        try {
            RunState.fromJson(json)
        } catch (_: Exception) {
            prefs.edit { remove(key(daily)) }
            null
        }
    }

    override fun clear(daily: Boolean) {
        prefs.edit { remove(key(daily)) }
        if (!daily) _saved.value = false
    }

    private fun key(daily: Boolean) = if (daily) KEY_DAILY else KEY

    private companion object {
        const val KEY = "run_v3"
        const val KEY_DAILY = "run_v3_daily"
        val OLD_KEYS = listOf("run_v1", "run_v2", "run_v2_daily")
    }
}

/** Today's daily tour as far as the title screen cares: played yet, finished, best result. */
data class DailyResult(val played: Boolean = false, val finished: Boolean = false, val bestTables: Int = 0) {
    /** The title screen's button label. */
    val label: String get() = when {
        finished -> "DAILY: BEST $bestTables"
        else -> "DAILY TOUR"
    }
}

/** Remembers today's daily tour (date, finished, most tables cleared) in [prefs]. */
class DailyRecord(private val prefs: SharedPreferences, private val today: () -> LocalDate = LocalDate::now) {
    private val _result = MutableStateFlow(read())
    val result: StateFlow<DailyResult> = _result.asStateFlow()

    /** Re-reads for the current date (a new day resets it). */
    fun refresh() {
        _result.value = read()
    }

    /** Records [state] if it is today's daily tour. */
    fun update(state: RunState) {
        val date = today()
        if (!state.daily || state.seed != RunState.dailySeed(date)) return
        val current = read()
        val next = DailyResult(
            played = true,
            finished = current.finished || state.phase == RunPhase.GAME_OVER,
            bestTables = maxOf(current.bestTables, state.stats.tablesCleared),
        )
        if (next == current) return
        prefs.edit {
            putLong(KEY_DATE, date.toEpochDay())
            putBoolean(KEY_FINISHED, next.finished)
            putInt(KEY_BEST, next.bestTables)
        }
        _result.value = next
    }

    private fun read(): DailyResult {
        if (prefs.getLong(KEY_DATE, Long.MIN_VALUE) != today().toEpochDay()) return DailyResult()
        return DailyResult(played = true, finished = prefs.getBoolean(KEY_FINISHED, false), bestTables = prefs.getInt(KEY_BEST, 0))
    }

    private companion object {
        const val KEY_DATE = "daily_date"
        const val KEY_FINISHED = "daily_finished"
        const val KEY_BEST = "daily_best"
    }
}
