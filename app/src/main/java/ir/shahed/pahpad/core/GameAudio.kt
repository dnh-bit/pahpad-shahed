package ir.shahed.pahpad.core

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.Random
import java.util.concurrent.Executors

/**
 * صداهای بازی به صورت کاملاً محاسباتی (سنتز شده) تولید می‌شوند
 * تا پروژه به هیچ فایل صوتی بیرونی نیاز نداشته باشد.
 * برای جایگزینی با صداهای واقعی، کافی است بدنه‌ی متدها را با SoundPool عوض کنید.
 */
class GameAudio(private val context: Context, private val save: SaveManager) {

    private val rate = 22050
    private val pool = Executors.newFixedThreadPool(3)
    private val rnd = Random()
    private var engine: AudioTrack? = null
    private var vibrator: Vibrator? = null

    init {
        vibrator = try {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } catch (t: Throwable) {
            null
        }
    }

    private fun enabled() = save.soundOn

    // ------------------------------------------------------------- ابزار پایه

    private fun buildTrack(data: ShortArray): AudioTrack? = try {
        val bytes = data.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bytes.coerceAtLeast(512))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, data.size)
        track
    } catch (t: Throwable) {
        null
    }

    private fun playOnce(data: ShortArray, volume: Float, durationMs: Long) {
        if (!enabled()) return
        pool.execute {
            val track = buildTrack(data) ?: return@execute
            try {
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()
                Thread.sleep(durationMs + 60L)
            } catch (t: Throwable) {
                // نادیده گرفتن؛ صدا حیاتی نیست
            } finally {
                try { track.stop() } catch (t: Throwable) {}
                try { track.release() } catch (t: Throwable) {}
            }
        }
    }

    // ------------------------------------------------------------- افکت‌ها

    /** انفجار: نویز سفید با افت نمایی و ضربه‌ی بیس */
    fun explosion(power: Float = 1f) {
        val ms = (620 * power).toInt().coerceIn(200, 1200)
        val n = rate * ms / 1000
        val data = ShortArray(n)
        var lp = 0f
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val env = Math.pow((1f - t).toDouble(), 2.2).toFloat()
            val noise = (rnd.nextFloat() * 2f - 1f)
            lp += (noise - lp) * 0.22f
            val boom = Math.sin(2.0 * Math.PI * (52.0 - 26.0 * t) * i / rate).toFloat()
            val s = (lp * 0.72f + boom * 0.5f) * env
            data[i] = (s * 26000f).toInt().coerceIn(-32000, 32000).toShort()
        }
        playOnce(data, 0.95f, ms.toLong())
    }

    /** بوق کوتاه؛ برای رابط کاربری و رادار */
    fun beep(freq: Float = 880f, ms: Int = 70, volume: Float = 0.35f) {
        val n = rate * ms / 1000
        val data = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val env = Math.min(1f, t * 12f) * Math.pow((1f - t).toDouble(), 1.4).toFloat()
            val s = Math.sin(2.0 * Math.PI * freq * i / rate).toFloat()
            data[i] = (s * env * 20000f).toInt().toShort()
        }
        playOnce(data, volume, ms.toLong())
    }

    fun click() = beep(1180f, 45, 0.28f)

    fun back() = beep(520f, 55, 0.24f)

    fun radarPing() = beep(1420f, 55, 0.18f)

    /** آژیر پدافند: دو نوای متناوب */
    fun alarm() {
        val ms = 420
        val n = rate * ms / 1000
        val data = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val f = if ((i / (rate / 9)) % 2 == 0) 760f else 520f
            val env = Math.min(1f, t * 20f) * Math.min(1f, (1f - t) * 6f)
            val s = Math.sin(2.0 * Math.PI * f * i / rate).toFloat()
            data[i] = (s * env * 17000f).toInt().toShort()
        }
        playOnce(data, 0.5f, ms.toLong())
    }

    /** شلیک پدافند */
    fun aaShot() {
        val ms = 200
        val n = rate * ms / 1000
        val data = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val env = Math.pow((1f - t).toDouble(), 3.0).toFloat()
            val noise = rnd.nextFloat() * 2f - 1f
            val tone = Math.sin(2.0 * Math.PI * (1500.0 - 900.0 * t) * i / rate).toFloat()
            data[i] = ((noise * 0.5f + tone * 0.5f) * env * 15000f).toInt().toShort()
        }
        playOnce(data, 0.4f, ms.toLong())
    }

    fun launch() {
        val ms = 700
        val n = rate * ms / 1000
        val data = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val env = Math.min(1f, t * 4f) * (1f - t * 0.6f)
            val f = 120.0 + 420.0 * t
            val s = Math.sin(2.0 * Math.PI * f * i / rate).toFloat()
            val noise = (rnd.nextFloat() * 2f - 1f) * 0.35f
            data[i] = ((s * 0.7f + noise) * env * 18000f).toInt().toShort()
        }
        playOnce(data, 0.7f, ms.toLong())
    }

    fun success() {
        pool.execute {
            beep(660f, 110, 0.4f); Thread.sleep(110)
            beep(880f, 110, 0.4f); Thread.sleep(110)
            beep(1320f, 220, 0.45f)
        }
    }

    fun failure() {
        pool.execute {
            beep(400f, 160, 0.4f); Thread.sleep(150)
            beep(260f, 320, 0.42f)
        }
    }

    fun coin() = beep(1560f, 60, 0.3f)

    // ------------------------------------------------------------- موتور پهباد

    fun startEngine() {
        if (!enabled()) return
        stopEngine()
        try {
            val cycle = 512
            val data = ShortArray(cycle * 8)
            for (i in data.indices) {
                val ph = (i % cycle).toFloat() / cycle
                // موج دندانه‌اره‌ای نرم + هارمونیک؛ صدای موتور پیستونی پهباد
                val saw = (ph * 2f - 1f)
                val h2 = Math.sin(2.0 * Math.PI * 2 * ph).toFloat() * 0.3f
                val n = (rnd.nextFloat() * 2f - 1f) * 0.12f
                data[i] = ((saw * 0.55f + h2 + n) * 9000f).toInt().coerceIn(-32000, 32000).toShort()
            }
            val track = buildTrack(data) ?: return
            track.setLoopPoints(0, data.size, -1)
            track.setVolume(0.32f)
            track.play()
            engine = track
        } catch (t: Throwable) {
            engine = null
        }
    }

    /** تنظیم زیر و بمی صدای موتور بر اساس سرعت (۰ تا ۱) */
    fun setEngineIntensity(value01: Float) {
        val tr = engine ?: return
        try {
            @Suppress("DEPRECATION")
            tr.setPlaybackRate((rate * (0.65f + 0.85f * value01.coerceIn(0f, 1f))).toInt())
        } catch (t: Throwable) {
        }
    }

    fun stopEngine() {
        val tr = engine ?: return
        engine = null
        try { tr.stop() } catch (t: Throwable) {}
        try { tr.release() } catch (t: Throwable) {}
    }

    // ------------------------------------------------------------- لرزش

    fun vibrate(ms: Long) {
        if (!save.vibrateOn) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (t: Throwable) {
        }
    }

    fun release() {
        stopEngine()
        try { pool.shutdownNow() } catch (t: Throwable) {}
    }
}
