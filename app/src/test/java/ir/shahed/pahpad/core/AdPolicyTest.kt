package ir.shahed.pahpad.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * سیاست تبلیغ: باخت → بنر آنی، هر ۳ برد → آنی ویدیو، لغو → هیچ.
 *
 * هر ادعا با یک «کنترل منفی» سنجیده می‌شود: همان ورودی با آمادگیِ کشِ برعکس
 * باید نتیجهٔ متفاوت بدهد. تستی که با کدِ خراب هم سبز شود ارزشی ندارد.
 */
class AdPolicyTest {

    @Test
    fun lossShowsBanner() {
        val p = AdPolicy()
        assertEquals(AdChoice.BANNER, p.onMissionEnd(lost = true, nowMs = 1_000, bannerReady = true, videoReady = true))
        // کنترل منفی: بدون کش بنر، همان باخت نباید هیچ تبلیغی بدهد (باید NONE باشد نه VIDEO)
        val q = AdPolicy()
        assertEquals(AdChoice.NONE, q.onMissionEnd(lost = true, nowMs = 1_000, bannerReady = false, videoReady = true))
    }

    @Test
    fun secondLossIsBlockedByGap() {
        val p = AdPolicy(bannerMinGapMs = 45_000)
        assertEquals(AdChoice.BANNER, p.onMissionEnd(true, 1_000, bannerReady = true, videoReady = false))
        // ۳۰ ثانیه بعد: همان باخت، همان کش → باید مسدود شود
        assertEquals(AdChoice.NONE, p.onMissionEnd(true, 31_000, bannerReady = true, videoReady = false))
        // کنترل منفی: بعد از گذشت فاصله، دوباره مجاز است
        assertEquals(AdChoice.BANNER, p.onMissionEnd(true, 46_500, bannerReady = true, videoReady = false))
    }

    @Test
    fun winNeverShowsBanner() {
        val p = AdPolicy(videoEveryNWins = 3)
        val a = p.onMissionEnd(lost = false, nowMs = 1_000, bannerReady = true, videoReady = true)
        assertEquals(AdChoice.NONE, a)
        val b = p.onMissionEnd(lost = false, nowMs = 2_000, bannerReady = true, videoReady = true)
        assertEquals(AdChoice.NONE, b)
        val c = p.onMissionEnd(lost = false, nowMs = 3_000, bannerReady = true, videoReady = true)
        assertEquals(AdChoice.VIDEO, c)
        // کنترل منفی: برد چهارم (نه مضرب ۳) باید دوباره NONE باشد، نه VIDEO
        assertEquals(AdChoice.NONE, p.onMissionEnd(lost = false, nowMs = 200_000, bannerReady = true, videoReady = true))
    }

    @Test
    fun winWithoutVideoCacheDoesNotFallBackToBanner() {
        val p = AdPolicy(videoEveryNWins = 1, videoMinGapMs = 0)
        val c = p.onMissionEnd(lost = false, nowMs = 1_000, bannerReady = true, videoReady = false)
        // کشِ ویدیو نیست → هیچ، حتی با بنرِ آماده. (اگر بنر برمی‌گشت، سیاست شکسته بود)
        assertEquals(AdChoice.NONE, c)
    }

    @Test
    fun videoGapBlocksTooFrequentVideo() {
        val p = AdPolicy(videoEveryNWins = 1, videoMinGapMs = 120_000)
        assertEquals(AdChoice.VIDEO, p.onMissionEnd(false, 1_000, bannerReady = false, videoReady = true))
        // ۶۰ ثانیه بعد، باز هم مضرب ۱ → ولی فاصلهٔ ۱۲۰ ثانیه نگذشته
        assertEquals(AdChoice.NONE, p.onMissionEnd(false, 61_000, bannerReady = false, videoReady = true))
        // کنترل منفی: بعد از ۱۲۱ ثانیه مجاز است
        assertEquals(AdChoice.VIDEO, p.onMissionEnd(false, 122_000, bannerReady = false, videoReady = true))
    }

    @Test
    fun winsCounterCountsOnlyWins() {
        val p = AdPolicy(videoEveryNWins = 100)
        repeat(4) { p.onMissionEnd(lost = true, nowMs = 1_000L * it, bannerReady = false, videoReady = false) }
        assertEquals(0, p.winsSinceStart())
        p.onMissionEnd(lost = false, nowMs = 9_000, bannerReady = false, videoReady = false)
        assertEquals(1, p.winsSinceStart())
    }
}
