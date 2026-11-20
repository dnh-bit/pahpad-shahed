package ir.shahed.pahpad.game

import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.sqrt

/**
 * باگ «پدافندها اصلاً نمی‌توانند شلیک کنند» (گزارش کاربر روی بچ ۶):
 * دهانه‌ی سامانه‌ی روی عرشه داخل حجم برخورد کشتیِ میزبان است (عرشه در
 * ۰٫۶ ارتفاع بدنه، سقف حجم برخورد تا خود ارتفاع) و موشک در همان گام
 * اول به کشتیِ خودش می‌خورد و بی‌صدا حذف می‌شد؛ سامانه فلش می‌زد ولی
 * موشکی پرواز نمی‌کرد. موشک نباید با سکوی پرتاب خودش برخورد کند.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class AaFireTest {

    @Test fun missileSpawnedOnDeckIsInsideHostVolume_bugProof() {
        val world = World(Levels.byId("L4_4")!!, 5L)
        val site = world.aaSites.firstOrNull { it.hostProp != null }
        assertNotNull("باید دست‌کم یک سامانه روی پراپ میزبان باشد", site)
        site!!
        // گام اول موشک از دهانه رو به بالا: تست عادی = برخورد با میزبان
        val sx = site.x; val sy = site.muzzleHeight; val sz = site.z
        assertTrue(
            "دهانه داخل حجم میزبان است — بدون معافیت، موشک همان‌جا می‌میرد",
            world.projectileHitsProp(sx, sy, sz, sx, sy + 3f, sz)
        )
        assertFalse(
            "با معافیت میزبان، مسیر شلیک آزاد است",
            world.projectileHitsPropExcept(sx, sy, sz, sx, sy + 30f, sz, site.hostProp)
        )
    }

    @Test fun hostedSiteActuallyFiresAndMissileFlies() {
        val app = RuntimeEnvironment.getApplication()
        val save = SaveManager(app)
        val mission = Mission(Levels.byId("L4_4")!!, save, MissionMode.STORY, 5L)
        val site = mission.world.aaSites.first { it.hostProp != null }
        mission.launch()

        var sawMissile = false
        var maxTravel = 0f
        // ۸ ثانیه‌ی شبیه‌سازی: پهپاد در برد سامانه نگه داشته می‌شود
        for (i in 0 until 480) {
            mission.x = site.x + 40f
            mission.y = site.y + 55f
            mission.z = site.z + 40f
            mission.update(1f / 60f)
            for (m in mission.world.missiles) {
                sawMissile = true
                val dx = m.x - site.x; val dy = m.y - site.muzzleHeight; val dz = m.z - site.z
                val d = sqrt(dx * dx + dy * dy + dz * dz)
                if (d > maxTravel) maxTravel = d
            }
            if (maxTravel > 60f) break
        }
        assertTrue("سامانه‌ی روی کشتی باید شلیک کند", sawMissile)
        assertTrue(
            "موشک باید واقعاً پرواز کند، نه اینکه در میزبان بمیرد (maxTravel=$maxTravel)",
            maxTravel > 60f
        )
    }
}
