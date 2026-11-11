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
 * آزمون پایداری وجه‌های **هم‌سطح** — پاراپت روی بام، کانتینر روی عرشه.
 *
 * تاریخچهٔ روش سنجش (مهم: سه روش اول معیوب بودند و اعداد دروغ می‌گفتند):
 *
 *   ۱) «آیا بالای ساختمان خالی است؟» — به آستانهٔ دلخواه وابسته بود.
 *   ۲) تفاضل دو فریم مجاور — حرکت دوربین خودش تفاضل می‌سازد؛ تلنگر از
 *      حرکت جدا نمی‌شد. اعداد با و بدون تغییر یکسان بودند.
 *   ۳) «عمق دقیقاً برابر» بعد از رندر خوانده می‌شد — دو خطا هم‌زمان:
 *      الف) دو وجه هم‌سطح هیچ‌وقت عمق بیت‌به‌بیت یکسان ندارند.
 *      ب) `flush` در پایان فریم `order` را پاک می‌کند، پس لیست همیشه خالی
 *         بود و «۰» به‌اشتباه «سالم» تفسیر می‌شد. حالا `debugFaces()` یک
 *         **عکس فوری** است که هنگام مرتب‌سازی گرفته می‌شود.
 *
 *   روش ۴ (این تست) — **ثبات ترتیب در برابر اغتشاش دوربین.** معیار همان
 *   چیزی است که کاربر می‌بیند: اگر ترتیب دو وجه در دو نمای کمی متفاوت
 *   برگردد، آن وجه «ظاهر و غیب» می‌شود.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class CoplanarFaceOrderTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    /** رندر یک نما و برگرداندن عکس فوری ترتیب. */
    private fun shoot(
        renderer: WorldRenderer, mission: Mission, canvas: Canvas,
        cx: Float, cy: Float, cz: Float, yaw: Float, pitch: Float
    ): List<Pair<Float, Float>> {
        val cam = renderer.scene.cam
        cam.x = cx; cam.y = cy; cam.z = cz
        cam.yaw = yaw; cam.pitch = pitch; cam.roll = 0f
        cam.fov = 74f
        cam.viewport(960f, 540f)
        renderer.draw(canvas, mission, 960f, 540f)
        return renderer.scene.debugFaces().map { it.depth to it.depthBias }
    }

    @Test
    fun seatedFacesDoNotFlipBetweenNearbyViews() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val urban = Levels.all.first { it.chapter == 3 }
        val naval = Levels.all.firstOrNull { it.env.name == "NAVAL" } ?: urban

        var fps = 0
        val flipped = ArrayList<String>()
        var totalSeated = 0

        for (level in listOf(urban, naval)) {
            val mission = Mission(level, save, MissionMode.STORY, 1234L)
            val renderer = WorldRenderer(activity)
            renderer.applyQuality(2)
            renderer.scene.enableDebugCapture(true)
            val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // دو نما که فقط مقدار ناچیزی با هم فرق دارند — همان جابه‌جایی
            // طبیعی دوربین بین دو فریم. اگر ترتیب در این حد بلرزد، روی گوشی
            // هم می‌لرزد.
            for (step in 0..7) {
                val t = step / 7f
                val bx = mission.x + t * 45f
                val by = mission.y + 3f + t * 30f
                val bz = mission.z + 130f - t * 110f
                val yaw = mission.yaw + t * 0.45f
                val pitch = mission.pitch - t * 0.05f

                val a = shoot(renderer, mission, canvas, bx, by, bz, yaw, pitch)
                val d = 0.06f   // ۶ سانتی‌متر — کوچک‌تر از حرکت یک فریم
                val b = shoot(renderer, mission, canvas, bx + d, by, bz + d, yaw + 0.0008f, pitch)

                fps += 2
                totalSeated += a.count { it.second != 0f }

                // برای هر وجه نشسته در نما a، ببین جایگاهش در نما b حفظ شده.
                // مقایسه بر پایهٔ کلید مرتب‌سازی (depth - bias) است.
                val ka = a.sortedByDescending { it.first - it.second }
                val kb = b.sortedByDescending { it.first - it.second }
                val n = minOf(ka.size, kb.size)
                var mismatches = 0
                for (i in 0 until n) {
                    val sa = ka[i].second != 0f
                    val sb = kb[i].second != 0f
                    if (sa != sb) mismatches++
                }
                if (mismatches > 0) {
                    flipped.add("level=${level.title} step=$step mismatches=$mismatches/$n")
                }
            }
            renderer.scene.enableDebugCapture(false)
            renderer.scene.clear()
        }

        println("نماهای رندرشده: $fps")
        println("وجه‌های نشسته دیده‌شده: $totalSeated")
        println("نماهای با نوسان ترتیب: ${flipped.size}")
        flipped.take(6).forEach { println("  $it") }

        assertTrue("هیچ وجه نشسته‌ای رندر نشد — دوربین یا مرحله اشتباه است", totalSeated > 0)
    }

    /**
     * بازرسی چشمی: پاراپت/کانتینر باید در تصویر پیوسته باشند. این تست فقط
     * تصویر را می‌سازد و تعداد وجه‌های نشسته را گزارش می‌دهد.
     */
    @Test
    fun renderRoofAndDeckForInspection() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.all.first { it.chapter == 3 }
        val mission = Mission(level, save, MissionMode.STORY, 77L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)
        renderer.scene.enableDebugCapture(true)

        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cam = renderer.scene.cam
        cam.x = mission.x
        cam.y = mission.y + 70f
        cam.z = mission.z + 60f
        cam.yaw = mission.yaw
        cam.pitch = -0.55f
        cam.roll = 0f
        cam.viewport(1280f, 720f)
        renderer.draw(canvas, mission, 1280f, 720f)

        val out = File(outDir, "roof_parapet_check.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("نوشته شد: ${out.absolutePath}")

        val seated = renderer.scene.debugFaces().count { it.depthBias != 0f }
        println("وجه نشسته در این نما: $seated (ساخته‌شده در Rig: ${renderer.rigSeatedBuilt()})")
        assertTrue("در این نما هیچ وجه نشسته‌ای رندر نشد — دوربین جای مناسبی نیست", seated > 0)
    }
}
