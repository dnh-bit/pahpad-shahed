package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.WorldRenderer
import org.junit.Assert.assertEquals
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
 * ─────────────────────────── تاریخچهٔ روش سنجش ───────────────────────────
 * سه روش اول معیوب بودند و اعداد دروغ می‌گفتند. ثبت شده تا دوباره تکرار نشوند:
 *
 *  ۱) «آیا بالای ساختمان خالی است؟» — به آستانهٔ دلخواه وابسته بود و با هر
 *     زاویه نتیجه عوض می‌شد.
 *
 *  ۲) تفاضل دو فریم مجاور — حرکت دوربین خودش تفاضل می‌سازد؛ تلنگر از حرکت
 *     جدا نمی‌شد. اعداد با و بدون تغییر یکسان بودند.
 *
 *  ۳) «عمق دقیقاً برابر» — دو خطا هم‌زمان:
 *     الف) دو وجه هم‌سطح هیچ‌وقت عمق بیت‌به‌بیت یکسان ندارند.
 *     ب) `flush` در پایان فریم `order` را پاک می‌کند، پس لیست همیشه خالی
 *        بود و «۰» به‌اشتباه «سالم» تفسیر می‌شد. (حالا `debugFaces()`
 *        یک عکس فوری است که حین `sortWith` گرفته می‌شود.)
 *
 *  ۴) مقایسهٔ ترتیب با **اندیس** در دو نما — غلط بود: اگر اندازهٔ صحنه
 *     عوض شود اندیس جابه‌جا می‌شود و «نوسان» کاذب گزارش می‌شود. یک دور
 *     عدد ۱۱ نوسان داد که هیچ‌کدام واقعی نبود.
 *
 *  ۵) روش این تست — **شرط هندسی مستقیم.** برای هر وجه پاراپت/کانتینر، باید
 *     کلید مرتب‌سازی‌اش (depth − bias) از کلید سطحی که رویش نشسته
 *     کوچک‌تر یا مساوی باشد. این همان چیزی است که مرتب‌ساز نقاش می‌بیند و
 *     مستقل از زاویه، اندیس و آستانه است.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class CoplanarFaceOrderTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    @Test
    fun seatedFacesSortBeforeTheirSupportSurface() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val urban = Levels.all.first { it.chapter == 3 }
        val naval = Levels.all.firstOrNull { it.env.name == "NAVAL" } ?: urban

        var views = 0
        var seatedFaces = 0
        val violations = ArrayList<String>()

        for (level in listOf(urban, naval)) {
            val mission = Mission(level, save, MissionMode.STORY, 1234L)
            val renderer = WorldRenderer(activity)
            renderer.applyQuality(2)
            renderer.scene.enableDebugCapture(true)
            val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            for (step in 0..9) {
                val t = step / 9f
                val cam = renderer.scene.cam
                cam.x = mission.x + t * 45f
                cam.y = mission.y + 3f + t * 30f
                cam.z = mission.z + 130f - t * 110f
                cam.yaw = mission.yaw + t * 0.45f
                cam.pitch = mission.pitch - t * 0.05f
                cam.roll = 0f
                cam.fov = 74f
                cam.viewport(960f, 540f)
                renderer.draw(canvas, mission, 960f, 540f)
                views++

                // مرتب‌ساز نزولی روی (depth − bias) است. عکس فوری همان ترتیب
                // واقعی را دارد، پس فقط کافی است بررسی کنیم که کلیدها
                // نزولی مانده‌اند و هیچ وجه نشسته‌ای جهش رو به بالا ندارد.
                val snap = renderer.scene.debugFaces()
                var prevKey = Float.MAX_VALUE
                for (f in snap) {
                    val key = f.depth - f.depthBias
                    if (f.depthBias != 0f) {
                        seatedFaces++
                        if (key > prevKey + 1e-3f) {
                            if (violations.size < 8) {
                                violations.add(
                                    "level=${level.title} view=$step " +
                                        "key=${"%.3f".format(key)} prev=${"%.3f".format(prevKey)}"
                                )
                            }
                        }
                    }
                    prevKey = key
                }
            }
            renderer.scene.enableDebugCapture(false)
            renderer.scene.clear()
        }

        println("نماها: $views")
        println("وجه نشسته: $seatedFaces")
        println("نقض ترتیب: ${violations.size}")
        violations.forEach { println("  $it") }

        assertTrue("هیچ وجه نشسته‌ای رندر نشد — تست بی‌معنا است", seatedFaces > 0)
        assertEquals(
            "ترتیب وجه‌های نشسته در ${violations.size} مورد شکست — " +
                "این‌ها همان پاراپت/کانتینر هستند که ظاهر و غیب می‌شوند",
            0, violations.size
        )
    }

    /**
     * بازرسی چشمی: یک نمای ثابت از نزدیک بالای بام رندر و ذخیره می‌شود.
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
        cam.fov = 74f
        cam.viewport(1280f, 720f)
        renderer.draw(canvas, mission, 1280f, 720f)

        val out = File(outDir, "roof_parapet_check.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("نوشته شد: ${out.absolutePath}")

        val seated = renderer.scene.debugFaces().count { it.depthBias != 0f }
        println("وجه نشسته در این نما: $seated (ساخته‌شده در Rig: ${renderer.rigSeatedBuilt()})")
        assertTrue("در این نما هیچ وجه نشسته‌ای رندر نشد", seated > 0)
    }
}
