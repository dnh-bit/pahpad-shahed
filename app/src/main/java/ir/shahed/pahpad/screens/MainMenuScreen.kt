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

/** منوی اصلی بازی */
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
        var y = h * 0.20f
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

        // بنر تصویری بالای ستون عنوان (پهباد بر فراز کویر در غروب)
        val heroW = vw * 0.44f
        val heroH = heroW * 300f / 640f
        val heroX = vw * 0.26f - heroW / 2f
        val heroY = vh * 0.26f - dp(58f)
        ui.image(ui.context, c, "menu_hero", heroX, heroY, heroW, heroH, radius = dp(16f))

        // ستون چپ: لوگو و وضعیت بازیکن
        val lx = vw * 0.26f
        val titleY = heroY + heroH + dp(34f)
        ui.text(c, "پهباد شاهد", lx, titleY, dp(34f), Theme.TEXT, bold = true)
        ui.text(c, "اپراتور پهباد؛ ماموریت‌های دقیق", lx, titleY + dp(26f), dp(13f), Theme.TEXT_DIM)

        val panelW = vw * 0.34f
        val panelH = dp(96f)
        val px = lx - panelW / 2f
        val py = vh * 0.58f
        ui.panel(c, px, py, panelW, panelH)
        val rank = save.rank
        ui.text(c, "رتبه: " + rank.title, px + panelW - dp(14f), py + dp(20f), dp(14.5f), Theme.MINT, bold = true, align = Paint.Align.RIGHT)
        val next = Ranks.next(rank)
        val xpText = if (next == null) "حداکثر رتبه" else "تا " + next.title + ": " + Fa.num(next.minXp - save.xp) + " تجربه"
        ui.text(c, xpText, px + panelW - dp(14f), py + dp(42f), dp(12f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        ui.bar(c, px + dp(14f), py + dp(56f), panelW - dp(28f), dp(7f), rank.progress(save.xp), Theme.MINT)
        ui.text(c, "ستاره‌ها: " + Fa.num(save.totalStars) + " از " + Fa.num(Levels.all.size * 3),
            px + panelW - dp(14f), py + dp(78f), dp(12f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)

        drawButtons(c)
        ui.text(c, "نسخه " + Fa.num("0.0.4"), vw - dp(16f), vh - dp(16f), dp(10.5f), Theme.TEXT_FAINT, align = Paint.Align.RIGHT)
    }
}
