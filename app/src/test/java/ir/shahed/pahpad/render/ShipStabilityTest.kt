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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * سنجش پایداری ناوها با حرکت دوربین — همان چیزی که کاربر در بازی می‌بیند
 * («ناوها تغییر شکل میدن و ظاهر و غیب میشن»).
 *
 * دو مسیر دوربین روی مرحله‌ی ناو هواپیمابر:
 * A) نزدیک‌شدن پیوسته از ۹۰۰ تا ۱۵۰ متر (گام ۵۰) — از هر دو آستانه‌ی
 *    LOD رد می‌شود؛ اگر جزئیات کشتی وسط راه عوض شود، تعداد وجه‌ها و
 *    تصویر بین دو فریم متوالی «می‌پرد».
 * B) چرخش ۱۰ درجه‌ای در فاصله‌ی ثابت ۳۰۰ متر — LOD ثابت می‌ماند و فقط
 *    پایداری ترتیب نقاش سنجیده می‌شود.
 *
 * فریم‌ها در build/screens با پیشوند sweep_ ذخیره و خط‌های گزارش
 * (dist/faces) چاپ می‌شوند تا تحلیل تفاضل فریم‌ها بیرون انجام شود.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class ShipStabilityTest {

    private val outDir = File("build/screens").apply { mkdirs() }
    private val w = 1280
    private val h = 720

    private fun shoot(
        renderer: WorldRenderer, mission: Mission, name: String,
        cx: Float, cy: Float, cz: Float, tx: Float, ty: Float, tz: Float
    ): Int {
        val cam = renderer.scene.cam
        cam.fov = 58f
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
        val faces = renderer.scene.debugFaces().size
        val f = File(outDir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return faces
    }

    @Test
    fun approachAndOrbitSweep() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.byId("L4_4")!!
        val mission = Mission(level, save, MissionMode.STORY, 5L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)
        renderer.scene.enableDebugCapture(true)
        renderer.update(1f / 60f, mission)

        val t = mission.world.targets[0]
        println("SWEEP target carrier at x=${t.x} z=${t.z} w=${t.w} d=${t.d} h=${t.h}")

        // A) نزدیک‌شدن: زاویه‌ی ثابت ۳۵ درجه از جلو-کنار، ارتفاع +۴۵
        val angA = 0.61f
        var i = 0
        var dist = 900f
        while (dist >= 150f) {
            val cx = t.x + sin(angA) * dist
            val cz = t.z - cos(angA) * dist
            val faces = shoot(renderer, mission, "sweep_a_%02d.png".format(i),
                cx, 48f, cz, t.x, 10f, t.z)
            println("SWEEP A frame=$i dist=$dist faces=$faces")
            i++
            dist -= 50f
        }

        // B) چرخش در فاصله‌ی ثابت ۳۰۰ متر با رندرر تازه (وضعیت LOD از نو)
        val renderer2 = WorldRenderer(activity)
        renderer2.applyQuality(2)
        renderer2.scene.enableDebugCapture(true)
        for (k in 0..11) {
            val ang = 0.61f + k * 0.1745f
            val cx = t.x + sin(ang) * 300f
            val cz = t.z - cos(ang) * 300f
            val faces = shoot(renderer2, mission, "sweep_b_%02d.png".format(k),
                cx, 48f, cz, t.x, 10f, t.z)
            println("SWEEP B frame=$k angDeg=${(Math.toDegrees(ang.toDouble())).toInt()} faces=$faces")
        }
        // C) چرخش نزدیک دور ناو در ۱۱۰ متری، گام ۴ درجه — محدوده‌ی حمله‌ی
        // واقعی بازیکن؛ وجه‌های بزرگ نزدیک صفحه‌ی نزدیک دوربین بریده می‌شوند.
        val renderer3 = WorldRenderer(activity)
        renderer3.applyQuality(2)
        renderer3.scene.enableDebugCapture(true)
        for (k in 0..23) {
            val ang = 0.61f + k * 0.0698f
            val cx = t.x + sin(ang) * 110f
            val cz = t.z - cos(ang) * 110f
            val faces = shoot(renderer3, mission, "sweep_c_%02d.png".format(k),
                cx, 34f, cz, t.x, 12f, t.z)
            println("SWEEP C frame=$k faces=$faces")
        }

        // D) پرواز موازی کنار یک ناوچه در فاصله‌ی ۳۵ متری از بدنه، گام ۶ متر
        // — همان مسیر حمله‌ای که کاربر در فیلمش داشت و به ناوچه خورد.
        val frigate = mission.world.props.first { it.kind == ir.shahed.pahpad.game.PropKind.SHIP }
        val renderer4 = WorldRenderer(activity)
        renderer4.applyQuality(2)
        renderer4.scene.enableDebugCapture(true)
        val fc = cos(frigate.rot); val fs = sin(frigate.rot)
        for (k in 0..24) {
            val lz = -72f + k * 6f
            val lx = frigate.w * 0.5f + 35f
            val cx = frigate.x + lx * fc - lz * fs
            val cz = frigate.z + lx * fs + lz * fc
            val faces = shoot(renderer4, mission, "sweep_d_%02d.png".format(k),
                cx, frigate.h * 0.9f, cz, frigate.x, frigate.h * 0.45f, frigate.z)
            println("SWEEP D frame=$k lz=$lz faces=$faces")
        }
        // E) نزدیک‌شدن به ناوچه از ۴۰۰ تا ۱۰۰ متر (گام ۱۲٫۵) — عبور از
        // آستانه‌ی LOD نزدیک (۲۵۰) وقتی کشتی بزرگِ کادر است.
        val renderer5 = WorldRenderer(activity)
        renderer5.applyQuality(2)
        renderer5.scene.enableDebugCapture(true)
        run {
            var i = 0
            var dist = 400f
            while (dist >= 100f) {
                val cx = frigate.x + sin(0.9f) * dist
                val cz = frigate.z - cos(0.9f) * dist
                val faces = shoot(renderer5, mission, "sweep_e_%02d.png".format(i),
                    cx, frigate.h * 1.6f, cz, frigate.x, frigate.h * 0.4f, frigate.z)
                println("SWEEP E frame=$i dist=$dist faces=$faces")
                i++
                dist -= 12.5f
            }
        }

        // F) نزدیک‌شدن به ناو هواپیمابر از ۴۰۰ تا ۱۰۰ متر (گام ۱۲٫۵)
        val renderer6 = WorldRenderer(activity)
        renderer6.applyQuality(2)
        renderer6.scene.enableDebugCapture(true)
        run {
            var i = 0
            var dist = 400f
            while (dist >= 100f) {
                val cx = t.x + sin(0.61f) * dist
                val cz = t.z - cos(0.61f) * dist
                val faces = shoot(renderer6, mission, "sweep_f_%02d.png".format(i),
                    cx, 44f, cz, t.x, 10f, t.z)
                println("SWEEP F frame=$i dist=$dist faces=$faces")
                i++
                dist -= 12.5f
            }
        }
        println("SWEEP done")
    }
}
