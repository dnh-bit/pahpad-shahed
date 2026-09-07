package ir.shahed.pahpad.data

import ir.shahed.pahpad.core.Fa

/**
 * قانون ستاره‌دهی.
 *
 * هر ستاره یک شرط مستقل و قابل اندازه‌گیری دارد. شرط‌ها قبل از ماموریت در بریفینگ و
 * بعد از ماموریت در صفحه‌ی نتیجه با عدد واقعی بازیکن نشان داده می‌شوند، پس همیشه
 * مشخص است ستاره بر چه اساس داده یا کم شده است.
 *
 * تفاوت با نسخه‌ی قبلی: ستاره‌ها دیگر به مجموع امتیاز (که سقف ۲۵۰۰ داشت و برای سه
 * ستاره ۲۲۰۰ لازم بود) وصل نیستند. بونوس‌های مخفی هم شرط ستاره نیستند و فقط امتیاز و
 * سکه می‌دهند.
 */
object StarRules {

    /** حد دقت برخورد برای ستاره‌ی دوم */
    const val ACCURACY_GATE = 0.50f

    /** ضریب سخاوتمندانه‌ی زمان مرجع نسبت به زمان پرواز نظری */
    private const val PAR_FACTOR = 2.1f

    /**
     * زمان مرجع ماموریت بر حسب ثانیه.
     * فقط از فاصله‌ی هدف، سرعت پروازی پهباد و تعداد اهداف ساخته می‌شود؛ هیچ عدد
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
        /** اندازه‌ی واقعی بازیکن؛ قبل از ماموریت خالی است */
        val measured: String,
        val passed: Boolean
    )

    /** تعداد ستاره‌ها؛ ستاره‌ی دوم و سوم بدون ستاره‌ی اول شمرده نمی‌شوند. */
    fun stars(
        success: Boolean,
        avgAccuracy: Float,
        damage: Int,
        elapsed: Float,
        parTime: Float
    ): Int {
        if (!success) return 0
        var count = 1
        if (avgAccuracy >= ACCURACY_GATE) count++
        if (damage == 0 || (parTime > 0f && elapsed <= parTime)) count++
        return count
    }

    /** سه قانون با مقدار سنجیده‌شده‌ی بازیکن. */
    fun evaluate(
        success: Boolean,
        targetsDestroyed: Int,
        targetsTotal: Int,
        avgAccuracy: Float,
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
                "همه‌ی اهداف اصلی منهدم شوند",
                Fa.num(targetsDestroyed) + " از " + Fa.num(targetsTotal.coerceAtLeast(1)) + " هدف",
                success
            ),
            Rule(
                2, "دقت برخورد",
                "میانگین دقت برخورد " + Fa.percent(ACCURACY_GATE) + " یا بیشتر",
                Fa.percent(avgAccuracy),
                success && avgAccuracy >= ACCURACY_GATE
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
    fun preview(targetsTotal: Int, parTime: Float): List<Rule> = listOf(
        Rule(1, "انهدام هدف", "همه‌ی اهداف اصلی منهدم شوند",
            Fa.num(targetsTotal.coerceAtLeast(1)) + " هدف", false),
        Rule(2, "دقت برخورد", "میانگین دقت برخورد " + Fa.percent(ACCURACY_GATE) + " یا بیشتر",
            Fa.percent(ACCURACY_GATE), false),
        Rule(3, "اجرای تمیز", "بدون آسیب پدافندی، یا رسیدن زیر زمان مرجع",
            Fa.clock(parTime), false)
    )
}
