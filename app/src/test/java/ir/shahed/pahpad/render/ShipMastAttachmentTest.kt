package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.Rig
import ir.shahed.pahpad.game.Scene3D
import ir.shahed.pahpad.game.WorldRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * رگرسیون «بالای ناو» — قطعهٔ شناور/جداافتاده.
 *
 * ─────────────────────────── شکایت کاربر ───────────────────────────
 * «بالای ناو هواپیمابر این‌جوریه»: دیسک کم‌رنگ روی چهار لولهٔ باریک
 * شناور بود (۰٫۱ ارتفاع بالاتر از سر لوله‌ها، بدون هیچ اتصال)، و کنارش
 * یک صفحهٔ کجِ جداافتاده (طبقهٔ پنجم جزیره با آفست z) دیده می‌شد.
 *
 * ─────────────────────────── سنجش (هندسی، مستقل از دوربین) ───────────────────────────
 * از کادر جهانیِ هر وجه (که `Scene3D` در حالت تشخیصی ثبت می‌کند):
 *  ۱) دیسک: مرکز آن باید داخل بازهٔ ارتفاعی تکیه‌گاهش (پایه‌های دکل +
 *     میله) باشد و در پلان به همان محور نزدیک باشد (فاصله < ۲٫۵ متر).
 *     اگر بالاتر از سر تکیه‌گاه باشد، شناور است.
 *  ۲) بالاترین بلوک جزیره: کف آن باید روی سقف بالاترین طبقه بنشیند
 *     (اختلاف < ۰٫۱ متر) و مرکزش در پلان با آن هم‌مرکز باشد (< ۱٫۵ متر).
 *     اگر افست بخورد، صفحهٔ جداافتاده می‌شود.
 *  ۳) میله/بازوی دکل نباید افقی از محور دور بیفتد (بازوهای «یتیم» که از
 *     هوا شروع می‌شدند).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class ShipMastAttachmentTest {

    private data class Ship(val label: String, val faces: List<Scene3D.FaceSnapshot>)

    private fun collect(levelId: String, quality: Int, pick: (WorldRenderer.ShipDiag) -> Boolean): List<Ship> {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val save = SaveManager(activity)
        val mission = Mission(Levels.byId(levelId)!!, save, MissionMode.STORY, 5L)
        val renderer = WorldRenderer(activity)
        renderer.applyQuality(quality)
        renderer.scene.enableDebugCapture(true)
        val bmp = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val scene = renderer.scene
        val out = ArrayList<Ship>()
        // فهرست از خود دنیا (نه رندر اولیه، که کشتی‌های دورتر از CULL را ندارد).
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
        for (s in ships.filter(pick)) {
            val cam = scene.cam
            cam.fov = 74f
            cam.x = s.x + 120f
            cam.z = s.z - 160f
            cam.y = s.h * 2f + 25f
            cam.yaw = atan2(s.x - cam.x, s.z - cam.z)
            cam.pitch = -0.5f
            cam.roll = 0f
            cam.viewport(960f, 540f)
            renderer.draw(canvas, mission, 960f, 540f)
            val cur = renderer.diagShips().minByOrNull {
                val dx = it.x - s.x; val dz = it.z - s.z; dx * dx + dz * dz
            } ?: continue
            if ((cur.x - s.x) * (cur.x - s.x) + (cur.z - s.z) * (cur.z - s.z) > 1f) continue
            out.add(Ship("$levelId g=${cur.group}", scene.debugFaces().filter { it.group == cur.group }))
        }
        scene.enableDebugCapture(false)
        scene.clear()
        return out
    }

    @Test
    fun radarDishSitsOnItsMast() {
        val ships = collect("L4_4", 2) { it.h > 25f } +
            collect("L2_5", 2) { it.d > 100f && it.h > 15f }
        var checked = 0
        val problems = ArrayList<String>()
        for (ship in ships) {
            val dish = ship.faces.filter { it.part == Rig.PART_DISH && it.poly.size >= 3 }
            if (dish.isEmpty()) continue
            val support = ship.faces.filter {
                (it.part == Rig.PART_MAST || it.part == Rig.PART_MAST_ARM) && it.poly.size >= 3
            }
            if (support.isEmpty()) {
                problems.add("${ship.label}: دیسک هست ولی هیچ تکیه‌گاهی (دکل/میله) رندر نشد")
                continue
            }
            checked++
            val dishY = dish.map { it.wy }.average().toFloat()
            val dishX = dish.map { it.wx }.average().toFloat()
            val dishZ = dish.map { it.wz }.average().toFloat()
            val supY0 = support.minOf { it.wy0 }
            val supY1 = support.maxOf { it.wy1 }
            val supNear = support.minOf { f ->
                sqrt((f.wx - dishX) * (f.wx - dishX) + (f.wz - dishZ) * (f.wz - dishZ))
            }
            println("MAST ${ship.label}: dish=(${"%.1f".format(dishX)},${"%.1f".format(dishY)},${"%.1f".format(dishZ)}) " +
                "supportY=[${"%.1f".format(supY0)},${"%.1f".format(supY1)}] nearest=${"%.2f".format(supNear)}m")
            if (dishY > supY1 + 0.35f || dishY < supY0 - 0.35f) {
                problems.add(
                    "${ship.label}: مرکز دیسک y=${"%.2f".format(dishY)} بیرون از بازهٔ تکیه‌گاه " +
                        "[${"%.2f".format(supY0)}, ${"%.2f".format(supY1)}] است — دیسک شناور است"
                )
            }
            if (supNear > 2.5f) {
                problems.add("${ship.label}: نزدیک‌ترین وجه تکیه‌گاه ${"%.2f".format(supNear)} متر از محور دیسک دور است — شناور است")
            }
        }
        problems.forEach { println("   MASTPROBLEM $it") }
        assertTrue("هیچ دیسک رادار/تکیه‌گاهی رندر نشد — تست بی‌معنا است", checked > 0)
        assertTrue("دیسک روی تکیه‌گاهش نمی‌نشیند:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun islandCapSitsOnTopTier() {
        val ships = collect("L4_4", 2) { it.h > 25f } +
            collect("L2_5", 2) { it.d > 100f && it.h > 15f }
        var checked = 0
        val problems = ArrayList<String>()
        for (ship in ships) {
            val cap = ship.faces.filter { it.part == Rig.PART_ISLAND_CAP && it.poly.size >= 3 }
            if (cap.isEmpty()) continue
            val topTier = ship.faces
                .filter { it.part in Rig.PART_ISLAND_TIER until Rig.PART_ISLAND_CAP && it.poly.size >= 3 }
                .maxByOrNull { it.part } ?: continue
            val tierPart = topTier.part
            val tiers = ship.faces.filter { it.part == tierPart && it.poly.size >= 3 }
            checked++
            val capY0 = cap.minOf { it.wy0 }
            val tierY1 = tiers.maxOf { it.wy1 }
            val capCx = (cap.minOf { it.wx0 } + cap.maxOf { it.wx1 }) * 0.5f
            val capCz = (cap.minOf { it.wz0 } + cap.maxOf { it.wz1 }) * 0.5f
            val tierCx = (tiers.minOf { it.wx0 } + tiers.maxOf { it.wx1 }) * 0.5f
            val tierCz = (tiers.minOf { it.wz0 } + tiers.maxOf { it.wz1 }) * 0.5f
            val dx = capCx - tierCx; val dz = capCz - tierCz
            val off = sqrt(dx * dx + dz * dz)
            println("CAP ${ship.label}: capBase=${"%.2f".format(capY0)} tierTop=${"%.2f".format(tierY1)} " +
                "offset=(${"%.2f".format(dx)},${"%.2f".format(dz)}) |off|=${"%.2f".format(off)}m")
            if (abs(capY0 - tierY1) > 0.1f) {
                problems.add("${ship.label}: کف بلوک بالا (${"%.2f".format(capY0)}) روی سقف طبقه (${"%.2f".format(tierY1)}) نیست")
            }
            if (off > 1.5f) {
                problems.add("${ship.label}: بلوک بالای جزیره ${"%.2f".format(off)} متر از مرکز طبقهٔ زیرش افست دارد — صفحهٔ جداافتاده")
            }
        }
        problems.forEach { println("   CAPROBLEM $it") }
        assertTrue("هیچ بلوک بالای جزیره‌ای رندر نشد — تست بی‌معنا است", checked > 0)
        assertTrue("بلوک بالای جزیره جدا افتاده است:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun mastArmsDoNotFloatOffTheMastAxis() {
        val ships = collect("L4_4", 2) { it.h > 25f } +
            collect("L2_5", 2) { it.d > 100f && it.h > 15f }
        var checked = 0
        val problems = ArrayList<String>()
        for (ship in ships) {
            val dish = ship.faces.filter { it.part == Rig.PART_DISH }
            val arms = ship.faces.filter { it.part == Rig.PART_MAST_ARM && it.poly.size >= 3 }
            if (arms.isEmpty() || dish.isEmpty()) continue
            checked++
            val axisX = dish.map { it.wx }.average().toFloat()
            val axisZ = dish.map { it.wz }.average().toFloat()
            for (a in arms) {
                val off = sqrt((a.wx - axisX) * (a.wx - axisX) + (a.wz - axisZ) * (a.wz - axisZ))
                if (off > 2.5f) {
                    problems.add("${ship.label}: بازوی دکل ${"%.2f".format(off)} متر از محور دور است (در هوا شروع می‌شود)")
                    break
                }
            }
        }
        problems.forEach { println("   ARMPROBLEM $it") }
        assertTrue("هیچ بازوی دکلی رندر نشد — تست بی‌معنا است", checked > 0)
        assertTrue("بازوی دکل به دکل وصل نیست:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
