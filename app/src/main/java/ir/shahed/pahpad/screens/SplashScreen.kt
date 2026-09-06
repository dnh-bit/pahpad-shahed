package ir.shahed.pahpad.screens

import android.graphics.Canvas
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Theme

/** اسپلش اسکرین: لوگو و انیمیشن پهباد */
class SplashScreen(game: MainActivity) : BaseScreen(game) {

    private var moved = false

    override fun update(dt: Float) {
        if (!moved && time > 2.7f) go()
    }

    private fun go() {
        if (moved) return
        moved = true
        post { game.show(MainMenuScreen(game)) }
    }

    override fun onTouchDown(x: Float, y: Float, pointerId: Int): Boolean {
        if (time > 0.6f) go()
        return true
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh)
        val cx = vw / 2f
        val cy = vh / 2f

        // مسیر پرواز پهباد
        val t = (time / 2.7f).coerceIn(0f, 1f)
        val ease = 1f - (1f - t) * (1f - t)
        val dx = -vw * 0.55f + vw * 0.55f * ease
        ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.25f)
        ui.stroke.strokeWidth = dp(1.4f)
        c.drawLine(cx - vw * 0.55f, cy - dp(60f), cx + dx, cy - dp(60f), ui.stroke)
        // اسپرایت پهباد در حال پرواز
        val bmp = ui.imageGet(ui.context, "drone_136")
        if (bmp != null) {
            val sz = dp(56f)
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
            c.save()
            c.rotate(90f, cx + dx, cy - dp(60f))
            c.drawBitmap(bmp, null,
                android.graphics.RectF(cx + dx - sz / 2f, cy - dp(60f) - sz / 2f, cx + dx + sz / 2f, cy - dp(60f) + sz / 2f),
                paint)
            c.restore()
        } else {
            Deco.droneIcon(this, c, cx + dx, cy - dp(60f), dp(18f), Theme.MINT, 90f)
        }

        ui.text(c, "پهباد شاهد", cx, cy + dp(10f), dp(40f), Theme.TEXT, bold = true)
        ui.text(c, "شبیه‌ساز اپراتور پهباد", cx, cy + dp(48f), dp(14f), Theme.TEXT_DIM)

        val alpha = 0.35f + 0.35f * (1f + Math.sin((time * 3f).toDouble()).toFloat())
        ui.text(c, "برای ورود ضربه بزنید", cx, vh - dp(40f), dp(13f), Theme.MINT, alpha = alpha.coerceIn(0f, 1f))
    }
}
