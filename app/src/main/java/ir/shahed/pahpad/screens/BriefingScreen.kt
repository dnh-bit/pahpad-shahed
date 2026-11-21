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

    /**
     * ناحیهٔ ترسیم نقشه (بدون نوار راهنمای رنگی پایین).
     *
     * چرا جدا شد: نقطهٔ پرتاب روی `my(0)` می‌نشیند که نزدیک لبهٔ پایین
     * نقشه است، و نوار راهنما هم همان‌جا بود. هر جای‌گیری برچسب یا روی
     * آیکون می‌افتاد یا روی «قرمز: پدافند…». با کنار گذاشتن یک نوار
     * اختصاصی، برچسب همیشه جای آزاد دارد.
     */
    private val plot = RectF()

    /**
     * جعبه‌های رسم‌شده برای تست — «چه چیزی واقعاً کشیده شد و کجا».
     *
     * چرا لازم است: سه باگ این صفحه (کارت پهبادِ غایب، زیرنویسِ بریده،
     * مناطقِ بیضی‌شده) هیچ‌کدام با تست تصویریِ چشمی گرفته نمی‌شدند. تست
     * باید **خروجی رسم** را ببیند، نه کد را دوباره حساب کند.
     */
    class DebugBox(val name: String, val rect: RectF)
    private val debugBoxes = ArrayList<DebugBox>()

    /** فقط برای تست؛ در بازی هیچ‌کس صدا نمی‌زند. */
    fun debugBoxes(): List<DebugBox> = debugBoxes

    private fun mark(name: String, l: Float, t: Float, r: Float, b: Float) {
        debugBoxes.add(DebugBox(name, RectF(l, t, r, b)))
    }

    private val start = UiButton("شروع ماموریت", Theme.MINT, filled = true) {
        // ابتدا صفحهٔ آماده‌سازی: دنیا و بافت‌ها آنجا ساخته و گرم می‌شوند،
        // پس فریم اول بازی لگ نمی‌زند.
        game.show(LoadingScreen(game, level, mode, seed))
    }
    private val hangar = UiButton("انتخاب پهپاد", Theme.AMBER) {
        game.show(ShopScreen(game) { BriefingScreen(game, level, mode) })
    }
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
        plot.set(map.left, map.top, map.right, map.bottom - dp(30f))
        val bw = (w * 0.52f - dp(20f)) / 3f
        val by = h - dp(62f)
        val x = w * 0.47f
        start.set(x, by, bw * 1.3f, dp(48f))
        hangar.set(x + bw * 1.3f + dp(8f), by, bw * 0.9f, dp(48f))
        back.set(x + bw * 1.3f + bw * 0.9f + dp(16f), by, bw * 0.7f, dp(48f))
    }

    private fun mx(worldX: Float): Float {
        val span = 900f
        return plot.centerX() + (worldX / span) * plot.width()
    }

    private fun my(worldZ: Float): Float {
        val minZ = -120f
        val maxZ = level.distance + 260f
        val t = ((worldZ - minZ) / (maxZ - minZ)).coerceIn(-0.2f, 1.2f)
        return plot.bottom - t * plot.height()
    }

    /**
     * شعاع مناطق روی نقشه — **دقیقاً همان مقیاس نسخهٔ ۰.۲.۷ (محور X)**.
     *
     * چرا برنگشتیم به مقیاس محور Z یا میانگین دو محور: نقشه در محور Z
     * فشرده‌تر است (چون کل مسیر پرواز در ارتفاع صفحه جا می‌شود). اگر
     * شعاع را با مقیاس Z بگیریم، حلقه‌ها کوچک و ناخوانا می‌شوند و اگر
     * بیضی بکشیم همان بیضی‌های پهنِ درهم می‌شوند که کاربر گفت «بدتر
     * شده». مقیاس X همان چیزی است که در نسخهٔ ۰.۲.۷ بود و به‌نظر خودش
     * بهتر بود؛ فقط شکل باید گرد بماند.
     */
    private fun radiusPx(meters: Float): Float = meters * (plot.width() / 900f)

    override fun render(c: Canvas) {
        debugBoxes.clear()
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
        val bh2 = dp(56f)
        ui.image(ui.context, c, envName, bx, dp(12f), bw2, bh2, radius = dp(10f))
        // پردهٔ تیره روی نیمهٔ پایین بنر تا متن روی عکس روشن خوانا باشد
        ui.fill.shader = null
        ui.fill.color = Theme.withAlpha(Theme.PANEL, 0.72f)
        c.drawRoundRect(bx, dp(12f) + bh2 * 0.46f, bx + bw2, dp(12f) + bh2, dp(10f), dp(10f), ui.fill)
        ui.fill.color = Theme.withAlpha(Theme.PANEL, 0.72f)
        c.drawRect(bx, dp(12f) + bh2 * 0.46f, bx + bw2, dp(12f) + bh2 - dp(10f), ui.fill)
        // عنوان باید داخل بنر بماند؛ اگر بلند بود کوچک‌تر می‌شود، نه اینکه
        // با آفست ثابت از پنل بیرون بزند و روی متن‌های دیگر بیفتد.
        val titleMaxW = bw2 - dp(24f)
        var titleSize = dp(16f)
        while (titleSize > dp(9.5f) && ui.measure(title, titleSize, bold = true) > titleMaxW) {
            titleSize -= dp(0.5f)
        }
        val titleText = if (ui.measure(title, titleSize, bold = true) > titleMaxW) {
            // حتی با کوچک‌ترین اندازه جا نشد → کوتاه کن
            var t = title
            while (t.length > 4 && ui.measure(t + "…", titleSize, bold = true) > titleMaxW) {
                t = t.dropLast(1)
            }
            t + "…"
        } else title
        mark("banner", bx, dp(12f), bx + bw2, dp(12f) + bh2)
        val titleW = ui.measure(titleText, titleSize, bold = true)
        mark("title", bx + bw2 / 2f - titleW / 2f, dp(12f) + bh2 * 0.60f - titleSize,
            bx + bw2 / 2f + titleW / 2f, dp(12f) + bh2 * 0.60f + dp(3f))
        ui.text(c, titleText, bx + bw2 / 2f, dp(12f) + bh2 * 0.60f, titleSize, Theme.TEXT, bold = true)
        // زیرنویس **داخل** بنر — قبلاً بیرون و در نوار ۹dp بین بنر و پنل اول
        // می‌افتاد و عملاً دیده نمی‌شد (شکایت کاربر: «بالا سمت راست یه جمله
        // دیده نمی‌شه»).
        val subW = ui.measure("بریفینگ ماموریت", dp(10f))
        mark("subtitle", bx + bw2 / 2f - subW / 2f, dp(12f) + bh2 * 0.86f - dp(10f),
            bx + bw2 / 2f + subW / 2f, dp(12f) + bh2 * 0.86f + dp(3f))
        ui.text(c, "بریفینگ ماموریت", bx + bw2 / 2f, dp(12f) + bh2 * 0.86f, dp(10f),
            Theme.withAlpha(Theme.TEXT_DIM, 0.95f))

        drawMap(c)
        drawInfo(c)
        drawButtons(c)
    }

    // ------------------------------------------------------------- نقشه

    private fun drawMap(c: Canvas) {
        mark("map", map.left, map.top, map.right, map.bottom)
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

        // مناطق جنگ الکترونیک — دایرهٔ گرد، نه بیضی کشیده
        for (e in world.ewZones) {
            val px = mx(e.x); val py = my(e.z)
            val rad = radiusPx(e.radius)
            ui.fill.color = Theme.withAlpha(Theme.VIOLET, 0.14f)
            ui.fill.shader = null
            c.drawCircle(px, py, rad, ui.fill)
        }
        // مناطق رادار
        for (r in world.radars) {
            val px = mx(r.x); val py = my(r.z)
            val rad = radiusPx(r.radius)
            ui.stroke.color = Theme.withAlpha(Theme.BLUE, 0.5f)
            ui.stroke.strokeWidth = dp(1.2f)
            c.drawCircle(px, py, rad, ui.stroke)
        }
        // پدافند
        for (a in world.aaSites) {
            val px = mx(a.x)
            val py = my(a.z)
            val rad = radiusPx(a.range)
            if (debugBoxes.none { it.name == "zone0" }) {
                mark("zone0", px - rad, py - rad, px + rad, py + rad)
            }
            ui.fill.color = Theme.withAlpha(Theme.RED, 0.12f)
            ui.fill.shader = null
            c.drawCircle(px, py, rad, ui.fill)
            ui.stroke.color = Theme.withAlpha(Theme.RED, 0.55f)
            ui.stroke.strokeWidth = dp(1f)
            c.drawCircle(px, py, rad, ui.stroke)
            ui.fill.color = Theme.RED
            c.drawCircle(px, py, dp(3.5f), ui.fill)
        }
        // بونوس‌ها
        for (b in world.bonuses) {
            ui.iconStar(c, mx(b.x), my(b.z), dp(4.5f), Theme.withAlpha(Theme.AMBER, 0.8f))
        }
        // مسیر پیشنهادی
        world.targets.firstOrNull()?.let { t0 ->
            ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.55f)
            ui.stroke.strokeWidth = dp(1.6f)
            c.drawLine(mx(0f), my(0f), mx(t0.x), my(t0.z), ui.stroke)
        }

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
        // نقطه پرتاب — همان پهباد شاهد انتخاب‌شده، با برچسب درست
        //
        // چرا برچسب قبلاً دیده نمی‌شد: با `my(0f)` که نزدیک لبهٔ پایین نقشه
        // است، `+dp(22f)` برچسب را **بیرون** از `clipRect` می‌انداخت و
        // کاملاً بریده می‌شد. آیکون هم اسپرایت واقعی پهباد است، نه شکل ساده.
        //
        // جای‌گیری برچسب باید از نوار راهنمای رنگی (پایین نقشه، تمام عرض)
        // دور بماند؛ وگرنه متن روی «قرمز: پدافند…» می‌افتد. تا وقتی
        // «پایین آیکون» با آن نوار برخورد نکند همان‌جا می‌ماند، وگرنه
        val lx = mx(0f)
        val ly = my(0f)
        Deco.droneMapIcon(this, c, lx, ly, dp(9f), Theme.MINT, save.selectedDroneId, 0f)
        // plot از قبل نوار راهنما را کنار گذاشته، پس «پایین آیکون»
        // همیشه داخل ناحیهٔ ترسیم است و به نوار رنگی نمی‌رسد.
        // برچسب همیشه **بالای** آیکون. پایین آیکون لبهٔ ناحیهٔ ترسیم و
        // نوار راهنمای رنگی است؛ هر تلاشی برای جا دادن برچسب آنجا یا روی
        // نوار می‌افتاد یا با clamp روی خود آیکون (باگ تست).
        val labelY = (ly - dp(16f)).coerceAtLeast(plot.top + dp(12f))
        mark("droneIcon", lx - dp(12f), ly - dp(12f), lx + dp(12f), ly + dp(12f))
        val dW = ui.measure("پهباد شاهد", dp(10.5f))
        mark("droneLabel", lx - dW / 2f, labelY - dp(10.5f), lx + dW / 2f, labelY + dp(3f))
        ui.text(c, "پهباد شاهد", lx, labelY, dp(10.5f), Theme.MINT)

        c.restore()

        // راهنما و مقیاس
        mark("mapLegendDist", map.left, map.top + dp(2f), map.right, map.top + dp(20f))
        ui.text(c, "فاصله تا هدف: " + Fa.meters(level.distance), map.centerX(), map.top + dp(14f),
            dp(11.5f), Theme.TEXT_DIM)
        val legendY = map.bottom - dp(14f)
        mark("mapLegendColors", map.left, legendY - dp(10f), map.right, legendY + dp(3f))
        ui.text(c, "قرمز: پدافند · آبی: رادار · بنفش: اخلال · زرد: پاداش", map.centerX(), legendY,
            dp(10.5f), Theme.TEXT_FAINT)
    }

    // ------------------------------------------------------------- اطلاعات

    /**
     * کارت «پهباد انتخابی» — تصویر نمای کنارِ همان پهباد + چهار آمار.
     *
     * جدا شد چون ارتفاعش پویا است: در گوشی‌های کوتاه به مقدار فضای مانده
     * کوتاه می‌شود، نه اینکه کلاً حذف شود. اگر ارتفاع کم بود، سطر «سطح»
     * (کمترین اهمیت) اول قربانی می‌شود، ولی نام و تصویر پهباد همیشه
     * می‌مانند.
     */
    private fun drawDroneCard(
        c: Canvas, x: Float, y: Float, w: Float, right: Float, h: Float,
        model: DroneModels.Model
    ) {
        mark("droneCard", x, y, x + w, y + h)
        ui.panel(c, x, y, w, h)
        val sideName = when (model.id) {
            "shahed136" -> "drone_136_side"
            "shahed238" -> "drone_238_side"
            "shahedx" -> "drone_x_side"
            else -> "drone_131_side"
        }
        val imgW = dp(86f)
        ui.imageFit(ui.context, c, sideName, x + dp(6f), y + dp(3f), imgW, h - dp(6f))
        ui.text(c, "پهباد انتخابی: " + model.name, right - dp(12f), y + dp(15f), dp(12.5f), model.color,
            bold = true, align = Paint.Align.RIGHT)
        val stats = listOf(
            Pair("برد", UpgradeKind.RANGE),
            Pair("سرعت", UpgradeKind.SPEED),
            Pair("هدایت", UpgradeKind.GUIDANCE),
            Pair("مقاومت", UpgradeKind.ARMOR)
        )
        val colW = (w - imgW - dp(28f)) / stats.size
        val showLevel = h >= dp(66f)
        for ((i, st) in stats.withIndex()) {
            val cxs = right - dp(12f) - colW * i - colW / 2f
            ui.text(c, st.first, cxs, y + dp(34f), dp(9.5f), Theme.TEXT_FAINT)
            val lvl = save.upgradeLevel(model.id, st.second)
            ui.text(c, st.second.valueAt(lvl, model), cxs, y + dp(50f), dp(10.5f), Theme.TEXT)
            if (showLevel) {
                ui.text(c, "سطح " + Fa.num(lvl), cxs, y + dp(64f), dp(9f), Theme.TEXT_DIM)
            }
        }
    }

    private fun drawInfo(c: Canvas) {
        val x = vw * 0.47f
        val w = vw - x - dp(16f)
        val right = x + w
        val bottom = vh - dp(74f)
        var y = dp(78f)
        val gap = dp(6f)

        fun fits(height: Float): Boolean = y + height <= bottom

        // ---------------------------------------------------------- بریفینگ
        //
        // چرا ارتفاع پنل‌ها فشرده شد: در گوشی‌های ۲۰:۹ ستون اطلاعات از
        // بنر تا نوار دکمه‌ها فقط ~dp(560) جا دارد و چهار پنل با اندازه‌های
        // قبلی روی هم ~dp(600) می‌شدند. آخرین پنل («پهباد انتخابی») با
        // `fits()` رد می‌شد و **کلاً کشیده نمی‌شد** — همان چیزی که کاربر
        // دید: «اون پایینش باید پهباد شاهد باشه». فشرده‌کردن فاصله‌ها
        // جای کارت پهباد را باز می‌کند؛ ترتیب اولویت هم حفظ می‌شود.
        val briefLines = ui.wrap(level.briefing, dp(11f), w - dp(26f))
        val briefH = dp(30f) + briefLines.size * dp(15f)
        if (fits(briefH)) {
            ui.panel(c, x, y, w, briefH)
            ui.text(c, "هدف: " + level.targetName, right - dp(14f), y + dp(17f), dp(13.5f), Theme.AMBER,
                bold = true, align = Paint.Align.RIGHT)
            var ty = y + dp(36f)
            for (line in briefLines) {
                ui.text(c, line, right - dp(14f), ty, dp(11f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
                ty += dp(15f)
            }
            y += briefH + gap
        }

        // ------------------------------------------------------ شرط ستاره‌ها
        val model = save.effectiveDrone()
        val cruise = model.speedAt(save.upgradeLevel(model.id, UpgradeKind.SPEED)) * 0.8f / 3.6f
        val par = StarRules.parTime(level.distance, cruise, level.targets)
        val preview = StarRules.preview(level.targets, par, world.bonuses.size)
        val starsH = dp(24f) + preview.size * dp(22f)
        if (fits(starsH)) {
            ui.panel(c, x, y, w, starsH, Theme.withAlpha(Theme.AMBER, 0.08f),
                Theme.withAlpha(Theme.AMBER, 0.35f))
            ui.text(c, "شرط ستاره‌ها", right - dp(12f), y + dp(14f), dp(12f), Theme.AMBER, bold = true,
                align = Paint.Align.RIGHT)
            ui.text(c, "ستاره‌ی خودت را قبل از پرواز بشناس", x + dp(12f), y + dp(14f), dp(9.5f),
                Theme.TEXT_FAINT, align = Paint.Align.LEFT)
            var sy = y + dp(32f)
            for (rule in preview) {
                ui.star(c, right - dp(16f), sy, dp(6f), true, Theme.withAlpha(Theme.AMBER, 0.75f))
                ui.text(c, rule.requirement, right - dp(30f), sy, dp(10f), Theme.TEXT,
                    align = Paint.Align.RIGHT)
                sy += dp(22f)
            }
            y += starsH + gap
        }

        // ------------------------------------------------------------ چالش‌ها
        val chipsH = dp(58f)
        if (fits(chipsH)) {
            ui.panel(c, x, y, w, chipsH)
            ui.text(c, "چالش این ماموریت", right - dp(14f), y + dp(15f), dp(11f), Theme.TEXT, bold = true,
                align = Paint.Align.RIGHT)
            // لهجه نازک بالای پنل
            ui.fill.color = Theme.withAlpha(Theme.MINT, 0.22f)
            c.drawRoundRect(x + dp(10f), y + dp(7f), x + w - dp(10f), y + dp(9f), dp(2f), dp(2f), ui.fill)
            var cxChip = right - dp(46f)
            val chipY = y + dp(38f)
            val chips = ArrayList<Pair<String, Int>>()
            chips.add(Pair(level.newChallenge, Theme.difficultyColor(level.difficulty)))
            if (level.aaSites > 0) chips.add(Pair("پدافند " + Fa.num(level.aaSites), Theme.RED))
            if (level.radarZones > 0) chips.add(Pair("رادار", Theme.BLUE))
            if (level.ewZones > 0) chips.add(Pair("جنگ الکترونیک", Theme.VIOLET))
            if (level.movingTarget) chips.add(Pair("هدف متحرک", Theme.ORANGE))
            if (level.wind > 0.3f) chips.add(Pair("باد شدید", Theme.TEXT_DIM))
            if (level.timeLimit > 0f) chips.add(Pair("زمان " + Fa.num(level.timeLimit, 0) + " ثانیه", Theme.AMBER))
            for (ch in chips) {
                val cw = ui.measure(ch.first, dp(10.5f)) + dp(20f)
                if (cxChip - cw < x + dp(10f)) break
                ui.chip(c, cxChip - cw / 2f, chipY, ch.first, ch.second, dp(10.5f))
                cxChip -= cw + dp(8f)
            }
            y += chipsH + gap
        }

        // ------------------------------------------------------ پهباد انتخابی
        //
        // ارتفاع ۷۴dp است نه ۸۴dp تا با فشرده‌سازی بالای صفحه در همهٔ
        // نسبت‌های تصویر جا شود. اگر حتی الان هم جا نشد، به‌جای حذف کامل
        // فقط ارتفاع را تا سقف فضای مانده کوتاه می‌کنیم — کارت پهباد
        // آخرین چیزی است که می‌تواند غیب شود.
        val droneWant = dp(74f)
        val droneSpace = bottom - y
        if (droneSpace >= dp(54f)) {
            val droneH = minOf(droneWant, droneSpace)
            drawDroneCard(c, x, y, w, right, droneH, model)
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
