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
 * آزمون تلنگر وجه‌های هم‌سطح — پاراپت روی بام و کانتینر روی عرشه.
 *
 * روش سنجش (نسخهٔ سوم؛ دو روش قبلی معیوب بودند):
 *
 *   روش ۱ (رد شد): «بالای ساختمان را ببین، آیا خالی است؟» — به آستانهٔ
 *   دلخواه وابسته بود و با هر تغییر زاویه، نتیجه‌اش عوض می‌شد.
 *
 *   روش ۲ (رد شد): تفاضل دو فریم مجاور. حرکت دوربین خودش تفاضل ایجاد
 *   می‌کند، پس نمی‌شد تلنگر را از حرکت جدا کرد. اعداد با و بدون تغییر
 *   یکسان بودند — یعنی معیار بی‌اعتبار بود.
 *
 *   روش ۳ (این تست): **حلقهٔ بسته روی ترتیب رسم.** همان مقایسه‌ای که
 *   مرتب‌ساز نقاش انجام می‌دهد بازسازی می‌شود و برای هر جفت وجه هم‌سطح
 *   بررسی می‌شود که آیا ترتیبشان با نویز اعشاری می‌تواند برگردد.
 *
 * چرا این روش درست است: علت باگ، برخورد عددی در `Comparator` است — نه
 * ظاهر پیکسلی. اگر ترتیب دو وجه هم‌سطح به فاصله‌ای کمتر از خطای گِرد
 * برسد، همان تلنگر رخ می‌دهد. تست همان شرط را مستقیم روی اعداد واقعی
 * صحنه می‌سنجد، پس نه به رنگ وابسته است و نه به حرکت دوربین.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class CoplanarFaceOrderTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    /** خطای گِرد مؤثر float در عمق‌های چندگیاه‌متری. */
    private val eps = 1e-3f

    @Test
    fun parapetAndDeckBoxesNeverTieWithTheirSeat() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)

        // مرحلهٔ شهری: بیشترین تعداد ساختمان با پاراپت
        val urban = Levels.all.first { it.chapter == 3 }
        // مرحلهٔ دریایی: کشتی‌ها با کانتینر
        val naval = Levels.all.firstOrNull { it.env.name == "NAVAL" } ?: urban

        var seatsChecked = 0
        var ties = 0
        val worst = ArrayList<String>()

        for (level in listOf(urban, naval)) {
            val mission = Mission(level, save, MissionMode.STORY, 1234L)
            val renderer = WorldRenderer(activity)
            renderer.applyQuality(2)
            val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // چند موقعیت دوربین: با نزدیک شدن، عمق‌ها به هم می‌رسند و خطر
            // برخورد عددی بیشتر می‌شود.
            for (step in 0..7) {
                val cam = renderer.scene.cam
                val t = step / 7f
                cam.x = mission.x + t * 40f
                cam.y = mission.y + 3f + t * 25f
                cam.z = mission.z + 120f - t * 100f
                cam.yaw = mission.yaw + t * 0.4f
                cam.pitch = mission.pitch - t * 0.05f
                cam.roll = 0f
                cam.viewport(960f, 540f)

                renderer.draw(canvas, mission, 960f, 540f)

                val faces = renderer.scene.debugFaces()
                // جفت‌هایی که در فاصلهٔ بسیار کم هستند ولی هم‌سطح‌اند
                for (i in faces.indices) {
                    for (j in i + 1 until faces.size) {
                        val a = faces[i]
                        val b = faces[j]
                        if (Math.abs(a.depth - b.depth) > eps) continue
                        if (Math.abs(a.depthBias - b.depthBias) > eps) continue
                        seatsChecked++
                        // همان وجه‌های هم‌سطح با عمق یکسان → ترتیبشان در
                        // مرتب‌ساز نقاش قطعی نیست.
                        ties++
                        if (worst.size < 5) {
                            worst.add(
                                "level=${level.title} step=$step " +
                                    "depth=${"%.6f".format(a.depth)} " +
                                    "bias=${"%.4f".format(a.depthBias)}"
                            )
                        }
                    }
                }
            }
            renderer.scene.clear()
        }

        println("جفت‌های هم‌سطح بررسی‌شده: $seatsChecked")
        println("جفت‌های با عمق یکسان (خطر تلنگر): $ties")
        worst.forEach { println("  $it") }

        // شرط: تعداد جفت‌های هم‌سطحِ کاملاً یکسان باید صفر باشد.
        assertTrue(
            "وجوه هم‌سطح با عمق یکسان پیدا شد ($ties مورد از $seatsChecked) — " +
                "این‌ها همان پاراپت/کانتینر هستند که ظاهر و غیب می‌شوند",
            ties == 0
        )
    }

    /** رندر بصری برای بازرسی چشمی: پاراپت و کانتینر باید پیوسته باشند. */
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
        // از بالا و نزدیک، طوری که پشت‌بام‌ها واضح دیده شوند
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
    }
}
