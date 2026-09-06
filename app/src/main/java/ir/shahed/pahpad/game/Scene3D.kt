package ir.shahed.pahpad.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import ir.shahed.pahpad.core.Theme

/**
 * دوربین پرسپکتیو ساده.
 * دستگاه مختصات جهان: x شرق، y ارتفاع (بالا)، z شمال.
 * yaw صفر یعنی نگاه به سمت z مثبت.
 */
class Camera {

    var x = 0f
    var y = 0f
    var z = 0f
    var yaw = 0f      // رادیان
    var pitch = 0f    // رادیان، مثبت = نگاه به بالا
    var roll = 0f     // رادیان، فقط برای چرخش تصویر
    var fov = 74f

    var width = 0f; private set
    var height = 0f; private set
    var screenCx = 0f; private set
    var screenCy = 0f; private set
    var focal = 0f; private set

    val near = 0.6f

    private var sinYaw = 0f
    private var cosYaw = 1f
    private var sinPitch = 0f
    private var cosPitch = 1f

    fun viewport(w: Float, h: Float) {
        width = w
        height = h
        screenCx = w / 2f
        screenCy = h / 2f
        val half = Math.toRadians((fov / 2f).toDouble())
        focal = (h / 2f) / Math.tan(half).toFloat()
    }

    fun refresh() {
        sinYaw = Math.sin(yaw.toDouble()).toFloat()
        cosYaw = Math.cos(yaw.toDouble()).toFloat()
        sinPitch = Math.sin(pitch.toDouble()).toFloat()
        cosPitch = Math.cos(pitch.toDouble()).toFloat()
    }

    /** تبدیل نقطه‌ی جهان به مختصات دوربین: out = [راست، بالا، جلو] */
    fun toCamera(px: Float, py: Float, pz: Float, out: FloatArray) {
        val dx = px - x
        val dy = py - y
        val dz = pz - z
        val right = dx * cosYaw - dz * sinYaw
        val fwd = dx * sinYaw + dz * cosYaw
        out[0] = right
        out[1] = dy * cosPitch - fwd * sinPitch
        out[2] = dy * sinPitch + fwd * cosPitch
    }

    /** تبدیل مختصات دوربین به مختصات صفحه. out = [x، y، عمق] */
    fun projectCamera(cam: FloatArray, out: FloatArray): Boolean {
        val cz = cam[2]
        if (cz <= near) return false
        out[0] = screenCx + focal * cam[0] / cz
        out[1] = screenCy - focal * cam[1] / cz
        out[2] = cz
        return true
    }

    private val tmpCam = FloatArray(3)

    fun project(px: Float, py: Float, pz: Float, out: FloatArray): Boolean {
        toCamera(px, py, pz, tmpCam)
        return projectCamera(tmpCam, out)
    }

    /** ارتفاع خط افق روی صفحه */
    fun horizonY(): Float = screenCy + focal * Math.tan(pitch.toDouble()).toFloat()

    /** اندازه‌ی ظاهری یک شیء به قطر مشخص در عمق داده‌شده */
    fun scaleAt(depth: Float, size: Float): Float =
        if (depth <= near) 0f else focal * size / depth
}

/** یک سطح تخت آماده‌ی رسم */
class Face {
    var count = 0
    val xs = FloatArray(5)
    val ys = FloatArray(5)
    var color = 0
    var depth = 0f
    var outlineColor = 0
    var hasOutline = false
}

/**
 * موتور رسم سه‌بعدی سبک با الگوریتم نقاش (painter's algorithm).
 * برای سبک هنری Low-Poly کاملاً کافی است و روی موبایل سریع اجرا می‌شود.
 */
class Scene3D {

    val cam = Camera()

    private val pool = ArrayList<Face>(1600)
    private var used = 0
    private val order = ArrayList<Face>(1600)

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val a = FloatArray(3)
    private val b = FloatArray(3)
    private val p0 = FloatArray(3)
    private val p1 = FloatArray(3)
    private val p2 = FloatArray(3)
    private val p3 = FloatArray(3)

    var fogColor = Theme.DESERT.fog
    var fogStart = 260f
    var fogEnd = 1500f

    fun begin() {
        used = 0
        order.clear()
        cam.refresh()
    }

    private fun next(): Face {
        if (used >= pool.size) pool.add(Face())
        val f = pool[used]
        used++
        return f
    }

    private fun fog(color: Int, depth: Float): Int {
        if (depth <= fogStart) return color
        val t = ((depth - fogStart) / (fogEnd - fogStart)).coerceIn(0f, 0.86f)
        return Theme.mix(color, fogColor, t)
    }

    /** چهارضلعی سه‌بعدی. اگر هر رأس پشت دوربین باشد رسم نمی‌شود. */
    fun quad(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        color: Int,
        outline: Int = 0
    ) {
        if (!cam.project(x0, y0, z0, p0)) return
        if (!cam.project(x1, y1, z1, p1)) return
        if (!cam.project(x2, y2, z2, p2)) return
        if (!cam.project(x3, y3, z3, p3)) return
        val depth = (p0[2] + p1[2] + p2[2] + p3[2]) / 4f
        if (depth > fogEnd * 1.4f) return
        val f = next()
        f.count = 4
        f.xs[0] = p0[0]; f.ys[0] = p0[1]
        f.xs[1] = p1[0]; f.ys[1] = p1[1]
        f.xs[2] = p2[0]; f.ys[2] = p2[1]
        f.xs[3] = p3[0]; f.ys[3] = p3[1]
        f.depth = depth
        f.color = fog(color, depth)
        f.hasOutline = outline != 0
        f.outlineColor = outline
        order.add(f)
    }

    fun triangle(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        color: Int
    ) {
        if (!cam.project(x0, y0, z0, p0)) return
        if (!cam.project(x1, y1, z1, p1)) return
        if (!cam.project(x2, y2, z2, p2)) return
        val depth = (p0[2] + p1[2] + p2[2]) / 3f
        val f = next()
        f.count = 3
        f.xs[0] = p0[0]; f.ys[0] = p0[1]
        f.xs[1] = p1[0]; f.ys[1] = p1[1]
        f.xs[2] = p2[0]; f.ys[2] = p2[1]
        f.depth = depth
        f.color = fog(color, depth)
        f.hasOutline = false
        order.add(f)
    }

    /**
     * جعبه‌ی محور-همسو با چرخش حول محور عمودی.
     * cx,cz مرکز افقی، baseY کف جعبه.
     */
    fun box(
        cx: Float, baseY: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
        color: Int,
        rotY: Float = 0f,
        topColor: Int = Theme.shade(color, 1.22f),
        sideColor: Int = Theme.shade(color, 0.78f)
    ) {
        val hx = sx / 2f
        val hz = sz / 2f
        val s = Math.sin(rotY.toDouble()).toFloat()
        val c = Math.cos(rotY.toDouble()).toFloat()
        // چهار گوشه‌ی افقی
        val cxs = FloatArray(4)
        val czs = FloatArray(4)
        val ox = floatArrayOf(-hx, hx, hx, -hx)
        val oz = floatArrayOf(-hz, -hz, hz, hz)
        for (i in 0 until 4) {
            cxs[i] = cx + ox[i] * c - oz[i] * s
            czs[i] = cz + ox[i] * s + oz[i] * c
        }
        val topY = baseY + sy
        // دیواره‌ها
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val shade = if (i % 2 == 0) sideColor else Theme.shade(sideColor, 0.86f)
            quad(
                cxs[i], baseY, czs[i],
                cxs[j], baseY, czs[j],
                cxs[j], topY, czs[j],
                cxs[i], topY, czs[i],
                shade
            )
        }
        // سقف
        quad(
            cxs[0], topY, czs[0],
            cxs[1], topY, czs[1],
            cxs[2], topY, czs[2],
            cxs[3], topY, czs[3],
            topColor
        )
    }

    /** خط سه‌بعدی با برش نزدیک‌صفحه */
    fun line(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        canvas: Canvas, color: Int, width: Float
    ) {
        cam.toCamera(x0, y0, z0, a)
        cam.toCamera(x1, y1, z1, b)
        var ax = a[0]; var ay = a[1]; var az = a[2]
        var bx = b[0]; var by = b[1]; var bz = b[2]
        val near = cam.near
        if (az <= near && bz <= near) return
        if (az <= near) {
            val t = (near - az) / (bz - az)
            ax += (bx - ax) * t; ay += (by - ay) * t; az = near
        } else if (bz <= near) {
            val t = (near - bz) / (az - bz)
            bx += (ax - bx) * t; by += (ay - by) * t; bz = near
        }
        val sx0 = cam.screenCx + cam.focal * ax / az
        val sy0 = cam.screenCy - cam.focal * ay / az
        val sx1 = cam.screenCx + cam.focal * bx / bz
        val sy1 = cam.screenCy - cam.focal * by / bz
        linePaint.color = fog(color, (az + bz) / 2f)
        linePaint.strokeWidth = width
        canvas.drawLine(sx0, sy0, sx1, sy1, linePaint)
    }

    /** رسم همه‌ی سطوح از دور به نزدیک */
    fun flush(canvas: Canvas) {
        order.sortWith(Comparator { f1, f2 -> f2.depth.compareTo(f1.depth) })
        paint.style = Paint.Style.FILL
        for (f in order) {
            path.reset()
            path.moveTo(f.xs[0], f.ys[0])
            for (i in 1 until f.count) path.lineTo(f.xs[i], f.ys[i])
            path.close()
            paint.color = f.color
            canvas.drawPath(path, paint)
            if (f.hasOutline) {
                linePaint.color = f.outlineColor
                linePaint.strokeWidth = 1.4f
                canvas.drawPath(path, linePaint)
            }
        }
        order.clear()
        used = 0
    }
}
