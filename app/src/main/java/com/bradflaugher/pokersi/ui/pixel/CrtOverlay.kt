package com.bradflaugher.pokersi.ui.pixel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import kotlin.math.max

/**
 * CRT glass over its content: scanlines, a vignette, and a faint
 * red/blue chromatic fringe at the edges. All three are brushes built once
 * per size (a 1 x period scanline tile repeated by an image shader, and two
 * gradients), so drawing it costs three rect fills per frame.
 *
 * Off when [enabled] is false or [LocalCrtEnabled] is false.
 */
@Composable
fun CrtOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalCrtEnabled.current,
    scanlineAlpha: Float = 0.22f,
    vignetteAlpha: Float = 0.62f,
    content: @Composable BoxScope.() -> Unit,
) {
    val unit = LocalPixelUnit.current
    Box(
        modifier.drawWithCache {
            if (!enabled) return@drawWithCache onDrawWithContent { drawContent() }
            // Scanlines: one dark row every `period` device pixels.
            val period = max(3, unit)
            val dark = max(1, period / 3)
            val tile = createBitmap(1, period)
            for (y in 0 until period) {
                val a = if (y >= period - dark) scanlineAlpha else if (y == 0) scanlineAlpha * 0.25f else 0f
                tile[0, y] = Color.Black.copy(alpha = a).toArgb()
            }
            val scan = ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
            val center = Offset(size.width / 2, size.height / 2)
            val radius = max(size.width, size.height) * 0.75f
            val vignette = Brush.radialGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                0.85f to Color.Black.copy(alpha = vignetteAlpha * 0.55f),
                1f to Color.Black.copy(alpha = vignetteAlpha),
                center = center,
                radius = radius,
            )
            val fringe = Brush.horizontalGradient(
                0f to Color(0x33FF2A6A),
                0.06f to Color.Transparent,
                0.94f to Color.Transparent,
                1f to Color(0x332A8CFF),
            )
            onDrawWithContent {
                drawContent()
                drawRect(fringe, blendMode = BlendMode.Screen)
                drawRect(scan)
                drawRect(vignette)
            }
        },
        content = content,
    )
}
