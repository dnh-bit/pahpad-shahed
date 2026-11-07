package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.AdManager
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import kotlin.math.abs

/**
 * تنظیمات بازی — بخش‌بندی‌شده و اسکرول‌شونده.
 *
 * بازطراحی: قبلاً همهٔ دکمه‌ها در دو ستون پشت‌سرهم و بدون گروه‌بندی بودند و
 * روی صفحه‌های کم‌ارتفاع از پایین بیرون می‌زدند. حالا سه بخش روشن داریم
 * (صدا و کنترل / گرافیک / پیشرفت) و اگر جمع ارتفاع از فضا بیشتر شد، اسکرول می‌شود.
 */
class SettingsScreen(game: MainActivity) : BaseScreen(game) {

    private var confirmReset = false

    // ── صدا و کنترل ──
    private val sound = UiButton("صدا", Theme.MINT) { save.soundOn = !save.soundOn; refresh() }
    private val vibrate = UiButton("لرزش", Theme.MINT) { save.vibrateOn = !save.vibrateOn; refresh() }
    private val control = UiButton("شیوه کنترل", Theme.BLUE) { save.tiltControl = !save.tiltControl; refresh() }
    private val invert = UiButton("محور عمودی", Theme.BLUE) { save.invertPitch = !save.invertPitch; refresh() }
    private val hints = UiButton("راهنمای مراحل", Theme.AMBER) { save.showHints = !save.showHints; refresh() }

    // ── گرافیک ──
    private val quality = UiButton("کیفیت گرافیک", Theme.VIOLET) {
        save.graphicsQuality = (save.graphicsQuality + 1) % 3
        refresh()
    }
    private val effects = UiButton("افکت‌ها", Theme.VIOLET) { save.effectsOn = !save.effectsOn; refresh() }
    private val fps = UiButton("نرخ فریم", Theme.VIOLET) { save.frameCap = (save.frameCap + 1) % 2; refresh() }
    private val showFps = UiButton("نمایش FPS", Theme.VIOLET) { save.showFps = !save.showFps; refresh() }

    // ── پیشرفت ──
    private val reset = UiButton("پاک کردن پیشرفت", Theme.RED) {
        if (confirmReset) {
            save.resetAll()
            confirmReset = false
            audio.beep(300f, 200, 0.4f)
            refresh()
        } else {
            confirmReset = true
            refresh()
        }
    }

    private val back = UiButton("بازگشت", Theme.TEXT_DIM) { game.show(MainMenuScreen(game)) }

    private val audioItems = listOf(sound, vibrate, control, invert, hints)
    private val gfxItems = listOf(quality, effects, fps, showFps)
    private val items = audioItems + gfxItems + listOf(reset)

    init {
        buttons.addAll(items)
        buttons.add(back)
        refresh()
    }

    private fun refresh() {
        // تنظیمات گرافیک کش BaseScreen را هم به‌روز کن تا همان لحظه اثر کند
        refreshGraphicsCache()
        sound.subLabel = if (save.soundOn) "روشن" else "خاموش"
        vibrate.subLabel = if (save.vibrateOn) "روشن" else "خاموش"
        control.subLabel = if (save.tiltControl) "تیلت گوشی" else "جوی‌استیک"
        invert.subLabel = if (save.invertPitch) "معکوس" else "عادی"
        hints.subLabel = if (save.showHints) "نمایش" else "پنهان"
        quality.subLabel = when (save.graphicsQuality) {
            0 -> "سبک"
            1 -> "متعادل"
            else -> "بالا"
        }
        effects.subLabel = if (save.effectsOn) "روشن" else "خاموش"
        fps.subLabel = if (save.frameCap == 0) "۳۰ فریم" else "۶۰ فریم"
        showFps.subLabel = if (save.showFps) "روشن" else "خاموش"
        reset.subLabel = if (confirmReset) "برای تایید دوباره بزنید" else "همهٔ ستاره‌ها و سکه‌ها"
    }

    override fun onBack(): Boolean {
        game.show(MainMenuScreen(game))
        return true
    }

    // ------------------------------------------------------------ چیدمان

    private val viewport = RectF()
    private var scrollY = 0f
    private var maxScroll = 0f
    private var dragPointer = -1
    private var lastY = 0f
    private var moved = false

    private var sectionTitles = listOf<Triple<String, Float, Int>>()

    private val shellTop = 84f
    private val bhDp = 56f
    private val rowGapDp = 10f
    private val sectionGapDp = 24f
    private val cols = 2

    override fun layoutUi(w: Float, h: Float) {
        val top = dp(shellTop)
        val bottom = h - dp(56f)
        viewport.set(dp(20f), top, w - dp(20f), bottom)

        val gap = dp(12f)
        val bw = (viewport.width() - gap) / cols
        val bh = dp(bhDp)
        val rowGap = dp(rowGapDp)

        // ارتفاع کل محتوا (بدون اسکرول)
        fun sectionHeight(n: Int): Float {
            val rows = (n + cols - 1) / cols
            return dp(20f) + rows * bh + (rows - 1) * rowGap + dp(sectionGapDp)
        }

        val contentH = sectionHeight(audioItems.size) +
            sectionHeight(gfxItems.size) +
            sectionHeight(1)

        maxScroll = (contentH - viewport.height()).coerceAtLeast(0f)
        scrollY = scrollY.coerceIn(0f, maxScroll)

        val titles = ArrayList<Triple<String, Float, Int>>()
        var y = 0f

        fun placeSection(name: String, list: List<UiButton>, accent: Int) {
            titles.add(Triple(name, viewport.top + y - scrollY, accent))
            y += dp(20f)
            for ((i, b) in list.withIndex()) {
                val col = i % cols
                val row = i / cols
                b.set(
                    viewport.left + col * (bw + gap),
                    viewport.top + y + row * (bh + rowGap) - scrollY,
                    bw, bh,
                )
            }
            val rows = (list.size + cols - 1) / cols
            y += rows * bh + (rows - 1) * rowGap + dp(sectionGapDp)
        }

        placeSection("صدا و کنترل", audioItems, Theme.MINT)
        placeSection("گرافیک", gfxItems, Theme.VIOLET)
        placeSection("پیشرفت", listOf(reset), Theme.RED)

        sectionTitles = titles

        // دکمه‌های خارج از ویوپورت غیرفعال شوند
        for (b in items) {
            b.visible = b.rect.bottom > viewport.top && b.rect.top < viewport.bottom
        }

        back.set(dp(20f), h - dp(50f), dp(104f), dp(38f))
    }

    /** لمس داخل ویوپورت باید اسکرول کند، نه اینکه به دکمه برسد */
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
        if (abs(dy) >= 0.5f) {
            moved = true
            scrollY = (scrollY - dy).coerceIn(0f, maxScroll)
            layoutUi(vw, vh)
        }
    }

    override fun onTouchUp(x: Float, y: Float, pointerId: Int) {
        if (pointerId == dragPointer && !moved) {
            // ضربهٔ کوتاه = کلیک روی دکمه‌ای که زیر انگشت است
            for (b in items.reversed()) {
                if (b.visible && b.enabled && b.rect.contains(x, y)) {
                    audio.click()
                    b.onClick()
                    break
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

    // ------------------------------------------------------------ رسم

    override fun render(c: Canvas) {
        ui.background(c, vw, vh)
        Deco.header(this, c, "تنظیمات", "تجربه بازی را به سلیقه خود تغییر دهید")
        Deco.resources(this, c)

        c.save()
        c.clipRect(viewport)

        // عنوان بخش‌ها
        for ((name, ty, accent) in sectionTitles) {
            if (ty < viewport.top - dp(26f) || ty > viewport.bottom + dp(26f)) continue
            ui.text(c, name, viewport.right, ty, dp(12.5f), accent, bold = true, align = Paint.Align.RIGHT)
            ui.stroke.color = Theme.withAlpha(accent, 0.22f)
            ui.stroke.strokeWidth = dp(1f)
            c.drawLine(viewport.left, ty + dp(5f), viewport.right, ty + dp(5f), ui.stroke)
        }

        for (b in items) ui.button(c, b)
        c.restore()

        // نشانگر اسکرول
        if (maxScroll > 0f) {
            val trackH = viewport.height()
            val thumbH = (trackH * (trackH / (trackH + maxScroll))).coerceAtLeast(dp(26f))
            val t = scrollY / maxScroll
            val thumbY = viewport.top + t * (trackH - thumbH)
            ui.fill.color = Theme.withAlpha(Theme.MINT, 0.32f)
            ui.fill.shader = null
            c.drawRoundRect(viewport.right + dp(3f), thumbY, viewport.right + dp(6f),
                thumbY + thumbH, dp(2f), dp(2f), ui.fill)
        }

        ui.button(c, back)
        if (maxScroll > 0f) {
            ui.text(c, "برای دیدن بقیه، انگشت را بکشید", vw / 2f, vh - dp(40f), dp(10f), Theme.TEXT_FAINT)
        } else {
            ui.text(c, "کیفیت پایین‌تر = حرکت روان‌تر روی گوشی‌های قدیمی",
                vw / 2f, vh - dp(40f), dp(10f), Theme.TEXT_FAINT)
        }
        ui.text(c, "شبکهٔ تبلیغ: " + AdManager.status(), vw / 2f, vh - dp(18f), dp(9.5f), Theme.TEXT_FAINT)
    }
}
