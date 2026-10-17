package com.bradflaugher.pokersi.ui.pixel

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Every sound the game makes. All of them are synthesized; there are no audio files. */
enum class Sfx { Deal, Flip, Chip, Blip, Win, Lose, DrumRoll, Error }

/** Plays [Sfx]. Implementations must be cheap to call from the UI thread and never throw. */
fun interface SoundPlayer {
    fun play(sfx: Sfx)

    companion object {
        val None = SoundPlayer { }
    }
}

/** The sound player for the current screen; [SoundPlayer.None] by default (tests, previews). */
val LocalSoundPlayer = staticCompositionLocalOf { SoundPlayer.None }

/**
 * A tiny chiptune synth: square, triangle and noise voices rendered to PCM
 * once per effect (cached), then mixed on one background thread into a
 * streaming [AudioTrack]. The track is only open while something plays.
 * Every failure is caught and logged: sound is never worth a crash.
 * [volume] (0..1) scales the mix as it is written, so a change applies at once.
 */
class ChiptuneSound(@Volatile var enabled: Boolean = true, @Volatile var volume: Float = 1f) : SoundPlayer, AutoCloseable {
    private val cache = ConcurrentHashMap<Sfx, ShortArray>()
    private val queue = LinkedBlockingQueue<ShortArray>()
    @Volatile private var thread: Thread? = null
    @Volatile private var closed = false
    @Volatile private var broken = false

    override fun play(sfx: Sfx) {
        if (!enabled || closed || broken) return
        try {
            queue.offer(cache.getOrPut(sfx) { Synth.render(sfx) })
            ensureThread()
        } catch (t: Throwable) {
            fail(t)
        }
    }

    override fun close() {
        closed = true
        thread?.interrupt()
        queue.clear()
    }

    private fun fail(t: Throwable) {
        broken = true
        try { Log.w("PokerSI", "Sound disabled: $t") } catch (_: Throwable) { }
    }

    @Synchronized
    private fun ensureThread() {
        if (thread?.isAlive == true) return
        thread = Thread({ runMixer() }, "PokerSI-chiptune").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY - 1
            start()
        }
    }

    private class Voice(val data: ShortArray) { var pos = 0 }

    private fun runMixer() {
        var track: AudioTrack? = null
        try {
            val chunk = ShortArray(CHUNK)
            val mix = IntArray(CHUNK)
            val voices = ArrayList<Voice>()
            while (!closed) {
                if (voices.isEmpty()) {
                    // Idle: the track simply underruns (silence) until the next
                    // sound; after 3s of nothing the thread ends and frees it.
                    val first = queue.poll(3, TimeUnit.SECONDS) ?: break
                    voices += Voice(first)
                    if (track == null) track = newTrack().also { it.play() }
                }
                while (true) voices += Voice(queue.poll() ?: break)
                mix.fill(0)
                val it = voices.iterator()
                while (it.hasNext()) {
                    val v = it.next()
                    val n = min(CHUNK, v.data.size - v.pos)
                    for (i in 0 until n) mix[i] += v.data[v.pos + i]
                    v.pos += n
                    if (v.pos >= v.data.size) it.remove()
                }
                val gain = volume.coerceIn(0f, 1f)
                for (i in 0 until CHUNK) chunk[i] = (mix[i] * gain).toInt().coerceIn(-32767, 32767).toShort()
                track?.write(chunk, 0, CHUNK)
            }
        } catch (_: InterruptedException) {
        } catch (t: Throwable) {
            fail(t)
        } finally {
            try { track?.stop(); track?.release() } catch (_: Throwable) { }
            synchronized(this) { if (thread === Thread.currentThread()) thread = null }
        }
    }

    private fun newTrack(): AudioTrack {
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(max(min, CHUNK * 4))
            .build()
    }

    companion object {
        const val RATE = 22050
        private const val CHUNK = 512
    }
}

/** Remembers a [ChiptuneSound] for this composition, closed when it leaves. */
@Composable
fun rememberChiptuneSound(enabled: Boolean, volume: Float = 1f): SoundPlayer {
    val sound = remember { ChiptuneSound(enabled, volume) }
    sound.enabled = enabled
    sound.volume = volume
    DisposableEffect(sound) { onDispose { sound.close() } }
    return sound
}

/** Offline PCM synthesis for each [Sfx]. Pure math, so it is unit-testable on the JVM. */
object Synth {
    private const val RATE = ChiptuneSound.RATE

    fun render(sfx: Sfx): ShortArray {
        val buf = when (sfx) {
            Sfx.Deal -> FloatArray(ms(55)).also {
                noise(it, 0, ms(30), 0.35f, decay = 90f, seed = 7, highpass = true)
                square(it, 0, ms(18), 1900f, 1900f, 0.12f, duty = 0.25f, decay = 120f)
            }
            Sfx.Flip -> FloatArray(ms(80)).also {
                square(it, 0, ms(70), 520f, 1500f, 0.16f, duty = 0.25f, decay = 30f)
                noise(it, 0, ms(20), 0.12f, decay = 150f, seed = 3, highpass = true)
            }
            Sfx.Chip -> FloatArray(ms(140)).also {
                square(it, 0, ms(70), 2637f, 2637f, 0.16f, duty = 0.5f, decay = 45f)
                square(it, ms(35), ms(100), 3520f, 3520f, 0.12f, duty = 0.5f, decay = 40f)
                noise(it, 0, ms(8), 0.2f, decay = 300f, seed = 11, highpass = true)
            }
            Sfx.Blip -> FloatArray(ms(90)).also {
                square(it, 0, ms(35), 988f, 988f, 0.16f, duty = 0.25f, decay = 15f)
                square(it, ms(35), ms(55), 1319f, 1319f, 0.16f, duty = 0.25f, decay = 30f)
            }
            Sfx.Error -> FloatArray(ms(220)).also {
                square(it, 0, ms(90), 196f, 196f, 0.2f, duty = 0.5f, decay = 4f)
                square(it, ms(110), ms(110), 147f, 147f, 0.2f, duty = 0.5f, decay = 6f)
            }
            Sfx.Win -> FloatArray(ms(1150)).also {
                // C major arpeggio up two octaves, then a shimmering chord.
                val notes = listOf(72, 76, 79, 84, 88, 91, 96)
                notes.forEachIndexed { i, n ->
                    square(it, ms(65 * i), ms(110), midi(n), midi(n), 0.13f, duty = if (i % 2 == 0) 0.5f else 0.25f, decay = 10f)
                }
                val s = ms(65 * notes.size)
                square(it, s, ms(600), midi(84), midi(84), 0.11f, duty = 0.25f, decay = 3.5f, vibrato = 6f)
                square(it, s, ms(600), midi(88), midi(88), 0.09f, duty = 0.5f, decay = 3.5f, vibrato = 6f)
                triangle(it, s, ms(600), midi(60), 0.25f, decay = 3f)
            }
            Sfx.Lose -> FloatArray(ms(1700)).also {
                // Wah, wah, wah, waaaah.
                val notes = listOf(67, 66, 65)
                notes.forEachIndexed { i, n -> wah(it, ms(330 * i), ms(300), midi(n), 0.2f, vibrato = 0f) }
                wah(it, ms(990), ms(700), midi(64), 0.2f, vibrato = 7f)
            }
            Sfx.DrumRoll -> FloatArray(ms(1600)).also {
                // An accelerating snare roll with a crescendo, then a crash and a thump.
                var t = 0.0
                var gap = 0.09
                while (t < 1.15) {
                    val v = 0.08f + 0.25f * (t / 1.15).toFloat()
                    noise(it, (t * RATE).toInt(), ms(45), v, decay = 60f, seed = (t * 1000).toInt(), highpass = true)
                    t += gap
                    gap = max(0.028, gap * 0.92)
                }
                val hit = ms(1200)
                noise(it, hit, ms(400), 0.4f, decay = 7f, seed = 99, highpass = false)
                square(it, hit, ms(200), 110f, 55f, 0.3f, duty = 0.5f, decay = 12f)
            }
        }
        return ShortArray(buf.size) { (buf[it].coerceIn(-1f, 1f) * 32767f * 0.8f).toInt().toShort() }
    }

    private fun ms(m: Int) = RATE * m / 1000
    private fun midi(n: Int) = (440.0 * 2.0.pow((n - 69) / 12.0)).toFloat()

    private fun square(
        out: FloatArray, start: Int, len: Int, f0: Float, f1: Float, vol: Float,
        duty: Float, decay: Float, vibrato: Float = 0f,
    ) {
        var phase = 0.0
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= out.size) break
            val t = i.toFloat() / RATE
            val k = i.toFloat() / len
            var f = f0 + (f1 - f0) * k
            if (vibrato > 0) f *= (1 + 0.012f * sin(2 * PI * vibrato * t).toFloat())
            phase = (phase + f / RATE) % 1.0
            val env = attack(i) * exp(-decay * t)
            out[idx] += (if (phase < duty) vol else -vol) * env
        }
    }

    private fun triangle(out: FloatArray, start: Int, len: Int, f: Float, vol: Float, decay: Float) {
        var phase = 0.0
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= out.size) break
            phase = (phase + f / RATE) % 1.0
            val tri = (if (phase < 0.5) phase * 4 - 1 else 3 - phase * 4).toFloat()
            out[idx] += tri * vol * attack(i) * exp(-decay * i.toFloat() / RATE)
        }
    }

    private fun wah(out: FloatArray, start: Int, len: Int, f: Float, vol: Float, vibrato: Float) {
        var phase = 0.0
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= out.size) break
            val t = i.toFloat() / RATE
            val k = i.toFloat() / len
            var freq = f * (1f - 0.03f * k)
            if (vibrato > 0) freq *= (1 + 0.02f * sin(2 * PI * vibrato * t).toFloat())
            phase = (phase + freq / RATE) % 1.0
            // Duty sweep from hollow to nasal: the "wah".
            val duty = 0.5f - 0.38f * sin(PI * k).toFloat()
            val env = attack(i) * (1f - k).coerceAtLeast(0f).pow(0.6f)
            out[idx] += (if (phase < duty) vol else -vol) * env
        }
    }

    private fun noise(out: FloatArray, start: Int, len: Int, vol: Float, decay: Float, seed: Int, highpass: Boolean) {
        val r = Random(seed)
        var prev = 0f
        for (i in 0 until len) {
            val idx = start + i
            if (idx >= out.size) break
            val n = r.nextFloat() * 2 - 1
            val s = if (highpass) (n - prev) * 0.5f else n
            prev = n
            out[idx] += s * vol * exp(-decay * i.toFloat() / RATE)
        }
    }

    /** 2ms linear attack to avoid clicks. */
    private fun attack(i: Int): Float = min(1f, i / (RATE * 0.002f))
}
