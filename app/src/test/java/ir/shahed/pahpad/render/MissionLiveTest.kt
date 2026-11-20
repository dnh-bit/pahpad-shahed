package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.PropKind
import ir.shahed.pahpad.game.WorldRenderer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * بازسازی زنده‌ی گیم‌پلی: دنیای در حال حرکت (هدف‌ها در حال دریانوردی، آب
 * متحرک، دوربین تعقیبیِ غلتان مثل GameScreen) و مسیر حمله‌ی واقعی —
 * نزدیک‌شدن، چرخش کم‌ارتفاع دور ناو، و پرواز کنار ناوچه. اگر ناپایداری
 * ناوها در شرایط واقعی بازی وجود داشته باشد، در تفاضل فریم‌های متوالی
 * این اجرا دیده می‌شود؛ سویپ‌های ایستا پیدایش نکردند.
 *
 * فریم‌ها یکی‌درمیان در build/screens با پیشوند live_ ذخیره می‌شوند.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class MissionLiveTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    @Test
    fun liveAttackRun() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.byId("L4_4")!!
        val mission = Mission(level, save, MissionMode.STORY, 5L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)

        val w = 960
        val h = 540
        val dt = 1f / 60f
        val frigate = mission.world.props.first { it.kind == PropKind.SHIP }

        var saved = 0
        for (frame in 0 until 720) {
            // دنیای زنده: هدف‌ها حرکت می‌کنند، پدافندها سوارشان می‌مانند
            mission.world.update(dt)
            val t = mission.world.targets[0]
            val time = frame * dt

            // مسیر اسکریپتی پهپاد: ۴ ثانیه نزدیک‌شدن، ۴ ثانیه چرخش
            // کم‌ارتفاع دور ناو، ۴ ثانیه شیرجه و پرواز کنار ناوچه
            when {
                frame < 240 -> {
                    val u = frame / 240f
                    val ang = 0.61f + u * 0.5f
                    val r = 520f - u * 390f
                    mission.x = t.x + sin(ang) * r
                    mission.z = t.z - cos(ang) * r
                    mission.y = 60f - u * 36f
                    mission.yaw = ang + 3.14159f
                    mission.pitch = -0.10f
                    mission.roll = 0.15f * sin(time * 0.9f)
                }
                frame < 480 -> {
                    val u = (frame - 240) / 240f
                    val ang = 1.11f + u * 4.2f
                    mission.x = t.x + sin(ang) * 130f
                    mission.z = t.z - cos(ang) * 130f
                    mission.y = 24f + 5f * sin(time * 0.7f)
                    mission.yaw = ang + 3.14159f - 1.35f
                    mission.pitch = -0.06f
                    mission.roll = 0.38f
                }
                else -> {
                    val u = (frame - 480) / 240f
                    val lz = -80f + u * 160f
                    val fc = cos(frigate.rot); val fs = sin(frigate.rot)
                    val lx = frigate.w * 0.5f + 26f - u * 10f
                    mission.x = frigate.x + lx * fc - lz * fs
                    mission.z = frigate.z + lx * fs + lz * fc
                    mission.y = frigate.h * 0.75f
                    mission.yaw = frigate.rot
                    mission.pitch = -0.04f
                    mission.roll = 0.22f * sin(time * 1.3f)
                }
            }

            // دوربین تعقیبی دقیقاً با فرمول GameScreen
            val cam = renderer.scene.cam
            cam.fov = 74f
            val back = 26f
            val cp = cos(mission.pitch)
            cam.x = mission.x - sin(mission.yaw) * back * cp
            cam.z = mission.z - cos(mission.yaw) * back * cp
            cam.y = (mission.y - sin(mission.pitch) * back + 6f).coerceAtLeast(3f)
            cam.yaw = mission.yaw
            cam.pitch = mission.pitch * 0.85f - 0.05f
            cam.roll = mission.roll * 0.45f
            cam.viewport(w.toFloat(), h.toFloat())

            renderer.update(dt, mission)
            if (frame % 2 == 0) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                renderer.draw(c, mission, w.toFloat(), h.toFloat())
                val f = File(outDir, "live_%03d.png".format(saved))
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                saved++
            }
        }
        println("LIVE saved=$saved frames")
    }
}
