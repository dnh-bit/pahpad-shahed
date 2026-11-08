package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.WorldRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * صحنهٔ سه‌بعدی را در چند نقطه از پرواز رندر می‌کند تا بشود با چشم دید که
 * پنجره‌های ساختمان‌ها پایدارند یا نه.
 *
 * این تست همان مسیر واقعی [WorldRenderer.draw] را صدا می‌زند، پس هر چیزی که
 * روی گوشی دیده می‌شود اینجا هم دیده می‌شود.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class WorldRenderTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    @Test
    fun renderCityAtSeveralDistances() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.all.first { it.chapter == 3 }   // عملیات شهری

        val mission = Mission(level, save, MissionMode.STORY, 42L)
        // بررسی می‌کنیم بافت‌ها واقعاً لود می‌شوند، وگرنه رندر بی‌معناست
        for (n in listOf("material_wall", "material_roof")) {
            val b = ir.shahed.pahpad.core.Gfx.get(activity, n)
            println("بافت $n: " + (if (b == null) "NULL ❌" else "${b.width}x${b.height} ✅"))
        }
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)

        val w = 1600
        val h = 900

        // چند فاصلهٔ مختلف از مرکز شهر — تا بشود دید آیا الگوی پنجره با
        // حرکت جابه‌جا می‌شود یا نه.
        val stands = listOf(
            "near" to floatArrayOf(0f, 60f, 120f),
            "mid" to floatArrayOf(40f, 90f, 260f),
            "far" to floatArrayOf(-60f, 120f, 420f),
        )

        for ((tag, pos) in stands) {
            mission.x = pos[0]
            mission.y = pos[1]
            mission.z = pos[2]
            mission.yaw = 0f
            mission.pitch = -0.12f

            // همان چیدمان دوربینِ GameScreen — وگرنه صحنه دیده نمی‌شود
            val cam = renderer.scene.cam
            cam.fov = 74f
            val back = 26f
            val cp = Math.cos(mission.pitch.toDouble()).toFloat()
            cam.x = mission.x - Math.sin(mission.yaw.toDouble()).toFloat() * back * cp
            cam.z = mission.z - Math.cos(mission.yaw.toDouble()).toFloat() * back * cp
            cam.y = (mission.y - Math.sin(mission.pitch.toDouble()).toFloat() * back + 6f).coerceAtLeast(3f)
            cam.yaw = mission.yaw
            cam.pitch = mission.pitch * 0.85f - 0.05f
            cam.roll = mission.roll * 0.45f
            cam.viewport(w.toFloat(), h.toFloat())

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            renderer.update(1f / 60f, mission)
            renderer.draw(c, mission, w.toFloat(), h.toFloat())
            val f = File(outDir, "world_$tag.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("نوشتم ${f.absolutePath}")
        }

        // ---- نمای نزدیک یک ساختمان واقعی: پنجره‌ها باید شبکهٔ منظم باشند
        run {
            val m3 = Mission(level, save, MissionMode.STORY, 42L)
            // نزدیک‌ترین ساختمان به مرکز را پیدا کن و دوربین را رو‌به‌رویش بگذار
            val b = m3.world.props
                .filter { it.kind == ir.shahed.pahpad.game.PropKind.BUILDING }
                .minByOrNull { it.x * it.x + it.z * it.z }
            if (b == null) {
                println("⚠ هیچ ساختمانی پیدا نشد")
            } else {
                println("ساختمان: x=${b.x} z=${b.z} w=${b.w} h=${b.h} d=${b.d} rot=${b.rot}")
                m3.x = b.x; m3.y = b.h * 0.5f; m3.z = b.z
                m3.yaw = 0f; m3.pitch = 0f
                val cam = renderer.scene.cam
                cam.fov = 55f
                // ۲۸ متر جلوی دیوار، هم‌ارتفاع وسط ساختمان
                // دقیقاً رو‌به رخ -Z (نه گوشه) تا یک وجه کامل دیده شود
                cam.x = b.x
                cam.y = b.h * 0.45f
                cam.z = b.z - (b.d * 0.5f + b.w * 0.75f)
                cam.yaw = 0f
                cam.pitch = 0f
                cam.roll = 0f
                cam.viewport(w.toFloat(), h.toFloat())
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                renderer.update(1f / 60f, m3)
                renderer.draw(c, m3, w.toFloat(), h.toFloat())
                val f = File(outDir, "building_closeup.png")
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                println("نوشتم ${f.absolutePath}")
            }
        }

        // ---- نمای نزدیک پهپاد: مدل نباید داغان باشد
        run {
            val m2 = Mission(level, save, MissionMode.STORY, 42L)
            m2.x = 0f; m2.y = 40f; m2.z = 0f
            m2.yaw = 0.6f; m2.pitch = 0.05f; m2.roll = 0.18f
            val cam = renderer.scene.cam
            cam.fov = 50f
            cam.x = -22f; cam.y = 46f; cam.z = -22f
            cam.yaw = Math.toRadians(45.0).toFloat()
            cam.pitch = -0.12f; cam.roll = 0f
            cam.viewport(w.toFloat(), h.toFloat())
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            renderer.update(1f / 60f, m2)
            renderer.draw(c, m2, w.toFloat(), h.toFloat())
            val f = File(outDir, "drone_closeup.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("نوشتم ${f.absolutePath}")
        }

        // ---- اثبات پایداری پنجره‌ها
        // یک ساختمان ثابت را از دو فاصلهٔ خیلی نزدیک به هم رندر می‌کنیم. اگر
        // تصمیم «پنجرهٔ مجزا» به دوربین وابسته باشد، شدت پنجره بین این دو
        // فریم می‌پرد. چون الان بر اساس فاصلهٔ جهانی است، باید یکسان بماند.
        val wd = WorldRenderer(activity)
        val detailFn = WorldRenderer::class.java.getDeclaredMethod("windowDetail", Float::class.java)
        detailFn.isAccessible = true
        // فاصلهٔ جهانی ساختمان (ثابت است، هرگز با دوربین عوض نمی‌شود)
        for (d in listOf(200f, 200.001f, 300f, 300.001f)) {
            val v = detailFn.invoke(wd, d) as Float
            println("windowDetail(${d}) = $v")
        }
        val a = detailFn.invoke(wd, 260f) as Float
        val b = detailFn.invoke(wd, 260.001f) as Float
        assert(Math.abs(a - b) < 1e-6) { "شدت پنجره نباید با تغییر ناچیز عوض شود" }
    }
}
