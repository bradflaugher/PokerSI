package com.bradflaugher.pokersi.game

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The tour and the table hints remember what was shown, across launches. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class GuideTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("guide-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun theTourShowsOnFirstLaunchUntilClosed() {
        val first = Guide(prefs)
        assertTrue(first.tour.value)
        first.closeTour()
        assertFalse(first.tour.value)
        assertFalse(Guide(prefs).tour.value)
    }

    @Test
    fun playersWhoDismissedTheOldTipsAreNotToured() {
        prefs.edit().putBoolean("tips_seen", true).commit()
        assertFalse(Guide(prefs).tour.value)
    }

    @Test
    fun hintsAreSeenOnceAcrossLaunches() {
        val guide = Guide(prefs)
        assertTrue(guide.coachSeen.value.isEmpty())
        guide.markCoachSeen(Coach.YourTurn)
        guide.markCoachSeen(Coach.Hold)
        assertEquals(setOf(Coach.YourTurn, Coach.Hold), Guide(prefs).coachSeen.value)
    }

    @Test
    fun unknownHintNamesFromAnOlderVersionAreIgnored() {
        prefs.edit().putStringSet("coach_seen", setOf("YourTurn", "SomethingRetired")).commit()
        assertEquals(setOf(Coach.YourTurn), Guide(prefs).coachSeen.value)
    }

    @Test
    fun replayOpensTheTourAndResetsTheHints() {
        val guide = Guide(prefs)
        guide.closeTour()
        guide.markCoachSeen(Coach.Pause)
        guide.replay()
        assertTrue(guide.tour.value)
        assertTrue(guide.coachSeen.value.isEmpty())
        assertTrue(Guide(prefs).coachSeen.value.isEmpty())
        // Replaying doesn't make the tour pop up by itself on every launch again.
        assertFalse(Guide(prefs).tour.value)
    }

    @Test
    fun skippingForALaunchHidesEverythingButRemembersNothing() {
        val guide = Guide(prefs)
        guide.skipForLaunch()
        assertFalse(guide.tour.value)
        assertFalse(guide.coaching.value)
        val next = Guide(prefs)
        assertTrue(next.tour.value)
        assertTrue(next.coaching.value)
    }
}
