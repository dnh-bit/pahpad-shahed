package ir.shahed.pahpad.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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
    private val mapClip = android.graphics.Path()
    private var explosionShake = 0f
    private var shakeDuration = .3f
    private var shakePower = 0f
    private var visualFlash = 0f
    private var registeredSensor = false
    private val useTilt: Boolean get() = save.tiltControl && accelerometer != null

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
            shakeDuration = (.18f + p * .06f).coerceIn(.2f, .38f)
            explosionShake = shakeDuration
            shakePower = kotlin.math.sqrt(p.coerceIn(.25f, 3f))
            visualFlash = .05f
            mission.shake = 0f
            mission.flash = 0f
            renderer.fx.debris(x, y, z)
            audio.explosion(p)
            audio.vibrate(90)
        }
        // The original Mission RNG still advances unchanged; visual emission is time-based.
        mission.onSmoke = null
        mission.onTracer = { x, y, z -> renderer.fx.tracer(x, y, z) }
        mission.onLaunch = {
            audio.launch()
            audio.startEngine()
            calibrated = false
        }
        mission.onHit = {
            hitFlash = .45f
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
        cancelTouches()
        renderer.release()
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
        if (useTilt && !registeredSensor)
            registeredSensor = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun unregisterSensor() {
        sensorManager?.unregisterListener(this)
        registeredSensor = false
    }

    override fun onBack(): Boolean {
        if (finished) return true
        setPaused(!paused)
        return true
    }

    private fun setPaused(value: Boolean) {
        paused = value
        calibrated = false
        resume.visible = value
        retry.visible = value
        quit.visible = value
        if (value) {
            cancelTouches()
            audio.stopEngine()
        }
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
            if (mission.fpv) renderer.fx.clearEngineSmoke()
            audio.click()
            return true
        }
        if (boostRect.contains(x, y)) {
            if (boostPointer >= 0) return true
            boostPointer = pointerId
            mission.boost = true
            return true
        }
        if (x < vw * 0.45f) {
            if (useTilt) return true
            if (joyPointer >= 0) return true
            joyPointer = pointerId
            joyBaseX = x
            joyBaseY = y
            joyX = x
            joyY = y
            return true
        }
        if (throttlePointer >= 0) return true
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

    override fun onTouchCancel() {
        joyPointer = -1; throttlePointer = -1; boostPointer = -1
        joyX = joyBaseX; joyY = joyBaseY
        mission.steerX = 0f; mission.steerY = 0f; mission.boost = false
    }

    // ------------------------------------------------------------ به‌روزرسانی

    override fun update(dt: Float) {
        if (paused) return
        explosionShake = (explosionShake - dt).coerceAtLeast(0f)
        hitFlash = (hitFlash - dt).coerceAtLeast(0f)
        visualFlash = (visualFlash - dt).coerceAtLeast(0f)

        if (mission.phase == Mission.Phase.FLYING) {
            if (useTilt) {
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
            audio.setEngineIntensity((mission.speedKmh / (mission.maxSpeedKmh * 1.28f)).coerceIn(0f, 1f))
        } else {
            mission.steerX = 0f
            mission.steerY = 0f
        }

        renderer.update(dt, mission)
        mission.update(dt)
        if (mission.phase != Mission.Phase.FLYING) audio.stopEngine()

        if (finished) {
            finishTimer += dt
            if (finishTimer > 2.2f && !leaving) {
                val res = result
                if (res != null) {
                    leaving = true
                    post { if (game.currentScreen() === this) game.show(ResultScreen(game, level, mode, res, seed)) }
                }
            }
        }
    }

    // ------------------------------------------------------------ دوربین

    private fun setupCamera() {
        val cam = renderer.scene.cam
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

    // ---- لایه‌های پس‌پردازش (یک‌بار برای هر اندازهٔ صفحه ساخته می‌شوند)
    private var vignette: RadialGradient? = null
    private var hurt: RadialGradient? = null
    private var scan: BitmapShader? = null
    private var overlayW = 0f
    private var overlayH = 0f
    private var hitFlash = 0f
    private val tri = Path()

    private fun ensureOverlays() {
        if (vignette != null && overlayW == vw && overlayH == vh) return
        overlayW = vw
        overlayH = vh
        val r = Math.hypot((vw / 2f).toDouble(), (vh / 2f).toDouble()).toFloat().coerceAtLeast(1f)
        vignette = RadialGradient(vw / 2f, vh / 2f, r, intArrayOf(0x00000000, 0x00000000, 0x70000000),
            floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        hurt = RadialGradient(vw / 2f, vh / 2f, r, intArrayOf(0x00000000, 0x00000000, 0xD0B3141B.toInt()),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        if (scan == null) {
            val n = maxOf(3, dp(3f).toInt())
            val bmp = Bitmap.createBitmap(1, n, Bitmap.Config.ARGB_8888)
            bmp.setPixel(0, 0, 0x30000000)
            scan = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
    }

    override fun render(c: Canvas) {
        setupCamera()
        ensureOverlays()

        val fade = (explosionShake / shakeDuration).coerceIn(0f, 1f)
        val shake = if (paused) 0f else maxOf(mission.shake * .65f, fade * fade * shakePower)
        c.save()
        if (shake > 0f) {
            val amp = dp(7f) * shake
            c.translate(kotlin.math.sin(time * 91f) * amp, kotlin.math.sin(time * 113f + .6f) * amp)
        }
        // چرخش تصویر برای حس بنک
        val rollDeg = Math.toDegrees(renderer.scene.cam.roll.toDouble()).toFloat()
        c.save()
        c.rotate(rollDeg, vw / 2f, vh / 2f)
        renderer.draw(c, mission, vw, vh)
        if (mission.fpv && mission.phase == Mission.Phase.FLYING) drawPitchLadder(c)
        drawTargetMarkers(c)
        c.restore()
        c.restore()

        drawPost(c)
        if (visualFlash > 0f) {
            ui.fill.color = Theme.withAlpha(0xFFFFEAC8.toInt(), visualFlash / .05f * .48f)
            ui.fill.shader = null
            c.drawRect(0f, 0f, vw, vh, ui.fill)
        }
        drawSignalNoise(c)
        if (mission.fpv) drawFpvFrame(c)
        drawHud(c)
        drawControls(c)
        drawToasts(c)

        if (mission.phase == Mission.Phase.READY) drawPreLaunch(c)
        if (paused) drawPauseOverlay(c)
        if (finished) drawFinishBanner(c)
    }

    /** Vignette + damage edge glow. */
    private fun drawPost(c: Canvas) {
        ui.fill.shader = vignette
        ui.fill.alpha = 255
        c.drawRect(0f, 0f, vw, vh, ui.fill)
        val dmg = if (mission.armor > 0) mission.damage.toFloat() / mission.armor.toFloat() else 0f
        val pulse = .7f + .3f * kotlin.math.sin(time * 5f)
        val a = (hitFlash * 1.8f + dmg * .38f * pulse).coerceIn(0f, 1f)
        if (a > .01f) {
            ui.fill.shader = hurt
            ui.fill.alpha = (a * 255f).toInt()
            c.drawRect(0f, 0f, vw, vh, ui.fill)
        }
        ui.fill.shader = null
        ui.fill.alpha = 255
    }

    // ---- اخلال سیگنال
    private fun drawSignalNoise(c: Canvas) {
        val s = mission.signalLoss
        if (s < 0.12f) return
        ui.fill.shader = null
        val bands = (s * 16f).toInt()
        for (i in 0 until bands) {
            val y = rnd.nextFloat() * vh
            val h = dp(1.5f) + rnd.nextFloat() * dp(9f)
            val shift = (rnd.nextFloat() - .5f) * dp(30f) * s
            ui.fill.color = Theme.withAlpha(if (i % 3 == 0) 0xFFFFFFFF.toInt() else Theme.VIOLET, 0.04f + rnd.nextFloat() * 0.12f * s)
            c.drawRect(shift, y, vw + shift, y + h, ui.fill)
        }
        ui.fill.color = Theme.withAlpha(Theme.VIOLET, s * 0.10f)
        c.drawRect(0f, 0f, vw, vh, ui.fill)
        if (s > .45f) {
            val a = ((s - .45f) * 2f).coerceIn(0f, 1f) * (.6f + .4f * kotlin.math.sin(time * 18f))
            ui.text(c, "سیگنال ضعیف", vw / 2f, vh * .22f, dp(14f), Theme.VIOLET, bold = true, alpha = a)
        }
    }

    // ---- نمای دوربین نوک: خطوط اسکن، قاب و نشانگرها
    private fun drawFpvFrame(c: Canvas) {
        ui.fill.shader = scan
        ui.fill.alpha = 255
        c.drawRect(0f, 0f, vw, vh, ui.fill)
        ui.fill.shader = null
        ui.fill.color = Theme.withAlpha(0xFF1D3B33.toInt(), .10f)
        c.drawRect(0f, 0f, vw, vh, ui.fill)
        val m = dp(26f)
        val l = dp(34f)
        ui.stroke.color = Theme.withAlpha(Theme.MINT, .55f)
        ui.stroke.strokeWidth = dp(1.6f)
        // گوشه‌های قاب
        c.drawLine(m, m, m + l, m, ui.stroke); c.drawLine(m, m, m, m + l, ui.stroke)
        c.drawLine(vw - m, m, vw - m - l, m, ui.stroke); c.drawLine(vw - m, m, vw - m, m + l, ui.stroke)
        c.drawLine(m, vh - m, m + l, vh - m, ui.stroke); c.drawLine(m, vh - m, m, vh - m - l, ui.stroke)
        c.drawLine(vw - m, vh - m, vw - m - l, vh - m, ui.stroke); c.drawLine(vw - m, vh - m, vw - m, vh - m - l, ui.stroke)
        // نشانگر مرکز
        val cx = vw / 2f
        val cy = vh / 2f
        ui.stroke.strokeWidth = dp(1.3f)
        ui.stroke.color = Theme.withAlpha(Theme.MINT, .85f)
        c.drawLine(cx - dp(24f), cy, cx - dp(8f), cy, ui.stroke)
        c.drawLine(cx + dp(8f), cy, cx + dp(24f), cy, ui.stroke)
        c.drawLine(cx, cy + dp(8f), cx, cy + dp(18f), ui.stroke)
        ui.fill.color = Theme.MINT
        c.drawCircle(cx, cy, dp(1.8f), ui.fill)
        // REC
        if ((time * 1.2f) % 1f < .6f) {
            ui.fill.color = Theme.RED
            c.drawCircle(vw - m - dp(64f), m + dp(16f), dp(4f), ui.fill)
        }
        ui.text(c, "EO/IR  " + Fa.clock(mission.elapsed), vw - m - dp(12f), m + dp(16f), dp(10f),
            Theme.withAlpha(Theme.MINT, .8f), align = Paint.Align.RIGHT)
        ui.text(c, "دوربین نوک پهباد", cx, vh - dp(112f), dp(10.5f), Theme.withAlpha(Theme.MINT, 0.7f))
    }

    // ---- نردبان زاویه در نمای اول‌شخص (داخل بوم چرخیده تا با افق هم‌راستا بماند)
    private fun drawPitchLadder(c: Canvas) {
        val cam = renderer.scene.cam
        val cx = vw / 2f
        ui.stroke.strokeWidth = dp(1.1f)
        for (k in -6..6) {
            if (k == 0) continue
            val e = Math.toRadians(k * 10.0).toFloat()
            val a = e - cam.pitch
            if (kotlin.math.abs(a) > 1.2f) continue
            val y = cam.screenCy - cam.focal * kotlin.math.tan(a)
            if (y < vh * .14f || y > vh * .86f) continue
            val half = dp(if (k % 3 == 0) 62f else 46f)
            val gap = dp(30f)
            ui.stroke.color = Theme.withAlpha(Theme.MINT, if (k > 0) .55f else .38f)
            if (k > 0) {
                c.drawLine(cx - half, y, cx - gap, y, ui.stroke)
                c.drawLine(cx + gap, y, cx + half, y, ui.stroke)
            } else {
                // زیر افق: خط‌چین
                val seg = (half - gap) / 5f
                var x = gap
                while (x < half) {
                    c.drawLine(cx - x, y, cx - x - seg * .6f, y, ui.stroke)
                    c.drawLine(cx + x, y, cx + x + seg * .6f, y, ui.stroke)
                    x += seg
                }
            }
            val tick = if (k > 0) dp(5f) else -dp(5f)
            c.drawLine(cx - half, y, cx - half, y + tick, ui.stroke)
            c.drawLine(cx + half, y, cx + half, y + tick, ui.stroke)
            ui.text(c, Fa.num(kotlin.math.abs(k * 10)), cx - half - dp(13f), y, dp(9f), Theme.withAlpha(Theme.MINT, .7f))
        }
        val hy = cam.horizonY()
        ui.stroke.color = Theme.withAlpha(Theme.MINT, .5f)
        ui.stroke.strokeWidth = dp(1.4f)
        c.drawLine(cx - vw * .34f, hy, cx - dp(40f), hy, ui.stroke)
        c.drawLine(cx + dp(40f), hy, cx + vw * .34f, hy, ui.stroke)
    }

    // ---- نشانگر هدف
    private fun drawTargetMarkers(c: Canvas) {
        val t = mission.world.activeTarget() ?: return
        val p = renderer.project(t.x, t.centerY, t.z)
        val margin = dp(36f)
        if (p != null && p[0] > margin && p[0] < vw - margin && p[1] > margin && p[1] < vh - margin) {
            val px = p[0]
            val py = p[1]
            val dist = mission.distanceToTarget()
            val size = dp(16f) + dp(54f) / (1f + p[2] / 110f)
            val close = dist < 320f
            val pulse = (kotlin.math.sin(time * (if (close) 12f else 4f)) + 1f) / 2f
            val color = if (close) Theme.mix(Theme.RED, Theme.AMBER, pulse) else Theme.RED
            ui.stroke.color = Theme.withAlpha(color, .92f)
            ui.stroke.strokeWidth = dp(2f)
            val arm = size * .45f
            val s2 = size * (1f + (if (close) .12f else .05f) * pulse)
            // چهار براکت گوشه
            for (sx in -1..1 step 2) for (sy in -1..1 step 2) {
                val cx = px + sx * s2
                val cy = py + sy * s2
                c.drawLine(cx, cy, cx - sx * arm, cy, ui.stroke)
                c.drawLine(cx, cy, cx, cy - sy * arm, ui.stroke)
            }
            // لوزی مرکز
            tri.rewind()
            val dd = dp(5f)
            tri.moveTo(px, py - dd); tri.lineTo(px + dd, py); tri.lineTo(px, py + dd); tri.lineTo(px - dd, py); tri.close()
            ui.stroke.strokeWidth = dp(1.4f)
            c.drawPath(tri, ui.stroke)
            ui.text(c, t.label, px, py - s2 - dp(12f), dp(11.5f), color, bold = true)
            ui.text(c, Fa.meters(dist), px, py + s2 + dp(12f), dp(10.5f), Theme.withAlpha(Theme.TEXT, .9f))
            if (close) ui.text(c, "قفل روی هدف", px, py + s2 + dp(28f), dp(10f), color, bold = true, alpha = .6f + .4f * pulse)
        } else {
            // فلش لبهٔ صفحه به سمت هدف
            val cx = vw / 2f
            val cy = vh / 2f
            var dx: Float
            var dy: Float
            if (p != null) {
                dx = p[0] - cx; dy = p[1] - cy
            } else {
                val b = mission.bearingToTarget()
                dx = if (b >= 0f) 1f else -1f
                dy = .25f
            }
            val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(.001f)
            dx /= len; dy /= len
            val rx = vw * .42f
            val ry = vh * .36f
            val k = 1f / Math.sqrt(((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry)).toDouble()).toFloat()
            val ax = cx + dx * k
            val ay = cy + dy * k
            val sz = dp(13f)
            val nx = -dy
            val ny = dx
            tri.rewind()
            tri.moveTo(ax + dx * sz, ay + dy * sz)
            tri.lineTo(ax - dx * sz * .6f + nx * sz * .8f, ay - dy * sz * .6f + ny * sz * .8f)
            tri.lineTo(ax - dx * sz * .2f, ay - dy * sz * .2f)
            tri.lineTo(ax - dx * sz * .6f - nx * sz * .8f, ay - dy * sz * .6f - ny * sz * .8f)
            tri.close()
            val a = .65f + .35f * ((kotlin.math.sin(time * 6f) + 1f) / 2f)
            ui.fill.shader = null
            ui.fill.color = Theme.withAlpha(Theme.RED, a)
            c.drawPath(tri, ui.fill)
            ui.text(c, Fa.meters(mission.distanceToTarget()), ax - dx * dp(30f), ay - dy * dp(30f), dp(10.5f), Theme.RED, bold = true)
        }
    }

    // ---- HUD اصلی
    private fun hudPanel(c: Canvas, x: Float, y: Float, w: Float, h: Float, accent: Int) {
        ui.panel(c, x, y, w, h, Theme.withAlpha(Theme.BG_DEEP, .62f), Theme.withAlpha(accent, .28f), dp(10f))
        ui.fill.color = Theme.withAlpha(accent, .8f)
        ui.fill.shader = null
        c.drawRect(x + dp(10f), y, x + w - dp(10f), y + dp(1.6f), ui.fill)
    }

    private fun drawHeadingTape(c: Canvas, cx: Float, top: Float) {
        val tw = dp(250f)
        val th = dp(28f)
        val left = cx - tw / 2f
        hudPanel(c, left, top, tw, th, Theme.MINT)
        val heading = Math.toDegrees(mission.yaw.toDouble()).toFloat()
        val pxPerDeg = tw / 110f
        c.save()
        c.clipRect(left + dp(6f), top, left + tw - dp(6f), top + th)
        var d = (kotlin.math.floor((heading - 60f) / 5f) * 5f).toInt()
        ui.stroke.strokeWidth = dp(1f)
        while (d <= heading + 60f) {
            val x = cx + (d - heading) * pxPerDeg
            val major = ((d % 30) + 30) % 30 == 0
            ui.stroke.color = Theme.withAlpha(Theme.TEXT, if (major) .75f else .3f)
            c.drawLine(x, top + th - dp(if (major) 9f else 5f), x, top + th - dp(2f), ui.stroke)
            if (major) {
                val deg = ((d % 360) + 360) % 360
                ui.text(c, Fa.num(deg), x, top + dp(10f), dp(9f), Theme.TEXT_DIM)
            }
            d += 5
        }
        if (mission.world.activeTarget() != null) {
            val b = Math.toDegrees(mission.bearingToTarget().toDouble()).toFloat()
            val bx = cx + b.coerceIn(-52f, 52f) * pxPerDeg
            tri.rewind()
            tri.moveTo(bx, top + th - dp(3f)); tri.lineTo(bx - dp(5f), top + th - dp(11f)); tri.lineTo(bx + dp(5f), top + th - dp(11f)); tri.close()
            ui.fill.color = Theme.RED
            c.drawPath(tri, ui.fill)
        }
        c.restore()
        tri.rewind()
        tri.moveTo(cx, top + th + dp(1f)); tri.lineTo(cx - dp(5f), top + th + dp(7f)); tri.lineTo(cx + dp(5f), top + th + dp(7f)); tri.close()
        ui.fill.color = Theme.MINT
        c.drawPath(tri, ui.fill)
    }

    private fun drawHud(c: Canvas) {
        val pad = dp(14f)

        // ===== بالا چپ: نقشه کوچک
        val mapR = dp(50f)
        val mapCx = pad + mapR
        val mapCy = pad + mapR
        drawMiniMap(c, mapCx, mapCy, mapR)

        // ===== بالا راست: ارتفاع و سرعت
        val pw = dp(112f)
        val px = pauseRect.left - dp(8f) - pw
        val py = pad - dp(4f)
        hudPanel(c, px, py, pw, dp(96f), Theme.MINT)
        val rx = px + pw - dp(12f)
        ui.text(c, "ارتفاع", rx, py + dp(13f), dp(10f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        val lowAlt = mission.altitude < 25f
        ui.text(c, Fa.num(mission.altitude, 0) + " م", rx, py + dp(31f), dp(17f),
            if (lowAlt) Theme.AMBER else Theme.TEXT, bold = true, align = Paint.Align.RIGHT)
        // نشانگر اوج/شیرجه
        val vs = kotlin.math.sin(mission.pitch)
        val arrowX = px + dp(16f)
        val arrowY = py + dp(28f)
        tri.rewind()
        if (kotlin.math.abs(vs) > .04f) {
            val dir = if (vs > 0f) -1f else 1f
            tri.moveTo(arrowX, arrowY + dir * dp(7f)); tri.lineTo(arrowX - dp(5f), arrowY - dir * dp(2f)); tri.lineTo(arrowX + dp(5f), arrowY - dir * dp(2f)); tri.close()
            ui.fill.color = if (vs > 0f) Theme.MINT else Theme.AMBER
            ui.fill.shader = null
            c.drawPath(tri, ui.fill)
        }
        ui.text(c, "سرعت", rx, py + dp(54f), dp(10f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        ui.text(c, Fa.num(mission.speedKmh, 0), rx, py + dp(72f), dp(17f),
            if (mission.boost) Theme.ORANGE else Theme.TEXT, bold = true, align = Paint.Align.RIGHT)
        ui.text(c, "کیلومتر/ساعت", px + dp(12f), py + dp(86f), dp(8.5f), Theme.TEXT_FAINT, align = Paint.Align.LEFT)

        // ===== وسط بالا: قطب‌نما و فاصله تا هدف
        val topCx = vw / 2f
        drawHeadingTape(c, topCx, dp(10f))
        ui.text(c, Fa.meters(mission.distanceToTarget()), topCx, dp(52f), dp(15f), Theme.MINT, bold = true)
        if (level.timeLimit > 0f) {
            val danger = mission.timeLeft < 15f
            val a = if (danger) .6f + .4f * kotlin.math.sin(time * 8f) else 1f
            ui.text(c, "زمان " + Fa.clock(mission.timeLeft), topCx, dp(72f), dp(12.5f),
                if (danger) Theme.RED else Theme.TEXT, bold = true, alpha = a)
        }

        // ===== وسط پایین: نوار سوخت قطعه‌قطعه
        val fw = dp(220f)
        val fx0 = topCx - fw / 2f
        val fy = vh - dp(30f)
        hudPanel(c, fx0 - dp(12f), fy - dp(27f), fw + dp(24f), dp(52f), Theme.MINT)
        val fuel = mission.fuel01
        val fuelColor = when {
            fuel < 0.18f -> Theme.RED
            fuel < 0.4f -> Theme.AMBER
            else -> Theme.MINT
        }
        ui.text(c, "سوخت", fx0 + fw, fy - dp(15f), dp(10f), Theme.TEXT_DIM, align = Paint.Align.RIGHT)
        ui.text(c, Fa.percent(fuel), fx0, fy - dp(15f), dp(10.5f), fuelColor, bold = true, align = Paint.Align.LEFT)
        val segs = 20
        val gap = dp(2f)
        val sw = (fw - gap * (segs - 1)) / segs
        val lit = kotlin.math.ceil(fuel * segs).toInt()
        val blink = fuel < .18f && (time * 3f) % 1f < .5f
        ui.fill.shader = null
        for (i in 0 until segs) {
            val x0 = fx0 + i * (sw + gap)
            ui.fill.color = if (i < lit && !blink) fuelColor else Theme.withAlpha(Theme.TEXT_FAINT, .22f)
            c.drawRect(x0, fy - dp(3f), x0 + sw, fy + dp(7f), ui.fill)
        }

        // ===== وضعیت ماموریت
        val infoY = vh - dp(78f)
        ui.chip(c, topCx - dp(78f), infoY, "اهداف " + mission.targetsInfo, Theme.TEXT_DIM, dp(10.5f))
        ui.chip(c, topCx + dp(78f), infoY, "بونوس " + mission.bonusInfo, Theme.AMBER, dp(10.5f))
        if (mode == MissionMode.ENDLESS) {
            ui.text(c, "موج " + Fa.num(mission.wave), topCx, infoY - dp(22f), dp(12f), Theme.ORANGE, bold = true)
        }

        // ===== هشدارها
        var wy = mapCy + mapR + dp(28f)
        val chipX = pad + mapR
        if (mission.radarLock) {
            val blinkR = (Math.sin((time * 8f).toDouble()).toFloat() + 1f) / 2f
            ui.chip(c, chipX, wy, "شناسایی رادار", Theme.withAlpha(Theme.BLUE, 0.5f + blinkR * 0.5f))
            wy += dp(26f)
        }
        if (mission.ewActive) {
            ui.chip(c, chipX, wy, "اخلال سیگنال", Theme.VIOLET)
            wy += dp(26f)
        }
        if (mission.damage > 0) {
            ui.chip(c, chipX, wy, "آسیب " + Fa.num(mission.damage) + " از " + Fa.num(mission.armor), Theme.RED)
            wy += dp(26f)
        }
        if (mission.fuel01 < 0.2f) {
            ui.chip(c, chipX, wy, "سوخت کم", Theme.AMBER)
            wy += dp(26f)
        }
        if (mission.phase == Mission.Phase.FLYING && lowAlt && mission.pitch < -.05f) {
            val a = .5f + .5f * ((kotlin.math.sin(time * 10f) + 1f) / 2f)
            ui.chip(c, chipX, wy, "ارتفاع کم", Theme.withAlpha(Theme.AMBER, a))
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

    // ---- نقشه کوچک (رو به جلو؛ جهت چرخش اصلاح شد)
    private fun drawMiniMap(c: Canvas, cx: Float, cy: Float, r: Float) {
        ui.fill.color = Theme.withAlpha(Theme.BG_DEEP, 0.66f)
        ui.fill.shader = null
        c.drawCircle(cx, cy, r, ui.fill)

        val range = 700f
        val scale = r / range
        c.save()
        mapClip.rewind()
        mapClip.addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
        c.clipPath(mapClip)
        // حلقه‌های فاصله
        ui.stroke.strokeWidth = dp(1f)
        ui.stroke.color = Theme.withAlpha(Theme.MINT, .12f)
        c.drawCircle(cx, cy, r * .5f, ui.stroke)
        // مخروط دید
        tri.rewind()
        tri.moveTo(cx, cy)
        tri.lineTo(cx - r * .75f, cy - r)
        tri.lineTo(cx + r * .75f, cy - r)
        tri.close()
        ui.fill.color = Theme.withAlpha(Theme.MINT, .07f)
        c.drawPath(tri, ui.fill)
        // Heading-up: rotate the world by -yaw so the drone's heading points up.
        // (It used to rotate by +yaw, which doubled the error while turning.)
        c.rotate(-Math.toDegrees(mission.yaw.toDouble()).toFloat(), cx, cy)

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
        val active = mission.world.activeTarget()
        for (t in mission.world.targets) {
            ui.fill.color = if (t.destroyed) Theme.TEXT_FAINT else Theme.RED
            c.drawCircle(mapX(t.x), mapY(t.z), dp(if (t === active) 4f else 3f), ui.fill)
            if (t === active) {
                ui.stroke.color = Theme.withAlpha(Theme.RED, .5f + .5f * kotlin.math.sin(time * 5f))
                ui.stroke.strokeWidth = dp(1.2f)
                c.drawCircle(mapX(t.x), mapY(t.z), dp(7f), ui.stroke)
            }
        }
        // نشان شمال (محور +Z جهان)
        ui.text(c, "ش", mapX(mission.x), mapY(mission.z + range * .82f), dp(9f), Theme.withAlpha(Theme.TEXT, .7f), bold = true)
        c.restore()

        ui.stroke.color = Theme.withAlpha(Theme.MINT, 0.55f)
        ui.stroke.strokeWidth = dp(1.3f)
        c.drawCircle(cx, cy, r, ui.stroke)
        // پهباد در مرکز
        Deco.droneIcon(this, c, cx, cy, dp(7f), Theme.MINT, 0f)
    }

    // ---- کنترل‌ها
    private fun drawControls(c: Canvas) {
        ui.fill.shader = null
        // جوی‌استیک
        if (!useTilt) {
            val baseX = if (joyPointer >= 0) joyBaseX else dp(90f)
            val baseY = if (joyPointer >= 0) joyBaseY else vh - dp(90f)
            val r = dp(58f)
            val active = joyPointer >= 0
            ui.fill.color = Theme.withAlpha(Theme.BG_DEEP, if (active) .38f else .26f)
            c.drawCircle(baseX, baseY, r, ui.fill)
            ui.stroke.color = Theme.withAlpha(Theme.MINT, if (active) .55f else .3f)
            ui.stroke.strokeWidth = dp(1.4f)
            c.drawCircle(baseX, baseY, r, ui.stroke)
            ui.stroke.color = Theme.withAlpha(Theme.TEXT, .25f)
            for (i in 0 until 4) {
                val ang = i * Math.PI.toFloat() / 2f
                val ca = kotlin.math.cos(ang)
                val sa = kotlin.math.sin(ang)
                c.drawLine(baseX + ca * r * .78f, baseY + sa * r * .78f, baseX + ca * r * .94f, baseY + sa * r * .94f, ui.stroke)
            }
            var kx = baseX
            var ky = baseY
            if (active) {
                val dx = joyX - baseX
                val dy = joyY - baseY
                val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                val k = if (len > r) r / len else 1f
                kx = baseX + dx * k
                ky = baseY + dy * k
            }
            ui.fill.color = Theme.withAlpha(Theme.MINT, if (active) .22f else .12f)
            c.drawCircle(kx, ky, dp(30f), ui.fill)
            ui.fill.color = Theme.withAlpha(Theme.MINT, if (active) .75f else .45f)
            c.drawCircle(kx, ky, dp(19f), ui.fill)
            ui.fill.color = Theme.withAlpha(0xFFFFFFFF.toInt(), .35f)
            c.drawCircle(kx - dp(5f), ky - dp(6f), dp(6f), ui.fill)
        } else {
            ui.text(c, "کنترل با تیلت گوشی", dp(90f), vh - dp(90f), dp(11f),
                Theme.withAlpha(Theme.TEXT_DIM, 0.7f))
        }

        // دکمه دوربین
        ui.panel(c, camRect, Theme.withAlpha(Theme.BG_DEEP, 0.55f), Theme.withAlpha(Theme.MINT, 0.5f), dp(12f))
        ui.text(c, if (mission.fpv) "نمای سوم شخص" else "دوربین پهباد",
            camRect.centerX(), camRect.centerY(), dp(11f), Theme.MINT, bold = true)

        // دکمه بوست (دایره‌ای با هاله)
        val boostOn = mission.boost
        val bcx = boostRect.centerX()
        val bcy = boostRect.centerY()
        val br = boostRect.width() / 2f
        if (boostOn) {
            ui.fill.color = Theme.withAlpha(Theme.ORANGE, .22f + .1f * kotlin.math.sin(time * 14f))
            c.drawCircle(bcx, bcy, br * 1.25f, ui.fill)
        }
        ui.fill.color = Theme.withAlpha(if (boostOn) Theme.ORANGE else Theme.BG_DEEP, if (boostOn) .7f else .55f)
        c.drawCircle(bcx, bcy, br, ui.fill)
        ui.stroke.color = Theme.withAlpha(Theme.ORANGE, .8f)
        ui.stroke.strokeWidth = dp(1.6f)
        c.drawCircle(bcx, bcy, br, ui.stroke)
        ui.text(c, "بوست", bcx, bcy - dp(5f), dp(13f), if (boostOn) Theme.BG_DEEP else Theme.ORANGE, bold = true)
        ui.text(c, "نگه دارید", bcx, bcy + dp(12f), dp(9f), if (boostOn) Theme.BG_DEEP else Theme.TEXT_DIM)

        // اسلایدر گاز
        val tx = vw - dp(112f)
        val ty0 = vh * 0.30f
        val th = vh * 0.30f
        val tw = dp(8f)
        rectTmp.set(tx - tw / 2f, ty0, tx + tw / 2f, ty0 + th)
        ui.fill.color = Theme.withAlpha(Theme.BG_DEEP, .5f)
        c.drawRoundRect(rectTmp, tw / 2f, tw / 2f, ui.fill)
        val knobY = ty0 + th * (1f - mission.throttle)
        rectTmp.set(tx - tw / 2f, knobY, tx + tw / 2f, ty0 + th)
        ui.fill.color = Theme.withAlpha(if (mission.boost) Theme.ORANGE else Theme.MINT, .7f)
        c.drawRoundRect(rectTmp, tw / 2f, tw / 2f, ui.fill)
        ui.fill.color = Theme.TEXT
        c.drawCircle(tx, knobY, dp(7f), ui.fill)
        ui.text(c, "گاز", tx, ty0 - dp(12f), dp(10f), Theme.TEXT_DIM)
        ui.text(c, Fa.percent(mission.throttle), tx, ty0 + th + dp(12f), dp(9.5f), Theme.TEXT_DIM)
    }
    private val rectTmp = RectF()

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
        val hint = if (useTilt) "با تیلت گوشی هدایت کنید" else "با جوی‌استیک سمت چپ هدایت کنید"
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
