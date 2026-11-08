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
 * شبیه‌سازی **مسیر واقعی پرواز** روی شهر و رندر چند فریم پشت‌سرهم.
 *
 * چرا: باگ پنجره‌ها فقط در حرکت دیده می‌شود. رندر یک فریم ثابت کافی نیست؛
 * باید همان چیزی که کاربر روی گوشی می‌بیند بازسازی شود — دوربین سوم‌شخص
 * که پشت پهپاد حرکت می‌کند و ارتفاع/زاویه‌اش تغییر می‌کند.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class FlightPathRenderTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    @Test
    fun renderFlightOverCity() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.all.first { it.chapter == 2 }   // عملیات شهری
        val mission = Mission(level, save, MissionMode.STORY, 1234L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)

        val w = 1280
        val h = 576   // همان نسبت ویدیوی کاربر

        // بلندترین ساختمان‌های شهر را پیدا کن و دوربین را از میانشان بگذران
        val tall = mission.world.props
            .filter { it.kind == ir.shahed.pahpad.game.PropKind.BUILDING }
            .sortedByDescending { it.h }
            .take(6)
        println("بلندترین ساختمان‌ها:")
        for (b in tall) println("  x=${b.x} z=${b.z} w=${b.w} h=${b.h} d=${b.d} rot=${b.rot}")

        val cams = if (tall.size >= 3) {
            // دوربین باید **داخل** شهر و رو‌به ساختمان‌ها باشد.
            // قبلاً ارتفاع ۲۵٪ و فاصله ۱.۶ برابر عمق بود → دوربین بیرون
            // شهر می‌افتاد و فقط بیابان دیده می‌شد.
            val a = tall[0]; val b = tall[1]; val c = tall[2]
            listOf(
                // ارتفاع کم، ۳۰ متر عقب‌تر از ساختمان، رو‌به آن
                "low" to Triple(a.x, a.h * 0.55f, a.z - a.d * 0.5f - 30f),
                // کنار ساختمان دوم، هم‌ارتفاع وسط
                "mid" to Triple(b.x - b.w * 0.5f - 26f, b.h * 0.6f, b.z),
                // بالاتر، بالای ساختمان سوم
                "high" to Triple(c.x, c.h * 0.9f, c.z - c.d * 0.5f - 40f),
            )
        } else {
            listOf(
                "low" to Triple(0f, 20f, 150f),
                "mid" to Triple(0f, 60f, 200f),
                "high" to Triple(0f, 120f, 260f),
            )
        }

        for ((tag, pos) in cams) {
            val (cx, cy, cz) = pos
            mission.x = cx; mission.y = cy; mission.z = cz + 40f
            mission.yaw = 0f; mission.pitch = -0.05f; mission.roll = 0f

            val cam = renderer.scene.cam
            cam.fov = 74f
            cam.x = cx; cam.y = cy; cam.z = cz
            // رو‌به مرکز ساختمان نگاه کن
            val tx = if (tall.isNotEmpty()) tall[0].x else 0f
            val tz = if (tall.isNotEmpty()) tall[0].z else 200f
            cam.yaw = Math.atan2(tx - cx, tz - cz)
            cam.pitch = 0.05f
            cam.roll = 0f
            cam.viewport(w.toFloat(), h.toFloat())

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            renderer.update(1f / 60f, mission)
            renderer.draw(c, mission, w.toFloat(), h.toFloat())
            val f = File(outDir, "flight_$tag.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            println("نوشتم ${f.absolutePath}")
        }
    }
}
