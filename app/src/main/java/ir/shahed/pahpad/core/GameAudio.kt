package ir.shahed.pahpad.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.Random
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

/** All synthesis, AudioTrack writes and releases run on bounded background workers. */
class GameAudio(context: Context, private val save: SaveManager) {
    private val rate = EngineWave.RATE
    private val effects = ThreadPoolExecutor(3, 3, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(12), ThreadPoolExecutor.DiscardPolicy())
    private val engines = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(1), ThreadPoolExecutor.DiscardOldestPolicy())
    private val engineGeneration = AtomicInteger()
    private val effectGeneration = AtomicInteger()
    @Volatile private var released = false
    @Volatile private var suspended = false
    @Volatile private var intensity = .5f
    // Accessed only by the single engine worker.
    private var engineData: ShortArray? = null
    private val vibrator = context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private fun enabled() = !released && !suspended && save.soundOn

    private fun buildTrack(data: ShortArray): AudioTrack? {
        var track: AudioTrack? = null
        return try {
            val created = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes((data.size * 2).coerceAtLeast(512))
                .setTransferMode(AudioTrack.MODE_STATIC).build()
            track = created
            check(created.state != AudioTrack.STATE_UNINITIALIZED)
            var offset = 0
            while (offset < data.size) {
                val written = created.write(data, offset, data.size - offset)
                check(written > 0)
                offset += written
            }
            check(created.state == AudioTrack.STATE_INITIALIZED)
            created
        } catch (_: Exception) {
            dispose(track)
            null
        }
    }

    private fun dispose(track: AudioTrack?) {
        if (track == null) return
        try { track.stop() } catch (_: Exception) {}
        try { track.release() } catch (_: Exception) {}
    }

    private fun effect(ms: Int, volume: Float, synth: (Int, Random) -> ShortArray) {
        if (!enabled()) return
        val token = effectGeneration.get()
        effects.execute {
            if (!enabled() || token != effectGeneration.get()) return@execute
            var track: AudioTrack? = null
            try {
                val length = rate * ms.coerceIn(20, 1800) / 1000
                val data = synth(length, Random())
                // Two-sided 4 ms ramps avoid discontinuities in short effects.
                val ramp = (rate / 250).coerceAtMost(data.size / 2)
                for (i in 0 until ramp) {
                    val gain = i.toFloat() / ramp
                    data[i] = (data[i] * gain).toInt().toShort()
                    val j = data.lastIndex - i
                    data[j] = (data[j] * gain).toInt().toShort()
                }
                if (!enabled() || token != effectGeneration.get()) return@execute
                val tr = buildTrack(data) ?: return@execute
                track = tr
                tr.setVolume(volume.coerceIn(0f, 1f))
                tr.play()
                var remaining = data.size * 1000L / rate + 60L
                while (remaining > 0 && enabled() && token == effectGeneration.get()) {
                    Thread.sleep(20)
                    remaining -= 20
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                // Audio failure must never prevent gameplay.
            } finally { dispose(track) }
        }
    }

    fun explosion(power: Float = 1f) {
        val p = if (power.isFinite()) power.coerceIn(.2f, 3f) else 1f
        val ms = (620 * p).toInt().coerceIn(200, 1200)
        effect(ms, .95f) { n, rnd ->
            var lp = 0.0
            var phase = 0.0
            ShortArray(n) { i ->
                val t = i.toDouble() / n
                lp += (rnd.nextDouble() * 2 - 1 - lp) * .22
                phase += 2 * PI * (52 - 26 * t) / rate
                val s = (lp * .72 + sin(phase) * .5) * (1 - t).pow(2.2)
                (s * 26000).toInt().coerceIn(-32000, 32000).toShort()
            }
        }
    }

    fun beep(freq: Float = 880f, ms: Int = 70, volume: Float = .35f) {
        val hz = if (freq.isFinite()) freq.coerceIn(40f, 8000f) else 880f
        effect(ms, volume) { n, _ -> ShortArray(n) { i ->
            val t = i.toDouble() / n
            (sin(2 * PI * hz * i / rate) * min(1.0, t * 12) * (1 - t).pow(1.4) * 20000).toInt().toShort()
        } }
    }
    fun click() = beep(1180f, 45, .28f)
    fun back() = beep(520f, 55, .24f)
    fun radarPing() = beep(1420f, 55, .18f)
    fun coin() = beep(1560f, 60, .3f)

    fun alarm() = effect(420, .5f) { n, _ ->
        var phase = 0.0
        ShortArray(n) { i ->
            val t = i.toDouble() / n
            val hz = if ((i / (rate / 9)) % 2 == 0) 760 else 520
            phase += 2 * PI * hz / rate
            (sin(phase) * min(1.0, t * 20) * min(1.0, (1 - t) * 6) * 17000).toInt().toShort()
        }
    }
    fun aaShot() = effect(200, .4f) { n, rnd ->
        var phase = 0.0
        ShortArray(n) { i ->
            val t = i.toDouble() / n
            phase += 2 * PI * (1500 - 900 * t) / rate
            (((rnd.nextDouble() * 2 - 1) * .5 + sin(phase) * .5) * (1 - t).pow(3.0) * 15000).toInt().toShort()
        }
    }
    fun launch() = effect(700, .7f) { n, rnd ->
        var phase = 0.0
        ShortArray(n) { i ->
            val t = i.toDouble() / n
            phase += 2 * PI * (120 + 420 * t) / rate
            ((sin(phase) * .7 + (rnd.nextDouble() * 2 - 1) * .35) * min(1.0, t * 4) * (1 - t * .6) * 18000).toInt().toShort()
        }
    }
    // One clip per melody: no worker sleeps while enqueuing more work into its own pool.
    private fun melody(notes: IntArray, durations: IntArray) {
        val ms = durations.sum()
        effect(ms, .42f) { n, _ ->
            val data = ShortArray(n)
            var start = 0
            for (k in notes.indices) {
                val count = rate * durations[k] / 1000
                for (j in 0 until count.coerceAtMost(n - start)) {
                    val env = min(1.0, j / (rate * .004)) * min(1.0, (count - 1 - j) / (rate * .012))
                    data[start + j] = (sin(2 * PI * notes[k] * j / rate) * env * 14000).toInt().toShort()
                }
                start += count
            }
            data
        }
    }
    fun success() = melody(intArrayOf(660, 880, 1320), intArrayOf(110, 110, 220))
    fun failure() = melody(intArrayOf(400, 260), intArrayOf(160, 320))

    fun startEngine() {
        if (!enabled()) return
        val token = engineGeneration.incrementAndGet()
        engines.execute {
            if (!enabled() || token != engineGeneration.get()) return@execute
            var track: AudioTrack? = null
            try {
                val data = engineData ?: EngineWave.create().also { engineData = it }
                if (!enabled() || token != engineGeneration.get()) return@execute
                val tr = buildTrack(data) ?: return@execute
                track = tr
                check(tr.setLoopPoints(0, data.size, -1) == AudioTrack.SUCCESS)
                var smooth = intensity
                var volume = 0f
                tr.setVolume(0f)
                tr.setPlaybackRate((rate * (.8f + .4f * smooth)).toInt())
                tr.play()
                while (enabled() && token == engineGeneration.get()) {
                    smooth += (intensity - smooth) * .12f
                    volume += ((.28f + smooth * .12f) - volume) * .22f
                    tr.setPlaybackRate((rate * (.8f + .4f * smooth)).toInt())
                    tr.setVolume(volume)
                    Thread.sleep(20)
                }
                for (i in 3 downTo 0) { tr.setVolume(volume * i / 4f); Thread.sleep(8) }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
            } finally { dispose(track) }
        }
    }
    fun setEngineIntensity(value01: Float) {
        intensity = if (value01.isFinite()) value01.coerceIn(0f, 1f) else .5f
    }
    fun stopEngine() { engineGeneration.incrementAndGet(); engines.queue.clear() }
    fun pause() {
        suspended = true
        stopEngine()
        effectGeneration.incrementAndGet()
        effects.queue.clear()
    }
    fun resume() { if (!released) suspended = false }
    fun vibrate(ms: Long) {
        if (released || suspended || !save.vibrateOn) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createOneShot(ms.coerceIn(1L, 1000L), VibrationEffect.DEFAULT_AMPLITUDE))
            else {
                @Suppress("DEPRECATION")
                v.vibrate(ms.coerceIn(1L, 1000L))
            }
        } catch (_: Exception) {}
    }
    fun release() {
        if (released) return
        released = true
        pause()
        effects.shutdownNow()
        engines.shutdownNow()
        try { vibrator?.cancel() } catch (_: Exception) {}
    }
}
