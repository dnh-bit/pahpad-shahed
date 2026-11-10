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
 * سقف ساختمان‌ها باید تمیز دیده شوند.
 *
 * دو نقص جدا در بام‌ها بود و این تست دومی را می‌گیرد — همان که کاربر
 * با چشم می‌دید:
 *
 * ۱. هندسی: بدنه و «لبهٔ پشت‌بام» دو وجه هم‌سطح می‌ساختند و مرتب‌ساز
 *    نقاش با کوچک‌ترین حرکت دوربین جابه‌جا می‌شد. رفع شد با
 *    `topFace = false` روی بدنه. (این یکی با تست پیکسلی قابل اثبات
 *    نبود؛ ناحیهٔ بام در کادر تست به‌قدر کافی پر نبود.)
 * ۲. بافتی — همان چیزی که در ویدیو دیده می‌شد: بافت بام پرکنتراست بود
 *    (۲۲٪ پیکسل‌ها در بازهٔ روشنایی ۱۴۴-۲۵۵) و با تکرار ۷ متری مثل
 *    سنگ‌ریزه و خال روی بام می‌نشست. **این تست همان را می‌سنجد:**
 *    بازهٔ روشنایی ≤ ۴۰ و درز کاشی ≤ ۲.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class RoofStabilityTest {

    private val outDir = File("build/screens").apply { mkdirs() }
    private val w = 800
    private val h = 450

    /** رندر نمای بام از بالا — برای بازرسی چشمی در آرتیفکت CI. */
    @Test
    fun renderRoofsForInspection() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.all.first { it.chapter == 3 }
        val mission = Mission(level, save, MissionMode.STORY, 42L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)

        mission.x = 0f
        mission.y = 200f
        mission.z = 300f
        mission.yaw = 0f
        mission.pitch = -0.55f

        val cam = renderer.scene.cam
        cam.fov = 74f
        val back = 40f
        val cp = Math.cos(mission.pitch.toDouble()).toFloat()
        cam.x = mission.x
        cam.z = mission.z - cp * back
        cam.y = (mission.y - Math.sin(mission.pitch.toDouble()).toFloat() * back + 6f).coerceAtLeast(3f)
        cam.yaw = 0f
        cam.pitch = mission.pitch * 0.85f - 0.05f
        cam.roll = 0f
        cam.viewport(w.toFloat(), h.toFloat())

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(bmp), mission, w.toFloat(), h.toFloat())
        java.io.FileOutputStream(File(outDir, "roofs_top.png")).use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bmp.recycle()
        println("رندر بام نوشته شد: " + File(outDir, "roofs_top.png").absolutePath)
    }

    /**
     * نقصی که کاربر می‌دید در **بافت** بام بود، نه هندسه: بافت بام
     * پرکنتراست بود (۲۲٪ پیکسل‌ها در بازهٔ روشنایی ۱۴۴-۲۵۵) و با تکرار
     * ۷ متری روی بام مثل سنگ‌ریزه/خال دیده می‌شد.
     *
     * این تست همان نقص را می‌گیرد: کنتراست بافت بام و بی‌درز بودن کاشی.
     */
    @Test
    fun roofTextureIsCleanAndSeamless() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val bmp = ir.shahed.pahpad.core.Gfx.get(activity, "material_roof")
            ?: throw AssertionError("بافت material_roof لود نشد")
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        fun lum(i: Int): Int {
            val p = px[i]
            return ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
        }

        // ۱) کنتراست: بازهٔ روشنایی نباید از ۴۰ بیشتر باشد.
        //    با بافت قدیمی این عدد ۲۵۵ بود.
        var mn = 255
        var mx = 0
        for (i in px.indices) {
            val v = lum(i)
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        println("بافت بام: بازهٔ روشنایی $mn..$mx (اختلاف ${mx - mn})")
        assertTrue("بافت بام پرکنتراست است (${mx - mn}) — روی بام خال‌خال دیده می‌شود", mx - mn <= 40)

        // ۲) بی‌درز: اختلاف میانگین لبهٔ مقابل نباید از ۲ بیشتر باشد،
        //    وگرنه تکرار ۷ متری خط می‌سازد.
        fun avgCol(x: Int): Double {
            var s = 0L
            for (y in 0 until h) s += lum(y * w + x)
            return s.toDouble() / h
        }
        fun avgRow(y: Int): Double {
            var s = 0L
            for (x in 0 until w) s += lum(y * w + x)
            return s.toDouble() / w
        }
        val seamH = Math.abs(avgRow(0) - avgRow(h - 1))
        val seamV = Math.abs(avgCol(0) - avgCol(w - 1))
        println("درز بام: افقی ${"%.2f".format(seamH)} ، عمودی ${"%.2f".format(seamV)}")
        assertTrue("کاشی بام درز افقی دارد ($seamH)", seamH <= 2.0)
        assertTrue("کاشی بام درز عمودی دارد ($seamV)", seamV <= 2.0)
    }
}
