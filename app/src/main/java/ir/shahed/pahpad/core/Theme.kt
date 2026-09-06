package ir.shahed.pahpad.core

import android.graphics.Color

/** پالت رنگی و اندازه‌های مشترک رابط کاربری */
object Theme {

    // پالت پایه
    const val BG_DEEP = 0xFF070A0F.toInt()
    const val BG = 0xFF0B0F14.toInt()
    const val PANEL = 0xFF141B23.toInt()
    const val PANEL_HI = 0xFF1D2732.toInt()
    const val LINE = 0xFF283442.toInt()

    const val TEXT = 0xFFEDF3F7.toInt()
    const val TEXT_DIM = 0xFF9AAAB8.toInt()
    const val TEXT_FAINT = 0xFF61717F.toInt()

    const val MINT = 0xFF6FE3B4.toInt()
    const val MINT_DARK = 0xFF2E8F71.toInt()
    const val AMBER = 0xFFFFC53D.toInt()
    const val ORANGE = 0xFFF76808.toInt()
    const val RED = 0xFFE0564F.toInt()
    const val BLUE = 0xFF4C9AFF.toInt()
    const val VIOLET = 0xFF9B98FD.toInt()

    // پالت محیط‌ها
    val DESERT = Palette(
        skyTop = 0xFF2E4A6B.toInt(),
        skyBottom = 0xFFE0A86B.toInt(),
        ground = 0xFF9C7A4B.toInt(),
        groundFar = 0xFFC29A63.toInt(),
        grid = 0xFF7A5C36.toInt(),
        prop = 0xFF8A6B44.toInt(),
        fog = 0xFFD9B27C.toInt()
    )
    val URBAN = Palette(
        skyTop = 0xFF0B1020.toInt(),
        skyBottom = 0xFF25324A.toInt(),
        ground = 0xFF2B3340.toInt(),
        groundFar = 0xFF3A4454.toInt(),
        grid = 0xFF44506A.toInt(),
        prop = 0xFF515D70.toInt(),
        fog = 0xFF2A3346.toInt()
    )
    val NAVAL = Palette(
        skyTop = 0xFF13314F.toInt(),
        skyBottom = 0xFF7FA8C4.toInt(),
        ground = 0xFF20527A.toInt(),
        groundFar = 0xFF3D7CA6.toInt(),
        grid = 0xFF2F6C99.toInt(),
        prop = 0xFF4A5A66.toInt(),
        fog = 0xFF9DC2D8.toInt()
    )
    val SPECIAL = Palette(
        skyTop = 0xFF15062B.toInt(),
        skyBottom = 0xFF5E2A6B.toInt(),
        ground = 0xFF33223F.toInt(),
        groundFar = 0xFF4A3057.toInt(),
        grid = 0xFF6A3F7A.toInt(),
        prop = 0xFF5C4468.toInt(),
        fog = 0xFF48294F.toInt()
    )

    class Palette(
        val skyTop: Int,
        val skyBottom: Int,
        val ground: Int,
        val groundFar: Int,
        val grid: Int,
        val prop: Int,
        val fog: Int
    )

    fun withAlpha(color: Int, alpha01: Float): Int {
        val a = (alpha01.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun shade(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 4f)
        return Color.argb(
            Color.alpha(color),
            (Color.red(color) * f).toInt().coerceIn(0, 255),
            (Color.green(color) * f).toInt().coerceIn(0, 255),
            (Color.blue(color) * f).toInt().coerceIn(0, 255)
        )
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * k).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    /** رنگ ستاره‌های سختی */
    fun difficultyColor(level: Int): Int = when (level) {
        1 -> MINT
        2 -> AMBER
        3 -> ORANGE
        else -> RED
    }

    fun difficultyLabel(level: Int): String = when (level) {
        1 -> "آسان"
        2 -> "متوسط"
        3 -> "سخت"
        else -> "خیلی سخت"
    }
}
