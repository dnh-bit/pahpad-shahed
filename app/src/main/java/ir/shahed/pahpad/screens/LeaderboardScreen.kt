package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import ir.shahed.pahpad.data.Levels

/** لیدربورد محلی: بهترین امتیازها */
class LeaderboardScreen(game: MainActivity) : BaseScreen(game) {

    private val back = UiButton("بازگشت", Theme.TEXT_DIM) { game.show(MainMenuScreen(game)) }
    private var scroll = 0f
    private var dragging = false
    private var lastY = 0f

    init {
        buttons.add(back)
    }

    override fun onBack(): Boolean {
        game.show(MainMenuScreen(game))
        return true
    }

    override fun layoutUi(w: Float, h: Float) {
        back.set(dp(20f), h - dp(54f), dp(110f), dp(40f))
    }

    override fun onTouchDown(x: Float, y: Float, pointerId: Int): Boolean {
        dragging = true
        lastY = y
        return true
    }

    override fun onTouchMove(x: Float, y: Float, pointerId: Int) {
        if (!dragging) return
        scroll = (scroll + (y - lastY)).coerceIn(-dp(220f), 0f)
        lastY = y
    }

    override fun onTouchUp(x: Float, y: Float, pointerId: Int) {
        dragging = false
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh, Theme.BLUE)
        Deco.header(this, c, "لیدربورد", "رکوردهای شما در این دستگاه", Theme.BLUE)
        Deco.resources(this, c)

        // خلاصه
        val sw = vw * 0.3f
        val sx = dp(20f)
        var sy = vh * 0.2f
        ui.panel(c, sx, sy, sw, dp(150f))
        ui.text(c, "خلاصه عملکرد", sx + sw - dp(14f), sy + dp(20f), dp(13.5f), Theme.TEXT, bold = true,
            align = Paint.Align.RIGHT)
        val lines = listOf(
            Pair("ستاره‌های کسب‌شده", Fa.num(save.totalStars) + " از " + Fa.num(Levels.all.size * 3)),
            Pair("رتبه", save.rank.title),
            Pair("تجربه", Fa.grouped(save.xp)),
            Pair("رکورد بی‌نهایت", Fa.grouped(save.endlessBest)),
            Pair("رکورد چالش روزانه", Fa.grouped(save.dailyBest))
        )
        for ((i, l) in lines.withIndex()) {
            val y = sy + dp(46f) + i * dp(21f)
            ui.text(c, l.first, sx + sw - dp(14f), y, dp(11.5f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
            ui.text(c, l.second, sx + dp(14f), y, dp(11.5f), Theme.AMBER, align = Paint.Align.LEFT)
        }

        // فهرست مراحل
        val lx = vw * 0.36f
        val lw = vw - lx - dp(20f)
        c.save()
        c.clipRect(lx, vh * 0.16f, lx + lw, vh - dp(62f))
        var y = vh * 0.18f + scroll
        for (l in Levels.all) {
            val best = save.bestScoreOf(l.id)
            val stars = save.starsOf(l.id)
            ui.panel(c, lx, y, lw, dp(34f), Theme.withAlpha(Theme.PANEL, 0.95f), Theme.LINE, dp(8f))
            ui.text(c, Fa.num(l.code) + " · " + l.title, lx + lw - dp(12f), y + dp(17f), dp(12f),
                if (best > 0) Theme.TEXT else Theme.TEXT_FAINT, align = Paint.Align.RIGHT)
            ui.stars(c, lx + dp(120f), y + dp(17f), dp(6f), stars)
            ui.text(c, if (best > 0) Fa.grouped(best) else "—", lx + dp(14f), y + dp(17f), dp(12f),
                if (best > 0) Theme.AMBER else Theme.TEXT_FAINT, align = Paint.Align.LEFT)
            y += dp(38f)
        }
        c.restore()

        ui.text(c, "لیدربورد جهانی در نسخه آنلاین فعال می‌شود", vw / 2f, vh - dp(30f), dp(11f), Theme.TEXT_FAINT)
        ui.button(c, back)
    }
}
