package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import ir.shahed.pahpad.data.DroneModels
import ir.shahed.pahpad.data.UpgradeKind

/** فروشگاه: انتخاب پهباد و ارتقای قابلیت‌ها */
class ShopScreen(game: MainActivity) : BaseScreen(game) {

    private var selected: DroneModels.Model =
        DroneModels.byId(save.selectedDroneId) ?: DroneModels.all.first()

    private val droneButtons = ArrayList<UiButton>()
    private val upgradeButtons = ArrayList<UiButton>()
    private val back = UiButton("بازگشت", Theme.TEXT_DIM) { game.show(MainMenuScreen(game)) }
    private var message: String? = null
    private var messageTime = 0f

    init {
        for (m in DroneModels.all) {
            val b = UiButton(m.name, m.color) {
                if (save.isDroneUnlocked(m.id)) {
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
        for (k in UpgradeKind.values()) {
            val b = UiButton("ارتقا", Theme.MINT) { buy(k) }
            b.tag = k
            upgradeButtons.add(b)
        }
        buttons.addAll(droneButtons)
        buttons.addAll(upgradeButtons)
        buttons.add(back)
        refresh()
    }

    private fun say(text: String) {
        message = text
        messageTime = 2.6f
    }

    private fun buy(kind: UpgradeKind) {
        val current = save.upgradeLevel(selected.id, kind)
        if (current >= 10) {
            say("این قابلیت در حداکثر سطح است")
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
            if (lvl >= 10) {
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
        game.show(MainMenuScreen(game))
        return true
    }

    override fun layoutUi(w: Float, h: Float) {
        val lw = w * 0.28f
        val lh = dp(74f)
        var y = h * 0.19f
        for (b in droneButtons) {
            b.set(dp(20f), y, lw, lh)
            y += lh + dp(9f)
        }
        val rx = w * 0.34f
        val rw = w - rx - dp(20f)
        var uy = h * 0.19f
        for (b in upgradeButtons) {
            b.set(rx + rw - dp(120f), uy + dp(6f), dp(112f), dp(42f))
            uy += dp(54f)
        }
        back.set(dp(20f), h - dp(52f), dp(110f), dp(40f))
    }

    override fun update(dt: Float) {
        if (messageTime > 0f) {
            messageTime -= dt
            if (messageTime <= 0f) message = null
        }
    }

    override fun render(c: Canvas) {
        ui.background(c, vw, vh, Theme.AMBER)
        Deco.header(this, c, "فروشگاه و آشیانه", "پهباد خود را انتخاب و ارتقا دهید", Theme.AMBER)
        Deco.resources(this, c)

        // ---- کارت‌های پهباد
        for (b in droneButtons) {
            val m = b.tag as DroneModels.Model
            val unlocked = save.isDroneUnlocked(m.id)
            val r = b.rect
            ui.panel(c, r, if (b.pressed) Theme.PANEL_HI else Theme.PANEL,
                Theme.withAlpha(m.color, if (m.id == selected.id) 0.95f else 0.3f), dp(12f))
            val sideName = when (m.id) {
                "shahed136" -> "drone_136_side"; "shahed238" -> "drone_238_side"; "shahedx" -> "drone_x_side"
                else -> "drone_131_side"
            }
            val imgAlpha = if (unlocked) 255 else 110
            if (unlocked) {
                ui.imageFit(ui.context, c, sideName, r.left + dp(6f), r.top + dp(6f), dp(64f), r.height() - dp(12f))
            } else {
                ui.imageFit(ui.context, c, sideName, r.left + dp(6f), r.top + dp(6f), dp(64f), r.height() - dp(12f))
                ui.iconLock(c, r.left + dp(26f), r.centerY() + dp(2f), dp(10f))
            }
            ui.text(c, m.name, r.right - dp(12f), r.top + dp(18f), dp(14f),
                if (unlocked) Theme.TEXT else Theme.TEXT_FAINT, bold = true, align = Paint.Align.RIGHT)
            val sub = if (unlocked) {
                if (m.id == selected.id) "انتخاب شده" else "آماده انتخاب"
            } else {
                "نیازمند " + Fa.num(m.requiredStars) + " ستاره"
            }
            ui.text(c, sub, r.right - dp(12f), r.top + dp(38f), dp(11f),
                if (unlocked) Theme.MINT else Theme.TEXT_FAINT, align = Paint.Align.RIGHT)
        }

        // ---- مشخصات و ارتقاها
        val rx = vw * 0.34f
        val rw = vw - rx - dp(20f)
        ui.text(c, selected.tagline, rx + rw / 2f, vh * 0.145f, dp(11.5f), Theme.TEXT_DIM)

        var uy = vh * 0.19f
        for (b in upgradeButtons) {
            val k = b.tag as UpgradeKind
            val lvl = save.upgradeLevel(selected.id, k)
            ui.panel(c, rx, uy, rw, dp(48f), Theme.withAlpha(Theme.PANEL, 0.95f), Theme.LINE, dp(10f))
            ui.text(c, k.title, rx + rw - dp(14f), uy + dp(16f), dp(13f), Theme.TEXT, bold = true,
                align = Paint.Align.RIGHT)
            ui.text(c, k.valueAt(lvl, selected), rx + rw - dp(14f), uy + dp(34f), dp(11.5f), Theme.MINT,
                align = Paint.Align.RIGHT)
            // نشانگر سطح
            val pipStart = rx + dp(150f)
            for (i in 0 until 10) {
                ui.fill.color = if (i < lvl) Theme.AMBER else Theme.withAlpha(Theme.TEXT_FAINT, 0.3f)
                ui.fill.shader = null
                c.drawRect(pipStart + i * dp(10f), uy + dp(30f), pipStart + i * dp(10f) + dp(6f), uy + dp(38f), ui.fill)
            }
            ui.text(c, k.description, pipStart, uy + dp(16f), dp(10.5f), Theme.TEXT_FAINT, align = Paint.Align.LEFT)
            uy += dp(54f)
        }

        drawButtons(c)

        message?.let {
            val a = (messageTime / 0.6f).coerceIn(0f, 1f)
            ui.text(c, it, vw / 2f, vh - dp(28f), dp(12.5f), Theme.AMBER, bold = true, alpha = a)
        }
    }
}
