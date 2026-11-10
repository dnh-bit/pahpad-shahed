package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.WorldRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * سقف ساختمان‌ها نباید سوسو بزند.
 *
 * باگ: بدنهٔ ساختمان و «لبهٔ پشت‌بام» هر دو یک وجه **هم‌سطح و هم‌اندازه** در
 * بالای ساختمان می‌ساختند. مرتب‌ساز نقاش با عمق میانگین تصمیم می‌گیرد کدام
 * جلو باشد؛ روی دو وجه کاملاً هم‌سطح این تصمیم با کوچک‌ترین حرکت دوربین
 * عوض می‌شود → سقف یک فریم این‌طور، فریم بعد آن‌طور. روی گوشی «سقف‌ها
 * خرابند/لکه‌لکه‌اند» دیده می‌شد.
 *
 * این تست همان کار را می‌کند که چشم در حال پرواز: دو رندر با جابه‌جایی
 * خیلی کوچک دوربین، و تفاضل پیکسلی در ناحیهٔ بام. اگر سقف پایدار باشد،
 * تفاوت باید کوچک و یکنواخت باشد؛ اگر دو وجه هم‌سطح بجنگند، تفاوت
 * لکه‌های بزرگ و تصادفی می‌سازد.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class RoofStabilityTest {

    private val outDir = File("build/screens").apply { mkdirs() }
    private val w = 800
    private val h = 450

    private fun render(mission: Mission, renderer: WorldRenderer, shift: Float): IntArray {
        mission.x = shift
        mission.y = 130f
        mission.z = 260f
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        renderer.draw(c, mission, w.toFloat(), h.toFloat())
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        return px
    }

    @Test
    fun roofsDoNotFlickerOnTinyCameraMoves() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.all.first { it.chapter == 3 }
        val mission = Mission(level, save, MissionMode.STORY, 42L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)

        // دو فریم با ۲۵ سانتی‌متر جابه‌جایی — مثل یک فریم از پرواز.
        val a = render(mission, renderer, 0f)
        val b = render(mission, renderer, 0.25f)

        // ناحیهٔ بام: بالای تصویر
        val y0 = 0
        val y1 = (h * 0.55f).toInt()

        // شمارش اختلاف‌های بزرگ (بیش از ۴۰ در هر کانال) — سوسو لکه‌های
        // بزرگ می‌سازد، جابه‌جایی طبیعی دوربین فقط لبه‌های نازک.
        var bigDiff = 0
        var total = 0
        for (y in y0 until y1) {
            for (x in 0 until w) {
                val i = y * w + x
                val pa = a[i]; val pb = b[i]
                val dr = Math.abs((pa shr 16 and 0xFF) - (pb shr 16 and 0xFF))
                val dg = Math.abs((pa shr 8 and 0xFF) - (pb shr 8 and 0xFF))
                val db = Math.abs((pa and 0xFF) - (pb and 0xFF))
                total++
                if (dr + dg + db > 120) bigDiff++
            }
        }
        val ratio = bigDiff.toFloat() / total
        println("ناحیهٔ بام: $bigDiff از $total پیکسل اختلاف بزرگ " +
                "(${"%.3f".format(ratio * 100)}٪)")

        // آستانه: با سقف سالم، فقط لبه‌ها تغییر می‌کنند (< ۶٪).
        // با دو وجه هم‌سطح، سطح کامل بام می‌جنگد و درصد بالا می‌رود.
        assertTrue(
            "سقف سوسو می‌زند: ${"%.1f".format(ratio * 100)}٪ پیکسل‌های بام بین دو فریم " +
                    "با ۲۵ سانتی‌متر جابه‌جایی عوض شدند (حد ۶٪)",
            ratio < 0.06f
        )
    }
}
