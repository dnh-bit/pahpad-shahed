package ir.shahed.pahpad.game

import ir.shahed.pahpad.data.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * مراحل دریایی: هیچ پدافند و راداری نباید روی آب بنشیند (دستور کاربر).
 * هر سامانه یا روی عرشه‌ی یک کشتی/هدف سوار است یا روی جزیره (y > ۰)،
 * و سامانه‌ی سوار بر هدف متحرک باید همراهش حرکت کند و با غرق شدنش بمیرد.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class NavalDefenseTest {

    @Test fun carrierLevelDistributesSitesAcrossTheFleet() {
        val level = Levels.byId("L4_4")!!
        for (seed in 1L..10L) {
            val world = World(level, seed)
            assertEquals("seed=$seed", 5, world.aaSites.size)
            // حداکثر یکی روی خود ناو هدف — بقیه پخش روی ناوهای دیگر/جزیره‌ها
            val onTarget = world.aaSites.count { it.hostTarget != null }
            assertEquals("seed=$seed at most one site rides the target", 1, onTarget)
            for (a in world.aaSites) {
                assertTrue("seed=$seed no site may sit on water (y=${a.y})",
                    a.hostTarget != null || a.y > 0.5f)
            }
            // هیچ دو سامانه‌ای نباید روی هم انباشته شوند
            for (i in world.aaSites.indices) {
                for (j in i + 1 until world.aaSites.size) {
                    val a = world.aaSites[i]; val b = world.aaSites[j]
                    val dx = a.x - b.x; val dz = a.z - b.z
                    assertTrue("seed=$seed sites $i/$j must be spread out",
                        dx * dx + dz * dz > 25f * 25f)
                }
            }
            for (r in world.radars) {
                assertTrue("seed=$seed radar must sit on its island", r.y > 5f)
            }
            assertTrue("seed=$seed islands must exist",
                world.props.any { it.kind == PropKind.ISLAND })
        }
    }

    @Test fun destroyerLevelNeverLeavesDefensesOnWater() {
        val level = Levels.byId("L2_5")!!
        for (seed in 1L..10L) {
            val world = World(level, seed)
            assertEquals("seed=$seed", 3, world.aaSites.size)
            var hostedOnTarget = 0
            for (a in world.aaSites) {
                assertTrue("seed=$seed no site may sit on water (y=${a.y})",
                    a.hostTarget != null || a.y > 0.5f)
                if (a.hostTarget != null) hostedOnTarget++
            }
            assertEquals("seed=$seed one site rides the destroyer", 1, hostedOnTarget)
            for (r in world.radars) {
                assertTrue("seed=$seed radar must sit on its island", r.y > 5f)
            }
        }
    }

    @Test fun hostedSiteFollowsMovingTargetAndDiesWithIt() {
        val world = World(Levels.byId("L2_5")!!, 7L)
        val site = world.aaSites.first { it.hostTarget != null }
        val target = site.hostTarget!!
        val x0 = site.x; val z0 = site.z
        repeat(20) { world.update(0.25f) }
        assertTrue("hosted site must move with its ship", site.x != x0 || site.z != z0)
        assertEquals("hosted site stays at deck height", site.hostDeckY, site.y, 0.001f)
        // موقعیت باید دقیقاً آفست چرخیده‌ی روی عرشه باشد
        val s = kotlin.math.sin(target.heading); val c = kotlin.math.cos(target.heading)
        assertEquals(target.x + site.hostLX * c - site.hostLZ * s, site.x, 0.01f)
        assertEquals(target.z + site.hostLX * s + site.hostLZ * c, site.z, 0.01f)

        target.destroyed = true
        world.update(0.1f)
        assertFalse("site must die with its host ship", site.alive)
    }

    @Test fun freighterSitesNeverSitInsideTheContainerBlock() {
        // رگرسیون بچ ۸: نقطه‌ی نشستن روی کشتی باری +۰٫۳۶ طول بود — دقیقاً
        // توی ردیف چهارم کانتینرها (نوار کانتینر در دستگاه محلی کشتی:
        // z از ‎-۰٫۱۷ تا +۰٫۴۵ طول). سامانه باید در شکاف خالی عرشه بنشیند.
        for (id in listOf("L4_4", "L2_5")) {
            val level = Levels.byId(id)!!
            for (seed in 1L..10L) {
                val world = World(level, seed)
                for (a in world.aaSites) {
                    val host = a.hostProp ?: continue
                    if (host.kind != PropKind.FREIGHTER) continue
                    val dx = a.x - host.x; val dz = a.z - host.z
                    val s = kotlin.math.sin(host.rot); val c = kotlin.math.cos(host.rot)
                    val lz = -dx * s + dz * c
                    assertTrue("seed=$seed site must be on the ship (|lz|=${kotlin.math.abs(lz)}, d=${host.d})",
                        kotlin.math.abs(lz) < host.d * 0.5f)
                    assertTrue("seed=$seed site inside container block (lz=$lz, d=${host.d})",
                        lz < -host.d * 0.17f - 3f || lz > host.d * 0.45f + 3f)
                }
            }
        }
    }

    @Test fun warshipSitesSitOnTheHelipad() {
        // رگرسیون بچ ۲۹: روی ناوشکن/ناو، سامانه باید در نوار سکوی پد (نیمهٔ
        // عقبی عرشه) بنشیند: lz بین ‎-۰٫۵d و ‎-۰٫۳۴d با تلورانس ۱ متر —
        // نه روی جزیرهٔ فرمان و نه داخل برجک‌ها.
        var checked = 0
        for (id in listOf("L2_5", "L4_4")) {
            val level = Levels.byId(id)!!
            for (seed in 1L..10L) {
                val world = World(level, seed)
                for (a in world.aaSites) {
                    val host = a.hostProp
                    if (host != null && host.kind == PropKind.SHIP) {
                        val dx = a.x - host.x; val dz = a.z - host.z
                        val s = kotlin.math.sin(host.rot); val c = kotlin.math.cos(host.rot)
                        val lz = -dx * s + dz * c
                        val d = host.d
                        assertTrue("seed=$seed ship-prop site off the pad band (lz=$lz, d=$d)",
                            -0.50f * d - 1f <= lz && lz <= -0.34f * d + 1f)
                        checked++
                    }
                    val t = a.hostTarget
                    if (t != null && t.shape == TargetShape.SHIP) {
                        val d = t.d
                        assertTrue("seed=$seed ship-target site off the pad band (hostLZ=${a.hostLZ}, d=$d)",
                            -0.50f * d - 1f <= a.hostLZ && a.hostLZ <= -0.34f * d + 1f)
                        checked++
                    }
                }
            }
        }
        // ضدخلأ: تست نباید بی‌صدا از کنار هیچ سامانه‌ای رد شود.
        assertTrue("no warship-hosted site was checked", checked > 0)
    }

    @Test fun landLevelsKeepDefensesOnTheGround() {
        val world = World(Levels.byId("L1_1")!!, 3L)
        for (a in world.aaSites) {
            assertTrue(a.hostTarget == null)
            assertEquals(0f, a.y, 0.001f)
        }
        for (r in world.radars) assertEquals(0f, r.y, 0.001f)
        assertTrue(world.props.none { it.kind == PropKind.ISLAND })
    }
}
