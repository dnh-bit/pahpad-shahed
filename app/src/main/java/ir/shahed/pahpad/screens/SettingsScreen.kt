package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton

/** تنظیمات بازی */
class SettingsScreen(game: MainActivity) : BaseScreen(game) {

    private var confirmReset = false

    private val sound = UiButton("صدا", Theme.MINT) {
        save.soundOn = !save.soundOn
        refresh()
    }
    private val vibrate = UiButton("لرزش", Theme.MINT) {
        save.vibrateOn = !save.vibrateOn
        refresh()
    }
    private val control = UiButton("شیوه کنترل", Theme.BLUE) {
        save.tiltControl = !save.tiltControl
        refresh()
    }
    private val invert = UiButton("محور عمودی", Theme.BLUE) {
        save.invertPitch = !save.invertPitch
        refresh()
    }
    private val hints = UiButton("راهنمای مراحل", Theme.AMBER) {
        save.showHints = !save.showHints
        refresh()
    }
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

    private val items = listOf(sound, vibrate, control, invert, hints, reset)

    init {
        buttons.addAll(items)
        buttons.add(back)
        refresh()
    }

    private fun refresh() {
        sound.subLabel = if (save.soundOn) "روشن" else "خاموش"
        vibrate.subLabel = if (save.vibrateOn) "روشن" else "خاموش"
        control.subLabel = if (save.tiltControl) "تیلت گوشی (ژیروسکوپ)" else "جوی‌استیک مجازی"
        invert.subLabel = if (save.invertPitch) "معکوس" else "عادی"
        hints.subLabel = if (save.showHints) "نمایش داده شود" else "پنهان"
        reset.subLabel = if (confirmReset) "برای تایید دوباره بزنید" else "همه ستاره‌ها و سکه‌ها پاک می‌شود"
    }

    override fun onBack(): Boolean {
        game.show(MainMenuScreen(game))
        return true
    }

    override fun layoutUi(w: Float, h: Float) {
        val cols = 2
        val bw = (w - dp(48f) - dp(14f)) / cols
        val bh = dp(58f)
        var y = h * 0.22f
        for ((i, b) in items.withIndex()) {
            val col = i % cols
            b.set(dp(24f) + col * (bw + dp(14f)), y, bw, bh)
            if (col == cols - 1) y += bh + dp(12f)
        }
        back.set(dp(24f), h - dp(56f), dp(110f), dp(42f))
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh)
        Deco.header(this, c, "تنظیمات", "تجربه بازی را به سلیقه خود تغییر دهید")
        Deco.resources(this, c)
        drawButtons(c)
        ui.text(c, "کنترل تیلت به حس‌گر شتاب‌سنج گوشی نیاز دارد. اگر پرواز لرزان بود، جوی‌استیک را انتخاب کنید.",
            vw / 2f, vh - dp(36f), dp(11f), Theme.TEXT_FAINT)
    }
}
