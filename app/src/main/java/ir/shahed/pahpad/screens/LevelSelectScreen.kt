package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import ir.shahed.pahpad.data.LevelDef
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.MissionMode

/** انتخاب مرحله در یک فصل */
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
        val n = cards.size.coerceAtLeast(1)
        val cw = (w - margin * 2f - gap * (n - 1)) / n
        val top = h * 0.26f
        val ch = h * 0.48f
        for ((i, b) in cards.withIndex()) b.set(margin + i * (cw + gap), top, cw, ch)
        back.set(margin, h - dp(52f), dp(110f), dp(40f))
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
            ui.panel(c, r, if (b.pressed) Theme.PANEL_HI else Theme.PANEL,
                Theme.withAlpha(if (stars > 0) Theme.MINT else Theme.LINE, if (unlocked) 0.7f else 0.25f), dp(14f))
            val cx = r.centerX()

            ui.chip(c, cx, r.top + dp(20f), "مرحله " + Fa.num(l.code), Theme.difficultyColor(l.difficulty))
            for ((i, line) in ui.wrap(l.title, dp(14f), r.width() - dp(18f), true).withIndex()) {
                ui.text(c, line, cx, r.top + dp(48f) + i * dp(19f), dp(14f),
                    if (unlocked) Theme.TEXT else Theme.TEXT_FAINT, bold = true)
            }
            ui.text(c, "هدف: " + l.targetName, cx, r.top + dp(92f), dp(11.5f), Theme.TEXT_DIM)
            ui.text(c, l.difficultyLabel, cx, r.top + dp(112f), dp(11.5f), Theme.difficultyColor(l.difficulty))

            if (unlocked) {
                ui.stars(c, cx, r.bottom - dp(58f), dp(10f), stars)
                val best = save.bestScoreOf(l.id)
                ui.text(c, if (best > 0) "بهترین امتیاز: " + Fa.grouped(best) else "بازی نشده",
                    cx, r.bottom - dp(28f), dp(11.5f), if (best > 0) Theme.AMBER else Theme.TEXT_FAINT)
            } else {
                ui.iconLock(c, cx, r.bottom - dp(52f), dp(12f))
                ui.text(c, "قفل است", cx, r.bottom - dp(24f), dp(11.5f), Theme.TEXT_FAINT)
            }
        }
        ui.button(c, back)
    }
}
