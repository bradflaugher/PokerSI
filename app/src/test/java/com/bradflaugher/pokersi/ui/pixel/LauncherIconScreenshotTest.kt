package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradflaugher.pokersi.R
import com.bradflaugher.pokersi.ui.screenshot.renderToPng
import com.bradflaugher.pokersi.ui.screenshot.screenshotDir
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Previews the adaptive launcher icon (tools/icon/gen_icon.py) under a few masks. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconScreenshotTest {
    @Composable
    private fun Icon(shape: Shape, themed: Boolean = false) {
        // The launcher shows the middle 72dp of the 108dp layers.
        Box(Modifier.size(144.dp).clip(shape).background(if (themed) Color(0xFF283044) else Color.Transparent), contentAlignment = Alignment.Center) {
            val layer = Modifier.requiredSize(216.dp)
            if (themed) {
                Image(painterResource(R.drawable.ic_launcher_monochrome), null, layer, colorFilter = ColorFilter.tint(Color(0xFFC9D6FF)))
            } else {
                Image(painterResource(R.drawable.ic_launcher_background), null, layer)
                Image(painterResource(R.drawable.ic_launcher_foreground), null, layer)
            }
        }
    }

    @Test
    fun launcherIcon() = renderToPng(File(screenshotDir, "design/launcher_icon.png"), 1080, 500, 420) {
        Row(
            Modifier.fillMaxSize().background(Color(0xFF3A3F55)).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(CircleShape)
            Icon(RoundedCornerShape(30))
            Icon(CircleShape, themed = true)
        }
    }
}
