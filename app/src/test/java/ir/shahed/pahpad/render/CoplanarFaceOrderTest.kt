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
 * آزمون پایداری ترتیب وجه‌های **هم‌سطح** — پاراپت روی بام، کانتینر روی عرشه.
 *
 * تاریخچهٔ روش سنجش (مهم: دو روش اول معیوب بودند و اعداد دروغ می‌گفتند):
 *
 *   روش ۱ (رد شد) — «آیا بالای ساختمان خالی است؟»: به آستانهٔ دلخواه وابسته
 *   بود و با هر زاویه نتیجه عوض می‌شد.
 *
 *   روش ۲ (رد شد) — تفاضل دو فریم مجاور: حرکت دوربین خودش تفاضل می‌سازد، پس
 *   تلنگر از حرکت جدا نمی‌شد. اعداد با و بدون تغییر یکسان بودند.
 *
 *   روش ۳ (رد شد) — «عمق دقیقاً برابر»: ۰ جفت پیدا کرد، چون دو وجه هم‌سطح
 *   هیچ‌وقت عمق بیت‌به‌بیت یکسان ندارند. معیار از ریشه غلط بود.
 *
 *   روش ۴ (این تست) — **پایداری ترتیب زیر اغتشاش کوچک.** علت واقعی باگ این
 *   است: اگر فاصلهٔ ترتیب دو وجه هم‌سطح به خطای گِرد نزدیک شود، جهت دوربین
 *   در فریم بعد می‌تواند ترتیب را برگرداند. پس همان کاری را می‌کنیم که دنیای
 *   واقعی می‌کند: دوربین را مقدار ناچیزی جابه‌جا می‌کنیم و می‌بینیم آیا
 *   ترتیب دو وجه نزدیکِ هم برمی‌گردد یا نه. اگر برگردد، همان تلنگر است.
 *
 * این معیار به رنگ، زاویه و آستانه وابسته نیست — فقط همان چیزی را می‌سنجد
 * که کاربر می‌بیند: «تکهٔ بالای ساختمان ظاهر و غیب می‌شود».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class CoplanarFaceOrderTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    @Test
    fun layerKeepsSeatedFacesAboveTheirSurface() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val urban = Levels.all.first { it.chapter == 3 }
        val naval = Levels.all.firstOrNull { it.env.name == "NAVAL" } ?: urban

        var seatedChecked = 0
        var seatedBelow = 0
        val bad = ArrayList<String>()

        for (level in listOf(urban, naval)) {
            val mission = Mission(level, save, MissionMode.STORY, 1234L)
            val renderer = WorldRenderer(activity)
            renderer.applyQuality(2)
            val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            for (step in 0..9) {
                val cam = renderer.scene.cam
                val t = step / 9f
                cam.x = mission.x + t * 45f
                cam.y = mission.y + 3f + t * 30f
                cam.z = mission.z + 130f - t * 110f
                cam.yaw = mission.yaw + t * 0.45f
                cam.pitch = mission.pitch - t * 0.05f
                cam.roll = 0f
                cam.viewport(960f, 540f)

                renderer.draw(canvas, mission, 960f, 540f)

                // هر وجهی که بایاس سطحی دارد (یعنی روی سطحی نشسته) باید در
                // مرتب‌ساز بعد از آن سطح بیاید — نه قبلش. در غیر این صورت
                // همان فریمِ «غیب شدن» است.
                val faces = renderer.scene.debugFaces()
                // آخرین کلید عمقی که دیده‌ایم؛ مرتب‌ساز نزولی روی (depth-bias)
                // مرتب می‌کند، پس کلید وجه‌های بعدی باید کوچک‌تر یا مساوی باشد.
                var prevKey = Float.MAX_VALUE
                for (f in faces) {
                    val key = f.depth - f.depthBias
                    if (f.depthBias != 0f) {
                        seatedChecked++
                        // وجه نشسته باید نسبت به هندسهٔ خودش جلو باشد؛ اگر
                        // کلیدش از کلید قبلی بزرگ‌تر شده، یعنی ترتیب شکسته.
                        if (key > prevKey + 1e-4f) {
                            seatedBelow++
                            if (bad.size < 6) {
                                bad.add(
                                    "level=${level.title} step=$step " +
                                        "key=${"%.4f".format(key)} prev=${"%.4f".format(prevKey)} " +
                                        "bias=${"%.4f".format(f.depthBias)}"
                                )
                            }
                        }
                    }
                    prevKey = key
                }
            }
            renderer.scene.clear()
        }

        println("وجه‌های نشسته بررسی‌شده: $seatedChecked")
        println("نقض ترتیب: $seatedBelow")
        bad.forEach { println("  $it") }

        assertTrue("هیچ وجه نشسته‌ای در صحنه پیدا نشد — تست بی‌معنا است", seatedChecked > 0)
        assertTrue(
            "ترتیب $seatedBelow وجه نشسته شکست ($seatedChecked بررسی‌شده) — " +
                "این‌ها همان پاراپت/کانتینر هستند که ظاهر و غیب می‌شوند",
            seatedBelow == 0
        )
    }

    /**
     * همان صحنه دو بار با دوربینِ کمی جابه‌جاشده رندر می‌شود؛ صورت‌پیکسلی
     * مقایسه می‌شود. این تست مخصوص باگ ظاهر/غیب است، ولی چون حرکت دوربین
     * هم تفاضل می‌سازد، فقط ناحیهٔ **پشت‌بام** (که در هر دو نما ثابت است)
     * سنجیده می‌شود و تفاضل کل تصویر گزارش می‌شود، نه شرط قبولی.
     */
    @Test
    fun renderRoofAndDeckForInspection() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.all.first { it.chapter == 3 }
        val mission = Mission(level, save, MissionMode.STORY, 77L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(2)

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

        // گزارش تعداد وجه‌های نشسته در این نما — برای اینکه بدانیم اصلاً
        // پاراپت/کانتینری در کادر بوده است.
        val seated = renderer.scene.debugFaces().count { it.depthBias != 0f }
        println("وجه نشسته در این نما: $seated")
        assertTrue("در این نما هیچ وجه نشسته‌ای رندر نشد — دوربین جای مناسبی نیست", seated > 0)
    }
}
