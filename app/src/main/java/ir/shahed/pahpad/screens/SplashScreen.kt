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

    // اشیای رسم یک بار ساخته می‌شوند (نسخه‌ی قبل در هر فریم Paint و RectF تازه می‌ساخت)
    private val spritePaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val spriteRect = android.graphics.RectF()

    override fun render(c: Canvas) {
        ui.background(c, vw, vh)
        val cx = vw / 2f
        val cy = vh / 2f
        val fy = cy - dp(60f)

        // مسیر پرواز پهباد با دنباله‌ی محوشونده
        val t = (time / 2.7f).coerceIn(0f, 1f)
        val ease = 1f - (1f - t) * (1f - t)
        val dx = -vw * 0.55f + vw * 0.55f * ease
        val startX = cx - vw * 0.55f
        val headX = cx + dx
        val segs = 12
        for (i in 0 until segs) {
            val a0 = i / segs.toFloat()
            val a1 = (i + 1) / segs.toFloat()
            ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.05f + 0.35f * a1)
            ui.stroke.strokeWidth = dp(0.8f + 1.6f * a1)
            c.drawLine(startX + (headX - startX) * a0, fy, startX + (headX - startX) * a1, fy, ui.stroke)
        }
        // اسپرایت پهباد در حال پرواز
        val bmp = ui.imageGet(ui.context, "drone_136")
        if (bmp != null) {
            val sz = dp(56f)
            c.save()
            c.rotate(90f, headX, fy)
            spriteRect.set(headX - sz / 2f, fy - sz / 2f, headX + sz / 2f, fy + sz / 2f)
            c.drawBitmap(bmp, null, spriteRect, spritePaint)
            c.restore()
        } else {
            Deco.droneIcon(this, c, headX, fy, dp(18f), Theme.MINT, 90f)
        }

        val fade = ((time - 0.3f) / 0.8f).coerceIn(0f, 1f)
        ui.text(c, "پهباد شاهد", cx, cy + dp(10f), dp(40f), Theme.TEXT, bold = true, alpha = fade)
        ui.text(c, "شبیه‌ساز اپراتور پهباد", cx, cy + dp(48f), dp(14f), Theme.TEXT_DIM, alpha = fade)
        ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.4f * fade)
        ui.stroke.strokeWidth = dp(1.2f)
        val lw = vw * 0.16f * fade
        c.drawLine(cx - lw, cy + dp(68f), cx + lw, cy + dp(68f), ui.stroke)

        val alpha = 0.35f + 0.35f * (1f + Math.sin((time * 3f).toDouble()).toFloat())
        ui.text(c, "برای ورود ضربه بزنید", cx, vh - dp(40f), dp(13f), Theme.MINT, alpha = (alpha * fade).coerceIn(0f, 1f))
    }
}
