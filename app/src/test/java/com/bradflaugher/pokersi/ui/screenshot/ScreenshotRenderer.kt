package com.bradflaugher.pokersi.ui.screenshot

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.bradflaugher.pokersi.ui.pixel.LocalPixelRenderMode
import com.bradflaugher.pokersi.ui.pixel.PixelRenderMode
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File
import kotlin.math.ceil

/**
 * A screen to render for: pixel size and density. The presets match what
 * Google Play expects for phone, 7" and 10" tablet screenshots.
 */
data class DeviceSpec(val name: String, val widthPx: Int, val heightPx: Int, val dpi: Int) {
    val density: Float get() = dpi / 160f
    fun landscape(): DeviceSpec = copy(name = "$name-land", widthPx = heightPx, heightPx = widthPx)

    companion object {
        val PHONE = DeviceSpec("phone", 1080, 1920, 420)
        val TABLET_7 = DeviceSpec("tablet7", 1200, 1920, 320)
        val TABLET_10 = DeviceSpec("tablet10", 1600, 2560, 320)
        val ALL = listOf(PHONE, TABLET_7, TABLET_10)
    }
}

/** Where screenshots go unless a test says otherwise: `app/build/outputs/roborazzi/`. */
val screenshotDir: File = File(System.getProperty("pokersi.screenshotDir") ?: "build/outputs/roborazzi").also { it.mkdirs() }

/**
 * Renders [content] at exactly [widthPx] x [heightPx] device pixels and
 * [dpi] density on the JVM (Robolectric native graphics) and writes a PNG
 * to [file]. Must be called from a Robolectric test (see [ScreenshotTest]).
 *
 * Shaders and infinite animations: the pixel design system is switched to
 * [PixelRenderMode.Static] (no idle sway or blinking), so every
 * render is deterministic. Pass [renderMode] to override.
 */
@OptIn(ExperimentalTestApi::class)
fun renderToPng(
    file: File,
    widthPx: Int,
    heightPx: Int,
    dpi: Int,
    renderMode: PixelRenderMode = PixelRenderMode.Static,
    advanceMillis: Long = 0,
    content: @Composable () -> Unit,
) {
    val density = dpi / 160f
    // The window must be at least as large as the content; the capture is
    // cropped to the content node, so the PNG is exactly widthPx x heightPx.
    val wDp = ceil(widthPx / density).toInt() + 1
    val hDp = ceil(heightPx / density).toInt() + 1
    val orientation = if (widthPx > heightPx) "land" else "port"
    RuntimeEnvironment.setQualifiers("w${wDp}dp-h${hDp}dp-$orientation-${dpi}dpi")
    file.parentFile?.mkdirs()
    registerComponentActivity()
    runComposeUiTest {
        if (advanceMillis > 0) mainClock.autoAdvance = false
        setContent {
            val d = Density(density, 1f)
            CompositionLocalProvider(LocalDensity provides d, LocalPixelRenderMode provides renderMode) {
                Box(
                    Modifier
                        .requiredSize(with(d) { widthPx.toDp() }, with(d) { heightPx.toDp() })
                        .testTag(SHOT_TAG),
                ) { content() }
            }
        }
        if (advanceMillis > 0) mainClock.advanceTimeBy(advanceMillis)
        waitForIdle()
        onNodeWithTag(SHOT_TAG).captureRoboImage(file, RoborazziOptions())
    }
}

/** [renderToPng] for a [DeviceSpec] preset. */
fun renderToPng(
    file: File,
    device: DeviceSpec,
    renderMode: PixelRenderMode = PixelRenderMode.Static,
    advanceMillis: Long = 0,
    content: @Composable () -> Unit,
) = renderToPng(file, device.widthPx, device.heightPx, device.dpi, renderMode, advanceMillis, content)

/**
 * runComposeUiTest hosts content in a ComponentActivity; register it with
 * Robolectric's package manager so the app manifest doesn't need the
 * ui-test-manifest artifact.
 */
internal fun registerComponentActivity() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val info = ActivityInfo().apply {
        name = ComponentActivity::class.java.name
        packageName = app.packageName
    }
    shadowOf(app.packageManager).addOrUpdateActivity(info)
}

private const val SHOT_TAG = "pokersi-screenshot-root"
