package ir.shahed.pahpad.render

import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.game.PropKind
import ir.shahed.pahpad.game.Rig
import ir.shahed.pahpad.game.TargetShape
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
 * رگرسیون: **پنجره/آرایهٔ نصب‌شده روی دیوار جزیره نباید پشت دیوار میزبانش
 * مرتب شود.**
 *
 * ────────────────────────────── سازوکار ──────────────────────────────
 * پنل پل (و آرایهٔ رادار) یک جعبهٔ نازک است که از وجه یک طبقهٔ جزیره بیرون
 * زده و همان وجه را سوراخ می‌کند. مرتب‌ساز نقاش برای هر وجه فقط **یک**
 * عدد دارد: میانگین عمق. کلید پنل = عمق میانگین خودِ پنل؛ کلید دیوار =
 * عمق میانگین **کل چهارضلعی دیوار** (نزدیک مرکز دیوار). تا وقتی دوربین
 * بالاتر از پنل باشد، پنل به دوربین نزدیک‌تر از مرکز دیوار است و جلو
 * می‌ماند. ولی وقتی دوربین از ارتفاع پنل پایین‌تر بیاید — و پروفایل پرواز
 * کاربر ۱۷ تا ۸۰ متر است، در حالی که پنجرهٔ پل ناوشکن ≈ ۲۱ متر و ناو
 * هواپیمابر ≈ ۳۲ متر ارتفاع دارد — پنل از مرکز دیوار دورتر می‌شود، کلیدش
 * بزرگ‌تر می‌شود و دیوار **روی** پنل کشیده می‌شود: پنجره‌ها ظاهر و غیب
 * می‌شوند. این همان خانوادهٔ باگی است که برای پنجره‌های ساختمان با
 * `surfaceBias` حل شد، ولی پنل‌های کشتی آن را دور می‌زدند.
 *
 * ────────────────────────────── سنجش ──────────────────────────────
 * مقایسهٔ مستقیم کلید: برای هر جفت هم‌پوشانِ (وجه پنل، وجه طبقه) که مرکز
 * پنل داخل چندضلعیِ همان وجه طبقه روی صفحه است (یعنی همان وجه، دیوارِ
 * میزبان است)، باید کلید پنل کوچک‌تر از کلید دیوار باشد؛ در مرتب‌سازی
 * نزولی یعنی پنل **بعد** از دیوار رسم می‌شود. هر نقض = پنجره‌ای که دیوار
 * رویش افتاده.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class ShipPanelOrderTest {

    @Test
    fun mountedPanelsNeverSortBehindTheirHostWall() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        var panels = 0
        var compared = 0
        var lowCompared = 0
        var violations = 0
        var maxDelta = 0f
        val samples = ArrayList<String>()

        fun run(levelId: String, quality: Int, pick: (WorldRenderer.ShipDiag) -> Boolean, label: String) {
            ShipSortProbe.sweep(activity, levelId, quality, 5L, pick) { scene, ship, pose ->
                val faces = scene.debugFaces().filter { it.group == ship.group && it.poly.size >= 6 }
                val mounted = faces.filter {
                    it.part == Rig.PART_PANEL && kotlin.math.abs(it.nyE) < 0.6f &&
                        ShipSortProbe.screenArea(it) > 25f
                }
                val walls = faces.filter { it.part in Rig.PART_ISLAND_TIER until Rig.PART_ISLAND_CAP }
                if (mounted.isEmpty()) return@sweep
                panels += mounted.size
                for (p in mounted) {
                    val px = ShipSortProbe.polyCx(p.poly)
                    val py = ShipSortProbe.polyCy(p.poly)
                    // دیوار میزبان: وجه طبقه‌ای که (۱) مرکز پنل روی صفحه داخلش
                    // است، (۲) نرمالش موازیِ نرمال پنل است (همان سمتِ سازه) و
                    // (۳) نزدیک‌ترین کلید را دارد. شرط (۲) جلوی نقض کاذب با
                    // دیوارهای عمود (مثلاً دیوار کناری برای شیشهٔ جلو) را
                    // می‌گیرد؛ این‌ها مرکز پنل را هم در بر می‌گیرند ولی میزبان
                    // نیستند.
                    val pnx = p.nxE; val pnz = p.nzE
                    var host: ir.shahed.pahpad.game.Scene3D.FaceSnapshot? = null
                    var hostGap = Float.MAX_VALUE
                    for (w in walls) {
                        if (kotlin.math.abs(w.nyE) > 0.6f) continue
                        val dot = pnx * w.nxE + pnz * w.nzE
                        if (dot < 0.9f) continue
                        // پنل باید **بیرونِ** صفحهٔ همان دیوار باشد، نه ده‌ها
                        // متر آن‌سویش. دیوار دورِ همان طبقه هم مرکز پنل را روی
                        // صفحه در بر می‌گیرد (وقتی دوربین از پشت نگاه می‌کند)،
                        // ولی پنل آن‌سوی آن دیوار است و پشتش پنهان می‌ماند؛
                        // مقایسه با آن نقض کاذب است.
                        val along = p.nxE * (p.wx - w.wx) + p.nzE * (p.wz - w.wz)
                        if (along < -2.5f || along > 3f) continue
                        if (!ShipSortProbe.bboxOverlap(p, w)) continue
                        if (!ShipSortProbe.pointInPoly(px, py, w.poly)) continue
                        if (!ShipSortProbe.polysOverlap(p.poly, w.poly)) continue
                        val gap = kotlin.math.abs(ShipSortProbe.keyOf(p) - ShipSortProbe.keyOf(w))
                        if (gap < hostGap) { hostGap = gap; host = w }
                    }
                    val w = host ?: continue
                    compared++
                    if (pose.height < 40f) lowCompared++
                    val d = ShipSortProbe.keyOf(p) - ShipSortProbe.keyOf(w)
                    if (d > 0.05f) {
                        violations++
                        if (d > maxDelta) maxDelta = d
                        if (samples.size < 8) {
                            samples.add(
                                "$label pose=(${pose.height.toInt()}m r=${pose.radius.toInt()}) " +
                                    "panel=${"%.3f".format(ShipSortProbe.keyOf(p))} wall=${"%.3f".format(ShipSortProbe.keyOf(w))} " +
                                    "Δ=${"%.3f".format(d)} gap=${"%.3f".format(hostGap)}"
                            )
                        }
                    }
                }
            }
        }

        // ناو هواپیمابر (پنجرهٔ جزیره) + ناوشکن هدف + یک کشتی باری (شیشهٔ پل).
        run("L4_4", 2, { sd ->
            val t = sd.h > 25f
            t
        }, "carrier")
        run("L2_5", 2, { sd ->
            sd.d > 100f && sd.h > 15f
        }, "warship")
        run("L4_4", 2, { sd ->
            sd.d > 120f && sd.h < 18f
        }, "freighter")

        println("PANELORDER پنل=$panels جفت‌مقایسه=$compared (کم‌ارتفاع=$lowCompared) نقض=$violations بیشینهٔΔ=${"%.3f".format(maxDelta)}")
        samples.forEach { println("   $it") }

        assertTrue("هیچ پنل نصب‌شده‌ای رندر نشد — تست بی‌معنا است", panels > 0)
        assertTrue("هیچ جفت پنل/دیوار هم‌پوشانی نداشت — تست بی‌معنا است", compared > 100)
        assertTrue("سویپ هیچ نمای کم‌ارتفاعی نداشت — حالت باگ پوشش داده نشده", lowCompared > 20)
        assertEquals(
            "کلید پنل از کلید دیوار میزبانش بزرگ‌تر شد ($violations نقض از $compared جفت، " +
                "بیشینهٔ Δ=${"%.3f".format(maxDelta)} متر) — دیوار روی پنجره کشیده می‌شود و پنجره غیب می‌شود",
            0, violations
        )
    }
}
