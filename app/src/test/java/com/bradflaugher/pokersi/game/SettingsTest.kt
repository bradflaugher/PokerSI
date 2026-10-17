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

/** Defaults and persistence of the SETTINGS screen's choices. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SettingsTest {
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("settings-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun defaults() {
        val s = Settings(prefs)
        assertTrue("sound on", s.sound.value)
        assertEquals("full volume, as before there was a choice", SoundVolume.HIGH, s.volume.value)
        assertTrue("exit asks first", s.confirmExit.value)
        assertFalse(s.largerText.value)
        assertFalse(s.highContrast.value)
        assertFalse("table talk is opt-in", s.tableTalk.value)
        assertTrue(s.fastFold.value)
        assertFalse(s.debugConsole.value)
        assertEquals(GameSpeed.NORMAL, s.speed.value)
    }

    @Test
    fun choicesPersist() {
        Settings(prefs).apply {
            setVolume(SoundVolume.LOW)
            setConfirmExit(false)
            setLargerText(true)
            setHighContrast(true)
        }
        val again = Settings(prefs)
        assertEquals(SoundVolume.LOW, again.volume.value)
        assertFalse(again.confirmExit.value)
        assertTrue(again.largerText.value)
        assertTrue(again.highContrast.value)
    }

    @Test
    fun aSetterUpdatesItsFlowAtOnce() {
        val s = Settings(prefs)
        s.setVolume(SoundVolume.MED)
        s.setHighContrast(true)
        assertEquals(SoundVolume.MED, s.volume.value)
        assertTrue(s.highContrast.value)
    }

    @Test
    fun anUnknownVolumeFallsBackToHigh() {
        prefs.edit().putString("volume", "ELEVEN").commit()
        assertEquals(SoundVolume.HIGH, Settings(prefs).volume.value)
    }

    @Test
    fun volumesRiseToFull() {
        val gains = SoundVolume.entries.map { it.gain }
        assertEquals(gains.sorted(), gains)
        assertEquals(1f, SoundVolume.HIGH.gain, 0f)
        assertTrue(gains.all { it > 0f })
    }
}
