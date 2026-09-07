package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import ir.shahed.pahpad.data.DroneModels
import ir.shahed.pahpad.data.LevelDef
import ir.shahed.pahpad.data.StarRules
import ir.shahed.pahpad.data.UpgradeKind
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.Seeds
import ir.shahed.pahpad.game.World

/** بریفینگ ماموریت: توضیح هدف و نقشه تاکتیکی */
class BriefingScreen(
    game: MainActivity,
    private val level: LevelDef,
    private val mode: MissionMode
) : BaseScreen(game) {

    private val seed = Seeds.forMission(level, mode, save)
    private val world = World(level, seed)
    private val map = RectF()

    private val start = UiButton("شروع ماموریت", Theme.MINT, filled = true) {
        game.show(GameScreen(game, level, mode, seed))
    }
    private val hangar = UiButton("انتخاب پهباد", Theme.AMBER) { game.show(ShopScreen(game)) }
    private val back = UiButton("بازگشت", Theme.TEXT_DIM) { goBack() }

    init {
        buttons.addAll(listOf(start, hangar, back))
    }

    private fun goBack() {
        when (mode) {
            MissionMode.STORY -> game.show(LevelSelectScreen(game, level.chapter))
            else -> game.show(MainMenuScreen(game))
        }
    }

    override fun onBack(): Boolean {
        goBack()
        return true
    }

    override fun layoutUi(w: Float, h: Float) {
        map.set(dp(16f), h * 0.16f, w * 0.44f, h - dp(70f))
        val bw = (w * 0.52f - dp(20f)) / 3f
        val by = h - dp(62f)
        val x = w * 0.47f
        start.set(x, by, bw * 1.3f, dp(48f))
        hangar.set(x + bw * 1.3f + dp(8f), by, bw * 0.9f, dp(48f))
        back.set(x + bw * 1.3f + bw * 0.9f + dp(16f), by, bw * 0.7f, dp(48f))
    }

    private fun mx(worldX: Float): Float {
        val span = 900f
        return map.centerX() + (worldX / span) * map.width()
    }

    private fun my(worldZ: Float): Float {
        val minZ = -120f
        val maxZ = level.distance + 260f
        val t = ((worldZ - minZ) / (maxZ - minZ)).coerceIn(-0.2f, 1.2f)
        return map.bottom - t * map.height()
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh)
        Deco.resources(this, c)

        val title = if (mode == MissionMode.STORY) "ماموریت " + Fa.num(level.code) + " · " + level.title else level.title
        // بنر تصویری منطقه در بالای ستون اطلاعات
        val envName = when (level.env.name) {
            "DESERT" -> "env_desert"; "URBAN" -> "env_urban"
            "NAVAL" -> "env_naval"; else -> "env_special"
        }
        val bx = vw * 0.47f
        val bw2 = vw - bx - dp(16f)
        val bh2 = dp(54f)
        ui.image(ui.context, c, envName, bx, dp(12f), bw2, bh2, radius = dp(10f))
        ui.text(c, title, vw / 2f - dp(30f), dp(12f) + bh2 / 2f + dp(5f), dp(17f), Theme.TEXT, bold = true)
        ui.text(c, "بریفینگ ماموریت", vw / 2f, dp(12f) + bh2 + dp(20f), dp(12f), Theme.TEXT_DIM)

        drawMap(c)
        drawInfo(c)
        drawButtons(c)
    }

    // ------------------------------------------------------------- نقشه

    private fun drawMap(c: Canvas) {
        ui.panel(c, map, Theme.withAlpha(Theme.PANEL, 0.92f), Theme.LINE, dp(14f))
        c.save()
        c.clipRect(map)

        // شبکه
        ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.08f)
        ui.stroke.strokeWidth = dp(1f)
        var gx = map.left
        while (gx < map.right) { c.drawLine(gx, map.top, gx, map.bottom, ui.stroke); gx += dp(24f) }
        var gy = map.top
        while (gy < map.bottom) { c.drawLine(map.left, gy, map.right, gy, ui.stroke); gy += dp(24f) }

        // مناطق جنگ الکترونیک
        for (e in world.ewZones) {
            ui.fill.color = Theme.withAlpha(Theme.VIOLET, 0.14f)
            ui.fill.shader = null
            c.drawCircle(mx(e.x), my(e.z), (e.radius / 900f) * map.width(), ui.fill)
        }
        // مناطق رادار
        for (r in world.radars) {
            ui.stroke.color = Theme.withAlpha(Theme.BLUE, 0.5f)
            ui.stroke.strokeWidth = dp(1.2f)
            c.drawCircle(mx(r.x), my(r.z), (r.radius / 900f) * map.width(), ui.stroke)
        }
        // پدافند
        for (a in world.aaSites) {
            val px = mx(a.x)
            val py = my(a.z)
            ui.fill.color = Theme.withAlpha(Theme.RED, 0.12f)
            ui.fill.shader = null
            c.drawCircle(px, py, (a.range / 900f) * map.width(), ui.fill)
            ui.stroke.color = Theme.withAlpha(Theme.RED, 0.55f)
            ui.stroke.strokeWidth = dp(1f)
            c.drawCircle(px, py, (a.range / 900f) * map.width(), ui.stroke)
            ui.fill.color = Theme.RED
            c.drawCircle(px, py, dp(3.5f), ui.fill)
        }
        // بونوس‌ها
        for (b in world.bonuses) {
            ui.iconStar(c, mx(b.x), my(b.z), dp(4.5f), Theme.withAlpha(Theme.AMBER, 0.8f))
        }
        // مسیر پیشنهادی
        ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.55f)
        ui.stroke.strokeWidth = dp(1.6f)
        val t0 = world.targets.first()
        c.drawLine(mx(0f), my(0f), mx(t0.x), my(t0.z), ui.stroke)

        // اهداف
        for (t in world.targets) {
            val px = mx(t.x)
            val py = my(t.z)
            ui.stroke.color = Theme.AMBER
            ui.stroke.strokeWidth = dp(1.6f)
            c.drawCircle(px, py, dp(9f), ui.stroke)
            c.drawLine(px - dp(13f), py, px + dp(13f), py, ui.stroke)
            c.drawLine(px, py - dp(13f), px, py + dp(13f), ui.stroke)
        }
        // نقطه پرتاب
        Deco.droneIcon(this, c, mx(0f), my(0f), dp(8f), Theme.MINT, 0f)
        ui.text(c, "نقطه پرتاب", mx(0f), my(0f) + dp(22f), dp(10.5f), Theme.MINT)

        c.restore()

        // راهنما و مقیاس
        ui.text(c, "فاصله تا هدف: " + Fa.meters(level.distance), map.centerX(), map.top + dp(14f),
            dp(11.5f), Theme.TEXT_DIM)
        val legendY = map.bottom - dp(14f)
        ui.text(c, "قرمز: پدافند · آبی: رادار · بنفش: اخلال · زرد: بونوس", map.centerX(), legendY,
            dp(10.5f), Theme.TEXT_FAINT)
    }

    // ------------------------------------------------------------- اطلاعات

    private fun drawInfo(c: Canvas) {
        val x = vw * 0.47f
        val w = vw - x - dp(16f)
        val right = x + w
        val bottom = vh - dp(74f)
        var y = dp(84f)
        val gap = dp(8f)

        fun fits(height: Float): Boolean = y + height <= bottom

        // ---------------------------------------------------------- بریفینگ
        val briefLines = ui.wrap(level.briefing, dp(11.5f), w - dp(28f))
        val briefH = dp(34f) + briefLines.size * dp(16f)
        if (fits(briefH)) {
            ui.panel(c, x, y, w, briefH)
            ui.text(c, "هدف: " + level.targetName, right - dp(14f), y + dp(18f), dp(14f), Theme.AMBER,
                bold = true, align = Paint.Align.RIGHT)
            var ty = y + dp(40f)
            for (line in briefLines) {
                ui.text(c, line, right - dp(14f), ty, dp(11.5f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
                ty += dp(16f)
            }
            y += briefH + gap
        }

        // ------------------------------------------------------ شرط ستاره‌ها
        val model = DroneModels.byId(save.selectedDroneId) ?: DroneModels.all.first()
        val cruise = model.speedAt(save.upgradeLevel(model.id, UpgradeKind.SPEED)) * 0.8f / 3.6f
        val par = StarRules.parTime(level.distance, cruise, level.targets)
        val preview = StarRules.preview(level.targets, par)
        val starsH = dp(26f) + preview.size * dp(26f)
        if (fits(starsH)) {
            ui.panel(c, x, y, w, starsH, Theme.withAlpha(Theme.AMBER, 0.08f),
                Theme.withAlpha(Theme.AMBER, 0.35f))
            ui.text(c, "شرط ستاره‌ها", right - dp(12f), y + dp(15f), dp(12f), Theme.AMBER, bold = true,
                align = Paint.Align.RIGHT)
            ui.text(c, "ستاره‌ی خودت را قبل از پرواز بشناس", x + dp(12f), y + dp(15f), dp(10f),
                Theme.TEXT_FAINT, align = Paint.Align.LEFT)
            var sy = y + dp(36f)
            for (rule in preview) {
                ui.star(c, right - dp(16f), sy, dp(6.5f), true, Theme.withAlpha(Theme.AMBER, 0.75f))
                ui.text(c, rule.requirement, right - dp(30f), sy, dp(10.5f), Theme.TEXT,
                    align = Paint.Align.RIGHT)
                sy += dp(26f)
            }
            y += starsH + gap
        }

        // ------------------------------------------------------------ چالش‌ها
        val chipsH = dp(62f)
        if (fits(chipsH)) {
            ui.panel(c, x, y, w, chipsH)
            ui.text(c, "چالش این ماموریت", right - dp(14f), y + dp(16f), dp(12f), Theme.TEXT, bold = true,
                align = Paint.Align.RIGHT)
            var cxChip = right - dp(50f)
            val chipY = y + dp(41f)
            val chips = ArrayList<Pair<String, Int>>()
            chips.add(Pair(level.newChallenge, Theme.difficultyColor(level.difficulty)))
            if (level.aaSites > 0) chips.add(Pair("پدافند " + Fa.num(level.aaSites), Theme.RED))
            if (level.radarZones > 0) chips.add(Pair("رادار", Theme.BLUE))
            if (level.ewZones > 0) chips.add(Pair("جنگ الکترونیک", Theme.VIOLET))
            if (level.movingTarget) chips.add(Pair("هدف متحرک", Theme.ORANGE))
            if (level.wind > 0.3f) chips.add(Pair("باد شدید", Theme.TEXT_DIM))
            if (level.timeLimit > 0f) chips.add(Pair("زمان " + Fa.num(level.timeLimit, 0) + " ثانیه", Theme.AMBER))
            for (ch in chips) {
                val cw = ui.measure(ch.first, dp(11f)) + dp(22f)
                if (cxChip - cw < x + dp(10f)) break
                ui.chip(c, cxChip - cw / 2f, chipY, ch.first, ch.second, dp(11f))
                cxChip -= cw + dp(8f)
            }
            y += chipsH + gap
        }

        // ------------------------------------------------------ پهباد انتخابی
        val droneH = dp(84f)
        if (fits(droneH)) {
            ui.panel(c, x, y, w, droneH)
            val sideName = when (model.id) {
                "shahed136" -> "drone_136_side"
                "shahed238" -> "drone_238_side"
                "shahedx" -> "drone_x_side"
                else -> "drone_131_side"
            }
            ui.imageFit(ui.context, c, sideName, x + dp(8f), y + dp(6f), dp(96f), dp(70f))
            ui.text(c, "پهباد انتخابی: " + model.name, right - dp(14f), y + dp(16f), dp(13f), model.color,
                bold = true, align = Paint.Align.RIGHT)
            val stats = listOf(
                Pair("برد", UpgradeKind.RANGE),
                Pair("سرعت", UpgradeKind.SPEED),
                Pair("انفجار", UpgradeKind.BLAST),
                Pair("مقاومت", UpgradeKind.ARMOR)
            )
            val colW = (w - dp(120f)) / stats.size
            for ((i, st) in stats.withIndex()) {
                val cxs = right - dp(14f) - colW * i - colW / 2f
                ui.text(c, st.first, cxs, y + dp(38f), dp(10.5f), Theme.TEXT_FAINT)
                val lvl = save.upgradeLevel(model.id, st.second)
                ui.text(c, st.second.valueAt(lvl, model), cxs, y + dp(56f), dp(11f), Theme.TEXT)
                ui.text(c, "سطح " + Fa.num(lvl), cxs, y + dp(72f), dp(9.5f), Theme.TEXT_DIM)
            }
            y += droneH + gap
        }

        // ----------------------------------------------------------- راهنما
        val hint = level.tutorial
        if (hint != null && save.showHints) {
            val hintLines = ui.wrap("راهنما: " + hint, dp(11f), w - dp(24f))
            val hintH = dp(16f) + hintLines.size * dp(15f)
            if (fits(hintH)) {
                ui.panel(c, x, y, w, hintH, Theme.withAlpha(Theme.MINT, 0.1f),
                    Theme.withAlpha(Theme.MINT, 0.4f))
                var hy = y + dp(15f)
                for (line in hintLines) {
                    ui.text(c, line, right - dp(12f), hy, dp(11f), Theme.MINT, align = Paint.Align.RIGHT)
                    hy += dp(15f)
                }
            }
        }
    }
}
