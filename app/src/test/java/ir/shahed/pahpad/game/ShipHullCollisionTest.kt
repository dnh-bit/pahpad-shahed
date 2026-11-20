package ir.shahed.pahpad.game

import ir.shahed.pahpad.data.Levels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin

/**
 * برخورد کشتی‌ها باید هم‌شکل بدنه‌ی باریک‌شونده باشد: کنار دماغه و
 * پاشنه — جایی که بدنه باریک شده و آب دیده می‌شود — نباید «دیوار
 * نامرئی» باشد (فیلم کاربر: انفجار پهباد در هوای خالی کنار ناوچه)،
 * ولی وسط بدنه و خودِ دماغه همچنان جامد است.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ShipHullCollisionTest {

    /** نقطه‌ی محلی (lx, lz) روی پراپ را به مختصات دنیا می‌برد. */
    private fun worldPoint(p: Prop, lx: Float, lz: Float): FloatArray {
        val c = cos(p.rot); val s = sin(p.rot)
        return floatArrayOf(p.x + lx * c - lz * s, p.z + lx * s + lz * c)
    }

    @Test fun noInvisibleWallBesideTheBow() {
        val world = World(Levels.byId("L2_5")!!, 3L)
        val ship = world.props.first { it.kind == PropKind.SHIP }
        val y = ship.h * 0.5f

        // کنار دماغه: داخل مستطیل قدیمی، ولی بیرون از بدنه‌ی باریک‌شده
        val besideBow = worldPoint(ship, ship.w * 0.45f, ship.d * 0.47f)
        assertFalse("beside the tapered bow must be open water",
            world.hitsProp(besideBow[0], y, besideBow[1]))

        // کنار پاشنه هم همین‌طور (بدنه در عقب هم باریک می‌شود)
        val besideStern = worldPoint(ship, ship.w * 0.45f, -ship.d * 0.47f)
        assertFalse("beside the tapered stern must be open water",
            world.hitsProp(besideStern[0], y, besideStern[1]))

        // وسط بدنه جامد است
        val midship = worldPoint(ship, ship.w * 0.45f, 0f)
        assertTrue("midship hull must be solid",
            world.hitsProp(midship[0], y, midship[1]))

        // بیرون از پهن‌ترین نقطه‌ی بدنه آب آزاد است
        val outside = worldPoint(ship, ship.w * 0.62f, 0f)
        assertFalse("beyond the beam must be open water",
            world.hitsProp(outside[0], y, outside[1]))

        // پرواز جاروب‌شده از کنار دماغه رد می‌شود، ولی به وسط بدنه می‌خورد
        val bowOut = worldPoint(ship, ship.w * 0.45f, ship.d * 0.47f)
        val bowOut2 = worldPoint(ship, ship.w * 0.45f, ship.d * 0.30f)
        assertFalse("swept flight along the bow taper must pass",
            world.hitsPropSwept(bowOut[0], y, bowOut[1], bowOut2[0], y, bowOut2[1]))
        val sideOut = worldPoint(ship, ship.w * 0.62f, 0f)
        val sideIn = worldPoint(ship, ship.w * 0.20f, 0f)
        assertTrue("swept flight into midship must hit",
            world.hitsPropSwept(sideOut[0], y, sideOut[1], sideIn[0], y, sideIn[1]))
    }
}
