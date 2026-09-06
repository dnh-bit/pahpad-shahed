package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme

/** اجزای تکرارشونده‌ی رابط کاربری صفحه‌ها */
object Deco {

    private val path = Path()

    fun header(s: BaseScreen, c: Canvas, title: String, subtitle: String? = null, accent: Int = Theme.MINT) {
        val ui = s.ui
        ui.text(c, title, s.vw / 2f, ui.dp(30f), ui.dp(23f), Theme.TEXT, bold = true)
        subtitle?.let {
            ui.text(c, it, s.vw / 2f, ui.dp(54f), ui.dp(12.5f), Theme.TEXT_DIM)
        }
        ui.stroke.color = Theme.withAlpha(accent, 0.35f)
        ui.stroke.strokeWidth = ui.dp(1.2f)
        c.drawLine(s.vw * 0.28f, ui.dp(70f), s.vw * 0.72f, ui.dp(70f), ui.stroke)
    }

    /** نوار منابع بازیکن: سکه، ستاره، رتبه */
    fun resources(s: BaseScreen, c: Canvas) {
        val ui = s.ui
        val y = ui.dp(26f)
        val right = s.vw - ui.dp(20f)
        // سکه
        ui.iconCoin(c, right - ui.dp(8f), y, ui.dp(9f))
        val coins = Fa.grouped(s.save.coins)
        ui.text(c, coins, right - ui.dp(24f), y, ui.dp(14f), Theme.AMBER, bold = true, align = Paint.Align.RIGHT)
        // ستاره
        val starsX = right - ui.dp(24f) - ui.measure(coins, ui.dp(14f), true) - ui.dp(26f)
        ui.iconStar(c, starsX, y, ui.dp(9f), Theme.AMBER)
        val st = Fa.num(s.save.totalStars)
        ui.text(c, st, starsX - ui.dp(15f), y, ui.dp(14f), Theme.TEXT, bold = true, align = Paint.Align.RIGHT)
        // رتبه
        val rank = s.save.rank
        ui.text(c, rank.title, ui.dp(20f), y, ui.dp(14f), Theme.MINT, bold = true, align = Paint.Align.LEFT)
        val p = rank.progress(s.save.xp)
        ui.bar(c, ui.dp(20f), y + ui.dp(14f), ui.dp(120f), ui.dp(5f), p, Theme.MINT)
    }

    /** شبح پهباد برای صفحه‌ها و لوگو */
    fun droneIcon(s: BaseScreen, c: Canvas, cx: Float, cy: Float, size: Float, color: Int, rotation: Float = 0f) {
        val ui = s.ui
        c.save()
        c.rotate(rotation, cx, cy)
        path.reset()
        // بدنه
        path.moveTo(cx, cy - size)
        path.lineTo(cx + size * 0.16f, cy + size * 0.55f)
        path.lineTo(cx - size * 0.16f, cy + size * 0.55f)
        path.close()
        ui.fill.color = color
        ui.fill.shader = null
        c.drawPath(path, ui.fill)
        // بال مثلثی
        path.reset()
        path.moveTo(cx - size * 0.95f, cy + size * 0.42f)
        path.lineTo(cx + size * 0.95f, cy + size * 0.42f)
        path.lineTo(cx, cy - size * 0.1f)
        path.close()
        ui.fill.color = Theme.withAlpha(color, 0.85f)
        c.drawPath(path, ui.fill)
        // دم
        ui.fill.color = Theme.RED
        c.drawRect(cx - size * 0.1f, cy - size * 1.05f, cx + size * 0.1f, cy - size * 0.75f, ui.fill)
        c.restore()
    }

    fun difficultyDots(s: BaseScreen, c: Canvas, cx: Float, cy: Float, level: Int) {
        val ui = s.ui
        val color = Theme.difficultyColor(level)
        val r = ui.dp(3.4f)
        val gap = ui.dp(10f)
        val start = cx + gap * 1.5f
        for (i in 0 until 4) {
            ui.fill.color = if (i < level) color else Theme.withAlpha(Theme.TEXT_FAINT, 0.35f)
            ui.fill.shader = null
            c.drawCircle(start - i * gap, cy, r, ui.fill)
        }
    }
}
