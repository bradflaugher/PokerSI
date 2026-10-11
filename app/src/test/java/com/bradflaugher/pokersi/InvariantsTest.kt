package com.bradflaugher.pokersi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Guards the project rules in AGENTS.md that a compiler can't: a pure engine,
 * passive rating and feedback (no review API, no email), a locked-down manifest,
 * and no deck-builder leftovers in what the player sees. Unit tests run with the
 * app module as the working directory.
 */
class InvariantsTest {
    private val app = File(".").canonicalFile.let { if (File(it, "src/main").isDirectory) it else File(it, "app") }
    private val repo = app.parentFile
    private val main = File(app, "src/main")
    private val sources = main.walk()
        .filter { it.isFile && it.extension in setOf("kt", "xml") && !it.path.contains("${File.separator}cpp${File.separator}") }
        .toList()
    private val manifest = File(main, "AndroidManifest.xml").readText()

    @Test
    fun engineAndRunHaveNoAndroidImports() {
        for (dir in listOf("engine", "run")) {
            val files = File(main, "java/com/bradflaugher/pokersi/$dir").walk().filter { it.extension == "kt" }.toList()
            assertTrue("$dir sources not found", files.isNotEmpty())
            for (file in files) {
                val offending = file.readLines().filter { it.startsWith("import android") || it.startsWith("import androidx") }
                assertTrue("${file.name} imports Android: $offending", offending.isEmpty())
            }
        }
    }

    @Test
    fun noReviewPromptsOrEmail() {
        // By design: rating is a plain SETTINGS link (Links.rateApp), never the in-app review
        // API or a prompt, and feedback goes to GitHub issues, not an inbox.
        val build = File(app, "build.gradle.kts").readText() + File(repo, "gradle/libs.versions.toml").readText()
        assertFalse("no Play review library", build.contains("com.google.android.play"))
        assertTrue("sources not found", sources.size > 20)
        val email = Regex("""[\w.+-]+@[\w-]+\.[a-z]{2,}""")
        for (file in sources) {
            val text = file.readText()
            assertFalse("${file.name}: no review API", text.contains("ReviewManager") || text.contains("requestReviewFlow"))
            if (file.name != "Links.kt") assertFalse("${file.name}: store links live in Links.kt", text.contains("market://"))
            assertFalse("${file.name}: no email addresses", email.containsMatchIn(text) || text.contains("mailto:"))
        }
    }

    @Test
    fun onlyTheLauncherActivityIsExported() {
        val exported = Regex("""<(activity|service|receiver|provider)\b[^>]*?android:name="([^"]+)"[^>]*?android:exported="true"""")
            .findAll(manifest).map { it.groupValues[2] }.toList()
        assertEquals(listOf(".MainActivity"), exported)
        assertTrue("MainActivity is the launcher", manifest.contains("android.intent.category.LAUNCHER"))
    }

    @Test
    fun onlyTheExpectedPermissions() {
        val own = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
        )
        assertEquals(own, permissions(manifest))
        assertTrue("backups must stay off", manifest.contains("android:allowBackup=\"false\""))

        // The merged manifest adds WorkManager's two and AndroidX core's own signature
        // permission for unexported dynamic receivers; nothing else.
        val merged = File(app, "build/intermediates/merged_manifests/debug").walk().firstOrNull { it.name == "AndroidManifest.xml" }
        assumeTrue("merged manifest not built", merged != null)
        val allowed = own + setOf("android.permission.WAKE_LOCK", "android.permission.RECEIVE_BOOT_COMPLETED")
        val extra = permissions(merged!!.readText()).filterNot { it in allowed || it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
        assertTrue("unexpected permissions: $extra", extra.isEmpty())
    }

    @Test
    fun shareCanSeeTextTargets() {
        val queries = Regex("<queries>(.*?)</queries>", RegexOption.DOT_MATCHES_ALL).find(manifest)?.groupValues?.get(1).orEmpty()
        assertTrue(queries.contains("android.intent.action.SEND") && queries.contains("text/plain"))
    }

    @Test
    fun noDeckBuilderLeftovers() {
        // PokerSI is poker on a green table: no Jokers, no shop, no other game's name.
        // The one exception is the comment on dropping old (Joker shop) saves.
        val word = Regex("joker|balatro", RegexOption.IGNORE_CASE)
        for (file in sources) {
            val hits = file.readLines().filter { word.containsMatchIn(it) }
            val allowed = file.name == "Settings.kt" && hits.all { it.trimStart().startsWith("*") }
            assertTrue("${file.name}: $hits", hits.isEmpty() || allowed)
        }
    }

    @Test
    fun noVersionChecks() {
        val checks = main.walk().filter { it.extension == "kt" && it.readText().contains("SDK_INT") }.map { it.name }.toList()
        assertTrue("no Build.VERSION.SDK_INT checks: $checks", checks.isEmpty())
    }

    @Test
    fun skipTipsExtraMatchesTheDocs() {
        assertEquals("com.bradflaugher.pokersi.SKIP_TIPS", MainActivity.EXTRA_SKIP_TIPS)
        assertTrue(File(repo, "AGENTS.md").readText().contains(MainActivity.EXTRA_SKIP_TIPS))
    }

    private fun permissions(xml: String): Set<String> =
        Regex("""<uses-permission\s+android:name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.toSet()
}
