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
     * ناپایداری: دوربین در چهار موقعیت خیلی نزدیک حرکت می‌کند و بررسی
     * می‌شود ناحیهٔ **داخلی** بام رنگ ثابتی داشته باشد. اگر چیزی روی بام
     * ظاهر/غیب شود، رنگ همان ناحیه عوض می‌شود.
     */
    @Test
    fun roofSurfaceStaysStableWhileCameraMoves() {
        val shifts = floatArrayOf(0f, 0.4f, 0.8f, 1.2f)
        val bmps = shifts.map { renderAt(it, -0.55f) }

        java.io.FileOutputStream(File(outDir, "roofs_top.png")).use {
            bmps[0].compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        val px = Array(bmps.size) { i ->
            val a = IntArray(w * h)
            bmps[i].getPixels(a, 0, w, 0, 0, w, h)
            a
        }
        bmps.forEach { it.recycle() }

        // ناحیه‌های بام را با کد پیدا کن: پیکسل‌های روشنِ نسبتاً یکنواخت
        // در نیمهٔ بالای تصویر. سپس در همان ناحیه‌ها تغییر را بسنج.
        val y0 = (h * 0.18f).toInt()
        val y1 = (h * 0.50f).toInt()
        val x0 = (w * 0.20f).toInt()
        val x1 = (w * 0.80f).toInt()

        // تغییر هر پیکسل در طول چهار فریم
        val delta = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                var mn = 255
                var mx = 0
                for (k in px.indices) {
                    val p = px[k][i]
                    val v = ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
                    if (v < mn) mn = v
                    if (v > mx) mx = v
                }
                delta[i] = mx - mn
            }
        }

        // تفکیک «جابه‌جایی» از «تلنگر»:
        //
        // با حرکت دوربین، کل صحنه کمی جابه‌جا می‌شود — پس هم پیکسل و هم
        // همسایه‌هایش با هم تغییر می‌کنند. اما وقتی یک لایه روی لایهٔ دیگر
        // می‌پرد، **یک نقطهٔ موضعی** عوض می‌شود و همسایه‌هایش سر جایشان
        // می‌مانند. پس معیار درست، تغییر پیکسل نسبت به میانگین تغییر
        // همسایه‌های ۵×۵ است.
        var flicker = 0
        var total = 0
        var maxLocal = 0
        var worstX = -1
        var worstY = -1
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val i = y * w + x
                var sum = 0
                var n = 0
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        if (dx == 0 && dy == 0) continue
                        val yy = y + dy
                        val xx = x + dx
                        if (yy < 0 || yy >= h || xx < 0 || xx >= w) continue
                        sum += delta[yy * w + xx]
                        n++
                    }
                }
                val neighbor = if (n > 0) sum / n else 0
                val local = delta[i] - neighbor
                if (local > maxLocal) {
                    maxLocal = local; worstX = x; worstY = y
                }
                total++
                // تلنگر موضعی: این پیکسل خیلی بیشتر از همسایه‌ها عوض شد
                if (delta[i] > 55 && local > 45) flicker++
            }
        }
        val ratio = flicker.toFloat() / total
        println("تلنگر موضعی بام: $flicker از $total پیکسل " +
                "(${"%.2f".format(ratio * 100)}٪) — بیشترین اثر موضعی $maxLocal در ($worstX,$worstY)")

        // تشخیص: رنگ هر پیکسل بدقلق در چهار فریم را چاپ کن تا بفهمیم چه
        // چیزی روی چه چیزی می‌پرد (بام روی بام؟ بام روی آسمان؟ سایه؟).
        data class Hit(val i: Int, val local: Int)

        val hits = ArrayList<Hit>()
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val i = y * w + x
                if (delta[i] <= 55) continue
                var sum = 0
                var n = 0
                for (dy in -2..2) for (dx in -2..2) {
                    if (dx == 0 && dy == 0) continue
                    val yy = y + dy
                    val xx = x + dx
                    if (yy < 0 || yy >= h || xx < 0 || xx >= w) continue
                    sum += delta[yy * w + xx]; n++
                }
                val local = delta[i] - (if (n > 0) sum / n else 0)
                if (local > 45) hits.add(Hit(i, local))
            }
        }
        hits.sortByDescending { it.local }
        println("— تشخیص ۱۰ پیکسل بدقلق —")
        for (hit in hits.take(10)) {
            val x = hit.i % w
            val y = hit.i / w
            val rgb = px.joinToString(" ") { arr ->
                val p = arr[hit.i]
                "(${p shr 16 and 0xFF},${p shr 8 and 0xFF},${p and 0xFF})"
            }
            println("  ($x,$y) اثر=${hit.local}  فریم‌ها: $rgb")
        }
        // هیستوگرام: چه تعداد از پیکسل‌های بدقلق در فریم اول «بام»اند
        println("— رنگ غالب بام در فریم اول: نمونه ‎(${w / 2},${(y0 + y1) / 2})‎ = " +
                run {
                    val p = px[0][(y0 + y1) / 2 * w + w / 2]
                    "(${p shr 16 and 0xFF},${p shr 8 and 0xFF},${p and 0xFF})"
                })

        assertTrue(
            "روی بام لایه‌ها می‌پرند (تلنگر موضعی): ${"%.2f".format(ratio * 100)}٪ " +
                    "سطح بام، بیشترین اثر $maxLocal در ($worstX,$worstY)",
            ratio < 0.005f
        )
    }

    /**
     * کیفیت بافت بام نباید پایین بیاید. یک بار بافت را «صاف» کردم و
     * کاربر گفت کیفیت افت کرده — این تست جلوی تکرارش را می‌گیرد.
     */
    @Test
    fun roofTextureKeepsItsDetail() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val bmp = ir.shahed.pahpad.core.Gfx.get(activity, "material_roof")
            ?: throw AssertionError("بافت material_roof لود نشد")
        val tw = bmp.width
        val th = bmp.height
        val px = IntArray(tw * th)
        bmp.getPixels(px, 0, tw, 0, 0, tw, th)

        fun lum(i: Int): Int {
            val p = px[i]
            return ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3
        }

        var mn = 255
        var mx = 0
        for (i in px.indices) {
            val v = lum(i)
            if (v < mn) mn = v
            if (v > mx) mx = v
        }
        println("جزئیات بافت بام: بازهٔ روشنایی ${mx - mn}")

        assertTrue(
            "کیفیت بافت بام پایین آمده: بازهٔ روشنایی ${mx - mn} (باید ≥ ۱۲۰ باشد)",
            mx - mn >= 120
        )
    }
}
