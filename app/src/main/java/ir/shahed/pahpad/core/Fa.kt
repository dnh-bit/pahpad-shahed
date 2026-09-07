package ir.shahed.pahpad.core

/**
 * ابزار تبدیل اعداد و متن‌های عددی به فارسی.
 * تمام اعداد نمایشی بازی از این کلاس عبور می‌کنند تا رابط کاربری کامل فارسی باشد.
 */
object Fa {

    private val DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun num(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text) {
            when {
                c in '0'..'9' -> sb.append(DIGITS[c - '0'])
                c == '.' -> sb.append('٫')
                c == ',' -> sb.append('٬')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun num(value: Int): String = num(value.toString())

    fun num(value: Long): String = num(value.toString())

    fun num(value: Float, decimals: Int): String {
        val fixed = if (decimals <= 0) Math.round(value).toString()
        else String.format(java.util.Locale.US, "%.${decimals}f", value)
        return num(fixed)
    }

    /** جداکننده هزارگان فارسی: ۱۲٬۵۰۰ */
    fun grouped(value: Int): String {
        val s = value.toString()
        val neg = s.startsWith("-")
        val digits = if (neg) s.substring(1) else s
        val sb = StringBuilder()
        for ((i, c) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return num((if (neg) "-" else "") + sb.toString())
    }

    /** زمان به شکل ۰۱:۲۴ */
    fun clock(seconds: Float): String {
        val total = Math.max(0, Math.round(seconds))
        val m = total / 60
        val s = total % 60
        return num(String.format(java.util.Locale.US, "%02d:%02d", m, s))
    }

    fun meters(value: Float): String = num(Math.round(value)) + " متر"

    fun kmh(value: Float): String = num(Math.round(value)) + " کیلومتر/ساعت"

    fun percent(value01: Float): String = num(Math.round(value01 * 100f)) + "٪"
}
