package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.PropKind
import ir.shahed.pahpad.game.WorldRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/**
 * رگرسیون «ظاهر و غیب شدن» قطعات ناوها (شکایت کاربر، بچ ۹).
 *
 * مکانیزم از روی کد قطعی است: در سازنده‌های ناو/ناوچه/پدافند/رادار،
 * تکه‌هایی از هندسه فقط در جزئیات بالا ساخته می‌شوند (توپ نقطه‌زن و
 * قایق فقط detail=0، دیسک رادار و آرایه detail<=1، گام بدنه در
 * detail=2 عوض می‌شود). پس شمار وجه‌های یک کشتیِ ثابت، از یک زاویه‌ی
 * ثابت، نباید با فاصله‌ی دوربین عوض شود — اگر عوض شد یعنی جزئیات با
 * فاصله تعویض می‌شود و در بازی همان تعویض، «ظاهر شدن» قطعه است.
 *
 * چرا شمارش مستقیم وجه، نه تفاضل فریم در پرواز: هارنس پروازی امتحان
 * شد؛ ورود/خروج از frustum جهش‌های پله‌ای بزرگ‌تری از خودِ تعویض
 * جزئیات می‌سازد و سیگنال در نویز گم می‌شود (همان تله‌ای که سربرگ
 * CoplanarFaceOrderTest درباره‌ی روش‌های اندازه‌گیری قبلی هشدار داده).
 * این تست روش مستقیم است: کشتی ثابت، زاویه ثابت، فقط فاصله عوض می‌شود
 * و رندرر هر بار تازه است تا وضعیت LOD فقط از همان فاصله بیاید.
 *
 * تلورانس ۱۰ وجه برای اثرات لبه‌ای پرسپکتیو است؛ اثر تعویض جزئیات
 * یک مرتبه بزرگ‌تر است (ده‌ها تا صدها وجه).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class NavalDetailPinTest {

    private fun shipFaceCount(
        activity: ir.shahed.pahpad.MainActivity,
        mission: Mission,
        shipX: Float, shipZ: Float, shipRot: Float,
        dist: Float
    ): Int {
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(1)
        renderer.scene.enableDebugCapture(true)
        val a = shipRot + 1.5708f + 0.35f
        val cam = renderer.scene.cam
        cam.fov = 74f
        cam.x = shipX + sin(a) * dist
        cam.z = shipZ - cos(a) * dist
        cam.y = 60f
        // قرارداد جهت این موتور: yaw = atan2(dx, dz) به سمت هدف
        // (همان پوز محوری ShipDeckOrderTest که داده‌ی واقعی تولید کرد).
        cam.yaw = kotlin.math.atan2(shipX - cam.x, shipZ - cam.z)
        cam.pitch = -atan(50f / dist)
        cam.roll = 0f
        cam.viewport(960f, 540f)
        renderer.update(0.016f, mission)
        val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(bmp), mission, 960f, 540f)
        bmp.recycle()
        val sd = renderer.diagShips().minByOrNull {
            val dx = it.x - shipX; val dz = it.z - shipZ; dx * dx + dz * dz
        } ?: return -1
        return renderer.scene.debugFaces().count { it.group == sd.group }
    }

    @Test
    fun shipGeometryDoesNotDependOnCameraDistance() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val mission = Mission(Levels.byId("L4_4")!!, save, MissionMode.STORY, 5L)
        val world = mission.world

        val carrier = world.targets[0]
        val carrierCounts = listOf(500f, 1000f, 1500f).map {
            shipFaceCount(activity, mission, carrier.x, carrier.z, carrier.heading, it)
        }
        println("DETAILPIN carrier counts @500/1000/1500m: $carrierCounts")

        val escort = world.props.first { it.kind == PropKind.SHIP }
        val escortCounts = listOf(150f, 450f, 1000f).map {
            shipFaceCount(activity, mission, escort.x, escort.z, escort.rot, it)
        }
        println("DETAILPIN escort counts @150/450/1000m: $escortCounts")

        assertTrue("carrier must actually be in view (counts $carrierCounts)",
            carrierCounts.min() > 20)
        assertTrue("escort must actually be in view (counts $escortCounts)",
            escortCounts.min() > 20)
        assertTrue("carrier geometry changes with distance: $carrierCounts",
            carrierCounts.max() - carrierCounts.min() <= 10)
        assertTrue("escort geometry changes with distance: $escortCounts",
            escortCounts.max() - escortCounts.min() <= 10)
    }
}
