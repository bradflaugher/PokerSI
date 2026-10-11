package com.bradflaugher.pokersi.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradflaugher.pokersi.ui.screenshot.DeviceSpec
import com.bradflaugher.pokersi.ui.screenshot.renderToPng
import com.bradflaugher.pokersi.ui.screenshot.screenshotDir
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every [ScreenshotScenes] scene to
 * `app/build/outputs/roborazzi/screens/<device>/<scene>.png`. Look at them
 * after any visible change; the store screenshots come from the same scenes.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensScreenshotTest {
    private fun render(device: DeviceSpec, names: Set<String>? = null) {
        ScreenshotScenes.all.filter { names == null || it.name in names }.forEach { scene ->
            renderToPng(File(screenshotDir, "screens/${device.name}/${scene.name}.png"), device) { scene.content() }
        }
    }

    @Test
    fun phone() = render(DeviceSpec.PHONE)

    @Test
    fun tablet7() = render(DeviceSpec.TABLET_7)

    @Test
    fun tablet10() = render(DeviceSpec.TABLET_10)

    @Test
    fun phoneLandscape() = render(DeviceSpec.PHONE.landscape(), setOf("title", "table_holdem", "table_showdown", "run_table", "tips", "stats"))
}
