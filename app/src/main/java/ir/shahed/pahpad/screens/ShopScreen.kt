package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.AdManager
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import ir.shahed.pahpad.data.DroneModels
import ir.shahed.pahpad.data.UpgradeKind

/**
 * فروشگاه و آشیانه — بازنویسی‌شده.
 *
 * - لیست پهپادها **قابل اسکرول** است (کشیدن با انگشت روی ستون راست).
 * - همه‌چیز از خط هدر پایین می‌آید؛ هیچ متنی روی هدر یا روی هم نمی‌افد.
 * - دکمهٔ «بازگشت» همیشه از خطِ پایین، پنج در پنجرهٔ بدون تداخل برمی‌گردد
 *   و به صفحه‌ای برمی‌گردد که ورود از آن بود ([returnTo]).
 * - ارتقای «قدرت انفجار» حذف شده؛ ارتقای مقاومت و سایر قابلیت‌ها **سقف‌دار** است
 *   (سقف هر بدنه در [DroneModels.Model.maxLevel]).
 * - دکمهٔ پاداش ویدیویی نگه داشته شده است.
 */
class ShopScreen(
    game: MainActivity,
    private val returnTo: (() -> BaseScreen)? = null
) : BaseScreen(game) {

    private var selected: DroneModels.Model =
        DroneModels.byId(save.selectedDroneId) ?: DroneModels.all.first()

    private val kinds = listOf(UpgradeKind.RANGE, UpgradeKind.SPEED, UpgradeKind.GUIDANCE, UpgradeKind.ARMOR)
    private val droneButtons = ArrayList<UiButton>()
    private val upgradeButtons = ArrayList<UiButton>()
    private val back = UiButton("بازگشت", Theme.TEXT_DIM) {
        val to = returnTo?.invoke() ?: MainMenuScreen(game)
        game.show(to)
    }

    /** پاداش ویدیویی داخل فروشگاه — تماشای ویدیو → سکه رایگان */
    private val bonus = UiButton("تماشای ویدیو ← ۱۰ سکه", Theme.VIOLET) { claimBonus() }
    private var bonusClaimed = false

    private fun claimBonus() {
        if (bonusClaimed) return
        AdManager.showRewarded(game) {
            bonusClaimed = true
            bonus.visible = false
            save.addCoins(10)
            audio.beep(880f, 140, 0.45f)
            say("+۱۰ سکه هدیهٔ ویدیو اضافه شد ✦")
        }
    }

    private var message: String? = null
    private var messageTime = 0f

    init {
        for (m in DroneModels.all) {
            val b = UiButton(m.name, m.color) {
                if (moved) {
                    moved = false   // این لمس، اسکرولِ پیشرونده بود نه کلیک
                } else if (save.isDroneUnlocked(m.id)) {
                    selected = m
                    save.selectedDroneId = m.id
                    refresh()
                } else {
                    say("برای آنلاک به " + Fa.num(m.requiredStars) + " ستاره نیاز دارید")
                    audio.beep(320f, 90, 0.3f)
                }
            }
            b.tag = m
            droneButtons.add(b)
        }
        for (k in kinds) {
            val b = UiButton("ارتقا", Theme.MINT) { buy(k) }
            b.tag = k
            upgradeButtons.add(b)
        }
        buttons.addAll(droneButtons)
        buttons.addAll(upgradeButtons)
        buttons.add(back)
        buttons.add(bonus)
        refresh()
    }

    private fun say(text: String) {
        message = text
        messageTime = 2.6f
    }

    private fun buy(kind: UpgradeKind) {
        if (moved) { moved = false; return }
        val current = save.upgradeLevel(selected.id, kind)
        val cap = selected.maxLevel(kind)
        if (current >= cap) {
            say("سقف ارتقای «${kind.title}» برای این پهباد: سطح " + Fa.num(cap))
            return
        }
        val cost = kind.costFor(current + 1)
        if (!save.spendCoins(cost)) {
            say("سکه کافی نیست؛ " + Fa.grouped(cost) + " سکه لازم است")
            audio.beep(320f, 120, 0.3f)
            return
        }
        save.setUpgradeLevel(selected.id, kind, current + 1)
        audio.coin()
        say(kind.title + " به سطح " + Fa.num(current + 1) + " ارتقا یافت")
        refresh()
    }

    private fun refresh() {
        for (b in upgradeButtons) {
            val k = b.tag as UpgradeKind
            val lvl = save.upgradeLevel(selected.id, k)
            val cap = selected.maxLevel(k)
            if (lvl >= cap) {
                b.label = "حداکثر"
                b.enabled = false
                b.subLabel = null
            } else {
                val cost = k.costFor(lvl + 1)
                b.label = Fa.grouped(cost) + " سکه"
                b.enabled = save.coins >= cost
                b.subLabel = "سطح " + Fa.num(lvl) + " ← " + Fa.num(lvl + 1)
            }
        }
        for (b in droneButtons) {
            val m = b.tag as DroneModels.Model
            b.filled = m.id == selected.id
            b.enabled = true
        }
    }

    override fun onBack(): Boolean {
        val to = returnTo?.invoke() ?: MainMenuScreen(game)
        game.show(to)
        return true
    }

    // ------------------------------------------------------------ چیدمان و اسکرول

    private lateinit var listRect: RectF
    private val viewportRect = RectF()
    private var scrollY = 0f
    private var maxScroll = 0f
    private var dragPointer = -1
    private var lastY = 0f
    private var moved = false
    private var cardH = 0f
    private var cardGap = 0f
    private var colTop = 0f
    private var colBottom = 0f

    override fun layoutUi(w: Float, h: Float) {
        // همه‌چیز زیر خط هدر (dp 74) — هیچ متنی با عنوان و زیرعنوان تداخل ندارد
        colTop = dp(84f)
        val bottomStrip = dp(60f)
        colBottom = h - bottomStrip

        val lw = w * 0.28f
        cardH = dp(66f)
        cardGap = dp(8f)
        val viewportTop = colTop + dp(26f)          // زیر برچسب ستون
        val viewportBottom = colBottom - dp(10f)
        viewportRect.set(dp(16f), viewportTop, dp(16f) + lw, viewportBottom)
        listRect = RectF(viewportRect)

        val contentH = DroneModels.all.size * (cardH + cardGap) - cardGap
        maxScroll = (contentH - (viewportBottom - viewportTop)).coerceAtLeast(0f)
        clampScroll()

        var y = viewportTop + dp(2f) - scrollY
        for (b in droneButtons) {
            b.set(dp(16f), y, lw, cardH)
            // فقط کارت‌های داخل ویوپورت کلیک‌پذیرند (جلوگیری از لمسِ کارت‌های بیرون)
            b.visible = r_visible(b.rect, viewportTop, viewportBottom)
            y += cardH + cardGap
        }

        val rx = w * 0.34f
        val rw = w - rx - dp(20f)
        val rowH = dp(52f)
        var uy = viewportTop
        for (b in upgradeButtons) {
            b.set(rx + dp(10f), uy + dp(5f), dp(112f), dp(36f))
            uy += rowH + dp(6f)
        }

        val dy = h - dp(52f)
        back.set(dp(20f), dy, dp(110f), dp(40f))
        bonus.set(w * 0.42f, dy, dp(190f), dp(40f))
    }

    private fun clampScroll() {
        scrollY = scrollY.coerceIn(0f, maxScroll)
    }

    private fun r_visible(r: RectF, top: Float, bottom: Float): Boolean =
        r.top >= top - dp(4f) && r.bottom <= bottom + dp(4f)

    /** عرض دورهٔ مجاز متن توصیف داخل ردیف ارتقا */
    private fun availDescW(rx: Float, rw: Float, descX: Float): Float =
        ((rx + rw) - dp(14f) - descX).coerceAtLeast(dp(40f))

    /** متن را تا حداکثر دو خط در محدودهٔ موجود می‌کشد تا از پنل بیرون نزند */
    private fun drawWrapped(c: Canvas, text: String, x: Float, y: Float, maxW: Float, size: Float) {
        val lines = ui.wrap(text, size, maxW.coerceAtLeast(dp(40f))).take(2)
        var ly = y
        for (l in lines) {
            ui.text(c, l, x, ly, size, Theme.TEXT_FAINT, align = Paint.Align.LEFT)
            ly += dp(11f)
        }
    }

    override fun onTouchDown(x: Float, y: Float, pointerId: Int): Boolean {
        if (::listRect.isInitialized && listRect.contains(x, y)) {
            dragPointer = pointerId
            lastY = y
            moved = false
        }
        return true
    }

    override fun onTouchMove(x: Float, y: Float, pointerId: Int) {
        if (pointerId == dragPointer) {
            val dy = y - lastY
            lastY = y
            if (Math.abs(dy) >= 0.4f) {
                moved = true
                scrollY -= dy
                clampScroll()
            }
        }
    }

    override fun onTouchUp(x: Float, y: Float, pointerId: Int) {
        if (pointerId == dragPointer && !moved) {
            // یک لمسِ «بدون حرکت» روی دکمه‌ها به کار خودشان سپرده شد؛ فقط وضعیت را پاک کن
        }
        if (pointerId == dragPointer) dragPointer = -1
    }

    override fun update(dt: Float) {
        bonus.visible = !bonusClaimed && AdManager.isRewardedAdReady()
        if (messageTime > 0f) {
            messageTime -= dt
            if (messageTime <= 0f) message = null
        }
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh, Theme.AMBER)
        Deco.header(this, c, "فروشگاه و آشیانه", "پهباد را انتخاب و ارتقا دهید", Theme.AMBER)
        Deco.resources(this, c)

        // ---- برچسب ستون‌ها در دو گوشهٔ جدا — هرگز روی هم نمی‌افتند
        ui.text(c, "انتخاب پهباد", viewportRect.left + dp(4f), colTop + dp(12f), dp(12f),
            Theme.TEXT, bold = true, align = Paint.Align.LEFT)
        val rx = vw * 0.34f
        val rw = vw - rx - dp(20f)
        ui.text(c, "ارتقای ${selected.name}", rx + rw, colTop + dp(12f), dp(12f),
            Theme.TEXT, bold = true, align = Paint.Align.RIGHT)
        ui.text(c, selected.tagline, rx + rw / 2f, colTop + dp(28f), dp(10.5f), Theme.TEXT_FAINT)

        // ---- کارت‌های پهباد: داخل ویوپورت با کلیپ، قابل اسکرول
        c.save()
        c.clipRect(viewportRect)
        for (b in droneButtons) {
            val m = b.tag as DroneModels.Model
            val r = b.rect
            if (r.bottom < viewportRect.top || r.top > viewportRect.bottom) continue
            val unlocked = save.isDroneUnlocked(m.id)
            ui.panel(c, r, if (b.pressed) Theme.PANEL_HI else Theme.PANEL,
                Theme.withAlpha(m.color, if (m.id == selected.id) 0.95f else 0.3f), dp(12f))
            val sideName = when (m.id) {
                "shahed136" -> "drone_136_side"; "shahed238" -> "drone_238_side"; "shahedx" -> "drone_x_side"
                else -> "drone_131_side"
            }
            val imgAlpha = if (unlocked) 255 else 90
            ui.imageFit(ui.context, c, sideName, r.left + dp(6f), r.top + dp(6f), dp(56f), r.height() - dp(12f), imgAlpha)
            if (!unlocked) ui.iconLock(c, r.left + dp(34f), r.centerY() + dp(2f), dp(9f))
            ui.text(c, m.name, r.right - dp(12f), r.top + dp(20f), dp(13f),
                if (unlocked) Theme.TEXT else Theme.TEXT_FAINT, bold = true, align = Paint.Align.RIGHT)
            val sub = if (unlocked) {
                if (m.id == selected.id) "انتخاب شده" else "آماده انتخاب"
            } else {
                "نیازمند " + Fa.num(m.requiredStars) + " ستاره"
            }
            ui.text(c, sub, r.right - dp(12f), r.top + dp(42f), dp(10.5f),
                if (unlocked) Theme.MINT else Theme.TEXT_FAINT, align = Paint.Align.RIGHT)
        }
        c.restore()

        // ---- ردیف‌های ارتقا
        val rowH = dp(52f)
        var uy = viewportRect.top
        for (b in upgradeButtons) {
            val k = b.tag as UpgradeKind
            val lvl = save.upgradeLevel(selected.id, k)
            val cap = selected.maxLevel(k)
            val maxed = lvl >= cap
            ui.panel(c, rx, uy, rw, rowH - dp(4f), Theme.withAlpha(Theme.PANEL, 0.95f), Theme.LINE, dp(10f))
            ui.text(c, k.title, rx + rw - dp(14f), uy + dp(15f), dp(12.5f), Theme.TEXT, bold = true,
                align = Paint.Align.RIGHT)
            val lvlText = if (maxed) k.valueAt(lvl, selected) + " · حداکثر" else k.valueAt(lvl, selected)
            ui.text(c, lvlText, rx + rw - dp(14f), uy + dp(34f), dp(11f), Theme.MINT,
                align = Paint.Align.RIGHT)
            // نشانگر سطح: تعداد خانه‌ها = سقف همین پهباد
            val pipStart = rx + dp(118f)
            val pipW = dp(7f); val pipGap = dp(4f)
            for (i in 0 until cap) {
                ui.fill.color = if (i < lvl) Theme.AMBER else Theme.withAlpha(Theme.TEXT_FAINT, 0.3f)
                ui.fill.shader = null
                val x0 = pipStart + i * (pipW + pipGap)
                c.drawRect(x0, uy + dp(28f), x0 + pipW, uy + dp(36f), ui.fill)
            }
            if (!maxed) {
                drawWrapped(c, k.description, pipStart + cap * (pipW + pipGap) + dp(6f), uy + dp(12f),
                    availDescW(rx, rw, pipStart + cap * (pipW + pipGap) + dp(6f)), dp(9.5f))
            } else {
                drawWrapped(c, "سقف این بدنه در این قابلیت", pipStart + cap * (pipW + pipGap) + dp(6f), uy + dp(12f),
                    availDescW(rx, rw, pipStart + cap * (pipW + pipGap) + dp(6f)), dp(9.5f))
            }
            uy += rowH + dp(6f)
        }

        // ---- دکمه‌ها
        for (b in upgradeButtons) ui.button(c, b)
        ui.button(c, back)

        message?.let {
            val a = (messageTime / 0.6f).coerceIn(0f, 1f)
            ui.text(c, it, vw / 2f, vh - dp(24f), dp(11.5f), Theme.AMBER, bold = true, alpha = a)
        }
    }
}
