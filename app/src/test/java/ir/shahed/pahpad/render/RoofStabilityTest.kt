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
 * سقف‌ها باید **پایدار** و **باکیفیت** باشند.
 *
 * دو شکایت جدا از کاربر:
 *   «روی سقف‌ها چیزی ظاهر و غیب می‌شود»            → ناپایداری
 *   «کیفیت سقف‌ها را آوردی پایین و فقط صافشون کردی» → افت جزئیات
 *
 * این تست هر دو را عددی می‌سنجد تا دیگر با حدس جلو نروم.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class RoofStabilityTest {

    private val outDir = File("build/screens").apply { mkdirs() }
    private val w = 800
    private val h = 450

    private fun renderAt(dx: Float, pitch: Float): Bitmap {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.all.first { it.chapter == 3 }
        val mission = Mission(level, save, MissionMode.STORY, 42L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)

        mission.x = dx
        mission.y = 200f
        mission.z = 300f
        mission.yaw = 0f
        mission.pitch = pitch

        val cam = renderer.scene.cam
        cam.fov = 74f
        val back = 40f
        val cp = Math.cos(pitch.toDouble()).toFloat()
        cam.x = mission.x
        cam.z = mission.z - cp * back
        cam.y = (mission.y - Math.sin(pitch.toDouble()).toFloat() * back + 6f).coerceAtLeast(3f)
        cam.yaw = 0f
        cam.pitch = pitch * 0.85f - 0.05f
        cam.roll = 0f
        cam.viewport(w.toFloat(), h.toFloat())

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(bmp), mission, w.toFloat(), h.toFloat())
        return bmp
    }

    /**
     * ریشهٔ «روی سقف چیزی ظاهر/غیب می‌شود» در فاصله: بافت بام نویز
     * تک‌پیکسلی پرکنتراست داشت. در دوردست هر پیکسل بافت روی چند پیکسل
     * صفحه می‌افتد و چون mipmap نداریم، نمونه‌برداری تصادفی می‌شود —
     * یعنی خال‌هایی که هر فریم جای دیگری می‌افتند: «ظاهر و غیب».
     *
     * معیار درست، «بازهٔ روشنایی» نیست (که با چند لکهٔ بزرگ هم پر می‌شود)
     * بلکه **انرژی بسامد بالا** است: تفاوت هر پیکسل با میانگین همسایگان.
     * بافت خوب جزئیات درشت دارد ولی نویز ریز ندارد.
     */
    @Test
    fun roofTextureHasDetailWithoutHighFrequencyNoise() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val bmp = ir.shahed.pahpad.core.Gfx.get(activity, "material_roof")
            ?: throw AssertionError("بافت material_roof لود نشد")
        val tw = bmp.width
        val th = bmp.height
        val px = IntArray(tw * th)
        bmp.getPixels(px, 0, tw, 0, 0, tw, th)

        fun lum(x: Int, y: Int): Int {
            val p = px[y * tw + x]
            return ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
        }

        // ۱. جزئیات درشت باید باشد — بافت نباید «صاف» شود
        var mn = 255
        var mx = 0
        for (i in px.indices) {
            val p = px[i]
            val v = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        val range = mx - mn
        println("جزئیات بافت بام: بازهٔ روشنایی $range")

        // ۲. ولی نویز تک‌پیکسلی نباید باشد
        var hfSum = 0.0
        var hfMax = 0
        var count = 0
        for (y in 1 until th - 1) {
            for (x in 1 until tw - 1) {
                var sum = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    sum += lum(x + dx, y + dy)
                }
                val d = Math.abs(lum(x, y) - sum / 8)
                hfSum += d
                if (d > hfMax) hfMax = d
                count++
            }
        }
        val hfAvg = hfSum / count
        println("نویز ریز بافت بام: میانگین ${"%.2f".format(hfAvg)} ، حداکثر $hfMax")

        assertTrue(
            "کیفیت بافت بام پایین آمده (صاف شده): بازهٔ روشنایی $range — باید ≥ ۶۰ باشد",
            range >= 60
        )
        // میانگین ملاک است، نه حداکثر: خط درز پنل بتنی عمداً یک لبهٔ تیز
        // دارد و همان حداکثر را بالا می‌برد. آن چیزی که در فاصله سوسو
        // می‌زند، نویز **گسترده** است که میانگین می‌گیرد.
        assertTrue(
            "بافت بام نویز گسترده دارد که در فاصله سوسو می‌زند: " +
                    "میانگین ${"%.2f".format(hfAvg)} (باید ≤ ۲.۵) و حداکثر $hfMax (باید ≤ ۷۰)",
            hfAvg <= 2.5 && hfMax <= 70
        )
    }
}
