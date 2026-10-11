package com.bradflaugher.pokersi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class LinksTest {
    @Test
    fun linksPointAtTheReleaseAppAndTheRepo() {
        assertEquals("https://play.google.com/store/apps/details?id=com.bradflaugher.pokersi", Links.PLAY_URL)
        assertEquals("market://details?id=com.bradflaugher.pokersi", Links.MARKET_URL)
        assertEquals("https://github.com/bradflaugher/PokerSI/issues/new", Links.ISSUES_URL)
        assertTrue(Links.SHARE_TEXT.endsWith(Links.PLAY_URL))
    }

    @Test
    fun privacyPolicyIsTheBradflaugherComPage() {
        assertEquals("https://bradflaugher.com/privacy/pokersi/", Links.PRIVACY_POLICY_URL)
    }

    @Test
    fun feedbackUrlCarriesTheVersionAndDevice() {
        val url = Links.feedbackUrl("2026.10.10.1", "16", "Google Pixel 9")
        assertTrue(url.startsWith(Links.ISSUES_URL + "?body="))
        assertFalse("spaces are encoded", url.contains(' '))
        val body = URLDecoder.decode(url.substringAfter("?body="), Charsets.UTF_8)
        assertTrue(body.contains("PokerSI 2026.10.10.1 · Android 16 · Google Pixel 9"))
    }
}
