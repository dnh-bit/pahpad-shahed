package ir.shahed.pahpad.game

import android.graphics.Canvas
import android.graphics.Paint
import ir.shahed.pahpad.core.Theme
import java.util.Random

/** یک ذره‌ی سه‌بعدی */
class Particle {
    var x = 0f; var y = 0f; var z = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var life = 0f
    var maxLife = 1f
    var size = 1f
    var growth = 0f
    var color = 0
    var gravity = 0f
    var drag = 0.98f
    var alive = false
}

/**
 * سیستم ذرات: دود موتور، انفجار، آتش، خرده‌ها و رسام پدافند.
 */
class Fx(capacity: Int = 420) {

    private val pool = Array(capacity) { Particle() }
    private val rnd = Random()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val proj = FloatArray(3)
    private val drawOrder = ArrayList<Particle>(capacity)

    private fun spawn(): Particle? {
        for (p in pool) if (!p.alive) return p
        return null
    }

    fun clear() {
        for (p in pool) p.alive = false
    }

    fun smoke(x: Float, y: Float, z: Float, strength: Float = 1f) {
        val p = spawn() ?: return
        p.alive = true
        p.x = x; p.y = y; p.z = z
        p.vx = rnd.nextFloat() * 2f - 1f
        p.vy = 0.6f + rnd.nextFloat()
        p.vz = rnd.nextFloat() * 2f - 1f
        p.maxLife = 1.1f + rnd.nextFloat() * 0.7f
        p.life = p.maxLife
        p.size = 1.4f * strength
        p.growth = 3.2f
        p.gravity = 0f
        p.drag = 0.94f
        p.color = 0xFFB9C3CC.toInt()
    }

    fun explosion(x: Float, y: Float, z: Float, power: Float = 1f) {
        val count = (46 * power).toInt().coerceIn(18, 90)
        for (i in 0 until count) {
            val p = spawn() ?: return
            p.alive = true
            p.x = x; p.y = y; p.z = z
            val a = rnd.nextFloat() * Math.PI.toFloat() * 2f
            val e = rnd.nextFloat() * 1.2f
            val sp = (14f + rnd.nextFloat() * 40f) * power
            p.vx = Math.cos(a.toDouble()).toFloat() * sp
            p.vz = Math.sin(a.toDouble()).toFloat() * sp
            p.vy = e * sp * 0.8f
            p.maxLife = 0.7f + rnd.nextFloat() * 1.5f
            p.life = p.maxLife
            p.size = (2.2f + rnd.nextFloat() * 4.5f) * power
            p.growth = 5f
            p.gravity = -14f
            p.drag = 0.9f
            p.color = when (rnd.nextInt(4)) {
                0 -> Theme.AMBER
                1 -> Theme.ORANGE
                2 -> 0xFFFFF1C9.toInt()
                else -> 0xFF6E6A66.toInt()
            }
        }
        // ستون دود
        for (i in 0 until (10 * power).toInt().coerceAtMost(24)) {
            val p = spawn() ?: return
            p.alive = true
            p.x = x + (rnd.nextFloat() * 8f - 4f)
            p.y = y + rnd.nextFloat() * 6f
            p.z = z + (rnd.nextFloat() * 8f - 4f)
            p.vx = rnd.nextFloat() * 4f - 2f
            p.vy = 7f + rnd.nextFloat() * 9f
            p.vz = rnd.nextFloat() * 4f - 2f
            p.maxLife = 2.4f + rnd.nextFloat() * 2f
            p.life = p.maxLife
            p.size = 5f * power
            p.growth = 7f
            p.gravity = 0f
            p.drag = 0.95f
            p.color = 0xFF4A4A4A.toInt()
        }
    }

    fun debris(x: Float, y: Float, z: Float) {
        for (i in 0 until 8) {
            val p = spawn() ?: return
            p.alive = true
            p.x = x; p.y = y; p.z = z
            p.vx = rnd.nextFloat() * 30f - 15f
            p.vy = 6f + rnd.nextFloat() * 18f
            p.vz = rnd.nextFloat() * 30f - 15f
            p.maxLife = 1.4f + rnd.nextFloat()
            p.life = p.maxLife
            p.size = 0.9f
            p.growth = 0f
            p.gravity = -22f
            p.drag = 0.99f
            p.color = 0xFF8C8577.toInt()
        }
    }

    fun tracer(x: Float, y: Float, z: Float) {
        val p = spawn() ?: return
        p.alive = true
        p.x = x; p.y = y; p.z = z
        p.vx = 0f; p.vy = 0f; p.vz = 0f
        p.maxLife = 0.35f
        p.life = p.maxLife
        p.size = 1.1f
        p.growth = 0.4f
        p.gravity = 0f
        p.drag = 1f
        p.color = Theme.AMBER
    }

    fun spark(x: Float, y: Float, z: Float, color: Int) {
        val p = spawn() ?: return
        p.alive = true
        p.x = x; p.y = y; p.z = z
        p.vx = rnd.nextFloat() * 10f - 5f
        p.vy = rnd.nextFloat() * 8f
        p.vz = rnd.nextFloat() * 10f - 5f
        p.maxLife = 0.5f
        p.life = p.maxLife
        p.size = 0.8f
        p.growth = 0.6f
        p.gravity = -8f
        p.drag = 0.95f
        p.color = color
    }

    fun update(dt: Float) {
        for (p in pool) {
            if (!p.alive) continue
            p.life -= dt
            if (p.life <= 0f) {
                p.alive = false
                continue
            }
            p.vy += p.gravity * dt
            val d = Math.pow(p.drag.toDouble(), (dt * 60f).toDouble()).toFloat()
            p.vx *= d; p.vy *= d; p.vz *= d
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            p.size += p.growth * dt
            if (p.y < 0f) {
                p.y = 0f
                p.vy = -p.vy * 0.3f
            }
        }
    }

    fun draw(canvas: Canvas, scene: Scene3D) {
        drawOrder.clear()
        for (p in pool) if (p.alive) drawOrder.add(p)
        // مرتب‌سازی از دور به نزدیک برای همپوشانی درست
        drawOrder.sortWith(Comparator { a, b ->
            val da = dist2(a, scene)
            val db = dist2(b, scene)
            db.compareTo(da)
        })
        for (p in drawOrder) {
            if (!scene.cam.project(p.x, p.y, p.z, proj)) continue
            val r = scene.cam.scaleAt(proj[2], p.size)
            if (r < 0.4f) continue
            val t = (p.life / p.maxLife).coerceIn(0f, 1f)
            paint.color = Theme.withAlpha(p.color, t * 0.9f)
            canvas.drawCircle(proj[0], proj[1], r, paint)
        }
    }

    private fun dist2(p: Particle, scene: Scene3D): Float {
        val dx = p.x - scene.cam.x
        val dy = p.y - scene.cam.y
        val dz = p.z - scene.cam.z
        return dx * dx + dy * dy + dz * dz
    }
}
