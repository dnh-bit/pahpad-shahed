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
import ir.shahed.pahpad.game.MissionResult
import ir.shahed.pahpad.game.Seeds

/** صفحه نتیجه ماموریت: امتیاز، ستاره، سکه و تجربه */
class ResultScreen(
    game: MainActivity,
    private val level: LevelDef,
    private val mode: MissionMode,
    private val res: MissionResult,
    private val seed: Long
) : BaseScreen(game) {

    private val nextLevel = if (mode == MissionMode.STORY) Levels.next(level) else null

    private val again = UiButton("تلاش مجدد", Theme.AMBER) {
        game.show(GameScreen(game, level, mode, Seeds.forMission(level, mode, save)))
    }
    private val next = UiButton("ماموریت بعدی", Theme.MINT, filled = true) {
        val n = nextLevel
        if (n != null && save.isLevelUnlocked(n.id)) game.show(BriefingScreen(game, n, MissionMode.STORY))
        else game.show(ChapterSelectScreen(game))
    }
    private val menu = UiButton("منوی اصلی", Theme.TEXT_DIM) { game.show(MainMenuScreen(game)) }

    private var revealed = 0
    private var revealTimer = 0f

    private val rows: List<Triple<String, Int, Int>> = listOf(
        Triple("دقت برخورد", res.accuracy, 1000),
        Triple("زمان ماموریت", res.timeScore, 500),
        Triple("اجتناب از پدافند", res.defense, 300),
        Triple("مدیریت سوخت", res.fuelScore, 200),
        Triple("بونوس‌های مخفی", res.bonusScore, 500)
    )

    init {
        buttons.addAll(listOf(next, again, menu))
        next.visible = res.success && nextLevel != null
        if (!next.visible) next.enabled = false
    }

    override fun onBack(): Boolean {
        game.show(MainMenuScreen(game))
        return true
    }

    override fun layoutUi(w: Float, h: Float) {
        val bw = w * 0.22f
        val bh = dp(46f)
        val y = h - dp(64f)
        val total = if (next.visible) 3 else 2
        val gap = dp(10f)
        val startX = (w - (bw * total + gap * (total - 1))) / 2f
        var i = 0
        if (next.visible) { next.set(startX, y, bw, bh); i++ }
        again.set(startX + i * (bw + gap), y, bw, bh); i++
        menu.set(startX + i * (bw + gap), y, bw, bh)
    }

    override fun update(dt: Float) {
        revealTimer += dt
        if (revealTimer > 0.28f && revealed < rows.size) {
            revealed++
            revealTimer = 0f
            audio.beep(700f + revealed * 90f, 60, 0.25f)
        }
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh, if (res.success) Theme.MINT else Theme.RED)

        val color = if (res.success) Theme.MINT else Theme.RED
        ui.text(c, if (res.success) "ماموریت موفق" else "ماموریت ناموفق", vw / 2f, dp(34f),
            dp(26f), color, bold = true)
        ui.text(c, res.reason, vw / 2f, dp(60f), dp(12.5f), Theme.TEXT_DIM)

        // ---- ستون چپ: ستاره‌ها و پاداش
        val lx = vw * 0.24f
        ui.stars(c, lx, vh * 0.30f, dp(20f), if (revealed >= rows.size) res.stars else 0)
        ui.text(c, "امتیاز نهایی", lx, vh * 0.44f, dp(12f), Theme.TEXT_DIM)
        ui.text(c, Fa.grouped(res.total), lx, vh * 0.52f, dp(34f), Theme.TEXT, bold = true)

        val pw = vw * 0.34f
        val px = lx - pw / 2f
        val py = vh * 0.60f
        ui.panel(c, px, py, pw, dp(80f))
        ui.iconCoin(c, px + pw - dp(24f), py + dp(24f), dp(9f))
        ui.text(c, "سکه دریافتی: " + Fa.grouped(res.coins), px + pw - dp(40f), py + dp(24f),
            dp(13.5f), Theme.AMBER, bold = true, align = Paint.Align.RIGHT)
        ui.text(c, "تجربه دریافتی: " + Fa.grouped(res.xp), px + pw - dp(40f), py + dp(50f),
            dp(13.5f), Theme.MINT, bold = true, align = Paint.Align.RIGHT)
        ui.text(c, "زمان: " + Fa.clock(res.elapsed), px + dp(16f), py + dp(64f), dp(11.5f),
            Theme.TEXT_DIM, align = Paint.Align.LEFT)

        // ---- ستون راست: ریز امتیازها
        val rx = vw * 0.52f
        val rw = vw * 0.42f
        var y = vh * 0.18f
        ui.text(c, "جزئیات امتیاز", rx + rw - dp(6f), y - dp(14f), dp(13f), Theme.TEXT, bold = true,
            align = Paint.Align.RIGHT)
        for ((i, row) in rows.withIndex()) {
            val shown = i < revealed
            ui.panel(c, rx, y, rw, dp(38f), Theme.withAlpha(Theme.PANEL, if (shown) 1f else 0.4f),
                Theme.LINE, dp(10f))
            ui.text(c, row.first, rx + rw - dp(14f), y + dp(19f), dp(12.5f),
                if (shown) Theme.TEXT else Theme.TEXT_FAINT, align = Paint.Align.RIGHT)
            val value = if (shown) Fa.num(row.second) + " / " + Fa.num(row.third) else "—"
            ui.text(c, value, rx + dp(14f), y + dp(19f), dp(12.5f),
                if (shown && row.second > 0) Theme.AMBER else Theme.TEXT_FAINT, align = Paint.Align.LEFT)
            if (shown) {
                ui.bar(c, rx + dp(90f), y + dp(30f), rw - dp(200f), dp(3.5f),
                    row.second.toFloat() / row.third.toFloat(), Theme.MINT)
            }
            y += dp(44f)
        }

        if (mode == MissionMode.STORY && res.success) {
            val best = save.bestScoreOf(level.id)
            ui.text(c, "بهترین امتیاز شما در این مرحله: " + Fa.grouped(best), rx + rw - dp(6f), y + dp(6f),
                dp(11.5f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        }
        if (mode == MissionMode.ENDLESS) {
            ui.text(c, "رکورد حالت بی‌نهایت: " + Fa.grouped(save.endlessBest), rx + rw - dp(6f), y + dp(6f),
                dp(11.5f), Theme.ORANGE, align = Paint.Align.RIGHT)
        }

        drawButtons(c)
    }
}
