package com.bradflaugher.pokersi.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradflaugher.pokersi.R
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.ui.pixel.MarqueeFrame
import com.bradflaugher.pokersi.ui.pixel.PixelCard
import com.bradflaugher.pokersi.ui.pixel.PixelText
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.pixel.PixelType
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
 * class its real window size in dp), plus the pieces of the feature
 * graphic and the Play icon. Writes `<repo>/build/screenshots/<class>/`.
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

    /** The marquee logo for the feature graphic. */
    @Test
    fun featureLogo() = renderToPng(File(out, "feature/logo.png"), 440, 300, 240) {
        PixelTheme {
            val p = PixelTheme.palette
            MarqueeFrame(Modifier.fillMaxSize()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PixelText("POKER", style = PixelType.Banner, color = p.yellow, shadowUnits = 2, fontSize = 40.sp)
                    PixelText("S.I.", style = PixelType.Banner, color = p.hotPink, shadowUnits = 2, fontSize = 40.sp)
                }
            }
        }
    }

    /**
     * A fanned royal flush with the winning-card glow, on [KEY], which
     * caption.py keys out (Robolectric captures are opaque).
     */
    @Test
    fun featureCards() = renderToPng(File(out, "feature/cards.png"), 500, 340, 320) {
        PixelTheme {
            Box(Modifier.fillMaxSize().background(KEY), contentAlignment = Alignment.Center) {
                Card.parseList("Th Jh Qh Kh Ah").forEachIndexed { i, c ->
                    val k = i - 2
                    PixelCard(
                        c, faceUp = true, highlighted = true, size = 68.dp,
                        modifier = Modifier.offset(x = 37.dp * k, y = (k * k * 4).dp).rotate(k * 8f),
                    )
                }
            }
        }
    }

    /** The Play icon: the adaptive icon's visible 72dp of its 108dp layers, full bleed. */
    @Test
    fun playIcon() = renderToPng(File(out, "icon/icon512.png"), 512, 512, 160) { LauncherLayers(Modifier.fillMaxSize()) }

    @androidx.compose.runtime.Composable
    private fun LauncherLayers(modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            // The box is the 72dp viewport; the layers are 108/72 of it.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val layer = Modifier.requiredSize(maxWidth * 1.5f)
                Image(painterResource(R.drawable.ic_launcher_background), null, layer)
                Image(painterResource(R.drawable.ic_launcher_foreground), null, layer)
            }
        }
    }

    companion object {
        // Exact 9:16. Densities give each class its real window: ~411dp
        // phone, 612dp 7-inch, 810dp 10-inch (tablets get tablet layouts).
        val PHONE = DeviceSpec("phone", 1080, 1920, 420)
        val TAB7 = DeviceSpec("tab7", 1224, 2176, 320)
        val TAB10 = DeviceSpec("tab10", 1620, 2880, 320)

        /** The feature-graphic key color; caption.py's FEATURE_KEY. */
        val KEY = Color(0xFF03231F)
    }
}
