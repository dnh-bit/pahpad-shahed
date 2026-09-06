package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import ir.shahed.pahpad.data.ChapterDef
import ir.shahed.pahpad.data.Levels

/** انتخاب فصل: نقشه عملیات */
class ChapterSelectScreen(game: MainActivity) : BaseScreen(game) {

    private val cards = ArrayList<UiButton>()
    private val back = UiButton("بازگشت", Theme.TEXT_DIM) { game.show(MainMenuScreen(game)) }

    init {
        for (ch in Levels.chapters) {
            val b = UiButton(ch.title, accentOf(ch)) {
                if (save.isChapterUnlocked(ch.number)) {
                    game.show(LevelSelectScreen(game, ch.number))
                } else {
                    audio.beep(320f, 90, 0.3f)
                }
            }
            b.tag = ch
            cards.add(b)
        }
        buttons.addAll(cards)
        buttons.add(back)
    }

    private fun accentOf(ch: ChapterDef): Int = when (ch.number) {
        1 -> Theme.MINT
        2 -> Theme.AMBER
        3 -> Theme.BLUE
        else -> Theme.VIOLET
    }

    override fun onBack(): Boolean {
        game.show(MainMenuScreen(game))
        return true
    }

    override fun layoutUi(w: Float, h: Float) {
        val margin = dp(18f)
        val gap = dp(12f)
        val n = cards.size
        val cw = (w - margin * 2f - gap * (n - 1)) / n
        val chH = h * 0.52f
        val top = h * 0.24f
        for ((i, b) in cards.withIndex()) {
            b.set(margin + i * (cw + gap), top, cw, chH)
        }
        back.set(margin, h - dp(56f), dp(110f), dp(42f))
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh)
        Deco.header(this, c, "انتخاب فصل عملیات", "برای ورود به هر فصل روی آن ضربه بزنید")
        Deco.resources(this, c)

        for (b in cards) {
            val ch = b.tag as ChapterDef
            val unlocked = save.isChapterUnlocked(ch.number)
            val levels = Levels.ofChapter(ch.number)
            val stars = levels.sumOf { save.starsOf(it.id) }
            val maxStars = levels.size * 3
            val r = b.rect

            ui.panel(c, r, if (b.pressed) Theme.PANEL_HI else Theme.PANEL,
                Theme.withAlpha(b.accent, if (unlocked) 0.6f else 0.2f), dp(16f))

            val cx = r.centerX()
            Deco.droneIcon(this, c, cx, r.top + dp(44f), dp(15f), if (unlocked) b.accent else Theme.TEXT_FAINT, 0f)
            ui.text(c, ch.title, cx, r.top + dp(84f), dp(15f), if (unlocked) Theme.TEXT else Theme.TEXT_FAINT, bold = true)
            for ((i, line) in ui.wrap(ch.subtitle, dp(11.5f), r.width() - dp(24f)).withIndex()) {
                ui.text(c, line, cx, r.top + dp(104f) + i * dp(16f), dp(11.5f), Theme.TEXT_DIM)
            }

            if (unlocked) {
                ui.text(c, Fa.num(levels.size) + " ماموریت", cx, r.bottom - dp(66f), dp(12f), Theme.TEXT_DIM)
                ui.iconStar(c, cx + dp(26f), r.bottom - dp(40f), dp(8f), Theme.AMBER)
                ui.text(c, Fa.num(stars) + " از " + Fa.num(maxStars), cx - dp(4f), r.bottom - dp(40f),
                    dp(13f), Theme.AMBER, bold = true)
                ui.bar(c, r.left + dp(16f), r.bottom - dp(24f), r.width() - dp(32f), dp(6f),
                    if (maxStars > 0) stars.toFloat() / maxStars else 0f, b.accent)
            } else {
                ui.iconLock(c, cx, r.bottom - dp(56f), dp(13f))
                ui.text(c, "نیازمند " + Fa.num(ch.requiredStars) + " ستاره", cx, r.bottom - dp(26f),
                    dp(12f), Theme.TEXT_FAINT)
            }
        }
        ui.button(c, back)
    }
}
