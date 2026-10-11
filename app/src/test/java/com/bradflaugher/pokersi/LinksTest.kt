package com.bradflaugher.pokersi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
    fun privacyPolicyIsThePublishedDocsPage() {
        assertEquals("https://bradflaugher.github.io/PokerSI/privacy.html", Links.PRIVACY_POLICY_URL)
        val app = File(".").canonicalFile.let { if (File(it, "src/main").isDirectory) it else File(it, "app") }
        assertTrue("docs/privacy.html must exist", File(app.parentFile, "docs/privacy.html").isFile)
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
