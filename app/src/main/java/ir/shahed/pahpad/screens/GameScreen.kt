package ir.shahed.pahpad.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import ir.shahed.pahpad.data.LevelDef
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.MissionResult
import ir.shahed.pahpad.game.WorldRenderer
import java.util.Random

/** صفحه‌ی گیم‌پلی: پرواز پهباد، HUD و کنترل‌ها */
class GameScreen(
    game: MainActivity,
    private val level: LevelDef,
    private val mode: MissionMode,
    private val seed: Long
) : BaseScreen(game), SensorEventListener {

    private val mission = Mission(level, save, mode, seed)
    private val renderer = WorldRenderer(game)
    private val rnd = Random()

    private val sensorManager = game.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var restAx = 0f
    private var restAy = 0f
    private var calibrated = false
    private var tiltX = 0f
    private var tiltY = 0f

    // ---- لمس
    private var joyPointer = -1
    private var joyBaseX = 0f
    private var joyBaseY = 0f
    private var joyX = 0f
    private var joyY = 0f
    private var throttlePointer = -1
    private var throttleStartY = 0f
    private var throttleStart = 0.7f
    private var boostPointer = -1

    private val boostRect = RectF()
    private val camRect = RectF()
    private val pauseRect = RectF()

    private var paused = false
    private var finished = false
    private var finishTimer = 0f
    private var result: MissionResult? = null
    private var leaving = false

    // ---- دکمه‌های منوی توقف
    private val resume = UiButton("ادامه", Theme.MINT, filled = true) { setPaused(false) }
    private val retry = UiButton("تلاش مجدد", Theme.AMBER) {
        game.show(GameScreen(game, level, mode, seed))
    }
    private val quit = UiButton("خروج از ماموریت", Theme.RED) {
        mission.abort()
        exitToMenu()
    }

    init {
        resume.visible = false
        retry.visible = false
        quit.visible = false
        buttons.addAll(listOf(resume, retry, quit))

        mission.onExplosion = { x, y, z, p ->
            renderer.fx.explosion(x, y, z, p)
            renderer.fx.debris(x, y, z)
            audio.explosion(p)
            audio.vibrate(90)
        }
        mission.onSmoke = { x, y, z -> renderer.fx.smoke(x, y, z, 0.9f) }
        mission.onTracer = { x, y, z -> renderer.fx.tracer(x, y, z) }
        mission.onLaunch = {
            audio.launch()
            audio.startEngine()
            calibrated = false
        }
        mission.onHit = {
            audio.aaShot()
            audio.vibrate(140)
        }
        mission.onAlarm = { audio.alarm() }
        mission.onCollect = { audio.coin() }
        mission.onFinish = { res ->
            result = res
            finished = true
            finishTimer = 0f
            audio.stopEngine()
            if (res.success) audio.success() else audio.failure()
        }
    }

    // ------------------------------------------------------------ چرخه عمر

    override fun onEnter() {
        registerSensor()
    }

    override fun onExit() {
        unregisterSensor()
        audio.stopEngine()
    }

    override fun onScreenPause() {
        super.onScreenPause()
        unregisterSensor()
        audio.stopEngine()
        if (!finished) setPaused(true)
    }

    override fun onScreenResume() {
        super.onScreenResume()
        registerSensor()
    }

    private fun registerSensor() {
        val sm = sensorManager ?: return
        val s = accelerometer ?: return
        if (save.tiltControl) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun unregisterSensor() {
        sensorManager?.unregisterListener(this)
    }

    override fun onBack(): Boolean {
        if (finished) return true
        setPaused(!paused)
        return true
    }

    private fun setPaused(value: Boolean) {
        paused = value
        resume.visible = value
        retry.visible = value
        quit.visible = value
        if (value) audio.stopEngine()
        else if (mission.phase == Mission.Phase.FLYING) audio.startEngine()
    }

    private fun exitToMenu() {
        when (mode) {
            MissionMode.STORY -> game.show(LevelSelectScreen(game, level.chapter))
            else -> game.show(MainMenuScreen(game))
        }
    }

    // ------------------------------------------------------------ حس‌گر

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val v0 = event.values[0]
        val v1 = event.values[1]
        var ax: Float
        var ay: Float
        val rotation = try {
            @Suppress("DEPRECATION")
            (game.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        } catch (t: Throwable) {
            Surface.ROTATION_90
        }
        when (rotation) {
            Surface.ROTATION_90 -> { ax = -v1; ay = v0 }
            Surface.ROTATION_270 -> { ax = v1; ay = -v0 }
            Surface.ROTATION_180 -> { ax = -v0; ay = -v1 }
            else -> { ax = v0; ay = v1 }
        }
        if (!calibrated) {
            restAx = ax
            restAy = ay
            calibrated = true
        }
        val sens = 4.2f
        tiltX = ((ax - restAx) / sens).coerceIn(-1f, 1f)
        tiltY = ((ay - restAy) / sens).coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ------------------------------------------------------------ لمس

    override fun layoutUi(w: Float, h: Float) {
        val size = dp(64f)
        boostRect.set(w - dp(20f) - size, h - dp(20f) - size, w - dp(20f), h - dp(20f))
        camRect.set(w - dp(20f) - size, boostRect.top - dp(12f) - dp(48f), w - dp(20f), boostRect.top - dp(12f))
        pauseRect.set(w - dp(58f), dp(14f), w - dp(20f), dp(52f))

        val bw = dp(190f)
        val bh = dp(46f)
        val cx = w / 2f - bw / 2f
        resume.set(cx, h * 0.42f, bw, bh)
        retry.set(cx, h * 0.42f + bh + dp(10f), bw, bh)
        quit.set(cx, h * 0.42f + (bh + dp(10f)) * 2f, bw, bh)
    }

    override fun onTouchDown(x: Float, y: Float, pointerId: Int): Boolean {
        if (finished) return true
        if (pauseRect.contains(x, y)) {
            setPaused(!paused)
            audio.click()
            return true
        }
        if (paused) return true

        if (mission.phase == Mission.Phase.READY) {
            mission.launch()
            return true
        }
        if (camRect.contains(x, y)) {
            mission.fpv = !mission.fpv
            audio.click()
            return true
        }
        if (boostRect.contains(x, y)) {
            boostPointer = pointerId
            mission.boost = true
            return true
        }
        if (x < vw * 0.45f) {
            if (save.tiltControl) return true
            joyPointer = pointerId
            joyBaseX = x
            joyBaseY = y
            joyX = x
            joyY = y
            return true
        }
        throttlePointer = pointerId
        throttleStartY = y
        throttleStart = mission.throttle
        return true
    }

    override fun onTouchMove(x: Float, y: Float, pointerId: Int) {
        if (paused || finished) return
        if (pointerId == joyPointer) {
            joyX = x
            joyY = y
        } else if (pointerId == throttlePointer) {
            val delta = (throttleStartY - y) / (vh * 0.45f)
            mission.throttle = (throttleStart + delta).coerceIn(0f, 1f)
        }
    }

    override fun onTouchUp(x: Float, y: Float, pointerId: Int) {
        if (pointerId == joyPointer) {
            joyPointer = -1
            joyX = joyBaseX
            joyY = joyBaseY
        }
        if (pointerId == throttlePointer) throttlePointer = -1
        if (pointerId == boostPointer) {
            boostPointer = -1
            mission.boost = false
        }
    }

    // ------------------------------------------------------------ به‌روزرسانی

    override fun update(dt: Float) {
        if (paused) return

        if (mission.phase == Mission.Phase.FLYING) {
            if (save.tiltControl) {
                mission.steerX = tiltX
                mission.steerY = -tiltY
            } else if (joyPointer >= 0) {
                val r = dp(58f)
                mission.steerX = ((joyX - joyBaseX) / r).coerceIn(-1f, 1f)
                mission.steerY = (-(joyY - joyBaseY) / r).coerceIn(-1f, 1f)
            } else {
                mission.steerX = 0f
                mission.steerY = 0f
            }
            audio.setEngineIntensity(mission.throttle * (if (mission.boost) 1f else 0.85f))
        } else {
            mission.steerX = 0f
            mission.steerY = 0f
        }

        mission.update(dt)
        renderer.fx.update(dt)

        if (finished) {
            finishTimer += dt
            if (finishTimer > 2.2f && !leaving) {
                val res = result
                if (res != null) {
                    leaving = true
                    post { game.show(ResultScreen(game, level, mode, res, seed)) }
                }
            }
        }
    }

    // ------------------------------------------------------------ دوربین

    private fun setupCamera() {
        val cam = renderer.scene.cam
        cam.viewport(vw, vh)
        if (mission.fpv) {
            cam.fov = 82f
            cam.x = mission.x
            cam.y = mission.y
            cam.z = mission.z
            cam.yaw = mission.yaw
            cam.pitch = mission.pitch
            cam.roll = mission.roll * 0.9f
        } else {
            cam.fov = 74f
            val back = 26f
            val cp = Math.cos(mission.pitch.toDouble()).toFloat()
            cam.x = mission.x - Math.sin(mission.yaw.toDouble()).toFloat() * back * cp
            cam.z = mission.z - Math.cos(mission.yaw.toDouble()).toFloat() * back * cp
            cam.y = (mission.y - Math.sin(mission.pitch.toDouble()).toFloat() * back + 6f).coerceAtLeast(3f)
            cam.yaw = mission.yaw
            cam.pitch = mission.pitch * 0.85f - 0.05f
            cam.roll = mission.roll * 0.45f
        }
        cam.viewport(vw, vh)
    }

    // ------------------------------------------------------------ رسم

    override fun render(c: Canvas) {
        setupCamera()

        val shake = mission.shake
        c.save()
        if (shake > 0f) {
            val amp = dp(9f) * shake
            c.translate((rnd.nextFloat() * 2f - 1f) * amp, (rnd.nextFloat() * 2f - 1f) * amp)
        }
        // چرخش تصویر برای حس بنک
        val rollDeg = Math.toDegrees(renderer.scene.cam.roll.toDouble()).toFloat()
        c.save()
        c.rotate(rollDeg, vw / 2f, vh / 2f)
        renderer.draw(c, mission, vw, vh)
        c.restore()
        c.restore()

        drawSignalNoise(c)
        drawTargetMarkers(c)
        drawHud(c)
        drawControls(c)
        drawToasts(c)

        if (mission.flash > 0f) {
            ui.fill.color = Theme.withAlpha(0xFFFFFFFF.toInt(), mission.flash * 0.75f)
            ui.fill.shader = null
            c.drawRect(0f, 0f, vw, vh, ui.fill)
        }
        if (mission.phase == Mission.Phase.READY) drawPreLaunch(c)
        if (paused) drawPauseOverlay(c)
        if (finished) drawFinishBanner(c)
    }

    // ---- اخلال سیگنال
    private fun drawSignalNoise(c: Canvas) {
        val s = mission.signalLoss
        if (s < 0.12f) return
        ui.fill.shader = null
        val bands = (s * 14f).toInt()
        for (i in 0 until bands) {
            val y = rnd.nextFloat() * vh
            val h = dp(2f) + rnd.nextFloat() * dp(10f)
            ui.fill.color = Theme.withAlpha(Theme.VIOLET, 0.05f + rnd.nextFloat() * 0.12f * s)
            c.drawRect(0f, y, vw, y + h, ui.fill)
        }
        ui.fill.color = Theme.withAlpha(Theme.VIOLET, s * 0.12f)
        c.drawRect(0f, 0f, vw, vh, ui.fill)
    }

    // ---- نشانگر هدف
    private fun drawTargetMarkers(c: Canvas) {
        val t = mission.world.activeTarget() ?: return
        val p = renderer.project(t.x, t.centerY, t.z)
        if (p != null && p[0] > 0f && p[0] < vw && p[1] > 0f && p[1] < vh) {
            val size = dp(20f) + dp(60f) / (1f + p[2] / 120f)
            ui.stroke.color = Theme.withAlpha(Theme.RED, 0.9f)
            ui.stroke.strokeWidth = dp(1.6f)
            c.drawCircle(p[0], p[1], size, ui.stroke)
            c.drawLine(p[0] - size * 1.4f, p[1], p[0] - size * 0.6f, p[1], ui.stroke)
            c.drawLine(p[0] + size * 0.6f, p[1], p[0] + size * 1.4f, p[1], ui.stroke)
            c.drawLine(p[0], p[1] - size * 1.4f, p[0], p[1] - size * 0.6f, ui.stroke)
            c.drawLine(p[0], p[1] + size * 0.6f, p[0], p[1] + size * 1.4f, ui.stroke)
            val bmp = ui.imageGet(ui.context, "hud_target")
            if (bmp != null) {
                val isz = dp(16f)
                val mp = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
                mp.alpha = 220
                c.drawBitmap(bmp, null,
                    android.graphics.RectF(p[0] - isz / 2f, p[1] - size - isz - dp(10f), p[0] + isz / 2f, p[1] - size - dp(10f)), mp)
            }
            ui.text(c, t.label, p[0], p[1] - size - dp(14f), dp(11.5f), Theme.RED, bold = true)
        } else {
            // فلش جهت هدف در لبه صفحه
            val bearing = mission.bearingToTarget()
            val cx = vw / 2f
            val cy = vh / 2f
            val r = Math.min(vw, vh) * 0.36f
            val px = cx + Math.sin(bearing.toDouble()).toFloat() * r
            val py = cy - dp(30f)
            ui.fill.color = Theme.withAlpha(Theme.RED, 0.85f)
            ui.fill.shader = null
            c.drawCircle(px, py, dp(6f), ui.fill)
            ui.text(c, if (bearing > 0) "هدف در سمت راست" else "هدف در سمت چپ", cx, cy - dp(60f),
                dp(12f), Theme.RED)
        }
    }

    // ---- HUD اصلی
    private fun drawHud(c: Canvas) {
        val pad = dp(14f)

        // ===== بالا چپ: نقشه کوچک
        val mapR = dp(52f)
        val mapCx = pad + mapR
        val mapCy = pad + mapR
        drawMiniMap(c, mapCx, mapCy, mapR)

        // ===== بالا راست: ارتفاع و سرعت
        val rx = vw - dp(70f)
        ui.text(c, "ارتفاع", rx, pad + dp(8f), dp(10.5f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        ui.text(c, Fa.num(mission.altitude, 0) + " م", rx, pad + dp(26f), dp(17f), Theme.TEXT,
            bold = true, align = Paint.Align.RIGHT)
        ui.text(c, "سرعت", rx, pad + dp(50f), dp(10.5f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        ui.text(c, Fa.num(mission.speedKmh, 0), rx, pad + dp(68f), dp(17f),
            if (mission.boost) Theme.ORANGE else Theme.TEXT, bold = true, align = Paint.Align.RIGHT)
        ui.text(c, "کیلومتر/ساعت", rx, pad + dp(84f), dp(9.5f), Theme.TEXT_FAINT, align = Paint.Align.RIGHT)

        // ===== وسط بالا: فاصله تا هدف و زمان
        val topCx = vw / 2f
        ui.panel(c, topCx - dp(90f), dp(10f), dp(180f), dp(42f),
            Theme.withAlpha(Theme.BG_DEEP, 0.55f), Theme.withAlpha(Theme.MINT, 0.35f), dp(10f))
        ui.text(c, "فاصله تا هدف", topCx, dp(21f), dp(10f), Theme.TEXT_DIM)
        ui.text(c, Fa.meters(mission.distanceToTarget()), topCx, dp(39f), dp(15f), Theme.MINT, bold = true)

        if (level.timeLimit > 0f) {
            val danger = mission.timeLeft < 15f
            ui.text(c, "زمان: " + Fa.clock(mission.timeLeft), topCx, dp(64f), dp(13f),
                if (danger) Theme.RED else Theme.TEXT, bold = true)
        }

        // ===== وسط پایین: نوار سوخت
        val fw = dp(220f)
        val fx0 = topCx - fw / 2f
        val fy = vh - dp(30f)
        val fuel = mission.fuel01
        val fuelColor = when {
            fuel < 0.18f -> Theme.RED
            fuel < 0.4f -> Theme.AMBER
            else -> Theme.MINT
        }
        ui.text(c, "سوخت / برد باقی‌مانده", topCx, fy - dp(16f), dp(10f), Theme.TEXT_DIM)
        ui.bar(c, fx0, fy, fw, dp(9f), fuel, fuelColor)
        ui.text(c, Fa.percent(fuel), topCx, fy + dp(20f), dp(11f), fuelColor)

        // ===== وضعیت ماموریت
        val infoY = vh - dp(78f)
        ui.text(c, "اهداف: " + mission.targetsInfo, topCx - dp(120f), infoY, dp(11.5f), Theme.TEXT_DIM)
        ui.text(c, "بونوس: " + mission.bonusInfo, topCx + dp(120f), infoY, dp(11.5f), Theme.AMBER)
        if (mode == MissionMode.ENDLESS) {
            ui.text(c, "موج " + Fa.num(mission.wave), topCx, infoY - dp(18f), dp(12f), Theme.ORANGE, bold = true)
        }

        // ===== هشدارها
        var wy = mapCy + mapR + dp(24f)
        if (mission.radarLock) {
            val blink = (Math.sin((time * 8f).toDouble()).toFloat() + 1f) / 2f
            ui.chip(c, dp(14f) + dp(58f), wy, "شناسایی رادار", Theme.withAlpha(Theme.BLUE, 0.5f + blink * 0.5f))
            wy += dp(28f)
        }
        if (mission.ewActive) {
            ui.chip(c, dp(14f) + dp(58f), wy, "اخلال سیگنال", Theme.VIOLET)
            wy += dp(28f)
        }
        if (mission.damage > 0) {
            ui.chip(c, dp(14f) + dp(58f), wy, "آسیب " + Fa.num(mission.damage) + " از " + Fa.num(mission.armor), Theme.RED)
            wy += dp(28f)
        }
        if (mission.fuel01 < 0.2f) {
            ui.chip(c, dp(14f) + dp(58f), wy, "سوخت کم", Theme.AMBER)
        }

        // ===== نشانه‌گیری در نمای دوربین پهباد
        if (mission.fpv) {
            val cx = vw / 2f
            val cy = vh / 2f
            ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.75f)
            ui.stroke.strokeWidth = dp(1.2f)
            c.drawCircle(cx, cy, dp(16f), ui.stroke)
            c.drawLine(cx - dp(26f), cy, cx - dp(20f), cy, ui.stroke)
            c.drawLine(cx + dp(20f), cy, cx + dp(26f), cy, ui.stroke)
            c.drawLine(cx, cy - dp(26f), cx, cy - dp(20f), ui.stroke)
            c.drawLine(cx, cy + dp(20f), cx, cy + dp(26f), ui.stroke)
            ui.text(c, "دوربین نوک پهباد", cx, vh - dp(56f), dp(10.5f), Theme.withAlpha(Theme.MINT, 0.7f))
        }

        // دکمه توقف
        ui.panel(c, pauseRect, Theme.withAlpha(Theme.BG_DEEP, 0.55f), Theme.withAlpha(Theme.TEXT, 0.25f), dp(9f))
        ui.fill.color = Theme.TEXT
        ui.fill.shader = null
        val pcx = pauseRect.centerX()
        val pcy = pauseRect.centerY()
        c.drawRect(pcx - dp(6f), pcy - dp(8f), pcx - dp(2f), pcy + dp(8f), ui.fill)
        c.drawRect(pcx + dp(2f), pcy - dp(8f), pcx + dp(6f), pcy + dp(8f), ui.fill)
    }

    // ---- نقشه کوچک
    private fun drawMiniMap(c: Canvas, cx: Float, cy: Float, r: Float) {
        ui.fill.color = Theme.withAlpha(Theme.BG_DEEP, 0.68f)
        ui.fill.shader = null
        c.drawCircle(cx, cy, r, ui.fill)
        ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.5f)
        ui.stroke.strokeWidth = dp(1.2f)
        c.drawCircle(cx, cy, r, ui.stroke)

        val range = 700f
        val scale = r / range
        c.save()
        val clip = RectF(cx - r, cy - r, cx + r, cy + r)
        c.clipRect(clip)
        c.rotate(Math.toDegrees(mission.yaw.toDouble()).toFloat(), cx, cy)

        fun mapX(wx: Float) = cx + (wx - mission.x) * scale
        fun mapY(wz: Float) = cy - (wz - mission.z) * scale

        for (e in mission.world.ewZones) {
            ui.fill.color = Theme.withAlpha(Theme.VIOLET, 0.22f)
            c.drawCircle(mapX(e.x), mapY(e.z), e.radius * scale, ui.fill)
        }
        for (rz in mission.world.radars) {
            ui.stroke.color = Theme.withAlpha(Theme.BLUE, 0.55f)
            ui.stroke.strokeWidth = dp(1f)
            c.drawCircle(mapX(rz.x), mapY(rz.z), rz.radius * scale, ui.stroke)
        }
        for (a in mission.world.aaSites) {
            if (!a.alive) continue
            ui.fill.color = Theme.withAlpha(Theme.RED, 0.16f)
            c.drawCircle(mapX(a.x), mapY(a.z), a.range * scale, ui.fill)
            ui.fill.color = Theme.RED
            c.drawCircle(mapX(a.x), mapY(a.z), dp(2.4f), ui.fill)
        }
        for (b in mission.world.bonuses) {
            if (b.collected) continue
            ui.fill.color = Theme.AMBER
            c.drawCircle(mapX(b.x), mapY(b.z), dp(1.8f), ui.fill)
        }
        for (t in mission.world.targets) {
            ui.fill.color = if (t.destroyed) Theme.TEXT_FAINT else Theme.AMBER
            c.drawCircle(mapX(t.x), mapY(t.z), dp(3.4f), ui.fill)
        }
        c.restore()

        // پهباد در مرکز
        Deco.droneIcon(this, c, cx, cy, dp(7f), Theme.MINT, 0f)
        ui.text(c, "نقشه تاکتیکی", cx, cy + r + dp(10f), dp(9.5f), Theme.TEXT_FAINT)
    }

    // ---- کنترل‌ها
    private fun drawControls(c: Canvas) {
        // جوی‌استیک
        if (!save.tiltControl) {
            val baseX = if (joyPointer >= 0) joyBaseX else dp(90f)
            val baseY = if (joyPointer >= 0) joyBaseY else vh - dp(90f)
            val r = dp(58f)
            ui.stroke.color = Theme.withAlpha(Theme.TEXT, 0.28f)
            ui.stroke.strokeWidth = dp(1.4f)
            c.drawCircle(baseX, baseY, r, ui.stroke)
            val kx = if (joyPointer >= 0) (joyX).coerceIn(baseX - r, baseX + r) else baseX
            val ky = if (joyPointer >= 0) (joyY).coerceIn(baseY - r, baseY + r) else baseY
            ui.fill.color = Theme.withAlpha(Theme.MINT, 0.4f)
            ui.fill.shader = null
            c.drawCircle(kx, ky, dp(22f), ui.fill)
        } else {
            ui.text(c, "کنترل با تیلت گوشی", dp(90f), vh - dp(90f), dp(11f),
                Theme.withAlpha(Theme.TEXT_DIM, 0.7f))
        }

        // دکمه دوربین
        ui.panel(c, camRect, Theme.withAlpha(Theme.BG_DEEP, 0.55f), Theme.withAlpha(Theme.MINT, 0.5f), dp(12f))
        ui.text(c, if (mission.fpv) "نمای سوم شخص" else "دوربین پهباد",
            camRect.centerX(), camRect.centerY(), dp(11f), Theme.MINT, bold = true)

        // دکمه بوست
        val boostOn = mission.boost
        ui.panel(c, boostRect, Theme.withAlpha(if (boostOn) Theme.ORANGE else Theme.BG_DEEP, if (boostOn) 0.6f else 0.55f),
            Theme.withAlpha(Theme.ORANGE, 0.7f), dp(14f))
        ui.text(c, "بوست", boostRect.centerX(), boostRect.centerY() - dp(6f), dp(13f), Theme.ORANGE, bold = true)
        ui.text(c, "نگه دارید", boostRect.centerX(), boostRect.centerY() + dp(12f), dp(9.5f), Theme.TEXT_DIM)

        // نشانگر گاز
        val tx = vw - dp(96f)
        val ty0 = vh * 0.32f
        val th = vh * 0.3f
        ui.stroke.color = Theme.withAlpha(Theme.TEXT, 0.22f)
        ui.stroke.strokeWidth = dp(1.2f)
        c.drawLine(tx, ty0, tx, ty0 + th, ui.stroke)
        val knobY = ty0 + th * (1f - mission.throttle)
        ui.fill.color = Theme.MINT
        ui.fill.shader = null
        c.drawCircle(tx, knobY, dp(5f), ui.fill)
        ui.text(c, "گاز", tx, ty0 - dp(12f), dp(10f), Theme.TEXT_DIM)
    }

    // ---- پیام‌ها
    private fun drawToasts(c: Canvas) {
        var y = vh * 0.30f
        for (t in mission.toasts) {
            val alpha = t.life.coerceIn(0f, 1f)
            ui.text(c, t.text, vw / 2f, y, dp(15f), t.color, bold = true, alpha = alpha)
            y += dp(24f)
        }
    }

    private fun drawPreLaunch(c: Canvas) {
        ui.fill.color = Theme.withAlpha(Theme.BG_DEEP, 0.45f)
        ui.fill.shader = null
        c.drawRect(0f, 0f, vw, vh, ui.fill)
        val cx = vw / 2f
        ui.text(c, "آماده پرتاب", cx, vh * 0.36f, dp(26f), Theme.MINT, bold = true)
        ui.text(c, "هدف: " + (mission.world.activeTarget()?.label ?: level.targetName),
            cx, vh * 0.44f, dp(14f), Theme.TEXT)
        val hint = if (save.tiltControl) "با تیلت گوشی هدایت کنید" else "با جوی‌استیک سمت چپ هدایت کنید"
        ui.text(c, hint, cx, vh * 0.51f, dp(12.5f), Theme.TEXT_DIM)
        val a = 0.4f + 0.6f * ((Math.sin((time * 3.4f).toDouble()).toFloat() + 1f) / 2f)
        ui.text(c, "برای پرتاب ضربه بزنید", cx, vh * 0.62f, dp(17f), Theme.AMBER, bold = true, alpha = a)
        ui.text(c, "پهبادهای باقی‌مانده: " + Fa.num(mission.dronesLeft), cx, vh * 0.70f, dp(12f), Theme.TEXT_DIM)
    }

    private fun drawPauseOverlay(c: Canvas) {
        ui.fill.color = Theme.withAlpha(Theme.BG_DEEP, 0.82f)
        ui.fill.shader = null
        c.drawRect(0f, 0f, vw, vh, ui.fill)
        ui.text(c, "ماموریت متوقف شد", vw / 2f, vh * 0.28f, dp(24f), Theme.TEXT, bold = true)
        ui.text(c, level.title, vw / 2f, vh * 0.35f, dp(13f), Theme.TEXT_DIM)
        drawButtons(c)
    }

    private fun drawFinishBanner(c: Canvas) {
        val res = result ?: return
        val a = (finishTimer / 0.5f).coerceIn(0f, 1f)
        ui.fill.color = Theme.withAlpha(Theme.BG_DEEP, 0.55f * a)
        ui.fill.shader = null
        c.drawRect(0f, 0f, vw, vh, ui.fill)
        val color = if (res.success) Theme.MINT else Theme.RED
        ui.text(c, if (res.success) "ماموریت موفق" else "ماموریت ناموفق", vw / 2f, vh * 0.42f,
            dp(30f), color, bold = true, alpha = a)
        ui.text(c, res.reason, vw / 2f, vh * 0.52f, dp(14f), Theme.TEXT_DIM, alpha = a)
    }
}
