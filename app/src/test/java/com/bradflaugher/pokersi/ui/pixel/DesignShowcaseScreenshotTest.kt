package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradflaugher.pokersi.engine.Card
import com.bradflaugher.pokersi.engine.Rank
import com.bradflaugher.pokersi.engine.Suit
import com.bradflaugher.pokersi.ui.screenshot.DeviceSpec
import com.bradflaugher.pokersi.ui.screenshot.renderToPng
import com.bradflaugher.pokersi.ui.screenshot.screenshotDir
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the design system to PNGs under app/build/outputs/roborazzi/
 * (look at them after any visual change).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignShowcaseScreenshotTest {
    private fun out(name: String) = File(screenshotDir, "design/$name.png")

    @Test
    fun showcasePhone() = renderToPng(out("showcase_phone"), DeviceSpec.PHONE) { DesignShowcase() }

    @Test
    fun showcaseFull() = renderToPng(out("showcase_full"), 1080, 7600, 420) { DesignShowcase(scrollable = false) }

    @Test
    fun showcaseTablets() {
        renderToPng(out("showcase_tablet7"), DeviceSpec.TABLET_7) { DesignShowcase() }
        renderToPng(out("showcase_tablet10"), DeviceSpec.TABLET_10) { DesignShowcase() }
    }

    @Test
    fun cardSheet() = renderToPng(out("cards"), 1080, 1760, 420) {
        PixelTheme {
            Column(
                Modifier.fillMaxSize().background(PixelPalette.Default.felt).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Suit.entries.forEach { s ->
                    Rank.entries.chunked(7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { r -> PixelCard(Card(r, s), size = 50.dp) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelCard(null, faceUp = false, size = 50.dp)
                    PixelCard(Card.parse("As"), held = true, size = 50.dp)
                    PixelCard(Card.parse("Ah"), highlighted = true, size = 50.dp)
                    PixelCard(Card.parse("Kd"), dimmed = true, size = 50.dp)
                }
            }
        }
    }

    @Test
    fun cardCloseUp() = renderToPng(out("cards_closeup"), 1080, 1340, 420) {
        PixelTheme {
            Column(
                Modifier.fillMaxSize().background(PixelPalette.Default.feltDark).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf(listOf("Ks", "Qh", "Jd", "As"), listOf("Kh", "Qc", "Js", null)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { if (it == null) PixelCard(null, faceUp = false, size = 84.dp) else PixelCard(Card.parse(it), size = 84.dp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Kd", "Qs", "Jc", "Ah", "Qd", "Kc", "Jh").forEach { PixelCard(Card.parse(it), size = 46.dp) }
                }
            }
        }
    }

    @Test
    fun avatarSheet() = renderToPng(out("avatars"), 1080, 1400, 420) {
        PixelTheme {
            Column(
                Modifier.fillMaxSize().background(PixelPalette.Default.cabinetDeep).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (0 until 6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until 6).forEach { col -> PixelAvatar(seed = (row * 6 + col).toLong() * 31 + 100, size = 62.dp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Mood.entries.take(6).forEach { PixelAvatar(seed = 77L, mood = it, size = 62.dp) }
                }
            }
        }
    }

    @Test
    fun sections() {
        val sections: List<Pair<String, @androidx.compose.runtime.Composable () -> Unit>> = listOf(
            "section_buttons" to { ShowcaseButtons(); ShowcaseCounters() },
            "section_talk" to { ShowcaseBubbles() },
            "section_paytable" to { ShowcasePayTable(); ShowcaseBanner() },
        )
        sections.forEach { (name, body) ->
            renderToPng(out(name), 1080, 1300, 420) {
                PixelTheme {
                    FeltBackground(Modifier.fillMaxSize()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) { body() }
                    }
                }
            }
        }
    }

    /** The Play feature graphic, for a quick look (the store copy comes from StoreScreenshotTest). */
    @Test
    fun featureGraphic() = renderToPng(out("feature_graphic"), com.bradflaugher.pokersi.ui.screens.FEATURE_W, com.bradflaugher.pokersi.ui.screens.FEATURE_H, com.bradflaugher.pokersi.ui.screens.FEATURE_DPI) {
        com.bradflaugher.pokersi.ui.screens.FeatureGraphic()
    }

    @Test
    fun felt() = renderToPng(out("felt"), 540, 960, 420) {
        PixelTheme { Box(Modifier.fillMaxSize()) { CrtOverlay(Modifier.fillMaxSize()) { FeltBackground(Modifier.fillMaxSize()) } } }
    }
}
