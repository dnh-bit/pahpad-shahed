package ir.shahed.pahpad.screens

import android.graphics.Canvas
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import ir.shahed.pahpad.data.LevelDef
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.MissionMode

/**
 * انتخاب مرحله در یک فصل — گرید تطبیقی.
 *
 * بازطراحی: قبلاً همهٔ مرحله‌ها در یک ردیف افقی چیده می‌شدند؛ روی گوشی‌های
 * باریک کارت‌ها به عرض چند پیکسل می‌رسیدند و متن‌ها کاملاً در هم می‌رفتند.
 * حالا تعداد ستون‌ها از روی عرض واقعی و حداقل عرض خوانا انتخاب می‌شود.
 */
class LevelSelectScreen(game: MainActivity, private val chapterNumber: Int) : BaseScreen(game) {

    private val chapter = Levels.chapter(chapterNumber)
    private val levels = Levels.ofChapter(chapterNumber)
    private val cards = ArrayList<UiButton>()
    private val back = UiButton("بازگشت", Theme.TEXT_DIM) { game.show(ChapterSelectScreen(game)) }

    init {
        for (l in levels) {
            val b = UiButton(l.title, Theme.MINT) {
                if (save.isLevelUnlocked(l.id)) {
                    game.show(BriefingScreen(game, l, MissionMode.STORY))
                } else {
                    audio.beep(320f, 90, 0.3f)
                }
            }
            b.tag = l
            cards.add(b)
        }
        buttons.addAll(cards)
        buttons.add(back)
    }

    override fun onBack(): Boolean {
        game.show(ChapterSelectScreen(game))
        return true
    }

    override fun layoutUi(w: Float, h: Float) {
        val margin = dp(16f)
        val gap = dp(10f)
        val availW = w - margin * 2f
        val n = cards.size.coerceAtLeast(1)

        // حداقل عرض خوانا برای یک کارت؛ هرچه جا شد ستون بیشتر
        val minCardW = dp(148f)
        var cols = ((availW + gap) / (minCardW + gap)).toInt().coerceIn(1, n)
        if (cols > n) cols = n
        val rows = (n + cols - 1) / cols

        val top = dp(76f)
        val bottom = h - dp(54f)
        val availH = bottom - top
        val cw = (availW - gap * (cols - 1)) / cols
        val rawCh = (availH - gap * (rows - 1)) / rows
        val ch = rawCh.coerceIn(dp(116f), dp(190f))

        val gridH = rows * ch + (rows - 1) * gap
        val startY = top + ((availH - gridH) / 2f).coerceAtLeast(0f)

        for ((i, b) in cards.withIndex()) {
            val col = i % cols
            val row = i / cols
            b.set(margin + col * (cw + gap), startY + row * (ch + gap), cw, ch)
        }
        back.set(margin, h - dp(50f), dp(110f), dp(38f))
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh)
        Deco.header(this, c, chapter?.title ?: "مراحل", chapter?.subtitle)
        Deco.resources(this, c)

        for (b in cards) {
            val l = b.tag as LevelDef
            val unlocked = save.isLevelUnlocked(l.id)
            val stars = save.starsOf(l.id)
            val r = b.rect
            val accent = if (stars > 0) Theme.MINT else Theme.difficultyColor(l.difficulty)
            ui.panel(c, r, if (b.pressed) Theme.PANEL_HI else Theme.PANEL,
                Theme.withAlpha(if (stars > 0) Theme.MINT else Theme.LINE, if (unlocked) 0.65f else 0.22f), dp(14f))

            // نوار لهجه بالا
            ui.fill.color = Theme.withAlpha(accent, if (unlocked) 0.85f else 0.25f)
            ui.fill.shader = null
            c.drawRoundRect(r.left + dp(12f), r.top + dp(8f), r.right - dp(12f), r.top + dp(10f), dp(2f), dp(2f), ui.fill)

            val cx = r.centerX()
            val maxW = r.width() - dp(16f)

            ui.chip(c, cx, r.top + dp(20f), "مرحله " + Fa.num(l.code), Theme.difficultyColor(l.difficulty))

            // عنوان: اندازه و تعداد خط بر اساس عرض واقعی کارت (نه عدد ثابت)
            var titleSize = dp(13.5f)
            var lines = ui.wrap(l.title, titleSize, maxW, true)
            var guard = 0
            while (lines.size > 2 && titleSize > dp(10f) && guard < 12) {
                titleSize -= dp(0.5f)
                lines = ui.wrap(l.title, titleSize, maxW, true)
                guard++
            }
            if (lines.size > 2) lines = lines.take(2)
            for ((i, line) in lines.withIndex()) {
                ui.text(c, line, cx, r.top + dp(42f) + i * (titleSize + dp(4f)), titleSize,
                    if (unlocked) Theme.TEXT else Theme.TEXT_FAINT, bold = true)
            }

            val infoTop = r.top + dp(42f) + lines.size * (titleSize + dp(4f)) + dp(10f)
            ui.text(c, l.difficultyLabel, cx, infoTop, dp(11f), Theme.difficultyColor(l.difficulty))

            if (unlocked) {
                ui.stars(c, cx, r.bottom - dp(50f), dp(9f), stars)
                val best = save.bestScoreOf(l.id)
                ui.text(c, if (best > 0) Fa.grouped(best) else "بازی نشده",
                    cx, r.bottom - dp(22f), dp(11f), if (best > 0) Theme.AMBER else Theme.TEXT_FAINT)
            } else {
                ui.iconLock(c, cx, r.bottom - dp(44f), dp(11f))
                ui.text(c, "قفل است", cx, r.bottom - dp(20f), dp(11f), Theme.TEXT_FAINT)
            }
        }
        ui.button(c, back)
    }
}
