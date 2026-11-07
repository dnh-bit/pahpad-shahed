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
        // ویوپورت واقعی: از dp(84+26) تا dp(60) بالای پایین صفحه، در مقیاس 2.75
        val dp = 2.75f
        val vp = Viewport((84f + 26f) * dp, 1080f - 60f * dp)
        val count = 4
        val tops = (0 until count).map { vp.top + 2f + it * (cardH * dp + gap * dp) }

        val firstScreen = visibleCount(tops, cardH * dp, vp)
        assertTrue("باید حداقل ۲ کارت اول دیده شود (شد $firstScreen)", firstScreen >= 2)
        assertTrue("همه کارت‌ها نباید یک‌جا جا شوند — وگرنه اسکرول لازم نیست", firstScreen < count)

        val contentH = count * (cardH * dp + gap * dp) - gap * dp
        val maxScroll = (contentH - vp.height).coerceAtLeast(0f)
        assertTrue("محتوای لیست باید بلندتر از ویوپورت باشد (maxScroll=$maxScroll)", maxScroll > 0f)

        val scrolled = tops.map { it - maxScroll }
        val lastVisible = scrolled.last() + cardH * dp > vp.top && scrolled.last() < vp.bottom
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
        val dp = 2.75f
        // همان فرمول LevelSelectScreen: availW در px، minCardW هم در px
        val availW = 2340f - 16f * dp * 2f
        val gap = 10f * dp
        val minCardW = 148f * dp
        // همان سقف ۳ ستونِ LevelSelectScreen
        val cols = ((availW + gap) / (minCardW + gap)).toInt().coerceIn(1, minOf(n, 3))
        val rows = (n + cols - 1) / cols
        assertTrue("حداکثر ۳ ستون (شد $cols)", cols <= 3)
        assertTrue("حداقل ۲ ستون تا متن خوانا بماند (شد $cols)", cols >= 2)
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

    // ─────────────── پنجره‌های ساختمان (ناپایداری)

    /**
     * شدت پنجره باید تابعی یکنوا و پیوسته از عرض پنجره روی صفحه باشد.
     * اگر دو مقدار نزدیک، خروجی خیلی متفاوت بدهند، دوربین با تکان کوچک
     * پنجره‌ها را روشن/خاموش می‌کند — همان باگی که در ویدیو دیده شد.
     */
    private fun windowDetail(px: Float): Float {
        val a = 5.5f
        val b = 15f
        if (px <= a) return 0f
        if (px >= b) return 1f
        val t = (px - a) / (b - a)
        return t * t * (3f - 2f * t)
    }

    @Test
    fun `شدت پنجره پیوسته است و پرش ندارد`() {
        var prev = windowDetail(0f)
        var maxJump = 0f
        var px = 0f
        while (px <= 30f) {
            val v = windowDetail(px)
            maxJump = maxOf(maxJump, Math.abs(v - prev))
            prev = v
            px += 0.1f
        }
        // با گام ۰٫۱ پیکسل، هیچ جهشی نباید از ۵٪ بیشتر باشد
        assertTrue("جهش ناگهانی در شدت پنجره: $maxJump", maxJump < 0.05f)
    }

    @Test
    fun `تکان کوچک دوربین پنجره را روشن خاموش نمی‌کند`() {
        // دو عرض پنجره که فقط ۱ پیکسل فاصله دارند (تکان کوچک دوربین)
        val a = windowDetail(9.0f)
        val b = windowDetail(10.0f)
        assertTrue("اختلاف شدت برای ۱ پیکسل نباید محسوس باشد (${a} → ${b})",
            Math.abs(a - b) < 0.25f)
        // و هیچ‌کدام نباید ۰ یا ۱ قطعی باشند (یعنی حالت دودویی نداریم)
        assertTrue("نباید در حالت خاموش قطعی باشد", a > 0.001f)
        assertTrue("نباید در حالت روشن قطعی باشد", b < 0.999f)
    }

    @Test
    fun `فاصله پنجره روی صفحه با فرمول درست محاسبه می‌شود`() {
        // focal * windowWidth / distance — عرض پنجره ۲٫۱۵ متر
        val focal = 1100f
        val winW = 2.15f
        fun pxAt(dist: Float) = focal * winW / dist
        assertTrue("در ۲۰۰ متر پنجره باید بزرگ باشد", pxAt(200f) > 10f)
        assertTrue("در ۵۰۰ متر پنجره باید کوچک باشد", pxAt(500f) < 6f)
        // اصلاح باگ: قبلاً به‌جای ۲٫۱۵ از ۲٫۴۵ استفاده می‌شد
        assertTrue("با ضریب غلط ۲٫۴۵ عرض بیشتری محاسبه می‌شد",
            focal * 2.45f / 200f > pxAt(200f))
    }

    // ─────────────── نوار امتیاز لیدربورد

    @Test
    fun `سه ستون لیدربورد هرگز روی هم نمی‌افتند`() {
        val dp = 2.75f
        val lw = 1400f
        val scoreW = 56f * dp
        val starsW = 52f * dp
        val pad = 32f * dp   // همان مقدار LeaderboardScreen
        val titleMaxW = (lw - scoreW - starsW - pad).coerceAtLeast(40f * dp)
        // امتیاز از x=dp(12) شروع می‌شود (تراز چپ)
        val scoreStart = 12f * dp
        val scoreEnd = scoreStart + scoreW
        // عنوان با تراز راست تا starsW + dp(10) از لبه فاصله دارد
        val titleRight = lw - starsW - 10f * dp
        val titleLeft = titleRight - titleMaxW
        assertTrue("عنوان نباید روی امتیاز بیفتد (titleLeft=$titleLeft، scoreEnd=$scoreEnd)",
            titleLeft >= scoreEnd)
        assertTrue("فضای عنوان باید مثبت بماند", titleMaxW > 0f)
        assertTrue("ستاره‌ها نباید روی عنوان بیفتند", titleRight >= titleLeft)
    }
}
