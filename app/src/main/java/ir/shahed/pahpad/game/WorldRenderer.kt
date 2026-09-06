package ir.shahed.pahpad.game

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Paint
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme

/**
 * رسم صحنه‌ی سه‌بعدی ماموریت با سبک Low-Poly.
 */
class WorldRenderer(
    private val gfxContext: android.content.Context
) {

    val scene = Scene3D()
    val fx = Fx()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spriteProps = ArrayList<Prop>()
    private val proj = FloatArray(3)
    private var skyShader: Shader? = null
    private var fogShader: Shader? = null
    private var skyKey = 0
    private var fogHeight = 0f
    private val fogMatrix = android.graphics.Matrix()

    /** آسمان و زمین با توجه به خط افق دوربین */
    private fun drawSkyAndGround(c: Canvas, w: Float, h: Float, world: World) {
        val p = world.palette
        val key = p.skyTop xor p.skyBottom
        if (skyShader == null || skyKey != key || fogHeight != h) {
            skyShader = LinearGradient(
                0f, -h, 0f, h * 1.2f,
                intArrayOf(p.skyTop, p.skyBottom), null, Shader.TileMode.CLAMP
            )
            fogHeight = h
            fogShader = LinearGradient(
                0f, 0f, 0f, h * 0.35f,
                intArrayOf(p.fog, p.ground), null, Shader.TileMode.CLAMP
            )
            skyKey = key
        }
        val horizon = scene.cam.horizonY()
        paint.shader = skyShader
        paint.style = Paint.Style.FILL
        c.drawRect(-w, -h, w * 2f, horizon, paint)
        paint.shader = null
        // زمین دور
        paint.color = p.groundFar
        c.drawRect(-w, horizon, w * 2f, h * 2f, paint)
        // مه نزدیک افق
        val fs = fogShader
        if (fs != null) {
            fogMatrix.reset()
            fogMatrix.setTranslate(0f, horizon)
            fs.setLocalMatrix(fogMatrix)
            paint.shader = fs
            c.drawRect(-w, horizon, w * 2f, horizon + h * 0.35f, paint)
            paint.shader = null
        }
    }

    /** شبکه‌ی زمین برای حس سرعت */
    private fun drawGroundGrid(c: Canvas, world: World, camX: Float, camZ: Float) {
        val step = 100f
        val span = 900f
        val gx0 = Math.floor(((camX - span) / step).toDouble()).toFloat() * step
        val gz0 = Math.floor(((camZ - span) / step).toDouble()).toFloat() * step
        val color = world.palette.grid
        var gx = gx0
        while (gx <= camX + span) {
            scene.line(gx, 0.2f, camZ - span, gx, 0.2f, camZ + span, c, color, 1.6f)
            gx += step
        }
        var gz = gz0
        while (gz <= camZ + span) {
            scene.line(camX - span, 0.2f, gz, camX + span, 0.2f, gz, c, color, 1.6f)
            gz += step
        }
    }

    fun draw(c: Canvas, mission: Mission, w: Float, h: Float) {
        val world = mission.world
        scene.fogColor = world.palette.fog
        scene.fogStart = 220f
        scene.fogEnd = 1400f
        scene.begin()

        drawSkyAndGround(c, w, h, world)
        drawGroundGrid(c, world, scene.cam.x, scene.cam.z)

        val camX = scene.cam.x
        val camZ = scene.cam.z
        val maxD2 = 1500f * 1500f

        // ---- عوارض محیط (درخت/صخره به‌صورت اسپرایت در پاسِ بعد از flush رسم می‌شوند)
        spriteProps.clear()
        for (p in world.props) {
            val dx = p.x - camX
            val dz = p.z - camZ
            if (dx * dx + dz * dz > maxD2) continue
            when (p.kind) {
                PropKind.TREE, PropKind.ROCK -> {
                    spriteProps.add(p)
                }
                PropKind.SHIP -> {
                    scene.box(p.x, 0f, p.z, p.w, p.h * 0.5f, p.d, p.color, p.rot)
                    scene.box(p.x, p.h * 0.5f, p.z, p.w * 0.6f, p.h * 0.6f, p.d * 0.3f,
                        Theme.shade(p.color, 1.15f), p.rot)
                }
                else -> scene.box(p.x, 0f, p.z, p.w, p.h, p.d, p.color, p.rot)
            }
        }

        // ---- مناطق رادار و اخلال (حلقه‌های زمینی)
        for (r in world.radars) drawZoneRing(c, r.x, r.z, r.radius, Theme.BLUE)
        for (e in world.ewZones) drawZoneRing(c, e.x, e.z, e.radius, Theme.VIOLET)

        // ---- سامانه‌های پدافندی
        for (a in world.aaSites) {
            if (!a.alive) continue
            val dx = a.x - camX
            val dz = a.z - camZ
            if (dx * dx + dz * dz > maxD2) continue
            drawZoneRing(c, a.x, a.z, a.range, Theme.RED)
            scene.box(a.x, 0f, a.z, 9f, 3.5f, 12f, 0xFF4A4F42.toInt(), 0f)
            scene.box(a.x, 3.5f, a.z, 5.5f, 3f, 6f, 0xFF5E6350.toInt(), a.barrelYaw)
            // لوله‌ها
            val bx = Math.sin(a.barrelYaw.toDouble()).toFloat()
            val bz = Math.cos(a.barrelYaw.toDouble()).toFloat()
            scene.box(a.x + bx * 4f, 5.2f, a.z + bz * 4f, 1.2f, 1.2f, 8f, 0xFF6E7360.toInt(), a.barrelYaw)
            if (a.flash > 0f) {
                fx.spark(a.x + bx * 8f, 6f, a.z + bz * 8f, Theme.AMBER)
            }
        }

        // ---- اهداف
        for (t in world.targets) drawTarget(t)

        // ---- بونوس‌ها
        for (b in world.bonuses) {
            if (b.collected) continue
            val s = 4.5f
            scene.box(b.x, b.y, b.z, s, s, s, Theme.AMBER, b.spin)
        }

        // ---- موشک‌های پدافند
        for (m in world.missiles) {
            scene.box(m.x, m.y, m.z, 1.2f, 1.2f, 4f, Theme.AMBER, 0f)
        }

        scene.flush(c)

        // ---- درخت‌ها و صخره‌ها به‌صورت اسپرایت (مرتب‌شده از دور به نزدیک)
        spriteProps.sortBy { pr ->
            val dxp = pr.x - scene.cam.x; val dzp = pr.z - scene.cam.z
            -(dxp * dxp + dzp * dzp)
        }
        for (p in spriteProps) {
            val isTree = p.kind == PropKind.TREE
            val bmp = Gfx.get(gfxContext, if (isTree) (if (p.h % 2f < 1f) "prop_tree1" else "prop_tree2")
                             else (if (p.w % 2f < 1f) "prop_rock1" else "prop_rock2")) ?: continue
            val sp = project(p.x, p.h * (if (isTree) 0.55f else 0.5f), p.z) ?: continue
            val hgt = p.h * 6.5f * scene.cam.focal / 620f
            val wid = hgt * (bmp.width.toFloat() / bmp.height.toFloat())
            spritePaint.alpha = 235
            c.drawBitmap(bmp, null,
                android.graphics.RectF(sp[0] - wid * 0.5f, sp[1] - hgt * 0.55f, sp[0] + wid * 0.5f, sp[1] + hgt * 0.45f),
                spritePaint)
            spritePaint.alpha = 255
        }

        // ---- پهباد در نمای سوم شخص (اسپرایت، بالای صحنه)
        if (!mission.fpv && mission.phase != Mission.Phase.ENDED) {
            drawDrone(c, mission)
        }

        fx.draw(c, scene)
    }

    private fun drawZoneRing(c: Canvas, x: Float, z: Float, radius: Float, color: Int) {
        val segments = 26
        var prevX = x + radius
        var prevZ = z
        for (i in 1..segments) {
            val a = (i.toFloat() / segments) * (Math.PI * 2).toFloat()
            val nx = x + Math.cos(a.toDouble()).toFloat() * radius
            val nz = z + Math.sin(a.toDouble()).toFloat() * radius
            scene.line(prevX, 0.6f, prevZ, nx, 0.6f, nz, c, Theme.withAlpha(color, 0.55f), 2.2f)
            prevX = nx
            prevZ = nz
        }
    }

    private fun drawTarget(t: Target) {
        val base = if (t.destroyed) 0xFF3A3430.toInt() else 0xFF8A6D4F.toInt()
        val accent = if (t.destroyed) 0xFF2B2724.toInt() else Theme.RED
        when (t.shape) {
            TargetShape.BUNKER -> {
                scene.box(t.x, 0f, t.z, t.w, t.h, t.d, base, t.heading)
                scene.box(t.x, t.h, t.z, t.w * 0.6f, 2.5f, t.d * 0.6f, accent, t.heading)
            }
            TargetShape.BUILDING -> {
                scene.box(t.x, 0f, t.z, t.w, t.h, t.d, base, t.heading)
                scene.box(t.x, t.h, t.z, t.w * 0.45f, 4f, t.d * 0.45f, accent, t.heading)
            }
            TargetShape.TOWER -> {
                scene.box(t.x, 0f, t.z, t.w, t.h * 0.85f, t.d, base, t.heading)
                scene.box(t.x, t.h * 0.85f, t.z, t.w * 1.4f, t.h * 0.15f, t.d * 1.4f, accent, t.heading)
            }
            TargetShape.SHIP -> {
                scene.box(t.x, 0f, t.z, t.w, t.h * 0.45f, t.d, 0xFF3F4A52.toInt(), t.heading)
                scene.box(t.x, t.h * 0.45f, t.z, t.w * 0.6f, t.h * 0.75f, t.d * 0.28f,
                    if (t.destroyed) base else 0xFF56646E.toInt(), t.heading)
                scene.box(t.x, t.h * 1.2f, t.z, 2f, 10f, 2f, accent, t.heading)
            }
            TargetShape.VEHICLE -> {
                scene.box(t.x, 0f, t.z, t.w, t.h * 0.6f, t.d, 0xFF5B5F3E.toInt(), t.heading)
                scene.box(t.x, t.h * 0.6f, t.z, t.w * 0.85f, t.h * 0.5f, t.d * 0.45f, accent, t.heading)
            }
            TargetShape.BRIDGE -> {
                scene.box(t.x, t.h * 0.7f, t.z, t.w, 3.5f, t.d, base, t.heading)
                var i = -2
                while (i <= 2) {
                    scene.box(t.x + i * t.w * 0.2f, 0f, t.z, 5f, t.h * 0.7f, 5f,
                        Theme.shade(base, 0.85f), t.heading)
                    i++
                }
            }
            TargetShape.ANTENNA -> {
                scene.box(t.x, 0f, t.z, t.w * 0.35f, t.h, t.d * 0.35f, base, t.heading)
                scene.box(t.x, t.h * 0.55f, t.z, t.w * 1.6f, 1.5f, t.d * 0.2f, accent, t.heading)
                scene.box(t.x, t.h * 0.8f, t.z, t.w * 1.2f, 1.5f, t.d * 0.2f, accent, t.heading)
                scene.box(t.x, 0f, t.z, t.w * 1.4f, 3f, t.d * 1.4f, Theme.shade(base, 0.8f), t.heading)
            }
            TargetShape.TRAIN -> {
                var i = -2
                while (i <= 2) {
                    val off = i * 30f
                    val ox = Math.sin(t.heading.toDouble()).toFloat() * off
                    val oz = Math.cos(t.heading.toDouble()).toFloat() * off
                    scene.box(t.x + ox, 0f, t.z + oz, t.w, t.h, 26f,
                        if (i == 0) accent else 0xFF4C4F55.toInt(), t.heading)
                    i++
                }
            }
            TargetShape.TUNNEL -> {
                scene.box(t.x, 0f, t.z, t.w * 2.2f, t.h * 1.8f, t.d * 2.2f, 0xFF57503F.toInt(), t.heading)
                scene.box(t.x, 0f, t.z - t.d * 1.05f, t.w, t.h, 2f, 0xFF14100C.toInt(), t.heading)
            }
        }
        if (t.destroyed && t.burn < 6f) {
            fx.smoke(t.x + (Math.random().toFloat() - 0.5f) * t.w, t.h * 0.6f,
                t.z + (Math.random().toFloat() - 0.5f) * t.d, 2f)
        }
    }

    private fun drawDrone(c: Canvas, m: Mission) {
        val droneBmp = droneBitmap(m.model.id) ?: return
        // --- سایه روی زمین
        val sp = project(m.x, 0.35f, m.z)
        if (sp != null) {
            val alt = (m.y - 0.35f).coerceAtLeast(0f)
            val sAlpha = (200f - alt * 2.0f).coerceIn(50f, 160f).toInt()
            val sR = (40f - alt * 0.045f).coerceAtLeast(13f) * scene.cam.focal / 620f
            shadowPaint.color = Theme.withAlpha(0xFF060A08.toInt(), sAlpha / 255f)
            c.drawOval(
                android.graphics.RectF(sp[0] - sR, sp[1] - sR * 0.4f, sp[0] + sR, sp[1] + sR * 0.4f),
                shadowPaint
            )
        }
        // --- اسپرایت پهباد با چرخش yaw
        val pp = project(m.x, m.y, m.z) ?: return
        val size = 120f * scene.cam.focal / 620f
        val rot = Math.toDegrees(m.yaw.toDouble()).toFloat() + 180f
        c.save()
        c.rotate(rot, pp[0], pp[1])
        c.drawBitmap(
            droneBmp, null,
            android.graphics.RectF(pp[0] - size * 0.55f, pp[1] - size * 0.55f, pp[0] + size * 0.55f, pp[1] + size * 0.55f),
            spritePaint
        )
        c.restore()
    }

    private fun droneBitmap(modelId: String): android.graphics.Bitmap? {
        val file = when (modelId) {
            "shahed136" -> "drone_136"; "shahed238" -> "drone_238"; "shahedx" -> "drone_x"
            else -> "drone_131"
        }
        return Gfx.get(gfxContext, file)
    }

    /** موقعیت صفحه‌ای یک نقطه‌ی جهان؛ برای نشانگرهای HUD */
    fun project(x: Float, y: Float, z: Float): FloatArray? =
        if (scene.cam.project(x, y, z, proj)) proj else null
}
