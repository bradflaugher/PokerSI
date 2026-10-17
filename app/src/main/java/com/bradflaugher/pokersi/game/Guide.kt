package com.bradflaugher.pokersi.game

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a new player has been shown, in [prefs]: the first-run tour (up on
 * first launch until finished or skipped) and each table [Coach] hint (once
 * ever). SETTINGS > REPLAY TOUR ([replay]) starts both over.
 */
class Guide(private val prefs: SharedPreferences) {
    private val _tour = MutableStateFlow(!prefs.getBoolean(KEY_TOUR_SEEN, false))
    private val _coachSeen = MutableStateFlow(readCoachSeen())
    private val _coaching = MutableStateFlow(true)

    /** The tour is showing. */
    val tour: StateFlow<Boolean> = _tour.asStateFlow()

    /** The table hints already shown. */
    val coachSeen: StateFlow<Set<Coach>> = _coachSeen.asStateFlow()

    /** Table hints may show (off for this launch after [skipForLaunch]). */
    val coaching: StateFlow<Boolean> = _coaching.asStateFlow()

    /** Opens the tour (it stays seen: closing it again is all it takes). */
    fun openTour() {
        _tour.value = true
    }

    /** The tour was finished or skipped: it won't open by itself again. */
    fun closeTour() {
        _tour.value = false
        prefs.edit { putBoolean(KEY_TOUR_SEEN, true) }
    }

    fun markCoachSeen(coach: Coach) {
        if (coach in _coachSeen.value) return
        _coachSeen.value += coach
        prefs.edit { putStringSet(KEY_COACH_SEEN, _coachSeen.value.mapTo(mutableSetOf()) { it.name }) }
    }

    /** SETTINGS > REPLAY TOUR: the tour opens now, and every table hint shows once more. */
    fun replay() {
        _coachSeen.value = emptySet()
        _coaching.value = true
        prefs.edit { remove(KEY_COACH_SEEN) }
        openTour()
    }

    /**
     * Screenshot and test launches (the SKIP_TIPS extra): no tour and no table
     * hints this launch, and nothing marked seen, so a real launch still shows them.
     */
    fun skipForLaunch() {
        _tour.value = false
        _coaching.value = false
    }

    private fun readCoachSeen(): Set<Coach> =
        prefs.getStringSet(KEY_COACH_SEEN, emptySet()).orEmpty().mapNotNullTo(mutableSetOf()) { name -> Coach.entries.firstOrNull { it.name == name } }

    private companion object {
        // The key of the old one-screen tips: a player who dismissed those isn't toured again.
        const val KEY_TOUR_SEEN = "tips_seen"
        const val KEY_COACH_SEEN = "coach_seen"
    }
}
