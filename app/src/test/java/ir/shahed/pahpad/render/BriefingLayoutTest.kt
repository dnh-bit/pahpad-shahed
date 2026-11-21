package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.screens.BriefingScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * تست صفحهٔ بریفینگ — سه شکایت واقعی کاربر که هیچ تستی نمی‌گرفت.
 *
 * تاریخچهٔ شکست‌ها (همه در نسخهٔ ۰.۲.۸ دیده شدند):
 * ۱. «این بخش عکس اهداف خیلی بدتر شده» — مناطق پدافند با `drawOval`
 *    کشیده می‌شدند و بیضی‌های پهنِ درهم می‌ساختند. معیار: نسبت عرض به
 *    ارتفاع هر منطقه ≈ ۱. اگر بیضی شود این نسبت از ~۳ رد می‌شود.
 * ۲. «اون پایینش باید پهباد شاهد باشه» — کارت «پهباد انتخابی» با
 *    `fits()` رد می‌شد و **هیچ‌وقت رسم نمی‌شد**. معیار: وجود جعبهٔ
 *    `droneCard` در خروجی رسم، و داخل بودنش در کادر.
 * ۳. «بالا سمت راست هم یه جمله دیده نمی‌شه» — زیرنویس «بریفینگ
 *    ماموریت» بیرون از بنر و در نوار ۹dp می‌افتاد. معیار: زیرنویس
 *    کاملاً **داخل** جعبهٔ بنر باشد.
 *
 * هر سه معیار از **خروجی رسم** می‌خوانند (جعبه‌هایی که کد واقعاً کشید)،
 * نه از محاسبهٔ دوبارهٔ فرمول — چون فرمول درست و رسم غلط بود.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class BriefingLayoutTest {

    /** اندازه‌های واقعی: کوتاه‌ترین و بلندترین نسبت‌ها بدترین حالت‌اند */
    private val sizes = listOf(
        "720x360" to (1280 to 720),
        "1080p" to (1920 to 1080),
        "20x9" to (2340 to 1080),
        "tablet" to (2560 to 1440),
    )

    private fun drawOnce(w: Int, h: Int): BriefingScreen {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val save = SaveManager(activity)
        for (name in listOf("save", "audio")) {
            val f = MainActivity::class.java.getDeclaredField(name)
            f.isAccessible = true
            when (name) {
                "save" -> f.set(activity, save)
                "audio" -> f.set(activity, ir.shahed.pahpad.core.GameAudio(activity, save))
            }
        }
        // سختی‌ترین سطح: بیشترین پدافند و رادار و جنگ الکترونیک + متن بلند
        val level = Levels.all.maxByOrNull { it.aaSites + it.radarZones + it.ewZones }!!
        val screen = BriefingScreen(activity, level, MissionMode.STORY)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        screen.layout(0, 0, w, h)
        screen.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
        )
        screen.layout(0, 0, w, h)
        val draw = Class.forName("ir.shahed.pahpad.core.BaseScreen")
            .getDeclaredMethod("onDraw", Canvas::class.java)
        draw.isAccessible = true
        draw.invoke(screen, c)
        return screen
    }

    // ---------------------------------------------------------------- ۱. نقشه

    @Test
    fun zonesAreRoundNotSquashed() {
        for ((tag, dims) in sizes) {
            val (w, h) = dims
            val screen = drawOnce(w, h)
            val mapBox = screen.debugBoxes().firstOrNull { it.name == "map" }
                ?: fail("$tag: جعبهٔ نقشه رسم نشد")
            val zone = screen.debugBoxes().firstOrNull { it.name == "zone0" }
                ?: fail("$tag: هیچ منطقه‌ای رسم نشد")
            val rw = zone.rect.width()
            val rh = zone.rect.height()
            assertTrue("$tag: منطقه عرض ${rw.toInt()} و ارتفاع ${rh.toInt()} — بیضی کشیده شده",
                rw > 0f && rh > 0f && rw / rh in 0.80f..1.25f)
            // و نباید از نقشه بزرگ‌تر باشد
            assertTrue("$tag: منطقه از نقشه بزرگ‌تر است",
                zone.rect.width() <= mapBox.rect.width() * 1.6f)
        }
    }

    // ------------------------------------------------------------- ۲. پهباد

    @Test
    fun droneCardIsAlwaysDrawn() {
        for ((tag, dims) in sizes) {
            val (w, h) = dims
            val screen = drawOnce(w, h)
            val card = screen.debugBoxes().firstOrNull { it.name == "droneCard" }
                ?: fail("$tag: کارت «پهباد انتخابی» رسم نشد — همان باگ «اون پایینش باید پهباد باشه»")
            assertTrue("$tag: ارتفاع کارت پهباد ${card.rect.height().toInt()} کم است",
                card.rect.height() >= 54f)
            assertTrue("$tag: کارت پهباد از پایین کادر بیرون زده",
                card.rect.bottom <= h - 60f)
        }
    }

    // ----------------------------------------------------------- ۳. زیرنویس

    @Test
    fun subtitleIsInsideBanner() {
        for ((tag, dims) in sizes) {
            val (w, h) = dims
            val screen = drawOnce(w, h)
            val banner = screen.debugBoxes().firstOrNull { it.name == "banner" }
                ?: fail("$tag: بنر رسم نشد")
            val sub = screen.debugBoxes().firstOrNull { it.name == "subtitle" }
                ?: fail("$tag: زیرنویس رسم نشد")
            assertTrue("$tag: زیرنویس از بالای بنر بیرون زده (${sub.rect})",
                sub.rect.top >= banner.rect.top - 1f)
            assertTrue("$tag: زیرنویس از پایین بنر بیرون زده (${sub.rect} vs ${banner.rect})",
                sub.rect.bottom <= banner.rect.bottom + 1f)
            val title = screen.debugBoxes().firstOrNull { it.name == "title" }
                ?: fail("$tag: عنوان رسم نشد")
            assertTrue("$tag: عنوان و زیرنویس روی هم افتاده‌اند", title.rect.bottom <= sub.rect.top)
        }
    }

    // -------------------------------------------------- ۴. برچسب نقطهٔ پرتاب

    @Test
    fun droneLabelIsInsideMap() {
        for ((tag, dims) in sizes) {
            val (w, h) = dims
            val screen = drawOnce(w, h)
            val mapBox = screen.debugBoxes().firstOrNull { it.name == "map" }
                ?: fail("$tag: جعبهٔ نقشه رسم نشد")
            val label = screen.debugBoxes().firstOrNull { it.name == "droneLabel" }
                ?: fail("$tag: برچسب پهباد رسم نشد")
            assertTrue("$tag: برچسب «پهباد شاهد» بیرون از نقشه افتاد (${label.rect}) — " +
                "قبلاً زیر clipRect بریده می‌شد",
                label.rect.top >= mapBox.rect.top - 1f && label.rect.bottom <= mapBox.rect.bottom + 1f)
            // برچسب نباید روی راهنمای رنگی پایین نقشه بیفتد
            val colors = screen.debugBoxes().firstOrNull { it.name == "mapLegendColors" }
            if (colors != null) {
                val overlap = RectF(label.rect).apply { intersect(colors.rect) }
                assertTrue("$tag: برچسب پهباد روی راهنمای رنگی افتاد " +
                    "label=${label.rect} colors=${colors.rect}",
                    overlap.isEmpty)
            }
        }
    }

    private fun fail(msg: String): Nothing = throw AssertionError(msg)
}
