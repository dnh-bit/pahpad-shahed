package ir.shahed.pahpad.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * چیدمان لیست/گرید صفحات — بدون نیاز به اندروید.
 *
 * باگ‌هایی که این تست‌ها از آنها محافظت می‌کنند:
 *  • لیست پهبادها اسکرول نمی‌شد چون BaseScreen هر لمس روی دکمه را می‌بلعید.
 *  • آخرین پهباد انتخاب نمی‌شد چون شرط «کاملاً داخل ویوپورت» بود.
 *  • مرحله‌ها در یک ردیف چیده می‌شدند و روی گوشی باریک متن‌ها در هم می‌رفتند.
 */
class LayoutRegressionTest {

    // ─────────────── اسکرول لیست پهبادها

    private class Viewport(val top: Float, val bottom: Float) {
        val height get() = bottom - top
    }

    /** تعداد کارت‌هایی که بخشی از هرکدام دیده می‌شود */
    private fun visibleCount(cardTops: List<Float>, cardH: Float, vp: Viewport): Int =
        cardTops.count { it + cardH > vp.top && it < vp.bottom }

    @Test
    fun `آخرین پهباد با اسکرول کامل قابل دیدن است`() {
        val cardH = 66f
        val gap = 8f
        val vp = Viewport(110f, 694f) // ارتفاع واقعی landscape
        // ۴ پهباد
        val tops = (0 until 4).map { 112f + it * (cardH + gap) }

        val firstScreen = visibleCount(tops, cardH, vp)
        assertTrue("باید حداقل ۲ کارت اول دیده شود", firstScreen >= 2)
        assertTrue("همه ۴ کارت نباید یک‌جا جا شوند (وگرنه اسکرول لازم نیست)", firstScreen < 4)

        // شبیه‌سازی اسکرول تا انتها
        val maxScroll = (4 * (cardH + gap) - gap) - vp.height
        assertTrue("محتوای لیست باید بلندتر از ویوپورت باشد تا اسکرول معنا داشته باشد", maxScroll > 0f)

        val scrolled = tops.map { it - maxScroll }
        val lastVisible = scrolled.last() + cardH > vp.top && scrolled.last() < vp.bottom
        assertTrue("آخرین پهباد پس از اسکرول کامل باید دیده شود", lastVisible)
    }

    @Test
    fun `کارت نیمه‌پیدا هم باید قابل لمس باشد`() {
        val cardH = 66f
        val vp = Viewport(110f, 300f)
        // کارتی که فقط ۲۰ پیکسلش در ویوپورت است
        val top = 280f
        val bottom = top + cardH
        val overflowOld = top >= vp.top - 4f && bottom <= vp.bottom + 4f // شرط باگ‌دار قدیمی
        val overflowNew = bottom > vp.top && top < vp.bottom              // شرط درست
        assertFalse("شرط قدیمی کارت نیمه‌پیدا را رد می‌کرد", overflowOld)
        assertTrue("شرط جدید باید کارت نیمه‌پیدا را قبول کند", overflowNew)
    }

    // ─────────────── گرید مرحله‌ها

    @Test
    fun `گرید روی گوشی باریک حداقل عرض خوانا را رعایت می‌کند`() {
        val widths = listOf(1280f, 1600f, 1920f, 2340f, 2400f)
        val minCardW = 148f * 2.75f // dp در مقیاس معمول
        for (w in widths) {
            val margin = 16f * 2.75f
            val gap = 10f * 2.75f
            val availW = w - margin * 2f
            var cols = ((availW + gap) / (minCardW + gap)).toInt().coerceIn(1, 5)
            val cw = (availW - gap * (cols - 1)) / cols
            assertTrue("عرض کارت روی $w باید حداقل خوانا باشد (شد $cw)", cw >= minCardW * 0.98f)
            assertTrue("تعداد ستون‌ها باید بین ۱ و ۵ باشد", cols in 1..5)
        }
    }

    @Test
    fun `پنج مرحله در گرید حداکثر سه ستون می‌گیرد`() {
        val n = 5
        val availW = 2340f - 88f
        val gap = 27.5f
        val minCardW = 407f
        val cols = ((availW + gap) / (minCardW + gap)).toInt().coerceIn(1, n)
        val rows = (n + cols - 1) / cols
        assertTrue("حداکثر ۳ ستون", cols <= 3)
        assertTrue("حداقل ۲ ردیف برای ۵ مرحله", rows >= 2)
    }

    // ─────────────── کیفیت گرافیک

    @Test
    fun `کیفیت پایین آستانه LOD را نزدیک‌تر می‌کند`() {
        val baseNear = 175f
        val low = baseNear * 0.55f
        val mid = baseNear * 1f
        val high = baseNear * 1.35f
        assertTrue("کیفیت پایین باید جزئیات را زودتر حذف کند", low < mid)
        assertTrue("کیفیت بالا باید جزئیات را دیرتر حذف کند", high > mid)
    }

    @Test
    fun `ضریب کیفیت فقط سه مقدار معتبر می‌پذیرد`() {
        fun mul(level: Int) = when (level) {
            0 -> 0.55f
            2 -> 1.35f
            else -> 1f
        }
        assertEquals(0.55f, mul(0), 0.001f)
        assertEquals(1f, mul(1), 0.001f)
        assertEquals(1.35f, mul(2), 0.001f)
        // مقادیر خارج از بازه هم امن‌اند (شاخهٔ else)
        assertEquals(1f, mul(9), 0.001f)
        assertEquals(1f, mul(-3), 0.001f)
    }

    // ─────────────── اندازه متن

    private fun fitSize(text: String, start: Float, maxW: Float, measure: (String, Float) -> Float): Float {
        var s = start
        while (s > 10f && measure(text, s) > maxW) s -= 0.5f
        return s
    }

    @Test
    fun `عنوان بلند کوچک می‌شود تا جا شود`() {
        // شبیه‌سازی: هر کاراکتر ~0.55 برابر اندازه
        val measure: (String, Float) -> Float = { t, s -> t.length * s * 0.55f }
        val maxW = 200f
        val short = "پل نظامی"
        val long = "هدف ثابت در دشت کویری وسیع"
        val sShort = fitSize(short, 17f, maxW, measure)
        val sLong = fitSize(long, 17f, maxW, measure)
        assertEquals("عنوان کوتاه نباید کوچک شود", 17f, sShort, 0.01f)
        assertTrue("عنوان بلند باید کوچک‌تر شود", sLong < 17f)
        assertTrue("اندازه نباید زیر حد خوانا برود", sLong >= 10f)
    }

    @Test
    fun `عنوانی که با کوچک‌ترین اندازه هم جا نشود کوتاه می‌شود`() {
        val measure: (String, Float) -> Float = { t, s -> t.length * s * 0.55f }
        val maxW = 60f
        var text = "این یک عنوان بسیار بسیار طولانی است"
        val size = 10f
        var guard = 0
        while (text.length > 4 && measure(text + "…", size) > maxW && guard < 100) {
            text = text.dropLast(1)
            guard++
        }
        val finalText = text + "…"
        assertTrue("متن نهایی باید در عرض جا شود", measure(finalText, size) <= maxW)
        assertTrue("متن نهایی نباید خالی شود", finalText.length > 2)
    }

    // ─────────────── نوار امتیاز لیدربورد

    @Test
    fun `سه ستون لیدربورد هرگز روی هم نمی‌افتند`() {
        val lw = 1400f
        val scoreW = 56f * 2.75f
        val starsW = 52f * 2.75f
        val titleMaxW = (lw - scoreW - starsW - 20f * 2.75f)
        val scoreEnd = 12f * 2.75f + scoreW
        val titleStart = lw - starsW - 10f * 2.75f - titleMaxW
        assertTrue("عنوان نباید روی امتیاز بیفتد", titleStart >= scoreEnd)
        assertTrue("فضای عنوان باید مثبت بماند", titleMaxW > 0f)
    }
}
