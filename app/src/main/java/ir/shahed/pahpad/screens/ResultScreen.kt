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

/**
 * صفحه‌ی نتیجه.
 *
 * ستون چپ قانون ستاره‌ها را نشان می‌دهد: هر ستاره یک شرط، متن شرط، و عددی که بازیکن
 * واقعاً به دست آورده. ستون راست ریز امتیازها است. ستاره‌ها دیگر به مجموع امتیاز
 * گره نخورده‌اند، پس همیشه معلوم است کدام شرط رد شده و چرا.
 */
class ResultScreen(
    game: MainActivity,
    private val level: LevelDef,
    private val mode: MissionMode,
    private val res: MissionResult,
    private val seed: Long
) : BaseScreen(game) {

    private val nextLevel = if (mode == MissionMode.STORY) Levels.next(level) else null
    private val rules = res.starRules()

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

    private val steps = rules.size + rows.size

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
        if (revealTimer > 0.22f && revealed < steps) {
            revealed++
            revealTimer = 0f
            audio.beep(700f + revealed * 70f, 60, 0.25f)
        }
    }

    override fun render(c: Canvas) {
        val accent = if (res.success) Theme.MINT else Theme.RED
        ui.background(c, vw, vh, accent)

        val title = when {
            res.aborted -> "ماموریت لغو شد"
            res.success -> "ماموریت موفق"
            else -> "ماموریت ناموفق"
        }
        ui.text(c, title, vw / 2f, dp(30f), dp(24f), accent, bold = true)
        ui.text(c, res.reason, vw / 2f, dp(54f), dp(12f), Theme.TEXT_DIM)

        drawStarRules(c)
        drawScoreRows(c)
        drawButtons(c)
    }

    // ------------------------------------------------------- قانون ستاره‌ها

    private fun drawStarRules(c: Canvas) {
        val pw = vw * 0.42f
        val px = dp(16f)
        val right = px + pw
        var y = dp(74f)

        val earned = if (revealed >= rules.size) res.stars else rules.take(revealed).count { it.passed }
        ui.stars(c, px + pw / 2f, y + dp(16f), dp(15f), earned)
        y += dp(40f)
        ui.text(c, "قانون ستاره‌ها", right - dp(6f), y, dp(12.5f), Theme.TEXT, bold = true,
            align = Paint.Align.RIGHT)
        y += dp(14f)

        val rowH = dp(46f)
        for ((i, rule) in rules.withIndex()) {
            val shown = i < revealed
            val ok = shown && rule.passed
            val fill = when {
                !shown -> Theme.withAlpha(Theme.PANEL, 0.4f)
                ok -> Theme.withAlpha(Theme.MINT, 0.13f)
                else -> Theme.withAlpha(Theme.RED, 0.10f)
            }
            val edge = when {
                !shown -> Theme.LINE
                ok -> Theme.withAlpha(Theme.MINT, 0.45f)
                else -> Theme.withAlpha(Theme.RED, 0.40f)
            }
            ui.panel(c, px, y, pw, rowH - dp(6f), fill, edge, dp(10f))
            ui.star(c, right - dp(18f), y + dp(20f), dp(9f), ok, if (ok) Theme.AMBER else Theme.TEXT_FAINT)
            ui.text(c, rule.title, right - dp(34f), y + dp(14f), dp(12f),
                if (shown) Theme.TEXT else Theme.TEXT_FAINT, bold = true, align = Paint.Align.RIGHT)
            ui.text(c, rule.requirement, right - dp(34f), y + dp(30f), dp(10f), Theme.TEXT_FAINT,
                align = Paint.Align.RIGHT)
            ui.text(c, if (shown) rule.measured else "—", px + dp(12f), y + dp(20f), dp(11.5f),
                if (ok) Theme.MINT else if (shown) Theme.RED else Theme.TEXT_FAINT,
                bold = true, align = Paint.Align.LEFT)
            y += rowH
        }

        y += dp(6f)
        ui.panel(c, px, y, pw, dp(64f))
        ui.text(c, "امتیاز نهایی", right - dp(14f), y + dp(18f), dp(11.5f), Theme.TEXT_DIM,
            align = Paint.Align.RIGHT)
        ui.text(c, Fa.grouped(res.total), right - dp(14f), y + dp(44f), dp(24f), Theme.TEXT,
            bold = true, align = Paint.Align.RIGHT)
        ui.iconCoin(c, px + dp(20f), y + dp(20f), dp(8f))
        ui.text(c, Fa.grouped(res.coins), px + dp(34f), y + dp(20f), dp(12.5f), Theme.AMBER,
            bold = true, align = Paint.Align.LEFT)
        ui.text(c, "تجربه " + Fa.grouped(res.xp), px + dp(34f), y + dp(44f), dp(11.5f), Theme.MINT,
            align = Paint.Align.LEFT)
        ui.text(c, Fa.clock(res.elapsed), px + pw / 2f + dp(20f), y + dp(44f), dp(11f), Theme.TEXT_DIM)
    }

    // --------------------------------------------------------- ریز امتیازها

    private fun drawScoreRows(c: Canvas) {
        val rw = vw * 0.44f
        val rx = vw - rw - dp(16f)
        var y = dp(88f)
        ui.text(c, "جزئیات امتیاز", rx + rw - dp(6f), y - dp(14f), dp(12.5f), Theme.TEXT, bold = true,
            align = Paint.Align.RIGHT)
        for ((i, row) in rows.withIndex()) {
            val shown = i + rules.size < revealed
            ui.panel(c, rx, y, rw, dp(36f), Theme.withAlpha(Theme.PANEL, if (shown) 1f else 0.4f),
                Theme.LINE, dp(10f))
            ui.text(c, row.first, rx + rw - dp(14f), y + dp(18f), dp(12f),
                if (shown) Theme.TEXT else Theme.TEXT_FAINT, align = Paint.Align.RIGHT)
            val value = if (shown) Fa.num(row.second) + " / " + Fa.num(row.third) else "—"
            ui.text(c, value, rx + dp(14f), y + dp(18f), dp(12f),
                if (shown && row.second > 0) Theme.AMBER else Theme.TEXT_FAINT, align = Paint.Align.LEFT)
            if (shown) {
                ui.bar(c, rx + dp(86f), y + dp(28f), (rw - dp(190f)).coerceAtLeast(dp(20f)), dp(3.5f),
                    row.second.toFloat() / row.third.toFloat(), Theme.MINT)
            }
            y += dp(42f)
        }
        y += dp(4f)
        ui.text(c, "بونوس‌ها فقط امتیاز و سکه می‌دهند و شرط ستاره نیستند.",
            rx + rw - dp(6f), y, dp(10.5f), Theme.TEXT_FAINT, align = Paint.Align.RIGHT)
        y += dp(18f)
        if (mode == MissionMode.STORY) {
            ui.text(c, "بهترین امتیاز شما در این مرحله: " + Fa.grouped(save.bestScoreOf(level.id)),
                rx + rw - dp(6f), y, dp(11f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        }
        if (mode == MissionMode.ENDLESS) {
            ui.text(c, "رکورد حالت بی‌نهایت: " + Fa.grouped(save.endlessBest),
                rx + rw - dp(6f), y, dp(11f), Theme.ORANGE, align = Paint.Align.RIGHT)
        }
        if (mode == MissionMode.DAILY) {
            ui.text(c, "رکورد چالش امروز: " + Fa.grouped(save.dailyBest),
                rx + rw - dp(6f), y, dp(11f), Theme.AMBER, align = Paint.Align.RIGHT)
        }
    }
}
