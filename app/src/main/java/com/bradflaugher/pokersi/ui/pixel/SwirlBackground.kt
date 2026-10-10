package com.bradflaugher.pokersi.ui.pixel

import android.graphics.RuntimeShader
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.core.graphics.createBitmap
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The slowly swirling, posterized paint backdrop (our take on Balatro's
 * famous background, in the PokerSI palette). Runs as an AGSL
 * [RuntimeShader]; when the shader can't be created, in
 * [PixelRenderMode.Static] or in inspection mode, the same math is run once
 * on the CPU at block resolution and drawn as a static pixel image.
 *
 * @param colors three paint colors, dark to light.
 * @param speed time multiplier (1 = default slow drift).
 * @param pixelSize size of one swirl "pixel"; defaults to 2 chrome pixels.
 * @param time freezes the swirl at this time (seconds) in the static fallback.
 */
@Composable
fun SwirlBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = LocalPixelPalette.current.let { listOf(it.swirlA, it.swirlB, it.swirlC) },
    speed: Float = 1f,
    pixelSize: Dp? = null,
    time: Float = 7f,
    forceFallback: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    require(colors.size == 3) { "SwirlBackground takes exactly three colors" }
    val static = forceFallback || LocalInspectionMode.current || LocalPixelRenderMode.current == PixelRenderMode.Static
    val unit = LocalPixelUnit.current
    val shader = remember(static) { if (static) null else SwirlShader.create() }

    var t by remember { mutableFloatStateOf(time) }
    if (shader != null) {
        LaunchedEffect(speed) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { now -> t = time + (now - start) / 1e9f * speed }
        }
    }

    Box(
        modifier.drawWithCache {
            val block = max(1f, pixelSize?.toPx()?.let { floor(it) } ?: (unit * 2f))
            if (shader != null) {
                val brush = ShaderBrush(shader)
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("block", block)
                shader.setColorUniform("colA", colors[0].toArgb())
                shader.setColorUniform("colB", colors[1].toArgb())
                shader.setColorUniform("colC", colors[2].toArgb())
                onDrawBehind {
                    shader.setFloatUniform("time", t)
                    drawRect(brush)
                }
            } else {
                val bw = ceil(size.width / block).toInt().coerceAtLeast(1)
                val bh = ceil(size.height / block).toInt().coerceAtLeast(1)
                val image = SwirlShader.renderCpu(bw, bh, colors, time)
                val scale = block.toInt()
                onDrawBehind { drawPixelArt(image, IntOffset.Zero, scale) }
            }
        },
    ) { content() }
}

/** The swirl, as AGSL and as an identical CPU port. */
object SwirlShader {
    private const val BANDS = 6f

    val AGSL = """
        uniform float2 resolution;
        uniform float time;
        uniform float block;
        layout(color) uniform half4 colA;
        layout(color) uniform half4 colB;
        layout(color) uniform half4 colC;

        half4 main(float2 fragCoord) {
            float2 cell = floor(fragCoord / block);
            float2 p = (cell + 0.5) * block;
            float2 uv = (p - 0.5 * resolution) / max(resolution.x, resolution.y) * 2.0;
            float r = length(uv);
            float a = atan(uv.y, uv.x);
            a += time * 0.11 + (1.3 - r) * (2.6 + 0.7 * sin(time * 0.13));
            float2 q = float2(cos(a), sin(a)) * r * 3.0;
            for (int i = 0; i < 5; i++) {
                float fi = float(i);
                q += float2(sin(q.y * 1.25 + time * 0.23 + fi),
                            cos(q.x * 1.05 - time * 0.19 + fi * 1.7)) * 0.6;
            }
            float v = 0.5 + 0.5 * sin(q.x * 1.15 + q.y * 0.85 + time * 0.1);
            float w = 0.5 + 0.5 * cos(length(q) * 1.35 - time * 0.27);
            float m = clamp(v * 0.62 + w * 0.38 - r * 0.08, 0.0, 1.0);
            float2 b = mod(cell, 2.0);
            float d = b.x < 0.5 ? (b.y < 0.5 ? 0.0 : 0.5) : (b.y < 0.5 ? 0.75 : 0.25);
            float k = clamp(floor(m * $BANDS + d) / ${BANDS - 1}, 0.0, 1.0);
            half4 c = k < 0.5 ? mix(colA, colB, half(k * 2.0)) : mix(colB, colC, half((k - 0.5) * 2.0));
            return half4(c.rgb, 1.0);
        }
    """.trimIndent()

    /** Compiles the shader, or null when the runtime can't (tests, broken drivers). */
    fun create(): RuntimeShader? = try {
        RuntimeShader(AGSL)
    } catch (_: Throwable) {
        null
    }

    private val cache = ArtCache<Any>(4)

    /** The swirl on the CPU, one pixel per block, at a fixed [time]. */
    fun renderCpu(w: Int, h: Int, colors: List<Color>, time: Float): ImageBitmap =
        cache.getOrPut(listOf(w, h, colors, time)) {
            val cols = colors.map { floatArrayOf(it.red, it.green, it.blue) }
            val px = IntArray(w * h)
            val maxDim = max(w, h).toFloat()
            for (y in 0 until h) for (x in 0 until w) {
                val ux = ((x + 0.5f) - 0.5f * w) / maxDim * 2f
                val uy = ((y + 0.5f) - 0.5f * h) / maxDim * 2f
                val r = sqrt(ux * ux + uy * uy)
                var a = atan2(uy, ux)
                a += time * 0.11f + (1.3f - r) * (2.6f + 0.7f * sin(time * 0.13f))
                var qx = cos(a) * r * 3f
                var qy = sin(a) * r * 3f
                for (i in 0 until 5) {
                    val nx = qx + sin(qy * 1.25f + time * 0.23f + i) * 0.6f
                    val ny = qy + cos(qx * 1.05f - time * 0.19f + i * 1.7f) * 0.6f
                    qx = nx; qy = ny
                }
                val v = 0.5f + 0.5f * sin(qx * 1.15f + qy * 0.85f + time * 0.1f)
                val wv = 0.5f + 0.5f * cos(sqrt(qx * qx + qy * qy) * 1.35f - time * 0.27f)
                val m = (v * 0.62f + wv * 0.38f - r * 0.08f).coerceIn(0f, 1f)
                val bx = x % 2; val by = y % 2
                val d = if (bx == 0) (if (by == 0) 0f else 0.5f) else (if (by == 0) 0.75f else 0.25f)
                val k = (floor(m * BANDS + d) / (BANDS - 1)).coerceIn(0f, 1f)
                val (c0, c1, f) = if (k < 0.5f) Triple(cols[0], cols[1], k * 2f) else Triple(cols[1], cols[2], (k - 0.5f) * 2f)
                val rr = c0[0] + (c1[0] - c0[0]) * f
                val gg = c0[1] + (c1[1] - c0[1]) * f
                val bb = c0[2] + (c1[2] - c0[2]) * f
                px[y * w + x] = android.graphics.Color.rgb((rr * 255).toInt(), (gg * 255).toInt(), (bb * 255).toInt())
            }
            createBitmap(w, h).also { it.setPixels(px, 0, w, 0, 0, w, h) }.asImageBitmap()
        }
}
