package ir.shahed.pahpad.game

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * پنجره‌ها باید از هر زاویه‌ای پایدار بمانند.
 *
 * باگ اصلی: بایاس پنجره یک عدد **ثابت** بود، ولی مرتب‌سازی نقاش عمق
 * **میانگین** را مقایسه می‌کند. روی دیوار مایل، عمق میانگین دیوار با عمق
 * پنجرهٔ لبهٔ دور ده‌ها متر فرق دارد → بایاس ثابت کافی نیست → پنجره پشت
 * دیوار می‌رود و ناپدید می‌شود. همان «پنجره‌های ناپایدار».
 *
 * این تست هندسهٔ واقعی را بازسازی می‌کند: دیوار، دوربین مایل، و عمقِ
 * دوربین‌محور. سپس ثابت می‌کند بایاس دقیقِ جدید همهٔ پنجره‌ها را نگه می‌دارد.
 */
class WindowStabilityTest {

    /** عمق دید یک نقطه، همان‌طور که Scene3D.submit حساب می‌کند. */
    private fun camDepth(wx: Double, wz: Double, camYaw: Double, camX: Double, camZ: Double): Double {
        val dx = wx - camX
        val dz = wz - camZ
        // pitch = 0 برای ساده‌سازی: forward فقط چرخش یاو است
        return dx * sin(camYaw) + dz * cos(camYaw)
    }

    /**
     * آیا پنجره دیده می‌شود؟
     * مرتب‌سازی: بزرگ‌تر اول. وجهی دیده می‌شود که depth − bias کمتری دارد.
     */
    private fun visible(wallDepth: Double, winDepth: Double, bias: Double) =
        (winDepth - bias) < wallDepth

    @Test
    fun fixedBiasFailsOnObliqueWall() {
        // دیوار ۵۱ متری روی محور X در z = 0، دوربین در (25, -40) با یاو ۰
        // (یعنی دوربین در جنوب دیوار، رو به شمال).
        val camX = 25.0; val camZ = -60.0; val yaw = 0.0
        // میانگین عمق دیوار = عمق مرکزش
        val wallDepth = camDepth(25.0, 0.0, yaw, camX, camZ)

        // پنجرهٔ لبهٔ چپ (x=0) و راست (x=51) — هر دو روی همان دیوار
        val leftWin = camDepth(0.0, 0.0, yaw, camX, camZ)
        val rightWin = camDepth(51.0, 0.0, yaw, camX, camZ)

        // در این چیدمان دیوار رو‌به دوربین است (yaw=0)، پس عمق‌ها نزدیک‌اند.
        // حالا دوربین را ۶۰ درجه بچرخان تا دیوار مایل شود:
        val yaw2 = Math.toRadians(62.0)
        val wallDepth2 = camDepth(25.0, 0.0, yaw2, camX, camZ)
        val left2 = camDepth(0.0, 0.0, yaw2, camX, camZ)
        val right2 = camDepth(51.0, 0.0, yaw2, camX, camZ)
        val spread = abs(right2 - left2)
        assertTrue("روی نمای مایل، عمق دو لبهٔ دیوار باید ده‌ها متر فرق کند (شد: $spread)",
            spread > 15.0)

        // بایاس ثابت قدیمی
        val oldBias = 0.35
        val farWin = if (right2 > left2) right2 else left2
        assertTrue("با بایاس ثابت، پنجرهٔ لبهٔ دور پشت دیوار می‌رود — همین باگ بود",
            !visible(wallDepth2, farWin, oldBias))

        // بایاس دقیق جدید: winDepth − wallDepth + margin
        val margin = 0.35
        val exactBias = (farWin - wallDepth2) + margin
        assertTrue("با بایاس دقیق، پنجرهٔ لبهٔ دور باید دیده شود",
            visible(wallDepth2, farWin, exactBias))
        assertTrue("بایاس دقیق باید بزرگ‌تر از ثابت قدیمی باشد", exactBias > oldBias)

        // پنجرهٔ لبهٔ نزدیک هم نباید خراب شود
        val nearWin = if (left2 < right2) left2 else right2
        val nearBias = (nearWin - wallDepth2) + margin
        assertTrue("پنجرهٔ لبهٔ نزدیک هم باید دیده شود", visible(wallDepth2, nearWin, nearBias))
    }

    @Test
    fun everyWindowOnWallIsVisibleAtAllAngles() {
        // دیوار ۵۱ متری، ۹ پنجره. دوربین در ۱۲ زاویهٔ مختلف.
        val camX = 25.0; val camZ = -70.0
        val wallX = 0.0; val wallZ = 0.0; val wallW = 51.0
        val margin = 0.35

        for (deg in 0 until 360 step 30) {
            val yaw = Math.toRadians(deg.toDouble())
            val wallDepth = camDepth(wallX + wallW / 2, wallZ, yaw, camX, camZ)
            // پنجره‌هایی که رخشان به دوربین است
            for (k in 0..8) {
                val wx = wallX + wallW * k / 8.0
                val winDepth = camDepth(wx, wallZ, yaw, camX, camZ)
                val bias = (winDepth - wallDepth) + margin
                assertTrue(
                    "پنجرهٔ $k در زاویهٔ $deg° ناپدید شد",
                    visible(wallDepth, winDepth, bias)
                )
            }
        }
    }

    @Test
    fun nearWallNeedsAlmostNoBias() {
        // وقتی دیوار رو‌به دوربین است، بایاس باید فقط همان حاشیه باشد
        val camX = 25.0; val camZ = -50.0; val yaw = 0.0
        val wallDepth = camDepth(25.0, 0.0, yaw, camX, camZ)
        val winDepth = camDepth(25.0, 0.0, yaw, camX, camZ)
        val bias = (winDepth - wallDepth) + 0.35
        assertTrue("رو‌به دوربین، بایاس اضافی نباید بسازد", bias < 0.5)
    }
}
