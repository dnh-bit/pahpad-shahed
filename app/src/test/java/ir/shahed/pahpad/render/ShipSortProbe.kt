package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.Scene3D
import ir.shahed.pahpad.game.WorldRenderer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * ابزار مشترک آزمون‌های پایداری ناو — **مقایسهٔ مستقیم کلید مرتب‌سازی**.
 *
 * چرا این روش: مثل سربرگ `CoplanarFaceOrderTest`، تفاضل فریم و «لیست مرتب
 * است» هیچ‌کدام باگ را نشان نمی‌دهند. تنها معیار معتبر این است که کلید واقعی
 * هر وجه (`depth − depthBias`، همان عددی که `Scene3D` مرتب می‌کند) با کلید
 * وجهی که باید پشت/جلویش باشد مقایسه شود. برای این کار تست باید بداند هر
 * وجه به کدام حجم تعلق دارد؛ شناسهٔ تشخیصی `FaceSnapshot.part` همین را
 * می‌دهد (هیچ نقشی در مرتب‌سازی ندارد).
 */
internal object ShipSortProbe {

    const val W = 960
    const val H = 540

    fun keyOf(f: Scene3D.FaceSnapshot): Float = f.depth - f.depthBias

    fun bboxOverlap(a: Scene3D.FaceSnapshot, b: Scene3D.FaceSnapshot): Boolean =
        !(a.maxX <= b.minX || a.minX >= b.maxX || a.maxY <= b.minY || a.minY >= b.maxY)

    fun pointInPoly(px: Float, py: Float, poly: FloatArray): Boolean {
        var inside = false
        val n = poly.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[2 * i]; val yi = poly[2 * i + 1]
            val xj = poly[2 * j]; val yj = poly[2 * j + 1]
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun segHit(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
        fun ccw(ux: Float, uy: Float, vx: Float, vy: Float, wx: Float, wy: Float) =
            (vx - ux) * (wy - uy) - (vy - uy) * (wx - ux)
        val d1 = ccw(ax, ay, bx, by, cx, cy); val d2 = ccw(ax, ay, bx, by, dx, dy)
        val d3 = ccw(cx, cy, dx, dy, ax, ay); val d4 = ccw(cx, cy, dx, dy, bx, by)
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0))
    }

    /** هم‌پوشانی واقعی دو چندضلعی روی صفحه (رأس داخل یا برخورد یال). */
    /** مساحت تقریبی چندضلعی روی صفحه (پیکسل مربع). */
    fun screenArea(f: Scene3D.FaceSnapshot): Float {
        var a = 0f
        val n = f.poly.size / 2
        for (i in 0 until n) {
            val j = (i + 1) % n
            a += f.poly[i * 2] * f.poly[j * 2 + 1] - f.poly[j * 2] * f.poly[i * 2 + 1]
        }
        return kotlin.math.abs(a) * 0.5f
    }

    fun polysOverlap(a: FloatArray, b: FloatArray): Boolean {
        if (a.size < 6 || b.size < 6) return false
        for (i in a.indices step 2) if (pointInPoly(a[i], a[i + 1], b)) return true
        for (i in b.indices step 2) if (pointInPoly(b[i], b[i + 1], a)) return true
        val na = a.size / 2; val nb = b.size / 2
        for (i in 0 until na) {
            val j = (i + 1) % na
            for (k in 0 until nb) {
                val l = (k + 1) % nb
                if (segHit(a[2 * i], a[2 * i + 1], a[2 * j], a[2 * j + 1], b[2 * k], b[2 * k + 1], b[2 * l], b[2 * l + 1])) return true
            }
        }
        return false
    }

    /** مرکز چندضلعی روی صفحه. */
    fun polyCx(p: FloatArray): Float { var s = 0f; for (i in p.indices step 2) s += p[i]; return s / (p.size / 2f) }
    fun polyCy(p: FloatArray): Float { var s = 0f; for (i in 1 until p.size step 2) s += p[i]; return s / (p.size / 2f) }

    /** مساحت تقریبی هم‌پوشانی کادرها — فقط برای اولویت‌دادن به نقض‌های بزرگ. */
    fun overlapArea(a: Scene3D.FaceSnapshot, b: Scene3D.FaceSnapshot): Float {
        val x0 = maxOf(a.minX, b.minX); val x1 = minOf(a.maxX, b.maxX)
        val y0 = maxOf(a.minY, b.minY); val y1 = minOf(a.maxY, b.maxY)
        return if (x0 >= x1 || y0 >= y1) 0f else (x1 - x0) * (y1 - y0)
    }

    /** مختصات محلی کشتی (z به سمت دماغه) از مرکز جهانیِ وجه. */
    fun localZ(ship: WorldRenderer.ShipDiag, wx: Float, wz: Float): Float {
        val dx = wx - ship.x; val dz = wz - ship.z
        return -dx * sin(ship.rot) + dz * cos(ship.rot)
    }

    class Pose(val angle: Float, val radius: Float, val height: Float)

    /**
     * ژست‌های دوربین حول یک کشتی — در محدودهٔ پرواز واقعی کاربر (۱۷ تا ۹۰
     * متر) و از هر طرف. ارتفاع‌های پایین عمداً هست: پنجره‌های پل بالاتر از
     * ارتفاع پروازند و همان حالت است که باگ را نشان می‌دهد.
     */
    fun poses(): List<Pose> {
        val out = ArrayList<Pose>()
        for (k in 0 until 8) {
            val a = k * (Math.PI.toFloat() / 4f)
            for (r in floatArrayOf(150f, 320f)) {
                for (y in floatArrayOf(17f, 34f, 62f, 90f)) out.add(Pose(a, r, y))
            }
        }
        return out
    }

    /**
     * برای هر ژست، صحنه را رندر می‌کند و برای **هر کشتیِ نزدیک به مرکز ژست**
     * بلوک را صدا می‌زند. انتخاب کشتی با یک فیلتر انجام می‌شود تا هر تست
     * فقط کشتی‌های مرتبط را بسنجد.
     */
    fun sweep(
        activity: MainActivity,
        levelId: String,
        quality: Int,
        seed: Long,
        pick: (WorldRenderer.ShipDiag) -> Boolean,
        block: (scene: Scene3D, ship: WorldRenderer.ShipDiag, pose: Pose) -> Unit
    ) {
        val save = SaveManager(activity)
        val mission = Mission(Levels.byId(levelId)!!, save, MissionMode.STORY, seed)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(quality)
        renderer.scene.enableDebugCapture(true)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // فهرست کشتی‌ها از خودِ دنیا ساخته می‌شود، نه از یک رندر اولیه: رندر
        // اولیه از مبدأ انجام می‌شود و کشتی‌های دورتر از CULL در آن فهرست
        // نمی‌آیند (یک دور همین باعث شد ناو هواپیمابر اصلاً سنجیده نشود).
        val ships = ArrayList<WorldRenderer.ShipDiag>()
        mission.world.targets.forEach {
            if (it.shape == ir.shahed.pahpad.game.TargetShape.CARRIER ||
                it.shape == ir.shahed.pahpad.game.TargetShape.SHIP)
                ships.add(WorldRenderer.ShipDiag(0, it.x, it.z, it.heading, it.w, it.h, it.d))
        }
        mission.world.props.forEach {
            if (it.kind == ir.shahed.pahpad.game.PropKind.SHIP ||
                it.kind == ir.shahed.pahpad.game.PropKind.FREIGHTER)
                ships.add(WorldRenderer.ShipDiag(0, it.x, it.z, it.rot, it.w, it.h, it.d))
        }
        val selected = ships.filter(pick)
        for (ship in selected) {
            for (pose in poses()) {
                val cam = renderer.scene.cam
                cam.fov = 74f
                cam.x = ship.x + sin(pose.angle + ship.rot) * pose.radius
                cam.z = ship.z - cos(pose.angle + ship.rot) * pose.radius
                cam.y = pose.height
                cam.yaw = atan2(ship.x - cam.x, ship.z - cam.z)
                cam.pitch = -atan2(pose.height - ship.h * 0.55f, pose.radius)
                cam.roll = 0f
                cam.viewport(W.toFloat(), H.toFloat())
                renderer.draw(canvas, mission, W.toFloat(), H.toFloat())
                // شناسهٔ گروه در هر رندر تازه صادر می‌شود؛ باید نسخهٔ همین
                // فریم را بدهیم، نه نسخهٔ رندر اولیه.
                val cur = renderer.diagShips().minByOrNull {
                    val dx = it.x - ship.x; val dz = it.z - ship.z; dx * dx + dz * dz
                } ?: continue
                if ((cur.x - ship.x) * (cur.x - ship.x) + (cur.z - ship.z) * (cur.z - ship.z) > 1f) continue
                block(renderer.scene, cur, pose)
            }
        }
        renderer.scene.enableDebugCapture(false)
        renderer.scene.clear()
    }
}
