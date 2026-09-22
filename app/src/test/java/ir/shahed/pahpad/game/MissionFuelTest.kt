package ir.shahed.pahpad.game

import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * باگ «کم نشدن نوار سوخت اول هر مرحله»:
 * سوخت اولیه عمداً بزرگ‌تر از `maxRange` است (حداقل ۱٫۴۵ برابر فاصلهٔ ماموریت
 * با ۱۵٪ ذخیره)؛ نوار باید نسبت به سوخت آغاز همین پهباد سنجیده شود نه نسبت به
 * `maxRange`، وگرنه تا مدّتی روی ۱۰۰٪ پین می‌ماند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class MissionFuelTest {

    @Test
    fun fuelBarStartsFullAndDropsAsSoonAsDroneFlies() {
        val app = RuntimeEnvironment.getApplication()
        val save = SaveManager(app)
        val level = Levels.byId("L1_1") ?: error("L1_1 must exist")
        val mission = Mission(level, save, MissionMode.STORY, seed = 42L)

        assertEquals("آغاز ماموریت باید سوخت ۱۰۰٪ باشد", 1f, mission.fuel01, 0.0001f)
        assertTrue("سوخت اولیه باید بزرگ‌تر از برد نهایی باشد (برای تضمین مسیر)",
            mission.fuelLeft > mission.maxRange)

        mission.launch()
        val startFuel = mission.fuelLeft
        // پرواز مستقیم تا مصرف حداقل ۶۰ متر (باد L1_1 صفر است)
        var frames = 0
        while (startFuel - mission.fuelLeft < 60f && frames < 3000) {
            mission.update(1f / 60f)
            frames++
        }
        assertTrue("پهباد باید واقعاً سوخت مصرف کرده باشد (frames=$frames)",
            startFuel - mission.fuelLeft >= 60f)
        assertTrue(
            "نوار سوخت باید بلافاصله بعد از پرواز از ۱۰۰٪ پایین بیاید " +
                "(باگ پین‌شدن: fuel01=${mission.fuel01}, fuelLeft=${mission.fuelLeft})",
            mission.fuel01 < 0.999f
        )
        assertTrue("نوار نباید منفی یا بیشتر از ۱ شود", mission.fuel01 > 0f)
    }
}
