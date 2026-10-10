package com.bradflaugher.pokersi.game

import android.content.SharedPreferences
import androidx.core.content.edit
import com.bradflaugher.pokersi.run.RunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Player preferences, persisted in [prefs] and observable as StateFlows. */
class Settings(private val prefs: SharedPreferences) {
    private val _sound = MutableStateFlow(prefs.getBoolean(KEY_SOUND, true))
    private val _haptics = MutableStateFlow(prefs.getBoolean(KEY_HAPTICS, true))
    private val _crt = MutableStateFlow(prefs.getBoolean(KEY_CRT, true))
    private val _cardSway = MutableStateFlow(prefs.getBoolean(KEY_SWAY, true))
    private val _speed = MutableStateFlow(
        prefs.getString(KEY_SPEED, null)?.let { s -> GameSpeed.entries.firstOrNull { it.name == s } } ?: GameSpeed.NORMAL,
    )

    val sound: StateFlow<Boolean> = _sound.asStateFlow()
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()
    val crt: StateFlow<Boolean> = _crt.asStateFlow()
    val cardSway: StateFlow<Boolean> = _cardSway.asStateFlow()
    val speed: StateFlow<GameSpeed> = _speed.asStateFlow()

    fun setSound(on: Boolean) = put(KEY_SOUND, on, _sound)
    fun setHaptics(on: Boolean) = put(KEY_HAPTICS, on, _haptics)
    fun setCrt(on: Boolean) = put(KEY_CRT, on, _crt)
    fun setCardSway(on: Boolean) = put(KEY_SWAY, on, _cardSway)

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
        const val KEY_SPEED = "speed"
    }
}

/** Keeps the run in SharedPreferences as [RunState.toJson]; [saved] tells the title screen there's one to CONTINUE. */
class PrefsRunSaver(private val prefs: SharedPreferences) : RunSaver {
    private val _saved = MutableStateFlow(load() != null)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    override fun save(state: RunState) {
        prefs.edit { putString(KEY, state.toJson()) }
        _saved.value = true
    }

    override fun load(): RunState? = prefs.getString(KEY, null)?.let { json ->
        try {
            RunState.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    override fun clear() {
        prefs.edit { remove(KEY) }
        _saved.value = false
    }

    private companion object {
        const val KEY = "run_v1"
    }
}
