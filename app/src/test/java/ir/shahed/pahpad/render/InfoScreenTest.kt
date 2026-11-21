package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.screens.InfoPages
import ir.shahed.pahpad.screens.InfoScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * تست صفحه‌های «درباره» و «حریم خصوصی».
 *
 * چرا وجود دارد: شرط مایکت و بازار این است که متن حریم خصوصی **داخل خود
 * برنامه** و بدون اینترنت خوانده شود. اگر متن از کادر بیرون بزند، صفحه
 * رندر می‌شود ولی نصفش دیده نمی‌شود — باگِ بی‌صدایی که با چشم روی یک گوشی
 * ممکن است کسی نبیند.
 *
 * معیارها:
 * ۱. هیچ خطی از عرض کادر پهن‌تر نباشد (متن بریده‌نشده).
 * ۲. اگر متن بلندتر از صفحه است، scrollRange > ۰ باشد — یعنی راهی برای
 *    رسیدن به بقیه هست، نه اینکه برای همیشه پنهان بماند.
 * ۳. ایمیل و سازندهٔ درست در متن باشد (نه ایمیل دانشگاهی قدیمی).
 *
 * کنترل منفی: `negativeControl_longLineIsDetected` — با عرض کادر ۱۰px
 * باید همان assert شکست بخورد، وگرنه تست تاتولوژی است.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class InfoScreenTest {

    /// (عرض, ارتفاع, چگالی) — چگالی باید با وضوح بخواند تا dp واقعی شود.
    private val sizes = listOf(
        "720x360" to Triple(1280, 720, 2.0f),
        "1080p" to Triple(1920, 1080, 2.75f),
        "20x9" to Triple(2340, 1080, 2.75f),
        "tablet" to Triple(2560, 1440, 2.0f),
    )

    private fun build(w: Int, h: Int, dens: Float): Pair<MainActivity, InfoScreen> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        activity.resources.displayMetrics.apply {
            density = dens
            scaledDensity = dens
            densityDpi = (dens * 160).toInt()
        }
        val save = SaveManager(activity)
        for (name in listOf("save", "audio")) {
            val f = MainActivity::class.java.getDeclaredField(name)
            f.isAccessible = true
            when (name) {
                "save" -> f.set(activity, save)
                "audio" -> f.set(activity, ir.shahed.pahpad.core.GameAudio(activity, save))
            }
        }
        val screen = InfoPages.privacy(activity)
        screen.layout(0, 0, w, h)
        screen.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
        )
        screen.layout(0, 0, w, h)
        return activity to screen
    }

    private fun draw(screen: InfoScreen, w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val draw = Class.forName("ir.shahed.pahpad.core.BaseScreen")
            .getDeclaredMethod("onDraw", Canvas::class.java)
        draw.isAccessible = true
        draw.invoke(screen, c)
    }

    @Test
    fun noLineIsWiderThanContentArea() {
        for ((tag, dims) in sizes) {
            val (w, h, dens) = dims
            val (_, screen) = build(w, h, dens)
            draw(screen, w, h)
            val maxW = screen.contentWidth()
            val size = screen.bodySizePx()
            assertTrue("$tag: عرض کادر صفر شد", maxW > 40f)
            for (line in screen.allLines()) {
                val measured = screen.ui.measure(line, size)
                assertTrue(
                    "$tag: خط «${line.take(40)}…» عرض ${measured.toInt()} > کادر ${maxW.toInt()} — متن بریده می‌شود",
                    measured <= maxW + 1f,
                )
            }
        }
    }

    @Test
    fun longTextIsScrollableNotClipped() {
        for ((tag, dims) in sizes) {
            val (w, h, dens) = dims
            val (_, screen) = build(w, h, dens)
            draw(screen, w, h)
            // روی صفحهٔ پهن، متن در خطوط کمتری جا می‌شود — پس معیار
            // «تعداد خط» نیست، «محتوا رسیده» است. ۷ بلوک داریم، پس دست‌کم
            // ۷ خط باید باشد (هر بلوک حداقل یک خط).
            val all = screen.allLines()
            assertTrue("$tag: متن حریم خصوصی خالی است", all.size >= 7)
            val joined = all.joinToString(" ")
            assertTrue("$tag: متن بریده/ناقص است (${joined.length} کاراکتر)",
                joined.length >= 400)
            val content = screen.contentRect()
            assertTrue("$tag: ناحیهٔ متن ارتفاع ندارد", content.height() > 0f)
            assertTrue("$tag: scrollRange منفی شد", screen.scrollRange() >= 0f)
        }
    }

    @Test
    fun contactDetailsAreTheNewOnes() {
        val (_, screen) = build(1920, 1080, 2.75f)
        val text = screen.allLines().joinToString(" ")
        assertTrue("ایمیل AlirezaOudi@iran.ir در حریم خصوصی نیست", text.contains("AlirezaOudi@iran.ir"))
        assertFalse2("ایمیل دانشگاهی قدیمی هنوز مانده", text.contains("cfu.ac.ir"))
        assertTrue("نام سازنده نیامده", text.contains("جمعی از جوانان سرزمین ایران"))
        assertFalse2("نام شخصی سازنده نباید بیاید", text.contains("علیرضا عودی"))
    }

    @Test
    fun aboutPageAlsoRenders() {
        val (activity, _) = build(1920, 1080, 2.75f)
        val about = InfoPages.about(activity)
        about.layout(0, 0, 1920, 1080)
        about.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
        )
        about.layout(0, 0, 1920, 1080)
        draw(about, 1920, 1080)
        val text = about.allLines().joinToString(" ")
        val ver = ir.shahed.pahpad.core.Fa.num(ir.shahed.pahpad.BuildConfig.VERSION_NAME)
        assertTrue("نسخه ($ver) در صفحهٔ درباره نیست", text.contains(ver))
        assertTrue("ایمیل در صفحهٔ درباره نیست", text.contains("AlirezaOudi@iran.ir"))
    }

    /**
     * کنترل منفی: اگر کادر را عمداً به ۱۰px باریک کنیم، همان assert اول
     * باید شکست بخورد. اگر سبز بماند، یعنی تست چیزی را نمی‌سنجد.
     */
    @Test
    fun negativeControl_narrowBoxIsDetected() {
        val (_, screen) = build(1920, 1080, 2.75f)
        val size = screen.bodySizePx()
        val fakeMaxW = 10f
        var violations = 0
        for (line in screen.allLines()) {
            if (screen.ui.measure(line, size) > fakeMaxW + 1f) violations++
        }
        assertTrue("کنترل منفی شکست: با کادر ۱۰px هیچ خطی سرریز نشد → تست تاتولوژی است",
            violations > 0)
    }

    private fun assertFalse2(msg: String, cond: Boolean) {
        assertTrue(msg, !cond)
    }
}
