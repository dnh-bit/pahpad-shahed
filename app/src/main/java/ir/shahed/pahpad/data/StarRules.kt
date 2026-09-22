package ir.shahed.pahpad.data

import ir.shahed.pahpad.core.Fa

/**
 * قانون ستاره‌دهی.
 *
 * هر ستاره یک شرط مستقل و قابل اندازه‌گیری دارد. شرط‌ها قبل از ماموریت در بریفینگ و
 * بعد از ماموریت در صفحهٔ نتیجه با عدد واقعی بازیکن نشان داده می‌شوند، پس همیشه
 * مشخص است ستاره بر چه اساس داده یا کم شده است.
 *
 * ستاره‌ها به مجموع امتیاز گره نیستند:
 *  - ستارهٔ یک: انهدام همهٔ اهداف اصلی.
 *  - ستارهٔ دو: جمعِ همهٔ بونوس‌های مخفی مسیر. شرط قبلی «میانگین دقت برخورد ۵۰٪»
 *    عملاً قابل گرفتن نبود (میانگین دقتِ برخورد به مرکز هدف با توزیع واقعی بازی
 *    زیر ۵۰٪ می‌ماند) و با شمارش بونوس‌ها جایگزین شد.
 *  - ستارهٔ سه: اجرای تمیز (بدون آسیب پدافندی) یا رسیدن زیر زمان مرجع.
 *
 * بونوس‌ها جدا از ستاره هم امتیاز و سکه می‌دهند.
 */
object StarRules {

    /** ضریب سخاوتمندانهٔ زمان مرجع نسبت به زمان پرواز نظری */
    private const val PAR_FACTOR = 2.1f

    /**
     * زمان مرجع ماموریت بر حسب ثانیه.
     * فقط از فاصلهٔ هدف، سرعت پروازی پهباد و تعداد اهداف ساخته می‌شود؛ هیچ عدد
     * پنهانی در کار نیست، پس بازیکن می‌تواند آن را از قبل ببیند.
     */
    fun parTime(distance: Float, cruiseMps: Float, targets: Int): Float {
        val legs = targets.coerceAtLeast(1)
        val cruise = cruiseMps.coerceAtLeast(20f)
        return (distance / cruise) * PAR_FACTOR * legs
    }

    class Rule(
        val star: Int,
        val title: String,
        /** متن قانون؛ همان چیزی که در بریفینگ نشان داده می‌شود */
        val requirement: String,
        /** اندازهٔ واقعی بازیکن؛ قبل از ماموریت خالی است */
        val measured: String,
        val passed: Boolean
    )

    /**
     * شرط ستارهٔ دو: همهٔ بونوس‌های موجودِ همان موج/ماموریت جمع شده باشند.
     * ماموریتی که بونوسی ندارد این شرط را به‌طور خودکار می‌گیرد تا ستاره‌گیری
     * هرگز به خاطر نبود بونوس ناممکن نشود.
     */
    fun allBonuses(bonusesCollected: Int, bonusesTotal: Int): Boolean =
        bonusesTotal <= 0 || bonusesCollected >= bonusesTotal

    /** تعداد ستاره‌ها؛ ستارهٔ دوم و سوم بدون ستارهٔ اول شمرده نمی‌شوند. */
    fun stars(
        success: Boolean,
        bonusesCollected: Int,
        bonusesTotal: Int,
        damage: Int,
        elapsed: Float,
        parTime: Float
    ): Int {
        if (!success) return 0
        var count = 1
        if (allBonuses(bonusesCollected, bonusesTotal)) count++
        if (damage == 0 || (parTime > 0f && elapsed <= parTime)) count++
        return count
    }

    /** سه قانون با مقدار سنجیده‌شدهٔ بازیکن. */
    fun evaluate(
        success: Boolean,
        targetsDestroyed: Int,
        targetsTotal: Int,
        bonusesCollected: Int,
        bonusesTotal: Int,
        damage: Int,
        elapsed: Float,
        parTime: Float
    ): List<Rule> {
        val cleanRun = damage == 0
        val inTime = parTime > 0f && elapsed <= parTime
        val cleanText = when {
            cleanRun -> "بدون آسیب پدافندی"
            inTime -> "زمان " + Fa.clock(elapsed) + " از " + Fa.clock(parTime)
            else -> Fa.num(damage) + " آسیب · زمان " + Fa.clock(elapsed)
        }
        return listOf(
            Rule(
                1, "انهدام هدف",
                "همهٔ اهداف اصلی منهدم شوند",
                Fa.num(targetsDestroyed) + " از " + Fa.num(targetsTotal.coerceAtLeast(1)) + " هدف",
                success
            ),
            Rule(
                2, "جمع بونوس‌ها",
                "همهٔ بونوس‌های مخفی جمع شوند",
                Fa.num(bonusesCollected) + " از " + Fa.num(bonusesTotal) + " بونوس",
                success && allBonuses(bonusesCollected, bonusesTotal)
            ),
            Rule(
                3, "اجرای تمیز",
                "بدون آسیب پدافندی، یا رسیدن زیر زمان مرجع (" + Fa.clock(parTime) + ")",
                cleanText,
                success && (cleanRun || inTime)
            )
        )
    }

    /** همان سه قانون، برای نمایش در بریفینگ قبل از پرواز. */
    fun preview(targetsTotal: Int, parTime: Float, bonusesTotal: Int): List<Rule> = listOf(
        Rule(1, "انهدام هدف", "همهٔ اهداف اصلی منهدم شوند",
            Fa.num(targetsTotal.coerceAtLeast(1)) + " هدف", false),
        Rule(2, "جمع بونوس‌ها", "همهٔ بونوس‌های مخفی جمع شوند",
            Fa.num(bonusesTotal) + " بونوس", false),
        Rule(3, "اجرای تمیز", "بدون آسیب پدافندی، یا رسیدن زیر زمان مرجع",
            Fa.clock(parTime), false)
    )
}
