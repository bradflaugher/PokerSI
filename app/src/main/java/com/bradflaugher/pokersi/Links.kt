package com.bradflaugher.pokersi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import java.net.URLEncoder

/**
 * The few ways PokerSI reaches outside itself besides model downloads. None of
 * them is a network call: each hands an intent to another app (the share sheet,
 * the Play Store, the browser) and that app does the rest. Plain Kotlin up top
 * so the unit tests can check the strings.
 */
object Links {
    /** The release application ID, not the debug build's, so a shared link always works. */
    const val APPLICATION_ID = "com.bradflaugher.pokersi"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=$APPLICATION_ID"

    /** The Play Store app's own link to the listing, where the rating stars are. */
    const val MARKET_URL = "market://details?id=$APPLICATION_ID"
    const val PLAY_STORE_PACKAGE = "com.android.vending"
    const val SOURCE_URL = "https://github.com/bradflaugher/PokerSI"
    const val ISSUES_URL = "$SOURCE_URL/issues/new"

    /** `docs/privacy.html`, published by GitHub Pages. */
    const val PRIVACY_POLICY_URL = "https://bradflaugher.github.io/PokerSI/privacy.html"

    const val SHARE_TEXT = "PokerSI: retro video poker against AI opponents that run on your phone. No ads, no accounts. $PLAY_URL"

    /** A new GitHub issue with the details a bug report needs already filled in. */
    fun feedbackUrl(appVersion: String, android: String, device: String): String {
        val body = """
            |**What happened, or what would you like?**
            |
            |
            |**Game and model (if it's about a hand)**
            |
            |
            |---
            |PokerSI $appVersion · Android $android · $device
        """.trimMargin()
        return "$ISSUES_URL?body=" + URLEncoder.encode(body, Charsets.UTF_8).replace("+", "%20")
    }
}

/** Opens the system share sheet with a link to PokerSI on Google Play. False if nothing can share. */
fun Context.shareApp(): Boolean {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "PokerSI")
        .putExtra(Intent.EXTRA_TEXT, Links.SHARE_TEXT)
    // The chooser itself always resolves, even with nothing to share to (some TVs and
    // kiosks), so ask about the send first. The manifest's <queries> makes the targets visible.
    if (packageManager.queryIntentActivities(send, 0).isEmpty()) return false
    return start(Intent.createChooser(send, "Share PokerSI"))
}

/**
 * Opens PokerSI's Google Play listing so the player can rate it: the Play Store app if
 * it's there, otherwise any app for market:// links, otherwise the web listing. A plain
 * link, only ever opened from SETTINGS: no review API, no prompt, no timing.
 */
fun Context.rateApp(): Boolean {
    val market = Intent(Intent.ACTION_VIEW, Links.MARKET_URL.toUri())
    return start(Intent(market).setPackage(Links.PLAY_STORE_PACKAGE)) || start(market) || openWeb(Links.PLAY_URL)
}

/** Opens a new GitHub issue in the browser. False if there is no browser (some TVs and kiosks). */
fun Context.sendFeedback(): Boolean =
    openWeb(Links.feedbackUrl(BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, "${Build.MANUFACTURER} ${Build.MODEL}"))

fun Context.openWeb(url: String): Boolean = start(Intent(Intent.ACTION_VIEW, url.toUri()))

private fun Context.start(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
