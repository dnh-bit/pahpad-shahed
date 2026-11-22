package ir.shahed.pahpad.core

/**
 * تصمیمِ «کدام تبلیغ، کِی» — کاملاً خالص (بدون اندروید) تا تست‌پذیر باشد.
 *
 * سیاست محصول:
 * - **باخت** → «بنر آنی» (تصویر تمام‌صفحه، سبک و کوتاه). بازیکن بعد از باخت
 *   بیشترین احتمال رفتن به فروشگاه/خروج را دارد، پس هر باخت واجد شرایط است؛
 *   فقط فاصلهٔ زمانی حداقلی جلوی تبلیغ پشت‌سرهم را می‌گیرد.
 * - **برد** → «آنی ویدیو» (طولانی است) فقط هر [videoEveryNWins] برد و با فاصلهٔ
 *   زمانی بلندتر.
 *
 * قواعد سخت:
 * - هیچ تبلیغی بدون کشِ آماده نمایش داده نمی‌شود (آفلاین = بی‌صدا، بدون وقفه).
 * - شمارنده‌ها فقط با فراخوانی [onMissionEnd] جلو می‌روند؛ نمایش می‌تواند شکست
 *   بخورد بدون این‌که شمارش را خراب کند.
 */
enum class AdChoice { NONE, BANNER, VIDEO }

class AdPolicy(
    private val bannerMinGapMs: Long = 45_000L,
    private val videoEveryNWins: Int = 3,
    private val videoMinGapMs: Long = 120_000L,
) {
    private var wins = 0
    private var lastBannerMs = 0L
    private var lastVideoMs = 0L

    /** فقط برای تست/گزارش */
    fun winsSinceStart(): Int = wins

    /**
     * @param lost پایان ماموریت با شکست (شامل لغو توسط بازیکن نیست — آن را
     *             فراخواننده جدا می‌کند، چون لغو خواستهٔ خود بازیکن است).
     * @param nowMs ساعت یکنوا (monotonic) — هرگز به عقب نمی‌رود.
     */
    fun onMissionEnd(lost: Boolean, nowMs: Long, bannerReady: Boolean, videoReady: Boolean): AdChoice {
        if (lost) {
            if (!bannerReady) return AdChoice.NONE
            if (lastBannerMs != 0L && nowMs - lastBannerMs < bannerMinGapMs) return AdChoice.NONE
            lastBannerMs = nowMs
            return AdChoice.BANNER
        }

        wins++
        if (wins % videoEveryNWins != 0) return AdChoice.NONE
        if (!videoReady) return AdChoice.NONE
        if (lastVideoMs != 0L && nowMs - lastVideoMs < videoMinGapMs) return AdChoice.NONE
        lastVideoMs = nowMs
        return AdChoice.VIDEO
    }
}
