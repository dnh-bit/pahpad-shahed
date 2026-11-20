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
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * رندر چشمی مراحل دریایی بعد از اصلاحات بچ ۶: آب، عرشه‌ی ناو هواپیمابر،
 * ناوچه‌ی اسکورت، و نمای نزدیک تک‌تک سامانه‌های پدافند — تا با چشم دیده
 * شود هر سامانه روی عرشه‌ی یک کشتیِ جدا یا جزیره نشسته، نه روی آب و نه
 * همه روی ناو هدف. خروجی‌ها در build/screens با پیشوند naval_ می‌آیند.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class NavalVisualTest {

    private val outDir = File("build/screens").apply { mkdirs() }
    private val w = 1280
    private val h = 720

    private fun shoot(
        renderer: WorldRenderer, mission: Mission, name: String,
        cx: Float, cy: Float, cz: Float, tx: Float, ty: Float, tz: Float, fov: Float = 60f
    ) {
        val cam = renderer.scene.cam
        cam.fov = fov
        cam.x = cx; cam.y = cy; cam.z = cz
        val dx = tx - cx; val dz = tz - cz
        val distXZ = sqrt(dx * dx + dz * dz).coerceAtLeast(1f)
        cam.yaw = atan2(dx, dz)
        cam.pitch = atan2(-(cy - ty), distXZ)
        cam.roll = 0f
        cam.viewport(w.toFloat(), h.toFloat())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        renderer.draw(c, mission, w.toFloat(), h.toFloat())
        val f = File(outDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("نوشتم ${f.absolutePath}")
    }

    @Test
    fun renderNavalScenes() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val texCheck = ir.shahed.pahpad.core.Gfx.get(activity, "terrain_naval")
        println("بافت terrain_naval: " + (if (texCheck == null) "NULL ❌" else "${texCheck.width}x${texCheck.height} ✅"))
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)

        // ---------- مرحله‌ی ناو هواپیمابر ----------
        val l44 = Levels.byId("L4_4")!!
        val m44 = Mission(l44, save, MissionMode.STORY, 5L)
        renderer.update(1f / 60f, m44)

        // نمای آب آزاد رو به ناوگان
        shoot(renderer, m44, "naval_water.png", 230f, 52f, 500f, 60f, 4f, 1500f, fov = 62f)

        // عرشه‌ی ناو هواپیمابر از بالا-جلو
        val carrier = m44.world.targets[0]
        shoot(renderer, m44, "naval_carrier.png",
            carrier.x - 78f, 62f, carrier.z - 165f, carrier.x + 6f, 12f, carrier.z + 30f, fov = 58f)

        // ناوچه‌ی اسکورت از نزدیک
        val frigate = m44.world.props.first { it.kind == PropKind.SHIP }
        shoot(renderer, m44, "naval_frigate.png",
            frigate.x - 34f, 30f, frigate.z - 78f, frigate.x, 9f, frigate.z, fov = 58f)

        // نمای نزدیک هر سامانه — باید روی عرشه یا جزیره باشد
        for ((i, a) in m44.world.aaSites.withIndex()) {
            println("L4_4 site$i: x=${a.x} y=${a.y} z=${a.z} hosted=${a.hostTarget != null}")
            shoot(renderer, m44, "naval_l44_site$i.png",
                a.x - 30f, a.y + 34f, a.z - 80f, a.x, a.y + 4f, a.z, fov = 58f)
        }

        // ---------- مرحله‌ی ناوگان دریایی ----------
        val l25 = Levels.byId("L2_5")!!
        val m25 = Mission(l25, save, MissionMode.STORY, 5L)
        renderer.update(1f / 60f, m25)
        for ((i, a) in m25.world.aaSites.withIndex()) {
            println("L2_5 site$i: x=${a.x} y=${a.y} z=${a.z} hosted=${a.hostTarget != null}")
            shoot(renderer, m25, "naval_l25_site$i.png",
                a.x - 30f, a.y + 34f, a.z - 80f, a.x, a.y + 4f, a.z, fov = 58f)
        }
        println("تمام شد: ${outDir.listFiles()?.count { it.name.startsWith("naval_") }} فریم دریایی")
    }
}
