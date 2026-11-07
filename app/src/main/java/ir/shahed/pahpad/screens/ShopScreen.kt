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
 * فروشگاه و آشیانه — بازطراحی کامل.
 *
 * چیدمان تازه: صفحه **یک** ستون اسکرول‌شونده است که دو بخش پشت‌سرهم دارد:
 *
 *   ۱) آشیانه — کارت پهبادها یکی زیر دیگری، تمام‌عرض.
 *   ۲) ارتقاها — ردیف‌های ارتقای پهباد انتخاب‌شده.
 *
 * چرا تک‌ستونه: نسخهٔ قبل دو ستون مستقل داشت — لیست پهباد در چپ و ارتقاها در
 * راست، با اسکرول فقط روی ستون چپ. روی گوشی landscape ستون چپ به ۲۸٪ عرض
 * می‌رسید و متن‌های عنوان/وضعیت همپوشانی می‌کردند، و کاربر مجبور بود انگشتش
 * را دقیقاً داخل همان نوار باریک بگذارد تا اسکرول کند.
 *
 * حالا کل صفحه یک ناحیهٔ اسکرول است؛ هر جای صفحه بکشی، اسکرول می‌شود. و
 * ترتیب عمودی حذف کاملِ همپوشانی است: هیچ دو متنی در یک مختصات نیستند.
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

    private val bonus = UiButton("تماشای ویدیو ← ۱۰ سکه", Theme.VIOLET) { claimBonus() }
    private var bonusClaimed = false

    private fun claimBonus() {
        if (bonusClaimed) return
        AdManager.showRewarded(game) {
            bonusClaimed = true
            bonus.visible = false
            save.addCoins(10)
            audio.beep(880f, 140, 0.45f)
            say("+۱۰ سکه هدیهٔ ویدیو اضافه شد")
            layoutUi(vw, vh)
        }
    }

    private var message: String? = null
    private var messageTime = 0f

    init {
        for (m in DroneModels.all) {
            val b = UiButton(m.name, m.color) {
                if (moved) {
                    moved = false
                } else if (save.isDroneUnlocked(m.id)) {
                    selected = m
                    save.selectedDroneId = m.id
                    refresh()
                    layoutUi(vw, vh)
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
        messageTime = 2.8f
    }

    private fun buy(kind: UpgradeKind) {
        if (moved) { moved = false; return }
        val current = save.upgradeLevel(selected.id, kind)
        val cap = selected.maxLevel(kind)
        if (current >= cap) {
            say("«" + kind.title + "» به سقف رسیده: سطح " + Fa.num(cap))
            return
        }
        val cost = kind.costFor(current + 1)
        if (!save.spendCoins(cost)) {
            say("سکه کافی نیست — " + Fa.grouped(cost) + " سکه لازم است")
            audio.beep(320f, 120, 0.3f)
            return
        }
        save.setUpgradeLevel(selected.id, kind, current + 1)
        audio.coin()
        say(kind.title + " به سطح " + Fa.num(current + 1) + " رسید")
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
                b.subLabel = null
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

    // ------------------------------------------------------------ چیدمان

    /** کل صفحه یک ویوپورت است، زیر هدر و بالای نوار پایین */
    private val viewport = RectF()
    private var scrollY = 0f
    private var maxScroll = 0f
    private var contentH = 0f

    private var dragPointer = -1
    private var lastY = 0f
    private var moved = false

    // موقعیت‌های محاسبه‌شده که render از آن‌ها استفاده می‌کند
    private val droneRowH = FloatArray(8)
    private val upgradeRowH = FloatArray(8)
    private var hangarLabelY = 0f
    private var upgradeLabelY = 0f
    private var taglineY = 0f
    private var statsY = 0f

    private val droneCardH = 62f
    private val upgradeRowHeight = 58f
    private val sectionGap = 18f
    private val labelH = 22f

    override fun layoutUi(w: Float, h: Float) {
        val top = dp(78f)
        val bottom = h - dp(58f)
        viewport.set(dp(14f), top, w - dp(14f), bottom)

        val vw2 = viewport.width()
        var y = 0f

        // ── سربرگ: نام پهباد انتخاب‌شده + آمار خیلی کوتاه
        taglineY = y
        y += dp(18f)
        statsY = y
        y += dp(20f)
        y += dp(10f)

        // ── عنوان بخش آشیانه
        hangarLabelY = y
        y += dp(labelH)

        // ── کارت‌های پهباد (تک‌ستون)
        for ((i, b) in droneButtons.withIndex()) {
            droneRowH[i] = y
            b.set(viewport.left, viewport.top + y - scrollY, vw2, dp(droneCardH))
            y += dp(droneCardH) + dp(8f)
        }

        y += dp(sectionGap)
        upgradeLabelY = y
        y += dp(labelH)

        // ── ردیف‌های ارتقا
        for ((i, b) in upgradeButtons.withIndex()) {
            upgradeRowH[i] = y
            b.set(
                viewport.right - dp(104f),
                viewport.top + y - scrollY + dp(upgradeRowHeight) - dp(40f),
                dp(92f), dp(32f),
            )
            y += dp(upgradeRowHeight) + dp(8f)
        }

        y += dp(sectionGap)

        contentH = y
        maxScroll = (contentH - viewport.height()).coerceAtLeast(0f)
        scrollY = scrollY.coerceIn(0f, maxScroll)

        // بازاعمال موقعیت با scrollY نهایی
        y = 0f
        taglineY = y; y += dp(18f)
        statsY = y; y += dp(20f); y += dp(10f)
        hangarLabelY = y; y += dp(labelH)
        for ((i, b) in droneButtons.withIndex()) {
            droneRowH[i] = y
            b.set(viewport.left, viewport.top + y - scrollY, vw2, dp(droneCardH))
            y += dp(droneCardH) + dp(8f)
        }
        y += dp(sectionGap)
        upgradeLabelY = y; y += dp(labelH)
        for ((i, b) in upgradeButtons.withIndex()) {
            upgradeRowH[i] = y
            b.set(
                viewport.right - dp(104f),
                viewport.top + y - scrollY + dp(upgradeRowHeight) - dp(40f),
                dp(92f), dp(32f),
            )
            y += dp(upgradeRowHeight) + dp(8f)
        }

        // دکمه‌های بیرون ویوپورت غیرفعال تا لمس نگیرند
        for (b in droneButtons) {
            b.visible = b.rect.bottom > viewport.top && b.rect.top < viewport.bottom
        }
        for (b in upgradeButtons) {
            b.visible = b.rect.bottom > viewport.top && b.rect.top < viewport.bottom
        }

        bonus.visible = !bonusClaimed && AdManager.isRewardedAdReady()
        bonus.set(viewport.left, h - dp(48f), dp(196f), dp(36f))
        back.set(viewport.right - dp(104f), h - dp(48f), dp(104f), dp(36f))
    }

    override fun claimsTouch(x: Float, y: Float): Boolean = viewport.contains(x, y)

    override fun onTouchDown(x: Float, y: Float, pointerId: Int): Boolean {
        if (viewport.contains(x, y)) {
            dragPointer = pointerId
            lastY = y
            moved = false
        }
        return true
    }

    override fun onTouchMove(x: Float, y: Float, pointerId: Int) {
        if (pointerId != dragPointer) return
        val dy = y - lastY
        lastY = y
        if (Math.abs(dy) >= 0.5f) {
            moved = true
            scrollY = (scrollY - dy).coerceIn(0f, maxScroll)
            layoutUi(vw, vh)
        }
    }

    override fun onTouchUp(x: Float, y: Float, pointerId: Int) {
        if (pointerId == dragPointer && !moved) {
            // ضربهٔ کوتاه = انتخاب پهباد یا خرید ارتقا
            var handled = false
            for (b in droneButtons.reversed()) {
                if (b.visible && b.contains(x, y)) {
                    audio.click(); b.onClick(); handled = true; break
                }
            }
            if (!handled) {
                for (b in upgradeButtons.reversed()) {
                    if (b.visible && b.enabled && b.contains(x, y)) {
                        audio.click(); b.onClick(); break
                    }
                }
            }
        }
        if (pointerId == dragPointer) dragPointer = -1
        moved = false
    }

    override fun onTouchCancel() {
        dragPointer = -1
        moved = false
    }

    override fun update(dt: Float) {
        if (messageTime > 0f) {
            messageTime -= dt
            if (messageTime <= 0f) message = null
        }
    }

    // ------------------------------------------------------------ رسم

    override fun render(c: Canvas) {
        ui.background(c, vw, vh, Theme.AMBER)
        Deco.header(this, c, "فروشگاه و آشیانه", null, Theme.AMBER)
        Deco.resources(this, c)

        c.save()
        c.clipRect(viewport)

        // ── سربرگ پهباد انتخاب‌شده
        val cx = viewport.centerX()
        ui.text(c, selected.name, cx, viewport.top + taglineY - scrollY + dp(13f),
            dp(14f), selected.color, bold = true)
        ui.text(c, selected.tagline, cx, viewport.top + statsY - scrollY + dp(12f),
            dp(10.5f), Theme.TEXT_DIM)

        // ── بخش آشیانه
        sectionLabel(c, "آشیانه — انتخاب پهباد", viewport.top + hangarLabelY - scrollY, Theme.AMBER)

        for ((i, b) in droneButtons.withIndex()) {
            val m = b.tag as DroneModels.Model
            val top = viewport.top + droneRowH[i] - scrollY
            val bottom = top + dp(droneCardH)
            if (bottom < viewport.top || top > viewport.bottom) continue
            val unlocked = save.isDroneUnlocked(m.id)
            val isSel = m.id == selected.id
            val r = RectF(viewport.left, top, viewport.right, bottom)

            ui.panel(c, r, if (isSel) Theme.withAlpha(m.color, 0.14f) else Theme.PANEL,
                Theme.withAlpha(if (isSel) m.color else Theme.LINE, if (isSel) 0.95f else 0.35f), dp(12f))

            // نوار لهجهٔ سمت راست کارت
            ui.fill.color = Theme.withAlpha(m.color, if (unlocked) 0.85f else 0.25f)
            ui.fill.shader = null
            c.drawRoundRect(r.right - dp(4f), r.top + dp(8f), r.right, r.bottom - dp(8f), dp(2f), dp(2f), ui.fill)

            val sideName = when (m.id) {
                "shahed136" -> "drone_136_side"
                "shahed238" -> "drone_238_side"
                "shahedx" -> "drone_x_side"
                else -> "drone_131_side"
            }
            ui.imageFit(ui.context, c, sideName, r.left + dp(6f), r.top + dp(8f),
                dp(52f), r.height() - dp(16f), if (unlocked) 255 else 80)

            // متن‌ها راست‌چین با عرض محدود — نه آفست ثابت
            val textRight = r.right - dp(14f)
            val textMaxW = (r.width() - dp(72f)).coerceAtLeast(dp(80f))

            var nameSize = dp(13.5f)
            while (ui.measure(m.name, nameSize, true) > textMaxW && nameSize > dp(10f)) nameSize -= dp(0.5f)
            ui.text(c, m.name, textRight, r.top + dp(22f), nameSize,
                if (unlocked) Theme.TEXT else Theme.TEXT_FAINT, bold = true, align = Paint.Align.RIGHT)

            val sub = when {
                !unlocked -> "نیازمند " + Fa.num(m.requiredStars) + " ستاره"
                isSel -> "انتخاب‌شده"
                else -> "برای انتخاب بزنید"
            }
            ui.text(c, sub, textRight, r.top + dp(42f), dp(10.5f),
                when {
                    !unlocked -> Theme.TEXT_FAINT
                    isSel -> Theme.MINT
                    else -> Theme.TEXT_DIM
                }, align = Paint.Align.RIGHT)

            if (!unlocked) ui.iconLock(c, r.left + dp(32f), r.centerY(), dp(9f))
        }

        // ── بخش ارتقاها
        sectionLabel(c, "ارتقای " + selected.name, viewport.top + upgradeLabelY - scrollY, Theme.MINT)

        for ((i, b) in upgradeButtons.withIndex()) {
            val k = b.tag as UpgradeKind
            val top = viewport.top + upgradeRowH[i] - scrollY
            val bottom = top + dp(upgradeRowHeight)
            if (bottom < viewport.top || top > viewport.bottom) continue

            val lvl = save.upgradeLevel(selected.id, k)
            val cap = selected.maxLevel(k)
            val maxed = lvl >= cap
            val r = RectF(viewport.left, top, viewport.right, bottom - dp(4f))

            ui.panel(c, r, Theme.withAlpha(Theme.PANEL, 0.96f), Theme.LINE, dp(11f))

            val textRight = r.right - dp(14f)
            val textMaxW = (r.width() - dp(122f)).coerceAtLeast(dp(90f))

            // خط ۱: عنوان قابلیت (راست)
            var titleSize = dp(12.5f)
            while (ui.measure(k.title, titleSize, true) > textMaxW * 0.62f && titleSize > dp(10f)) {
                titleSize -= dp(0.5f)
            }
            ui.text(c, k.title, textRight, r.top + dp(17f), titleSize, Theme.TEXT, bold = true,
                align = Paint.Align.RIGHT)

            // خط ۲: مقدار فعلی (راست)
            val valText = if (maxed) k.valueAt(lvl, selected) + " · حداکثر" else k.valueAt(lvl, selected)
            ui.text(c, valText, textRight, r.top + dp(36f), dp(11.5f), Theme.MINT,
                align = Paint.Align.RIGHT)

            // خط ۳: توضیح (چپ، یک خط با کوتاه‌سازی)
            var descSize = dp(9.5f)
            var desc = k.description
            val descMaxW = (r.width() - dp(120f)).coerceAtLeast(dp(60f))
            while (ui.measure(desc, descSize) > descMaxW && descSize > dp(8f)) descSize -= dp(0.5f)
            if (ui.measure(desc, descSize) > descMaxW) {
                while (desc.length > 6 && ui.measure(desc + "…", descSize) > descMaxW) desc = desc.dropLast(1)
                desc += "…"
            }
            ui.text(c, desc, r.left + dp(12f), r.top + dp(16f), descSize, Theme.TEXT_FAINT,
                align = Paint.Align.LEFT)

            // نشانگر سطح: خانه‌ها (چپ، زیر توضیح)
            val pipW = dp(6f)
            val pipGap = dp(3f)
            val pipY = r.top + dp(34f)
            var px = r.left + dp(12f)
            for (n in 0 until cap) {
                ui.fill.color = if (n < lvl) Theme.AMBER else Theme.withAlpha(Theme.TEXT_FAINT, 0.28f)
                ui.fill.shader = null
                c.drawRoundRect(px, pipY, px + pipW, pipY + dp(8f), dp(2f), dp(2f), ui.fill)
                px += pipW + pipGap
            }
            if (maxed) {
                ui.text(c, "سقف", px + dp(4f), pipY + dp(8f), dp(9f), Theme.AMBER,
                    align = Paint.Align.LEFT)
            }
        }

        // دکمه‌های ارتقا (روی ردیف‌ها)
        for (b in upgradeButtons) ui.button(c, b)

        c.restore()

        // نشانگر اسکرول
        if (maxScroll > 0f) {
            val trackH = viewport.height()
            val thumbH = (trackH * (trackH / contentH)).coerceAtLeast(dp(30f))
            val t = scrollY / maxScroll
            val thumbY = viewport.top + t * (trackH - thumbH)
            ui.fill.color = Theme.withAlpha(Theme.AMBER, 0.30f)
            ui.fill.shader = null
            c.drawRoundRect(viewport.right - dp(2f), thumbY, viewport.right, thumbY + thumbH,
                dp(2f), dp(2f), ui.fill)
        }

        ui.button(c, back)
        if (bonus.visible) ui.button(c, bonus)

        message?.let {
            val a = (messageTime / 0.6f).coerceIn(0f, 1f)
            ui.text(c, it, vw / 2f, vh - dp(22f), dp(11.5f), Theme.AMBER, bold = true, alpha = a)
        }
    }

    /** عنوان بخش: راست‌چین + خط زیرش تا انتهای ویوپورت */
    private fun sectionLabel(c: Canvas, text: String, y: Float, accent: Int) {
        if (y < viewport.top - dp(24f) || y > viewport.bottom + dp(24f)) return
        ui.text(c, text, viewport.right, y + dp(13f), dp(12f), accent, bold = true,
            align = Paint.Align.RIGHT)
        ui.stroke.color = Theme.withAlpha(accent, 0.22f)
        ui.stroke.strokeWidth = dp(1f)
        c.drawLine(viewport.left, y + dp(19f), viewport.right - dp(120f), y + dp(19f), ui.stroke)
    }
}
