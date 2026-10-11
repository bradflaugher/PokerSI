package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradflaugher.pokersi.R
import com.bradflaugher.pokersi.ui.screenshot.DeviceSpec
import com.bradflaugher.pokersi.ui.screenshot.renderToPng
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The raw Google Play and README captures: every [ScreenshotScenes] scene
 * at each Play screenshot class (exact 9:16, at a density that gives that
 * class its real window size in dp), plus the feature graphic and the
 * Play icon. Writes `<repo>/build/screenshots/<class>/`.
 *
 * Skipped unless Gradle is run with `-Pstore` (or POKERSI_STORE_SHOTS is
 * set), so a plain `test` stays fast. tools/screenshots/shoot.py runs it,
 * then caption.py turns the captures into the store images.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreScreenshotTest {
    private lateinit var out: File

    @Before
    fun gate() {
        val dir = System.getProperty("pokersi.storeShotsDir")
        assumeTrue("store screenshots render only with -Pstore", dir != null)
        out = File(dir!!)
    }

    private fun scenes(device: DeviceSpec) = ScreenshotScenes.all.forEach { scene ->
        renderToPng(File(out, "${device.name}/${scene.name}.png"), device) { scene.content() }
    }

    @Test
    fun phone() = scenes(PHONE)

    @Test
    fun tab7() = scenes(TAB7)

    @Test
    fun tab10() = scenes(TAB10)

    /** The whole feature graphic (see [FeatureGraphic]); caption.py only optimizes it. */
    @Test
    fun featureGraphic() = renderToPng(File(out, "feature/feature.png"), FEATURE_W, FEATURE_H, FEATURE_DPI) { FeatureGraphic() }

    /**
     * The Play icon, full bleed: the adaptive icon's layers drawn 720px wide
     * on the 512px canvas, so each of the art's 72 pixels is exactly 10px
     * (the middle 51 of them show, a hair more than a launcher's 48).
     */
    @Test
    fun playIcon() = renderToPng(File(out, "icon/icon512.png"), 512, 512, 160) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val layer = Modifier.requiredSize(720.dp)
            Image(painterResource(R.drawable.ic_launcher_background), null, layer)
            Image(painterResource(R.drawable.ic_launcher_foreground), null, layer)
        }
    }

    companion object {
        // Exact 9:16. Densities give each class its real window: ~411dp
        // phone, 612dp 7-inch, 810dp 10-inch (tablets get tablet layouts).
        val PHONE = DeviceSpec("phone", 1080, 1920, 420)
        val TAB7 = DeviceSpec("tab7", 1224, 2176, 320)
        val TAB10 = DeviceSpec("tab10", 1620, 2880, 320)

    }
}
