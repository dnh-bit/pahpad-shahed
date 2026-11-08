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
 * فروشگاه و آشیانه.
 *
 * اصل طراحی: **هر ردیف ارتقا به دو ناحیهٔ کاملاً جدا تقسیم می‌شود.**
 *
 *   ┌──────────────────────────────┬───────────┐
 *   │ عنوان / مقدار / توضیح / سطح  │ دکمهٔ خرید │
 *   │        (ناحیهٔ متن)           │ (ناحیهٔ دکمه)│
 *   └──────────────────────────────┴───────────┘
 *
 * دکمهٔ خرید هرگز داخل ناحیهٔ متن نیست، پس هیچ متنی نمی‌تواند زیر آن برود.
 * این تقسیم فضا، همپوشانی «مقدار» و «قیمت» را که در نسخهٔ قبل دیده می‌شد
 * ساختاراً غیرممکن می‌کند — نه با تنظیم آفست، بلکه با تفکیک ناحیه‌ها.
 *
 * همهٔ اندازه‌های متن با اندازه‌گیری واقعی ([ui.measure]) محاسبه می‌شوند.
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
                } else {
                    // حتی پهباد قفل هم قابل انتخاب است تا کاربر ببیند چه
                    // چیزی در انتظارش است؛ خرید با دکمه انجام می‌شود.
                    selected = m
                    save.selectedDroneId = m.id
                    refresh()
                    layoutUi(vw, vh)
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
        if (!save.isDroneUnlocked(selected.id)) {
            say("این پهپاد قفل است — " + Fa.num(selected.requiredStars) + " ستاره لازم است")
            audio.beep(320f, 110, 0.3f)
            return
        }
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
        val unlocked = save.isDroneUnlocked(selected.id)
        for (b in upgradeButtons) {
            val k = b.tag as UpgradeKind
            val lvl = save.upgradeLevel(selected.id, k)
            val cap = selected.maxLevel(k)
            when {
                !unlocked -> { b.label = "قفل"; b.enabled = false }
                lvl >= cap -> { b.label = "سقف"; b.enabled = false }
                else -> {
                    val cost = k.costFor(lvl + 1)
                    b.label = Fa.grouped(cost) + " سکه"
                    b.enabled = save.coins >= cost
                }
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

    private val viewport = RectF()
    private var scrollY = 0f
    private var maxScroll = 0f
    private var contentH = 0f

    private var dragPointer = -1
    private var lastY = 0f
    private var moved = false

    private val droneYs = FloatArray(16)
    private val upgradeYs = FloatArray(16)
    private var headerY = 0f
    private var hangarLabelY = 0f
    private var upgradeLabelY = 0f

    private fun cardH() = dp(56f)
    private fun rowH() = dp(62f)
    private val gapV get() = dp(7f)
    private val sectionGapV get() = dp(14f)
    private val labelHV get() = dp(22f)

    /** عرض ناحیهٔ دکمهٔ خرید در ردیف ارتقا */
    private fun btnAreaW() = dp(94f)

    /** عرض ناحیهٔ متن — همیشه با فاصله از ناحیهٔ دکمه */
    private fun textAreaW() = (viewport.width() - btnAreaW() - dp(16f)).coerceAtLeast(dp(120f))

    override fun layoutUi(w: Float, h: Float) {
        // پایین ویوپورت باید بالای نوار دکمه‌ها بایستد، وگرنه محتوا زیر
        // دکمهٔ «بازگشت» می‌رود (تست همپوشانی همین را پیدا کرد).
        viewport.set(dp(12f), dp(78f), w - dp(12f), h - dp(88f))

        contentH = computeContent()
        maxScroll = (contentH - viewport.height()).coerceAtLeast(0f)
        scrollY = scrollY.coerceIn(0f, maxScroll)
        placeAll()

        bonus.visible = !bonusClaimed && AdManager.isRewardedAdReady()
        bonus.set(viewport.left, h - dp(44f), dp(186f), dp(32f))
        back.set(viewport.right - dp(96f), h - dp(44f), dp(96f), dp(32f))
    }

    private fun computeContent(): Float {
        var y = dp(40f)                      // سربرگ: نام + شعار (دو خط)
        y += labelHV                          // عنوان «آشیانه»
        y += DroneModels.all.size * (cardH() + gapV)
        y += sectionGapV
        y += labelHV                          // عنوان «ارتقاها»
        y += upgradeButtons.size * (rowH() + gapV)
        return y
    }

    private fun placeAll() {
        var y = 0f
        headerY = y; y += dp(40f)
        hangarLabelY = y; y += labelHV

        for ((i, b) in droneButtons.withIndex()) {
            droneYs[i] = y
            b.set(viewport.left, viewport.top + y - scrollY, viewport.width(), cardH())
            y += cardH() + gapV
        }

        y += sectionGapV
        upgradeLabelY = y; y += labelHV

        for ((i, b) in upgradeButtons.withIndex()) {
            upgradeYs[i] = y
            // ناحیهٔ دکمه: چپ‌ترین بخش ردیف
            b.set(
                viewport.left + dp(10f),
                viewport.top + y - scrollY + (rowH() - dp(32f)) / 2f,
                btnAreaW() - dp(16f), dp(32f),
            )
            y += rowH() + gapV
        }
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
            placeAll()
        }
    }

    override fun onTouchUp(x: Float, y: Float, pointerId: Int) {
        if (pointerId == dragPointer && !moved) {
            var handled = false
            // دکمه‌های خرید اول (کوچک‌ترند)
            for (b in upgradeButtons.reversed()) {
                if (b.visible && b.rect.contains(x, y)) {
                    audio.click(); b.onClick(); handled = true; break
                }
            }
            if (!handled) {
                for (b in droneButtons.reversed()) {
                    if (b.visible && b.contains(x, y)) {
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
        ui.background(c, vw, vh, Theme.BLUE)
        Deco.header(this, c, "فروشگاه و آشیانه", null, Theme.AMBER)
        Deco.resources(this, c)

        c.save()
        c.clipRect(viewport)

        val base = viewport.top - scrollY

        // ── سربرگ
        ui.text(c, selected.name, viewport.right, base + headerY + dp(14f), dp(14.5f),
            selected.color, bold = true, align = Paint.Align.RIGHT)
        var tag = selected.tagline
        val tagMaxW = viewport.width() - dp(16f)
        var tagSize = dp(9.5f)
        while (ui.measure(tag, tagSize) > tagMaxW && tagSize > dp(8f)) tagSize -= dp(0.5f)
        if (ui.measure(tag, tagSize) > tagMaxW) {
            while (tag.length > 8 && ui.measure(tag + "…", tagSize) > tagMaxW) tag = tag.dropLast(1)
            tag += "…"
        }
        ui.text(c, tag, viewport.right, base + headerY + dp(30f), tagSize,
            Theme.TEXT_DIM, align = Paint.Align.RIGHT)

        // ── آشیانه
        sectionLabel(c, "آشیانه", base + hangarLabelY, Theme.AMBER)

        for ((i, b) in droneButtons.withIndex()) {
            val m = b.tag as DroneModels.Model
            val y = base + droneYs[i]
            if (y + cardH() < viewport.top || y > viewport.bottom) continue
            val unlocked = save.isDroneUnlocked(m.id)
            val isSel = m.id == selected.id
            val r = RectF(viewport.left, y, viewport.right, y + cardH())

            ui.panel(c, r,
                if (isSel) Theme.withAlpha(m.color, 0.16f) else Theme.PANEL,
                Theme.withAlpha(if (isSel) m.color else Theme.LINE, if (isSel) 1f else 0.28f),
                dp(10f))

            val sideName = when (m.id) {
                "shahed136" -> "drone_136_side"
                "shahed238" -> "drone_238_side"
                "shahedx" -> "drone_x_side"
                else -> "drone_131_side"
            }
            ui.imageFit(ui.context, c, sideName, r.left + dp(6f), r.top + dp(5f),
                dp(46f), r.height() - dp(10f), if (unlocked) 255 else 70)

            val tx = r.right - dp(12f)
            val maxW = r.width() - dp(110f)
            ui.text(c, m.name, tx, r.top + dp(23f),
                fitSize(m.name, dp(13.5f), maxW, bold = true),
                if (unlocked) Theme.TEXT else Theme.TEXT_FAINT, bold = true, align = Paint.Align.RIGHT)

            val status = when {
                !unlocked -> "قفل — " + Fa.num(m.requiredStars) + " ستاره"
                isSel -> "انتخاب‌شده"
                else -> "آمادهٔ انتخاب"
            }
            ui.text(c, status, tx, r.top + dp(41f), dp(10f),
                when {
                    !unlocked -> Theme.RED
                    isSel -> Theme.MINT
                    else -> Theme.TEXT_DIM
                }, align = Paint.Align.RIGHT)

            // نشان انتخاب: خطِ عمودی سمت راست کارت (جدا از همهٔ متن‌ها)
            if (isSel) {
                ui.fill.color = Theme.MINT
                ui.fill.shader = null
                c.drawRoundRect(r.right - dp(3f), r.top + dp(9f), r.right, r.bottom - dp(9f),
                    dp(1.5f), dp(1.5f), ui.fill)
            }
        }

        // ── ارتقاها
        sectionLabel(c, "ارتقای " + selected.name, base + upgradeLabelY, Theme.MINT)

        for ((i, b) in upgradeButtons.withIndex()) {
            val k = b.tag as UpgradeKind
            val y = base + upgradeYs[i]
            if (y + rowH() < viewport.top || y > viewport.bottom) continue

            val lvl = save.upgradeLevel(selected.id, k)
            val cap = selected.maxLevel(k)
            val unlocked = save.isDroneUnlocked(selected.id)
            val r = RectF(viewport.left, y, viewport.right, y + rowH() - dp(3f))
            ui.panel(c, r, Theme.withAlpha(Theme.PANEL, 0.97f), Theme.LINE, dp(9f))

            // ناحیهٔ متن: از لبهٔ راست تا شروع ناحیهٔ دکمه
            val textRight = r.right - dp(12f)
            val tW = textAreaW()

            // خط ۱ — عنوان (بالا، راست‌چین)
            ui.text(c, k.title, textRight, r.top + dp(17f),
                fitSize(k.title, dp(12.5f), tW, bold = true),
                Theme.TEXT, bold = true, align = Paint.Align.RIGHT)

            // خط ۲ — مقدار فعلی (وسط، راست‌چین)
            val valueNow = k.valueAt(lvl, selected)
            ui.text(c, valueNow, textRight, r.top + dp(33f),
                fitSize(valueNow, dp(11.5f), tW),
                if (unlocked) Theme.MINT else Theme.TEXT_FAINT, align = Paint.Align.RIGHT)

            // خط ۳ — توضیح (پایین، رو به چپ از ناحیهٔ متن)
            val desc = k.description
            var dSize = dp(9f)
            var dText = desc
            val dMaxW = tW - dp(6f)
            while (ui.measure(dText, dSize) > dMaxW && dSize > dp(8f)) dSize -= dp(0.5f)
            if (ui.measure(dText, dSize) > dMaxW) {
                while (dText.length > 6 && ui.measure(dText + "…", dSize) > dMaxW) dText = dText.dropLast(1)
                dText += "…"
            }
            ui.text(c, dText, textRight, r.top + dp(47f), dSize, Theme.TEXT_FAINT,
                align = Paint.Align.RIGHT)

            // نشانگر سطح: بالای دکمهٔ خرید، داخل ناحیهٔ دکمه.
            // جای دیگری نیست، پس با هیچ متنی قاطی نمی‌شود.
            val pipW = dp(4.5f); val pipGap = dp(2.5f)
            val totalPipsW = cap * (pipW + pipGap) - pipGap
            var px = r.left + dp(10f) + (btnAreaW() - dp(16f) - totalPipsW) / 2f
            val pipY = b.rect.top - dp(11f)
            if (pipY > r.top + dp(2f)) {
                for (n in 0 until cap) {
                    ui.fill.color = when {
                        !unlocked -> Theme.withAlpha(Theme.TEXT_FAINT, 0.18f)
                        n < lvl -> Theme.AMBER
                        else -> Theme.withAlpha(Theme.TEXT_FAINT, 0.3f)
                    }
                    ui.fill.shader = null
                    c.drawRoundRect(px, pipY, px + pipW, pipY + dp(4.5f), dp(1f), dp(1f), ui.fill)
                    px += pipW + pipGap
                }
            }
        }

        // دکمه‌های خرید — همیشه در ناحیهٔ خودشان
        for (b in upgradeButtons) ui.button(c, b)

        c.restore()

        // نشانگر اسکرول
        if (maxScroll > 0f) {
            val trackH = viewport.height()
            val thumbH = (trackH * (trackH / contentH)).coerceAtLeast(dp(28f))
            val t = scrollY / maxScroll
            val thumbY = viewport.top + t * (trackH - thumbH)
            ui.fill.color = Theme.withAlpha(Theme.AMBER, 0.32f)
            ui.fill.shader = null
            c.drawRoundRect(viewport.right - dp(2f), thumbY, viewport.right, thumbY + thumbH,
                dp(2f), dp(2f), ui.fill)
        }

        ui.button(c, back)
        if (bonus.visible) ui.button(c, bonus)

        message?.let {
            val a = (messageTime / 0.6f).coerceIn(0f, 1f)
            ui.text(c, it, vw / 2f, vh - dp(18f), dp(11f), Theme.AMBER, bold = true, alpha = a)
        }
    }

    /** عنوان بخش — راست‌چین با خط زیر */
    private fun sectionLabel(c: Canvas, text: String, y: Float, accent: Int) {
        if (y < viewport.top - dp(18f) || y > viewport.bottom + dp(18f)) return
        ui.text(c, text, viewport.right, y + dp(13f), dp(12f), accent, bold = true,
            align = Paint.Align.RIGHT)
        ui.stroke.color = Theme.withAlpha(accent, 0.22f)
        ui.stroke.strokeWidth = dp(1f)
        c.drawLine(viewport.left, y + dp(18f), viewport.right - dp(104f), y + dp(18f), ui.stroke)
    }

    /** بزرگ‌ترین اندازه‌ای که متن در آن جا شود (کف ۹dp) */
    private fun fitSize(text: String, start: Float, maxW: Float, bold: Boolean = false): Float {
        var s = start
        while (s > dp(9f) && ui.measure(text, s, bold) > maxW) s -= dp(0.5f)
        return s
    }
}
