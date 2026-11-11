package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.Rig
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
 * ─────────────────────── تاریخچهٔ روش سنجش (بخوانید) ───────────────────────
 * چهار روش اول غلط بودند و سبز می‌شدند، در حالی که کاربر باگ را می‌دید.
 * ثبت شده تا دوباره تکرار نشوند:
 *
 *  ۱) «آیا بالای ساختمان خالی است؟» — وابسته به آستانهٔ دلخواه.
 *  ۲) تفاضل دو فریم مجاور — حرکت دوربین خودش تفاضل می‌سازد؛ تلنگر از حرکت
 *     جدا نمی‌شد.
 *  ۳) «عمق دقیقاً برابر» — دو وجه هم‌سطح هیچ‌وقت عمق بیت‌به‌بیت یکسان
 *     ندارند. و بدتر: `flush` پایان فریم `order` را پاک می‌کرد، پس لیست
 *     خالی بود و «۰» به‌اشتباه «سالم» تفسیر می‌شد.
 *  ۴) «لیست مرتب است» — **تاتولوژی**. `debugFaces()` خودش خروجی `sortWith`
 *     است؛ چک کردن اینکه مرتب است هیچ اطلاعاتی دربارهٔ درستی ندارد. این
 *     روش با کد باگ‌دار هم سبز شد و یک دور کامل را هدر داد.
 *
 *  روش ۵ (این تست) — **مقایسهٔ مستقیم کلید پاراپت با کلید بام زیرش.** تست
 *  هر وجه را با برچسبش پیدا می‌کند (برچسب فقط تشخیصی است و در مرتب‌سازی
 *  هیچ نقشی ندارد) و می‌سنجد که آیا کلید واقعی پاراپت از کلید واقعی بام
 *  کوچک‌تر است یا نه. این معیار در نسخهٔ باگ‌دار **قرمز می‌شود**، چون دقیقاً
 *  همان نابرابری‌ای است که نقض می‌شد.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class CoplanarFaceOrderTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    @Test
    fun parapetSortsInFrontOfItsRoofFromEveryAngle() {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val urban = Levels.all.first { it.chapter == 3 }
        val naval = Levels.all.firstOrNull { it.env.name == "NAVAL" } ?: urban

        var seatFaces = 0
        var compared = 0
        val behindRoof = ArrayList<String>()

        for (level in listOf(urban, naval)) {
            val mission = Mission(level, save, MissionMode.STORY, 1234L)
            val renderer = WorldRenderer(activity)
            renderer.applyQuality(2)
            renderer.scene.enableDebugCapture(true)
            val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // زاویه‌های متنوع: از هر طرف، از بالا، از پایین.
            for (step in 0..15) {
                val t = step / 15f
                val ang = t * 6.2831853f
                val cam = renderer.scene.cam
                cam.x = mission.x + sinf(ang) * 90f
                cam.z = mission.z + cosf(ang) * 90f
                cam.y = mission.y + 8f + t * 70f
                cam.yaw = ang
                cam.pitch = -0.25f - t * 0.45f
                cam.roll = 0f
                cam.fov = 74f
                cam.viewport(960f, 540f)
                renderer.draw(canvas, mission, 960f, 540f)

                // عکس فوری: ترتیب واقعی رسم + برچسب + کلید مرتب‌سازی.
                val snap = renderer.scene.debugFaces()
                val seats = snap.filter { it.tag == Rig.TAG_SEAT }
                val roofs = snap.filter { it.tag == Rig.TAG_SURFACE }
                seatFaces += seats.size
                if (seats.isEmpty() || roofs.isEmpty()) continue

                for (s in seats) {
                    // بامِ خودِ این ساختمان — با شناسهٔ گروه، نه با حدس از
                    // همپوشانی تصویری (که بام ساختمان همسایه را انتخاب می‌کرد
                    // و «نقض» کاذب می‌ساخت).
                    val roof = roofs.firstOrNull { it.group == s.group } ?: continue
                    compared++
                    val seatKey = s.depth - s.depthBias
                    val roofKey = roof.depth - roof.depthBias
                    // مرتب‌ساز نزولی است: کلید بزرگ‌تر **اول** کشیده می‌شود.
                    // پاراپت باید بعد از بام کشیده شود ⇒ کلیدش باید کوچک‌تر
                    // یا مساوی باشد. اگر بزرگ‌تر باشد، بام رویش را می‌پوشاند
                    // و پاراپت «غیب» می‌شود.
                    if (seatKey > roofKey + 1e-2f) {
                        if (behindRoof.size < 8) {
                            behindRoof.add(
                                "level=${level.title} step=$step " +
                                    "parapet=${"%.3f".format(seatKey)} roof=${"%.3f".format(roofKey)} " +
                                    "Δ=${"%.3f".format(seatKey - roofKey)}"
                            )
                        }
                    }
                }
            }
            renderer.scene.enableDebugCapture(false)
            renderer.scene.clear()
        }

        println("وجه پاراپت/کانتینر دیده‌شده: $seatFaces")
        println("جفت‌های پاراپت-بام مقایسه‌شده: $compared")
        println("پاراپت‌هایی که پشت بام افتادند: ${behindRoof.size}")
        behindRoof.forEach { println("  $it") }

        assertTrue("هیچ وجه نشسته‌ای رندر نشد — دوربین یا مرحله اشتباه است", seatFaces > 0)
        assertTrue("هیچ جفت پاراپت-بامی همپوشانی نداشت — تست بی‌معنا است", compared > 0)
        assertEquals(
            "${behindRoof.size} پاراپت/کانتینر پشت سطح خودشان افتادند — " +
                "همان چیزی که کاربر به‌عنوان «ظاهر و غیب می‌شود» می‌بیند",
            0, behindRoof.size
        )
    }

    private fun sinf(v: Float): Float = kotlin.math.sin(v.toDouble()).toFloat()
    private fun cosf(v: Float): Float = kotlin.math.cos(v.toDouble()).toFloat()

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

        val byTag = renderer.scene.debugFaces().groupingBy { it.tag }.eachCount()
        println("وجه‌ها بر اساس برچسب: $byTag")
        assertTrue("هیچ پاراپتی رندر نشد", (byTag[Rig.TAG_SEAT] ?: 0) > 0)
    }
}
