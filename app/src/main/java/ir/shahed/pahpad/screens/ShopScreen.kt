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
 * بالای صفحه «پنل پهباد انتخاب‌شده» است: نام، شعار، وضعیت (قفل/فعال) و
 * چهار نوار مشخصات (برد/سرعت/هدایت/مقاومت) با مقدار مؤثرِ فعلیِ همان
 * پهباد — تا بازیکن پیش از خرید بداند چه چیزی را با چه مقایسه می‌کند.
 * روی هر کارت پهباد هم خط مشخصات پایه (ضریب‌ها) چاپ می‌شود و ردیف ارتقا
 * مقدار فعلی و مقدار سطح بعد را کنار هم نشان می‌دهد.
 *
 * ساختار دوصفحه‌ای (بازطراحی ۰.۲.۸): صفحهٔ اول فقط فهرست چهار پهباد است
 * با ویژگی‌ها و نشان پهباد انتخاب‌شده — بدون اسکرول گم نمی‌شود. لمس هر
 * کارت، همان پهباد را (اگر باز باشد) فعال می‌کند و صفحهٔ ارتقای خودش را
 * باز می‌کند: پنل مشخصات + چهار ردیف ارتقا. «بازگشت» در صفحهٔ ارتقا به
 * فهرست برمی‌گردد، نه به منوی اصلی.
 *
 * همهٔ اندازه‌های متن با اندازه‌گیری واقعی ([ui.measure]) محاسبه می‌شوند.
 */
class ShopScreen(
    game: MainActivity,
    private val returnTo: (() -> BaseScreen)? = null
) : BaseScreen(game) {

    private var selected: DroneModels.Model =
        DroneModels.byId(save.selectedDroneId) ?: DroneModels.all.first()

    /** پهبادی که صفحهٔ ارتقایش باز است؛ null یعنی صفحهٔ فهرست پهبادها. */
    private var detail: DroneModels.Model? = null

    /** پهبادی که پنل و ردیف‌های ارتقا برایش نشان داده می‌شوند. */
    private val viewing: DroneModels.Model get() = detail ?: selected

    private val kinds = listOf(UpgradeKind.RANGE, UpgradeKind.SPEED, UpgradeKind.GUIDANCE, UpgradeKind.ARMOR)
    private val droneButtons = ArrayList<UiButton>()
    private val upgradeButtons = ArrayList<UiButton>()

    private val back = UiButton("بازگشت", Theme.TEXT_DIM) {
        // در صفحهٔ ارتقا، «بازگشت» به فهرست پهبادها برمی‌گردد؛ در فهرست، به منو.
        if (detail != null) closeDetail() else exitToMenu()
    }

    private fun exitToMenu() {
        val to = returnTo?.invoke() ?: MainMenuScreen(game)
        game.show(to)
    }

    private fun closeDetail() {
        detail = null
        scrollY = 0f
        refresh()
        layoutUi(vw, vh)
    }

    private val bonus = UiButton("تماشای ویدیو ← ۱۰ سکه", Theme.VIOLET) { claimBonus() }
    private var bonusClaimed = false
    private var adInFlight = false

    private fun claimBonus() {
        if (bonusClaimed || adInFlight) return
        adInFlight = true
        AdManager.showRewarded(game) {
            post {
                adInFlight = false
                bonusClaimed = true
                save.addCoins(10)
                audio.beep(880f, 140, 0.45f)
                if (game.currentScreen() === this) {
                    say("+۱۰ سکه هدیهٔ ویدیو اضافه شد")
                    refresh()
                    if (vw > 0f && vh > 0f) layoutUi(vw, vh)
                }
            }
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
                    // لمس کارت: پهباد باز همان لحظه فعالِ پرواز می‌شود و
                    // صفحهٔ ارتقای خودش باز می‌شود. پهباد قفل هم صفحه‌اش
                    // باز می‌شود تا مشخصات و شرط باز شدنش دیده شود، ولی
                    // selectedDroneId (پهبادِ قابل پرواز) دست نمی‌خورد —
                    // باز شدن پهبادها با ستاره است و دکمهٔ خریدی ندارند.
                    selected = m
                    if (save.isDroneUnlocked(m.id)) {
                        save.selectedDroneId = m.id
                    } else {
                        say("این پهپاد قفل است — " + Fa.num(m.requiredStars) + " ستاره لازم است")
                        audio.beep(320f, 110, 0.3f)
                    }
                    detail = m
                    scrollY = 0f
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
        // دکمه‌های اسکرول‌شونده فقط از طریق viewport در onTouchUp هندل می‌شوند تا بخش بریده‌شده خارج از کادر کلیک نشود
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
        if (!save.isDroneUnlocked(viewing.id)) {
            say("این پهپاد قفل است — " + Fa.num(viewing.requiredStars) + " ستاره لازم است")
            audio.beep(320f, 110, 0.3f)
            return
        }
        val current = save.upgradeLevel(viewing.id, kind)
        val cap = viewing.maxLevel(kind)
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
        save.setUpgradeLevel(viewing.id, kind, current + 1)
        audio.coin()
        say(kind.title + " به سطح " + Fa.num(current + 1) + " رسید")
        refresh()
    }

    private fun refresh() {
        val unlocked = save.isDroneUnlocked(viewing.id)
        for (b in upgradeButtons) {
            val k = b.tag as UpgradeKind
            val lvl = save.upgradeLevel(viewing.id, k)
            val cap = viewing.maxLevel(k)
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
            b.filled = m.id == save.selectedDroneId
            b.enabled = true
        }
    }

    override fun onBack(): Boolean {
        if (detail != null) closeDetail() else exitToMenu()
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

    private fun headerH() = dp(118f)
    private fun cardH() = dp(72f)
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
        back.label = if (detail != null) "فهرست پهبادها" else "بازگشت"
        val backW = if (detail != null) dp(132f) else dp(96f)
        back.set(viewport.right - backW, h - dp(44f), backW, dp(32f))
    }

    private fun computeContent(): Float {
        return if (detail == null) {
            // صفحهٔ فهرست: فقط عنوان و چهار کارت — معمولاً بدون اسکرول جا می‌شود
            var y = labelHV
            y += DroneModels.all.size * (cardH() + gapV)
            y
        } else {
            // صفحهٔ ارتقا: پنل مشخصات + عنوان + چهار ردیف ارتقا
            var y = headerH()
            y += labelHV
            y += upgradeButtons.size * (rowH() + gapV)
            y
        }
    }

    private fun placeAll() {
        var y = 0f
        if (detail == null) {
            hangarLabelY = y; y += labelHV
            for ((i, b) in droneButtons.withIndex()) {
                droneYs[i] = y
                b.set(viewport.left, viewport.top + y - scrollY, viewport.width(), cardH())
                // دکمهٔ بیرون از ویوپورت نباید هیت‌تست شود؛ وگرنه کارتِ نامرئی
                // روی نوار پایین می‌افتد و لمسِ «بازگشت/بونوس» پهباد عوض می‌کند.
                b.visible = b.rect.bottom > viewport.top && b.rect.top < viewport.bottom
                y += cardH() + gapV
            }
            for (b in upgradeButtons) b.visible = false
        } else {
            headerY = y; y += headerH()
            upgradeLabelY = y; y += labelHV
            for (b in droneButtons) b.visible = false
            for ((i, b) in upgradeButtons.withIndex()) {
                upgradeYs[i] = y
                // ناحیهٔ دکمه: چپ‌ترین بخش ردیف
                b.set(
                    viewport.left + dp(10f),
                    viewport.top + y - scrollY + (rowH() - dp(32f)) / 2f,
                    btnAreaW() - dp(16f), dp(32f),
                )
                b.visible = b.rect.bottom > viewport.top && b.rect.top < viewport.bottom
                y += rowH() + gapV
            }
        }
    }

    override fun claimsTouch(x: Float, y: Float): Boolean = viewport.contains(x, y)

    private var downY = 0f

    override fun onTouchDown(x: Float, y: Float, pointerId: Int): Boolean {
        if (dragPointer == -1 && viewport.contains(x, y)) {
            dragPointer = pointerId
            downY = y
            lastY = y
            moved = false
        }
        return true
    }

    override fun onTouchMove(x: Float, y: Float, pointerId: Int) {
        if (pointerId != dragPointer) return
        val dy = y - lastY
        lastY = y
        if (!moved && maxScroll > 0f && Math.abs(y - downY) >= dp(8f)) {
            moved = true
        }
        if (moved && maxScroll > 0f) {
            scrollY = (scrollY - dy).coerceIn(0f, maxScroll)
            placeAll()
        }
    }

    override fun onTouchUp(x: Float, y: Float, pointerId: Int) {
        if (pointerId != dragPointer) return
        if (!moved && viewport.contains(x, y)) {
            var handled = false
            // دکمه‌های خرید اول (کوچک‌ترند)
            for (b in upgradeButtons.reversed()) {
                if (b.visible && b.rect.contains(x, y)) {
                    if (b.enabled) audio.click()
                    b.onClick(); handled = true; break
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
        dragPointer = -1
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
        // تبلیغ جایزه‌ای ممکن است بعد از ورود به فروشگاه آماده شود؛
        // نمایان بودن دکمهٔ بونوس فقط موقع چیدمان چک می‌شد و اگر تبلیغ
        // دیر می‌رسید، دکمه تا ورود بعدی ظاهر نمی‌شد.
        val wantBonus = !bonusClaimed && AdManager.isRewardedAdReady()
        if (bonus.visible != wantBonus) bonus.visible = wantBonus
    }

    // ------------------------------------------------------------ رسم

    override fun render(c: Canvas) {
        ui.background(c, vw, vh, Theme.BLUE)
        Deco.header(this, c, "فروشگاه و آشیانه", null, Theme.AMBER)
        Deco.resources(this, c)

        c.save()
        c.clipRect(viewport)

        val base = viewport.top - scrollY

        if (detail != null) {
        // ── پنل پهباد در صفحهٔ ارتقا
        val hp = RectF(viewport.left, base + headerY, viewport.right, base + headerY + headerH() - dp(6f))
        ui.panel(c, hp, Theme.withAlpha(viewing.color, 0.08f),
            Theme.withAlpha(viewing.color, 0.45f), dp(10f))
        val hRight = hp.right - dp(12f)
        ui.text(c, viewing.name, hRight, hp.top + dp(20f), dp(14.5f),
            viewing.color, bold = true, align = Paint.Align.RIGHT)
        var tag = viewing.tagline
        val tagMaxW = hp.width() - dp(24f)
        var tagSize = dp(9.5f)
        while (ui.measure(tag, tagSize) > tagMaxW && tagSize > dp(8f)) tagSize -= dp(0.5f)
        if (ui.measure(tag, tagSize) > tagMaxW) {
            while (tag.length > 8 && ui.measure(tag + "…", tagSize) > tagMaxW) tag = tag.dropLast(1)
            tag += "…"
        }
        ui.text(c, tag, hRight, hp.top + dp(36f), tagSize,
            Theme.TEXT_DIM, align = Paint.Align.RIGHT)

        val selUnlocked = save.isDroneUnlocked(viewing.id)
        val statusText = when {
            !selUnlocked -> "قفل — " + Fa.num(viewing.requiredStars) + " ستاره لازم است · ستاره‌های تو: " + Fa.num(save.totalStars)
            viewing.id == save.selectedDroneId -> "پهباد فعال برای پرواز"
            else -> "باز است — برای پرواز، روی کارتش بزن"
        }
        ui.text(c, statusText, hRight, hp.top + dp(52f), dp(10f),
            when {
                !selUnlocked -> Theme.RED
                selected.id == save.selectedDroneId -> Theme.MINT
                else -> Theme.TEXT_DIM
            }, align = Paint.Align.RIGHT)

        // چهار مشخصات در شبکهٔ ۲×۲: برچسب، نوار نسبی (نسبت به بهترین مدل)،
        // و مقدار مؤثر فعلیِ همین پهباد با ارتقاهای خودش.
        val colW = hp.width() / 2f
        for ((idx, k) in kinds.withIndex()) {
            val col = idx % 2
            val row = idx / 2
            val cellRight = hp.right - dp(12f) - col * colW
            val cy = hp.top + dp(72f) + row * dp(21f)
            val label = when (k) {
                UpgradeKind.RANGE -> "برد"
                UpgradeKind.SPEED -> "سرعت"
                UpgradeKind.GUIDANCE -> "هدایت"
                UpgradeKind.ARMOR -> "مقاومت"
            }
            ui.text(c, label, cellRight, cy + dp(3f), dp(9.5f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
            val barRight = cellRight - dp(40f)
            val barLeft = barRight - dp(52f)
            ui.fill.color = Theme.withAlpha(Theme.TEXT_FAINT, 0.22f)
            ui.fill.shader = null
            c.drawRoundRect(barLeft, cy - dp(2f), barRight, cy + dp(3f), dp(2f), dp(2f), ui.fill)
            val frac = statFraction(k, viewing).coerceIn(0f, 1f)
            ui.fill.color = when (k) {
                UpgradeKind.RANGE -> Theme.AMBER
                UpgradeKind.SPEED -> Theme.BLUE
                UpgradeKind.GUIDANCE -> Theme.VIOLET
                UpgradeKind.ARMOR -> Theme.MINT
            }
            if (frac > 0.01f)
                c.drawRoundRect(barLeft, cy - dp(2f), barLeft + (barRight - barLeft) * frac, cy + dp(3f), dp(2f), dp(2f), ui.fill)
            val value = k.valueAt(save.upgradeLevel(viewing.id, k), viewing)
            ui.text(c, value, barLeft - dp(6f), cy + dp(3f), dp(9.5f),
                if (selUnlocked) Theme.TEXT else Theme.TEXT_FAINT, align = Paint.Align.RIGHT)
        }
        } // پایان پنل (فقط صفحهٔ ارتقا)

        if (detail == null) {
        // ── آشیانه (صفحهٔ فهرست)
        sectionLabel(c, "آشیانه — برای ارتقا، روی پهباد بزن", base + hangarLabelY, Theme.AMBER)

        for ((i, b) in droneButtons.withIndex()) {
            val m = b.tag as DroneModels.Model
            val y = base + droneYs[i]
            if (y + cardH() < viewport.top || y > viewport.bottom) continue
            val unlocked = save.isDroneUnlocked(m.id)
            val isSel = m.id == save.selectedDroneId
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
            ui.text(c, status, tx, r.top + dp(39f), dp(10f),
                when {
                    !unlocked -> Theme.RED
                    isSel -> Theme.MINT
                    else -> Theme.TEXT_DIM
                }, align = Paint.Align.RIGHT)

            // خط مشخصات پایهٔ بدنه — مقایسهٔ سریع بین پهبادها بدون انتخاب
            val stats = statsLine(m)
            ui.text(c, stats, tx, r.top + dp(56f),
                fitSize(stats, dp(9.5f), r.width() - dp(64f)),
                Theme.TEXT_FAINT, align = Paint.Align.RIGHT)

            // نشان انتخاب: خطِ عمودی سمت راست کارت (جدا از همهٔ متن‌ها)
            if (isSel) {
                ui.fill.color = Theme.MINT
                ui.fill.shader = null
                c.drawRoundRect(r.right - dp(3f), r.top + dp(9f), r.right, r.bottom - dp(9f),
                    dp(1.5f), dp(1.5f), ui.fill)
            }
        }

        } // پایان فهرست (فقط صفحهٔ فهرست)

        if (detail != null) {
        // ── ارتقاها (صفحهٔ ارتقا)
        sectionLabel(c, "ارتقای " + viewing.name, base + upgradeLabelY, Theme.MINT)

        for ((i, b) in upgradeButtons.withIndex()) {
            val k = b.tag as UpgradeKind
            val y = base + upgradeYs[i]
            if (y + rowH() < viewport.top || y > viewport.bottom) continue

            val lvl = save.upgradeLevel(viewing.id, k)
            val cap = viewing.maxLevel(k)
            val unlocked = save.isDroneUnlocked(viewing.id)
            val r = RectF(viewport.left, y, viewport.right, y + rowH() - dp(3f))
            ui.panel(c, r, Theme.withAlpha(Theme.PANEL, 0.97f), Theme.LINE, dp(9f))

            // ناحیهٔ متن: از لبهٔ راست تا شروع ناحیهٔ دکمه
            val textRight = r.right - dp(12f)
            val tW = textAreaW()

            // خط ۱ — عنوان (بالا، راست‌چین)
            ui.text(c, k.title, textRight, r.top + dp(17f),
                fitSize(k.title, dp(12.5f), tW, bold = true),
                Theme.TEXT, bold = true, align = Paint.Align.RIGHT)

            // خط ۲ — مقدار فعلی و (تا سقف) مقدار سطح بعد (وسط، راست‌چین)
            val valueNow = if (lvl < cap) k.valueAt(lvl, viewing) + " ← " + k.valueAt(lvl + 1, viewing)
                else k.valueAt(lvl, viewing)
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
        } // پایان ارتقاها (فقط صفحهٔ ارتقا)

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
            ui.text(c, it, vw / 2f, vh - dp(64f), dp(11f), Theme.AMBER, bold = true, alpha = a)
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

    /** کسر نوار مشخصات: مقدار واقعی ارتقایافتهٔ این مدل نسبت به سقف کل مدل‌ها */
    private fun statFraction(kind: UpgradeKind, m: DroneModels.Model): Float {
        val lvl = save.upgradeLevel(m.id, kind)
        return when (kind) {
            UpgradeKind.RANGE -> m.rangeAt(lvl) / DroneModels.all.maxOf { it.rangeAt(it.maxLevel(kind)) }
            UpgradeKind.SPEED -> m.speedAt(lvl) / DroneModels.all.maxOf { it.speedAt(it.maxLevel(kind)) }
            UpgradeKind.GUIDANCE -> m.guidanceAt(lvl) / DroneModels.all.maxOf { it.guidanceAt(it.maxLevel(kind)) }
            UpgradeKind.ARMOR -> m.armorAt(lvl).toFloat() / DroneModels.all.maxOf { it.armorAt(it.maxLevel(kind)) }.toFloat()
        }
    }

    /** خط مشخصات پایه روی کارت پهباد: ضریب‌های بدنه نسبت به مدل پایه */
    private fun statsLine(m: DroneModels.Model): String =
        "برد ×" + Fa.num(m.rangeAt(1) / 1200f, 2) +
            " · سرعت ×" + Fa.num(m.speedAt(1) / 150f, 2) +
            " · هدایت ×" + Fa.num(m.guidanceAt(1), 2) +
            " · مقاومت " + Fa.num(m.armorAt(1)) + " ضربه"

    /** بزرگ‌ترین اندازه‌ای که متن در آن جا شود (کف ۹dp) */
    private fun fitSize(text: String, start: Float, maxW: Float, bold: Boolean = false): Float {
        var s = start
        while (s > dp(9f) && ui.measure(text, s, bold) > maxW) s -= dp(0.5f)
        return s
    }
}
