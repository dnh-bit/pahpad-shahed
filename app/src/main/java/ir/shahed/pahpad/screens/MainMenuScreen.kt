package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.data.Ranks
import ir.shahed.pahpad.game.MissionMode

/** منوی اصلی — بازطراحی تمیز و نظامی */
class MainMenuScreen(game: MainActivity) : BaseScreen(game) {

    private val play = UiButton("شروع ماموریت", Theme.MINT, filled = true) {
        game.show(ChapterSelectScreen(game))
    }
    private val cont = UiButton("ادامه بازی", Theme.MINT) {
        val id = save.nextPlayableLevelId()
        val level = Levels.byId(id) ?: Levels.all.first()
        game.show(BriefingScreen(game, level, MissionMode.STORY))
    }
    private val endless = UiButton("حالت بی‌نهایت", Theme.ORANGE) {
        game.show(BriefingScreen(game, Levels.endless(), MissionMode.ENDLESS))
    }
    private val daily = UiButton("چالش روزانه", Theme.VIOLET) {
        game.show(BriefingScreen(game, Levels.daily(save.dailySeed()), MissionMode.DAILY))
    }
    private val shop = UiButton("فروشگاه", Theme.AMBER) { game.show(ShopScreen(game)) }
    private val board = UiButton("لیدربورد", Theme.BLUE) { game.show(LeaderboardScreen(game)) }
    private val settings = UiButton("تنظیمات", Theme.TEXT_DIM) { game.show(SettingsScreen(game)) }

    init {
        buttons.addAll(listOf(play, cont, endless, daily, shop, board, settings))
    }

    override fun onEnter() {
        cont.enabled = save.hasProgress()
        cont.subLabel = if (save.hasProgress()) Levels.byId(save.nextPlayableLevelId())?.title else "ابتدا یک ماموریت را کامل کنید"
        daily.subLabel = if (save.dailyDone) "رکورد امروز: " + Fa.num(save.dailyBest) else "جایزه سکه دو برابر"
        endless.subLabel = "رکورد: " + Fa.num(save.endlessBest)
    }

    override fun layoutUi(w: Float, h: Float) {
        val colW = w * 0.42f
        val x = w * 0.53f
        val bh = dp(52f)
        val gap = dp(10f)
        var y = h * 0.18f
        play.set(x, y, colW, bh); y += bh + gap
        cont.set(x, y, colW, bh); y += bh + gap
        val halfW = (colW - gap) / 2f
        endless.set(x, y, halfW, bh)
        daily.set(x + halfW + gap, y, halfW, bh); y += bh + gap
        val third = (colW - gap * 2f) / 3f
        shop.set(x, y, third, bh)
        board.set(x + third + gap, y, third, bh)
        settings.set(x + (third + gap) * 2f, y, third, bh)
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh)
        Deco.resources(this, c)

        // ——— ستون چپ: هویت بازی ———
        val lx = vw * 0.26f
        // نشان کوچک تاکتیکی بالای عنوان
        val badgeY = dp(42f)
        ui.fill.color = Theme.withAlpha(Theme.MINT, 0.10f)
        ui.fill.shader = null
        c.drawRoundRect(lx - dp(52f), badgeY - dp(10f), lx + dp(52f), badgeY + dp(10f), dp(10f), dp(10f), ui.fill)
        ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.30f)
        ui.stroke.strokeWidth = dp(1f)
        c.drawRoundRect(lx - dp(52f), badgeY - dp(10f), lx + dp(52f), badgeY + dp(10f), dp(10f), dp(10f), ui.stroke)
        ui.text(c, "یگان پهپادی  •  آماده عملیات", lx, badgeY, dp(9f), Theme.MINT, bold = true)

        // تصویر هیرو با قاب تاکتیکی
        val panelW = vw * 0.36f
        val panelH = dp(96f)
        val px = lx - panelW / 2f
        val py = vh - dp(30f) - panelH
        val subY = py - dp(18f)
        val titleY = subY - dp(30f)
        val heroTop = dp(62f)
        val heroBottom = titleY - dp(24f)
        var heroW = vw * 0.44f
        var heroH = heroW * 300f / 640f
        if (heroH > heroBottom - heroTop) {
            heroH = Math.max(0f, heroBottom - heroTop)
            heroW = heroH * 640f / 300f
        }
        val heroX = lx - heroW / 2f
        val heroY = heroTop + (heroBottom - heroTop - heroH) / 2f
        if (heroH > dp(40f)) {
            // هاله نرم پشت هیرو
            ui.fill.color = Theme.withAlpha(Theme.MINT, 0.08f)
            c.drawRoundRect(heroX - dp(8f), heroY - dp(8f), heroX + heroW + dp(8f), heroY + heroH + dp(8f), dp(20f), dp(20f), ui.fill)
            ui.image(ui.context, c, "menu_hero", heroX, heroY, heroW, heroH, radius = dp(16f))
            ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.28f)
            ui.stroke.strokeWidth = dp(1.2f)
            c.drawRoundRect(heroX, heroY, heroX + heroW, heroY + heroH, dp(16f), dp(16f), ui.stroke)
            // براکت‌های گوشه
            drawCornerBrackets(c, heroX, heroY, heroW, heroH)
        }

        ui.text(c, "پهباد شاهد", lx, titleY, dp(34f), Theme.TEXT, bold = true)
        ui.text(c, "اپراتور پهباد  •  ماموریت‌های دقیق", lx, subY, dp(12.5f), Theme.TEXT_DIM)
        // خط تاکتیکی زیر عنوان
        ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.35f)
        ui.stroke.strokeWidth = dp(1.2f)
        c.drawLine(lx - dp(28f), subY + dp(12f), lx + dp(28f), subY + dp(12f), ui.stroke)
        ui.fill.color = Theme.MINT
        c.drawCircle(lx, subY + dp(12f), dp(2.2f), ui.fill)

        // پنل رتبه — طراحی تمیزتر
        ui.panel(c, px, py, panelW, panelH)
        // نوار لهجه بالا
        ui.fill.color = Theme.MINT
        c.drawRoundRect(px + dp(14f), py + dp(7f), px + panelW - dp(14f), py + dp(9f), dp(2f), dp(2f), ui.fill)
        val rank = save.rank
        ui.text(c, "رتبه: " + rank.title, px + panelW - dp(14f), py + dp(26f), dp(14f), Theme.MINT, bold = true, align = Paint.Align.RIGHT)
        val next = Ranks.next(rank)
        val xpText = if (next == null) "حداکثر رتبه" else "تا " + next.title + ": " + Fa.num(next.minXp - save.xp) + " XP"
        ui.text(c, xpText, px + panelW - dp(14f), py + dp(44f), dp(11.5f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        ui.bar(c, px + dp(14f), py + dp(58f), panelW - dp(28f), dp(7f), rank.progress(save.xp), Theme.MINT)
        ui.text(c, "ستاره‌ها  " + Fa.num(save.totalStars) + " / " + Fa.num(Levels.all.size * 3),
            px + panelW - dp(14f), py + dp(80f), dp(11.5f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        // آیکون ستاره کوچک کنار متن
        ui.iconStar(c, px + panelW - dp(14f) - ui.measure("ستاره‌ها  " + Fa.num(save.totalStars) + " / " + Fa.num(Levels.all.size * 3), dp(11.5f)) - dp(10f), py + dp(80f) - dp(2f), dp(5f), Theme.AMBER)

        drawButtons(c)
        ui.text(c, "نسخه " + Fa.num("0.0.10"), vw - dp(16f), vh - dp(16f), dp(10.5f), Theme.TEXT_FAINT, align = Paint.Align.RIGHT)
    }

    private fun drawCornerBrackets(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val len = dp(10f)
        val sw = dp(1.4f)
        ui.stroke.color = Theme.withAlpha(Theme.AMBER, 0.70f)
        ui.stroke.strokeWidth = sw
        // TL
        c.drawLine(x, y, x + len, y, ui.stroke); c.drawLine(x, y, x, y + len, ui.stroke)
        // TR
        c.drawLine(x + w - len, y, x + w, y, ui.stroke); c.drawLine(x + w, y, x + w, y + len, ui.stroke)
        // BL
        c.drawLine(x, y + h - len, x, y + h, ui.stroke); c.drawLine(x, y + h, x + len, y + h, ui.stroke)
        // BR
        c.drawLine(x + w - len, y + h, x + w, y + h, ui.stroke); c.drawLine(x + w, y + h - len, x + w, y + h, ui.stroke)
    }
}
