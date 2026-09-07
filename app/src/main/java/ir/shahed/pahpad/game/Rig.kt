package ir.shahed.pahpad.game

import android.graphics.Bitmap
import ir.shahed.pahpad.core.Theme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * ابزار ساخت احجام در دستگاه محلی هر وسیله.
 *
 * قرارداد چرخش عیناً همان `Scene3D.box` است: x' = x·cos − z·sin و z' = x·sin + z·cos،
 * بنابراین جهت (heading) اجسام تازه با محتوای قبلی بازی هم‌خوان می‌ماند.
 *
 * نورپردازی از همان خورشید مشترک `Scene3D.light` می‌آید. جهت نرمالِ هر وجه با مقایسه
 * با مرکز مرجعِ حجم به بیرون چرخانده می‌شود، پس ترتیب رأس‌ها نور را خراب نمی‌کند.
 */
class Rig(private val scene: Scene3D) {

    private var ox = 0f
    private var oy = 0f
    private var oz = 0f
    private var sy = 0f
    private var cy = 1f

    private val pa = FloatArray(3)
    private val pb = FloatArray(3)
    private val pc = FloatArray(3)
    private val pd = FloatArray(3)
    private val corner = FloatArray(24)
    private val ringA = FloatArray(3)
    private val ringB = FloatArray(3)

    /** ضریب بزرگنمایی یکنواخت؛ مدل‌ها با ابعاد واقعی (متر) نوشته می‌شوند. */
    var scale = 1f

    /** مرکز مرجع برای بیرون‌گرداندن نرمال‌ها (در دستگاه محلی) */
    var refX = 0f
    var refY = 0f
    var refZ = 0f

    fun place(x: Float, y: Float, z: Float, yaw: Float, unitScale: Float = 1f) {
        ox = x
        oy = y
        oz = z
        sy = sin(yaw)
        cy = cos(yaw)
        scale = if (unitScale > 0.0001f) unitScale else 1f
        refX = 0f
        refY = 0f
        refZ = 0f
    }

    fun ref(x: Float, y: Float, z: Float) {
        refX = x
        refY = y
        refZ = z
    }

    private fun world(lx0: Float, ly0: Float, lz0: Float, out: FloatArray) {
        val lx = lx0 * scale
        val ly = ly0 * scale
        val lz = lz0 * scale
        out[0] = ox + lx * cy - lz * sy
        out[1] = oy + ly
        out[2] = oz + lx * sy + lz * cy
    }

    // ------------------------------------------------------------------ وجه

    fun face(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy2: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        color: Int, texture: Bitmap? = null
    ) {
        world(ax, ay, az, pa)
        world(bx, by, bz, pb)
        world(cx, cy2, cz, pc)
        world(dx, dy, dz, pd)
        val ux = pb[0] - pa[0]; val uy = pb[1] - pa[1]; val uz = pb[2] - pa[2]
        val vx = pd[0] - pa[0]; val vy = pd[1] - pa[1]; val vz = pd[2] - pa[2]
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val inv = 1f / sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        nx *= inv; ny *= inv; nz *= inv
        // بیرون‌گرداندن نرمال نسبت به مرکز مرجع
        val mx = (ax + bx + cx + dx) * 0.25f - refX
        val my = (ay + by + cy2 + dy) * 0.25f - refY
        val mz = (az + bz + cz + dz) * 0.25f - refZ
        val wx = mx * cy - mz * sy
        val wz = mx * sy + mz * cy
        if (nx * wx + ny * my + nz * wz < 0f) {
            nx = -nx; ny = -ny; nz = -nz
        }
        val shaded = Theme.shade(color, scene.light(nx, ny, nz))
        scene.quad(
            pa[0], pa[1], pa[2], pb[0], pb[1], pb[2],
            pc[0], pc[1], pc[2], pd[0], pd[1], pd[2],
            shaded, texture = texture
        )
    }

    fun tri(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy2: Float, cz: Float,
        color: Int
    ) {
        world(ax, ay, az, pa)
        world(bx, by, bz, pb)
        world(cx, cy2, cz, pc)
        val ux = pb[0] - pa[0]; val uy = pb[1] - pa[1]; val uz = pb[2] - pa[2]
        val vx = pc[0] - pa[0]; val vy = pc[1] - pa[1]; val vz = pc[2] - pa[2]
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val inv = 1f / sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
        nx *= inv; ny *= inv; nz *= inv
        if (ny < 0f) { nx = -nx; ny = -ny; nz = -nz }
        scene.triangle(
            pa[0], pa[1], pa[2], pb[0], pb[1], pb[2], pc[0], pc[1], pc[2],
            Theme.shade(color, scene.light(nx, ny, nz))
        )
    }

    // --------------------------------------------------------------- شش‌وجهی

    /** هشت رأس: ۰..۳ پایین (خلاف ساعت)، ۴..۷ بالا با همان ترتیب. */
    private fun hexa(p: FloatArray, color: Int, side: Bitmap?, top: Bitmap?, bottom: Boolean = true) {
        fun v(i: Int, k: Int) = p[i * 3 + k]
        if (bottom) {
            face(v(3, 0), v(3, 1), v(3, 2), v(2, 0), v(2, 1), v(2, 2),
                v(1, 0), v(1, 1), v(1, 2), v(0, 0), v(0, 1), v(0, 2),
                Theme.shade(color, 0.72f), null)
        }
        face(v(4, 0), v(4, 1), v(4, 2), v(5, 0), v(5, 1), v(5, 2),
            v(6, 0), v(6, 1), v(6, 2), v(7, 0), v(7, 1), v(7, 2), color, top)
        for (i in 0..3) {
            val j = (i + 1) % 4
            face(v(i, 0), v(i, 1), v(i, 2), v(j, 0), v(j, 1), v(j, 2),
                v(j + 4, 0), v(j + 4, 1), v(j + 4, 2), v(i + 4, 0), v(i + 4, 1), v(i + 4, 2),
                color, side)
        }
    }

    private fun setCorner(i: Int, x: Float, y: Float, z: Float) {
        corner[i * 3] = x
        corner[i * 3 + 1] = y
        corner[i * 3 + 2] = z
    }

    /** جعبه‌ی محلی؛ cy پایه‌ی جعبه است نه مرکز آن. */
    fun box(
        cx: Float, baseY: Float, cz: Float,
        sx: Float, sy2: Float, sz: Float,
        color: Int, side: Bitmap? = null, top: Bitmap? = side, bottom: Boolean = true
    ) {
        val hx = sx * 0.5f
        val hz = sz * 0.5f
        val ty = baseY + sy2
        ref(cx, baseY + sy2 * 0.5f, cz)
        setCorner(0, cx - hx, baseY, cz - hz)
        setCorner(1, cx + hx, baseY, cz - hz)
        setCorner(2, cx + hx, baseY, cz + hz)
        setCorner(3, cx - hx, baseY, cz + hz)
        setCorner(4, cx - hx, ty, cz - hz)
        setCorner(5, cx + hx, ty, cz - hz)
        setCorner(6, cx + hx, ty, cz + hz)
        setCorner(7, cx - hx, ty, cz + hz)
        hexa(corner, color, side, top, bottom)
    }

    /** هرم ناقص: نیم‌عرض پایین و بالا جدا؛ برای بدنه‌ی شیب‌دار، برجک و دودکش. */
    fun taper(
        cx: Float, baseY: Float, cz: Float,
        hx0: Float, hz0: Float, hx1: Float, hz1: Float, height: Float,
        color: Int, side: Bitmap? = null, top: Bitmap? = side,
        shiftX: Float = 0f, shiftZ: Float = 0f, bottom: Boolean = true
    ) {
        val ty = baseY + height
        ref(cx, baseY + height * 0.5f, cz)
        setCorner(0, cx - hx0, baseY, cz - hz0)
        setCorner(1, cx + hx0, baseY, cz - hz0)
        setCorner(2, cx + hx0, baseY, cz + hz0)
        setCorner(3, cx - hx0, baseY, cz + hz0)
        setCorner(4, cx - hx1 + shiftX, ty, cz - hz1 + shiftZ)
        setCorner(5, cx + hx1 + shiftX, ty, cz - hz1 + shiftZ)
        setCorner(6, cx + hx1 + shiftX, ty, cz + hz1 + shiftZ)
        setCorner(7, cx - hx1 + shiftX, ty, cz + hz1 + shiftZ)
        hexa(corner, color, side, top, bottom)
    }

    /**
     * بدنه با مقطع متغیر در طول محور Z: برای دماغه‌ی کشتی و سینه‌ی زرهی تانک.
     * halfW0/halfW1 نیم‌عرض دو سر، deckY ارتفاع عرشه، keelY ارتفاع کف.
     */
    fun hull(
        z0: Float, z1: Float, halfW0: Float, halfW1: Float,
        keelY: Float, deckY: Float, keelHalf0: Float, keelHalf1: Float,
        color: Int, side: Bitmap?, top: Bitmap?
    ) {
        ref(0f, (keelY + deckY) * 0.5f, (z0 + z1) * 0.5f)
        setCorner(0, -keelHalf0, keelY, z0)
        setCorner(1, keelHalf0, keelY, z0)
        setCorner(2, keelHalf1, keelY, z1)
        setCorner(3, -keelHalf1, keelY, z1)
        setCorner(4, -halfW0, deckY, z0)
        setCorner(5, halfW0, deckY, z0)
        setCorner(6, halfW1, deckY, z1)
        setCorner(7, -halfW1, deckY, z1)
        hexa(corner, color, side, top, true)
    }

    /** گُوِه: جلوی جسم پایین‌تر از عقب آن است (سینه‌ی تانک، دهانه‌ی سرپناه). */
    fun wedge(
        cx: Float, baseY: Float, cz: Float,
        sx: Float, sz: Float, frontY: Float, backY: Float,
        color: Int, side: Bitmap? = null, top: Bitmap? = side
    ) {
        val hx = sx * 0.5f
        val hz = sz * 0.5f
        ref(cx, baseY + (frontY + backY) * 0.25f, cz)
        setCorner(0, cx - hx, baseY, cz - hz)
        setCorner(1, cx + hx, baseY, cz - hz)
        setCorner(2, cx + hx, baseY, cz + hz)
        setCorner(3, cx - hx, baseY, cz + hz)
        setCorner(4, cx - hx, baseY + frontY, cz - hz)
        setCorner(5, cx + hx, baseY + frontY, cz - hz)
        setCorner(6, cx + hx, baseY + backY, cz + hz)
        setCorner(7, cx - hx, baseY + backY, cz + hz)
        hexa(corner, color, side, top, true)
    }

    // ------------------------------------------------------------- استوانه

    /** استوانه بین دو نقطه‌ی محلی؛ برای لوله‌ی توپ، دکل، دودکش و چرخ. */
    fun tube(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        radius: Float, sides: Int, color: Int, caps: Boolean = true, texture: Bitmap? = null
    ) {
        var ax = x1 - x0
        var ay = y1 - y0
        var az = z1 - z0
        val len = sqrt(ax * ax + ay * ay + az * az)
        if (len < 0.0001f) return
        ax /= len; ay /= len; az /= len
        // دو محور عمود بر محور استوانه
        var ux: Float; var uy: Float; var uz: Float
        if (kotlin.math.abs(ay) < 0.9f) { ux = -az; uy = 0f; uz = ax } else { ux = 1f; uy = 0f; uz = 0f }
        val ui = 1f / sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(0.0001f)
        ux *= ui; uy *= ui; uz *= ui
        val vx = ay * uz - az * uy
        val vy = az * ux - ax * uz
        val vz = ax * uy - ay * ux
        ref((x0 + x1) * 0.5f, (y0 + y1) * 0.5f, (z0 + z1) * 0.5f)
        val n = sides.coerceIn(3, 16)
        var pax = 0f; var pay = 0f; var paz = 0f
        var pbx = 0f; var pby = 0f; var pbz = 0f
        for (i in 0..n) {
            val ang = i * 2f * Math.PI.toFloat() / n
            val cc = cos(ang) * radius
            val ss = sin(ang) * radius
            val ex = ux * cc + vx * ss
            val ey = uy * cc + vy * ss
            val ez = uz * cc + vz * ss
            val nax = x0 + ex; val nay = y0 + ey; val naz = z0 + ez
            val nbx = x1 + ex; val nby = y1 + ey; val nbz = z1 + ez
            if (i > 0) {
                face(pax, pay, paz, nax, nay, naz, nbx, nby, nbz, pbx, pby, pbz, color, texture)
                if (caps) {
                    tri(x0, y0, z0, pax, pay, paz, nax, nay, naz, Theme.shade(color, 0.88f))
                    tri(x1, y1, z1, nbx, nby, nbz, pbx, pby, pbz, Theme.shade(color, 1.06f))
                }
            }
            pax = nax; pay = nay; paz = naz
            pbx = nbx; pby = nby; pbz = nbz
        }
    }

    /** صفحه‌ی دیسکی (آنتن سهموی، صفحه‌ی رادار، چرخ). */
    fun disc(
        cx: Float, cy2: Float, cz: Float,
        nx: Float, ny: Float, nz: Float,
        radius: Float, sides: Int, color: Int
    ) {
        var ux: Float; var uy: Float; var uz: Float
        if (kotlin.math.abs(ny) < 0.9f) { ux = -nz; uy = 0f; uz = nx } else { ux = 1f; uy = 0f; uz = 0f }
        val ui = 1f / sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(0.0001f)
        ux *= ui; uy *= ui; uz *= ui
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux
        ref(cx - nx, cy2 - ny, cz - nz)
        val n = sides.coerceIn(3, 16)
        var px = 0f; var py = 0f; var pz = 0f
        for (i in 0..n) {
            val ang = i * 2f * Math.PI.toFloat() / n
            val cc = cos(ang) * radius
            val ss = sin(ang) * radius
            val ex = cx + ux * cc + vx * ss
            val ey = cy2 + uy * cc + vy * ss
            val ez = cz + uz * cc + vz * ss
            if (i > 0) tri(cx, cy2, cz, px, py, pz, ex, ey, ez, color)
            px = ex; py = ey; pz = ez
        }
    }
}
