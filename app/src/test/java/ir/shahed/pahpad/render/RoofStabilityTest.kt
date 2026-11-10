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
        mission.yaw = 0f
        mission.pitch = -0.12f
        // دوربین باید مثل GameScreen ست شود؛ وگرنه draw از دوربین قبلی
        // استفاده می‌کند و دو رندر عیناً یکی می‌شوند (تفاضل صفر = تست بی‌اثر).
        val cam = renderer.scene.cam
        cam.fov = 74f
        val back = 26f
        val cp = Math.cos(mission.pitch.toDouble()).toFloat()
        cam.x = mission.x - Math.sin(mission.yaw.toDouble()).toFloat() * back * cp
        cam.z = mission.z - Math.cos(mission.yaw.toDouble()).toFloat() * back * cp
        cam.y = (mission.y - Math.sin(mission.pitch.toDouble()).toFloat() * back + 6f).coerceAtLeast(3f)
        cam.yaw = mission.yaw
        cam.pitch = mission.pitch * 0.85f - 0.05f
        cam.roll = 0f
        cam.viewport(w.toFloat(), h.toFloat())

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        renderer.draw(c, mission, w.toFloat(), h.toFloat())
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        return px
    }

    private fun ratioWith(doubleRoof: Boolean): Float {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.all.first { it.chapter == 3 }
        val mission = Mission(level, save, MissionMode.STORY, 42L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)
        renderer.debugDoubleRoof = doubleRoof

        val a = render(mission, renderer, 0f)
        val b = render(mission, renderer, 0.25f)

        val y1 = (h * 0.55f).toInt()
        var bigDiff = 0
        var total = 0
        for (y in 0 until y1) {
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
        var anyDiff = 0
        for (i in a.indices) if (a[i] != b[i]) anyDiff++
        assertTrue("تست بی‌اثر است: دو رندر عیناً یکی هستند", anyDiff > 1000)
        return bigDiff.toFloat() / total
    }

    @Test
    fun renderRoofsForInspection() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.all.first { it.chapter == 3 }
        val mission = Mission(level, save, MissionMode.STORY, 42L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)
        // ارتفاع بالا و رو به پایین → بام‌ها در کادر
        mission.pitch = -0.55f
        mission.y = 200f
        mission.z = 300f
        mission.x = 0f
        mission.yaw = 0f
        val cam = renderer.scene.cam
        cam.fov = 74f
        val back = 40f
        val cp = Math.cos(mission.pitch.toDouble()).toFloat()
        cam.x = 0f
        cam.z = mission.z - Math.cos(0.0).toFloat() * back * cp
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

    @Test
    fun roofsDoNotFlickerOnTinyCameraMoves() {
        // کنترل: با باگ قدیمی (دو وجه هم‌سطح) باید سوسو داشته باشیم
        val buggy = ratioWith(doubleRoof = true)
        // اصلاح‌شده
        val fixed = ratioWith(doubleRoof = false)
        println("سوسوی بام — با باگ: ${"%.3f".format(buggy * 100)}٪ ، اصلاح‌شده: ${"%.3f".format(fixed * 100)}٪")
        // اگر هر دو عدد یکی باشند یعنی پرچم اثر نکرده — اندازه‌گیری معتبر نیست.
        assertTrue(
            "تست بی‌اعتبار: با و بدون وجه تکراری عدد یکسان شد " +
                    "(${"%.3f".format(buggy * 100)}٪ / ${"%.3f".format(fixed * 100)}٪)",
            buggy != fixed
        )
        assertTrue(
            "سقف هنوز سوسو می‌زند: ${"%.2f".format(fixed * 100)}٪ (حد ۶٪)",
            fixed < 0.06f
        )
    }
}
