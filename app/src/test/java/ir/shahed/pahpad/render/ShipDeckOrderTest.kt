package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.Rig
import ir.shahed.pahpad.game.Scene3D
import ir.shahed.pahpad.game.WorldRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.cos
import kotlin.math.sin

/**
 * آزمون رگرسیون ناپایداری ناوها — «تغییر شکل و ظاهر/غیب شدن قطعات».
 *
 * ─────────────────────── سازوکار (آنچه این آزمون می‌سنجد) ───────────────────────
 * مرتب‌ساز نقاش در `Scene3D` برای هر وجه **یک** کلید عمق دارد: میانگین عمق
 * چندضلعیِ بریده‌شده (`Scene3D.submit`). عرشهٔ هر کشتی یک چهارضلعی بزرگ است
 * (بدنهٔ ناوشکن: ۷۲٪ طول ناو در یک جعبه؛ باری: ۸۰٪) و عمق میانگینش در
 * **مرکز عرشه** می‌نشیند. هر سازه‌ای که روی عرشه است (برجک توپ، بلوک VLS،
 * جزیرهٔ فرماندهی، دودکش، آشیانه، خانهٔ سکان) عمق میانگین خودش را دارد؛
 * به‌محض این‌که دوربین (یا کشتیِ در حال دریانوری) طوری بچرخد که مرکز آن
 * قطعه دورتر از مرکز عرشه شود، کلید قطعه **بزرگ‌تر** از کلید عرشه می‌شود،
 * عرشه **بعد از** قطعه رسم می‌شود و نیمهٔ پایین قطعه را می‌پوشاند — همان
 * «قطعه غیب می‌شود / ناو تغییر شکل می‌دهد». در پرواز واقعی (هدف در حال
 * حرکت + پهپاد در حال حرکت) این برعکس‌شدن هر لحظه رخ می‌دهد.
 *
 * اندازه‌گیری پیش از درمان روی همین کد (هر دو مرحله دریایی، چند صد پوز
 * دوربین): ۴۵۵۲ از ۴۰۶۸۵ جفتِ هم‌پوشانِ (قطعه، عرشه) نقض داشتند؛ بیشینهٔ
 * وارونگی کلید ۶۲ متر.
 *
 * ─────────────────────── روش سنجش (روش ۵، نه روش‌های قبلی) ───────────────────────
 * مثل سربرگ `CoplanarFaceOrderTest`:
 *  ۱) تفاضل فریم نمی‌گیریم — حرکت دوربین خودش تفاضل می‌سازد.
 *  ۲) «لیست مرتب است» نمی‌سنجیم — تاتولوژی است.
 *  سنجش مستقیم: کلید مرتب‌سازی واقعی هر وجهِ سازهٔ عرشه در برابر کلید وجه
 *  رویی عرشهٔ زیر آن. دوربین همیشه بالاتر از صفحهٔ عرشه است؛ در این حالت
 *  هندسی هیچ پیکسلی از عرشه نباید روی سازهٔ رویش بنشیند، پس باید سازه
 *  **بعد از** عرشه رسم شود ⟸ کلید سازه < کلید عرشه (مرتب‌سازی نزولی است).
 *  وجه‌ها با برچسب تشخیصی پیدا می‌شوند (برچسب هیچ نقشی در مرتب‌سازی ندارد)
 *  و فقط جفت‌هایی مقایسه می‌شوند که کادرهایشان روی صفحه همپوشانی دارد.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class ShipDeckOrderTest {

    private fun keyOf(d: Scene3D.FaceSnapshot) = d.depth - d.depthBias

    /** هر دوربین بالاتر از کران بالاییِ هر عرشهٔ ممکن این ناو است. */
    private fun minCamY(s: WorldRenderer.ShipDiag) = s.h * 0.68f + 0.5f

    private fun sweep(levelId: String, quality: Int, seed: Long): IntArray {
        val activity = Robolectric.buildActivity(ir.shahed.pahpad.MainActivity::class.java).get()
        val save = SaveManager(activity)
        val level = Levels.byId(levelId)!!
        val mission = Mission(level, save, MissionMode.STORY, seed)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(quality)
        renderer.scene.enableDebugCapture(true)
        val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val t = mission.world.targets.first { !it.destroyed }
        val ships = mission.world.props.filter {
            it.kind == ir.shahed.pahpad.game.PropKind.SHIP || it.kind == ir.shahed.pahpad.game.PropKind.FREIGHTER
        }

        var compared = 0
        var violations = 0
        var deckFaces = 0
        var partFaces = 0
        val samples = ArrayList<String>()
        var maxViol = 0f

        fun record(r: IntArray, label: String) {
            compared += r[1]
            violations += r[0]
            deckFaces += r[2]
            partFaces += r[3]
            val mv = r[4] / 100f
            if (mv > maxViol) maxViol = mv
            if (samples.size < 8 && r[0] > 0) samples.add("$label viol=${r[0]} max=$mv")
        }

        // پوزهای دوربین: مدار دور هر ناو + ایستادن روی محور طولی آن (جلوی دماغه
        // و پشت پاشنه) — همان جهتی که وارونگی در آن بیشینه است.
        val targets = ArrayList<WorldRenderer.ShipDiag>()
        targets.add(WorldRenderer.ShipDiag(0, t.x, t.z, t.heading, t.w, t.h, t.d))
        for (p in ships) targets.add(WorldRenderer.ShipDiag(0, p.x, p.z, p.rot, p.w, p.h, p.d))

        for (s in targets) {
            val c = cos(s.rot); val sn = sin(s.rot)
            val camY = minCamY(s) + 6f
            for (k in 0..5) {
                val a = k * (Math.PI.toFloat() / 3f) + s.rot * 0.5f
                val r = if (s.d > 200f) 340f else 150f
                // مدار
                run {
                    val cam = renderer.scene.cam
                    cam.fov = 74f
                    cam.x = s.x + sin(a) * r; cam.y = camY; cam.z = s.z - cos(a) * r
                    cam.yaw = a; cam.pitch = -0.22f; cam.roll = 0f
                    cam.viewport(960f, 540f)
                    renderer.draw(canvas, mission, 960f, 540f)
                    for (sd in renderer.diagShips()) if (camY > minCamY(sd)) {
                        record(judge(renderer, sd), "orbit k=$k g=${sd.group}")
                    }
                }
                // روی محور طولی: جلوی دماغه و پشت پاشنه، نگاه به مرکز ناو
                for (sgn in intArrayOf(1, -1)) {
                    val along = s.d * 0.72f * sgn
                    val wx = s.x - along * sn; val wz = s.z + along * c
                    val cam = renderer.scene.cam
                    cam.fov = 74f
                    cam.x = wx; cam.y = camY + 4f; cam.z = wz
                    cam.yaw = kotlin.math.atan2(s.x - wx, s.z - wz)
                    cam.pitch = -0.18f; cam.roll = 0f
                    cam.viewport(960f, 540f)
                    renderer.draw(canvas, mission, 960f, 540f)
                    for (sd in renderer.diagShips()) if (camY > minCamY(sd)) {
                        record(judge(renderer, sd), "axis sgn=$sgn g=${sd.group}")
                    }
                }
            }
        }
        println("DECKORDER $levelId q=$quality: جفت‌مقایسه=$compared نقض=$violations بیشینهٔ$maxViol " +
            "عرشه=$deckFaces قطعه=$partFaces")
        samples.forEach { println("   $it") }
        assertTrue("هیچ وجه عرشه‌ای رندر نشد — دوربین یا مرحله اشتباه است", deckFaces > 0)
        assertTrue("هیچ وجه سازهٔ عرشه‌ای رندر نشد — دوربین یا مرحله اشتباه است", partFaces > 0)
        assertTrue("هیچ جفت هم‌پوشانی نبود — آزمون بی‌معنا است", compared > 100)
        return intArrayOf(violations, compared)
    }

    /** در یک فریم رندرشده، همهٔ جفت‌های هم‌پوشان (قطعهٔ عرشه، وجه عرشهٔ همان ناو) را می‌سنجد.
     *  خروجی: [نقض، مقایسه، وجه عرشه، وجه سازه، بیشینهٔ وارونگی]. */
    /** چندضلعی نقطه داخل است؟ (حتی‌فرد، پرتوی افقی) */
    private fun pointInPoly(px: Float, py: Float, poly: FloatArray): Boolean {
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

    /** برخورد دو پاره‌خط. */
    private fun segHit(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
        fun ccw(ux: Float, uy: Float, vx: Float, vy: Float, wx: Float, wy: Float) =
            (vx - ux) * (wy - uy) - (vy - uy) * (wx - ux)
        val d1 = ccw(ax, ay, bx, by, cx, cy); val d2 = ccw(ax, ay, bx, by, dx, dy)
        val d3 = ccw(cx, cy, dx, dy, ax, ay); val d4 = ccw(cx, cy, dx, dy, bx, by)
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0))
    }

    /** همپوشانی واقعی دو چندضلعی روی صفحه (رأس داخل یا برخورد یال). */
    private fun polysOverlap(a: FloatArray, b: FloatArray): Boolean {
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

    private fun judge(renderer: WorldRenderer, s: WorldRenderer.ShipDiag): IntArray {
        val snap = renderer.scene.debugFaces()
        val g = s.group
        val decks = snap.filter { it.tag == Rig.TAG_SHIP_DECK && it.group == g }
        // سازهٔ روی عرشه: هم وجه‌های برچسب‌خوردهٔ سازه و هم حجم‌های نشستهٔ
        // داخل سازه (درپوش VLS، کانتینر، پد، خط فرود).
        val parts = snap.filter {
            (it.tag == Rig.TAG_SHIP_PART && it.group == g) ||
                (it.tag == Rig.TAG_SEAT && it.group == g)
        }
        var viol = 0
        var compared = 0
        var maxViol = 0f
        for (dk in decks) for (pt in parts) {
            // پیش‌فیلتر کادر، بعد همپوشانی واقعی چندضلعی روی صفحه.
            if (pt.maxX <= dk.minX || pt.minX >= dk.maxX || pt.maxY <= dk.minY || pt.minY >= dk.maxY) continue
            if (!polysOverlap(pt.poly, dk.poly)) continue
            compared++
            if (keyOf(pt) > keyOf(dk) + 1e-3f) {
                viol++
                val d = keyOf(pt) - keyOf(dk)
                if (d > maxViol) maxViol = d
            }
        }
        return intArrayOf(viol, compared, decks.size, parts.size, (maxViol * 100f).toInt())
    }

    @Test
    fun mountedPartsNeverSortBehindTheirOwnDeck() {
        var totalViol = 0
        var totalCompared = 0
        for (levelId in listOf("L2_5", "L4_4")) {
            for (quality in intArrayOf(2, 0)) {
                val r = sweep(levelId, quality, if (levelId == "L4_4") 5L else 3L)
                totalViol += r[0]
                totalCompared += r[1]
            }
        }
        println("DECKORDER مجموع: مقایسه=$totalCompared نقض=$totalViol")
        assertEquals(
            "جفت‌های هم‌پوشان (قطعهٔ عرشه، عرشهٔ زیر آن) کلید وارون دارند — عرشه روی سازهٔ خودش رسم شده؛ " +
                "همان «قطعه غیب می‌شود» که کاربر می‌بیند. مجموع نقض: $totalViol از $totalCompared جفت",
            0, totalViol
        )
    }
}
