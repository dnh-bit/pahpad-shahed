package ir.shahed.pahpad.render

import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.game.Rig
import ir.shahed.pahpad.game.WorldRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * رگرسیون: **چیزهایی که روی عرشه ایستاده‌اند نباید پشتِ عرشه یا پشتِ
 * نشانه‌های کف عرشه مرتب شوند.**
 *
 * ─────────────────────────── سازوکار (اندازه‌گیری‌شده) ───────────────────────────
 * یک کشتی سه لایهٔ هندسی دارد که با **یک** کلید عمق مرتب می‌شوند:
 *  ۱) بدنه (زیر عرشه)
 *  ۲) وجه رویی عرشه — با لنگر «دورترین گوشهٔ کل کشتی» عمیق‌ترین کلید را
 *     می‌گیرد تا هیچ سازه‌ای زیر عرشهٔ خودش نرود (رفع باگ قبلی)
 *  ۳) خط‌ها/هواپیماهای روی عرشه
 * مشکل این بود که بدنه هم مثل لایهٔ ۳ کلید **میانگین عمق** می‌گرفت؛ یعنی
 * وجه رویی بدنه (که ۱٫۸ متر زیر عرشه است) بعد از عرشه رسم می‌شد و
 * رویش می‌افتاد، و کلیدش دقیقاً در محدودهٔ کلید خط فرود و هواپیماهای
 * پارک‌شده بود. اندازه‌گیری پیش از فیکس: تا **۳۰٬۳۴۰ پیکسل مربع** هم‌پوشانی
 * با ترتیب وارون بین وجه رویی بدنه و خط فرود ناو هواپیمابر، و ۱۱ جفت
 * بدنه/عرشه در هر فریم از هر کشتی که بدنه بعد از عرشه رسم می‌شد.
 * هواپیماها هم کلید خام داشتند در حالی که خط‌ها با مرجعِ سگمنتی لنگر
 * می‌گرفتند؛ دو سیستم کلید متفاوت روی یک سطح = ترتیب ناپایدار.
 *
 * ─────────────────────────── سنجش (مستقیم، بدون تفاضل فریم) ───────────────────────────
 * سه نابرابریِ هندسی که مستقل از دوربین باید همیشه برقرار باشند:
 *  الف) هر وجهی از بدنه که روی صفحه با وجه رویی عرشه هم‌پوشانی دارد باید
 *       کلید بزرگ‌تری بگیرد (اول رسم شود) — بدنه زیر عرشه است.
 *  ب) هر وجه هواپیمای پارک‌شده که با نشانهٔ کفِ زير خودش هم‌پوشانی دارد
 *       باید کلید کوچک‌تری بگیرد (بعد از نشانه رسم شود) — هواپیما بالای
 *       نشانه است و باید رویش بماند.
 *  ج) حلقهٔ پد باید بعد از صفحهٔ پد رسم شود (کلید کوچک‌تر).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class ShipDeckFurnitureOrderTest {

    private val carrier: (WorldRenderer.ShipDiag) -> Boolean = { it.h > 25f }
    private val warship: (WorldRenderer.ShipDiag) -> Boolean = { it.d > 100f && it.h > 15f }
    private val freighter: (WorldRenderer.ShipDiag) -> Boolean = { it.d > 120f && it.h < 18f }

    @Test
    fun hullNeverSortsAfterItsOwnDeck() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        var compared = 0
        var violations = 0
        var worstArea = 0f
        var maxDelta = 0f

        fun run(levelId: String, pick: (WorldRenderer.ShipDiag) -> Boolean) {
            ShipSortProbe.sweep(activity, levelId, 2, 5L, pick) { scene, ship, _ ->
                val faces = scene.debugFaces().filter { it.group == ship.group && it.poly.size >= 6 }
                val deck = faces.filter { it.tag == Rig.TAG_SHIP_DECK }
                val hull = faces.filter { it.part == Rig.PART_HULL }
                for (h in hull) for (d in deck) {
                    if (!ShipSortProbe.bboxOverlap(h, d)) continue
                    if (!ShipSortProbe.polysOverlap(h.poly, d.poly)) continue
                    compared++
                    val key = ShipSortProbe.keyOf(h) - ShipSortProbe.keyOf(d)
                    // بدنه باید اول رسم شود ⇒ کلیدش بزرگ‌تر باشد.
                    if (key < -0.05f) {
                        violations++
                        if (-key > maxDelta) maxDelta = -key
                        val a = ShipSortProbe.overlapArea(h, d)
                        if (a > worstArea) worstArea = a
                    }
                }
            }
        }
        run("L4_4", carrier)
        run("L2_5", warship)
        run("L4_4", freighter)

        println("DECKFURN hull↔deck: جفت=$compared نقض=$violations بیشینهٔΔ=${"%.2f".format(maxDelta)} بزرگ‌ترین‌مساحت=${"%.0f".format(worstArea)}px²")
        assertTrue("هیچ هم‌پوشانی بدنه/عرشه‌ای سنجیده نشد — تست بی‌معنا است", compared > 20)
        assertEquals(
            "وجه‌های بدنه بعد از عرشهٔ خودشان رسم شدند ($violations از $compared جفت، تا " +
                "${"%.0f".format(worstArea)} پیکسل مربع) — بدنه/بادگیر روی عرشه می‌افتد و با " +
                "نشانه‌های عرشه فریم‌به‌فریم جابه‌جا می‌شود",
            0, violations
        )
    }

    @Test
    fun parkedAircraftNeverSortBehindDeckMarkings() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        var compared = 0
        var violations = 0
        var maxDelta = 0f

        ShipSortProbe.sweep(activity, "L4_4", 0, 5L, carrier) { scene, ship, _ ->
            val faces = scene.debugFaces().filter { it.group == ship.group && it.poly.size >= 6 }
            val jets = faces.filter { it.part == Rig.PART_JET }
            val marks = faces.filter { it.part == Rig.PART_DECK_MARK }
            for (j in jets) for (m in marks) {
                // فقط جفتِ نزدیک در پلان: هواپیما دقیقاً بالای همان نشانه.
                if (kotlin.math.abs(ShipSortProbe.localZ(ship, j.wx, j.wz) - ShipSortProbe.localZ(ship, m.wx, m.wz)) > 15f) continue
                if (xLocal(ship, j) - xLocal(ship, m) > 15f || xLocal(ship, m) - xLocal(ship, j) > 15f) continue
                if (!ShipSortProbe.bboxOverlap(j, m)) continue
                if (!ShipSortProbe.polysOverlap(j.poly, m.poly)) continue
                compared++
                val d = ShipSortProbe.keyOf(j) - ShipSortProbe.keyOf(m)
                // هواپیما باید بعد از نشانه رسم شود ⇒ کلیدش کوچک‌تر باشد.
                if (d > 0.05f) {
                    violations++
                    if (d > maxDelta) maxDelta = d
                }
            }
        }

        println("DECKFURN jet↔mark: جفت=$compared نقض=$violations بیشینهٔΔ=${"%.3f".format(maxDelta)}")
        assertTrue("هیچ جفت هواپیما/نشانهٔ نزدیکی سنجیده نشد — تست بی‌معنا است", compared > 20)
        assertEquals(
            "هواپیمای پارک‌شده قبل از نشانهٔ زیر خودش رسم شد ($violations از $compared جفت) — " +
                "نشانه از روی بال/بدنه می‌گذرد و با حرکت دوربین چشمک می‌زند",
            0, violations
        )
    }

    @Test
    fun padRingsStayOnTheirPadPlate() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        var compared = 0
        var violations = 0
        var maxDelta = 0f

        ShipSortProbe.sweep(activity, "L2_5", 2, 5L, warship) { scene, ship, _ ->
            val faces = scene.debugFaces().filter { it.group == ship.group && it.poly.size >= 6 }
            val rings = faces.filter { it.part == Rig.PART_PAD_RING }
            val plates = faces.filter { it.part == Rig.PART_SEATED && it.tag == Rig.TAG_PAD }
            for (r in rings) for (p in plates) {
                if (!ShipSortProbe.bboxOverlap(r, p)) continue
                if (!ShipSortProbe.polysOverlap(r.poly, p.poly)) continue
                compared++
                val d = ShipSortProbe.keyOf(r) - ShipSortProbe.keyOf(p)
                // حلقه باید بعد از صفحه رسم شود ⇒ کلیدش کوچک‌تر باشد.
                if (d > 0.05f) {
                    violations++
                    if (d > maxDelta) maxDelta = d
                }
            }
        }

        println("DECKFURN ring↔pad: جفت=$compared نقض=$violations بیشینهٔΔ=${"%.3f".format(maxDelta)}")
        assertTrue("هیچ جفت حلقه/صفحهٔ پدی سنجیده نشد — تست بی‌معنا است", compared > 10)
        assertEquals(
            "حلقهٔ پد قبل از صفحهٔ پد رسم شد ($violations از $compared جفت) — حلقه از دید دماغه " +
                "غیب می‌شود و با چرخش دوربین ظاهر/غیب می‌شود",
            0, violations
        )
    }

    /** مختصات محلی x (عرض کشتی) از مرکز جهانی وجه. */
    private fun xLocal(ship: WorldRenderer.ShipDiag, f: ir.shahed.pahpad.game.Scene3D.FaceSnapshot): Float {
        val dx = f.wx - ship.x; val dz = f.wz - ship.z
        return dx * kotlin.math.cos(ship.rot) + dz * kotlin.math.sin(ship.rot)
    }
}
