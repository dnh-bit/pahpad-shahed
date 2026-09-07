package ir.shahed.pahpad.game

import android.content.Context
import android.graphics.Canvas
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * لایه‌ی نمایش دنیا. مراحل، کنترل‌ها و ذخیره‌ها بدون تغییر می‌مانند.
 *
 * سخت‌افزار نظامی از `Vehicles` می‌آید: هندسه‌ی رویه‌ای با ابعاد واقعی، سه سطح جزئیات
 * بر اساس فاصله‌ی دوربین، و بافت‌های اختصاصی (استتار، ورق فلز، عرشه، زنجیر، فلز سوخته).
 */
class WorldRenderer(private val gfxContext: Context) {

    val scene = Scene3D()
    val fx = Fx()

    private val art = SceneryArt(gfxContext)
    private val drone = DroneArt(gfxContext)
    private val hardware = Vehicles(gfxContext)
    private val rig = Rig(scene)

    private val proj = FloatArray(3)
    private var exhaustTimer = 0f
    private var wreckTimer = 0f
    private var sparkTimer = 0f

    companion object {
        /** مرز سطح جزئیات کامل و متوسط بر حسب متر */
        private const val NEAR_LOD = 250f
        private const val MID_LOD = 820f
        private const val CULL = 1700f
    }

    private fun lod(d2: Float): Int = when {
        d2 < NEAR_LOD * NEAR_LOD -> 0
        d2 < MID_LOD * MID_LOD -> 1
        else -> 2
    }

    /**
     * ساختمان‌های شهری صدها عدد هستند، پس آستانه‌ی جزئیات آن‌ها بسیار تنگ‌تر است؛
     * وگرنه تعداد وجه‌های یک فریم به چند هزار می‌رسد و نرخ فریم می‌افتد.
     */
    private fun buildingLod(d2: Float): Int = when {
        d2 < 175f * 175f -> 0
        d2 < 420f * 420f -> 1
        else -> 2
    }

    fun update(dt: Float, mission: Mission) {
        drone.update(dt, mission)
        fx.update(dt)
        if (mission.fpv) fx.clearEngineSmoke()
        if (mission.phase == Mission.Phase.FLYING && !mission.fpv) {
            exhaustTimer += dt
            val interval = if (mission.boost) .075f else .12f
            if (exhaustTimer >= interval) {
                exhaustTimer %= interval
                val cp = cos(mission.pitch)
                fx.engineSmoke(
                    mission.x - sin(mission.yaw) * cp * 5.5f,
                    mission.y - sin(mission.pitch) * 5.5f - .2f,
                    mission.z - cos(mission.yaw) * cp * 5.5f, mission.boost
                )
            }
        } else exhaustTimer = 0f
        wreckTimer += dt
        sparkTimer += dt
        if (wreckTimer >= .12f) {
            wreckTimer %= .12f
            for (t in mission.world.targets) if (t.destroyed && t.burn < 6f) fx.smoke(t.x, t.h * .6f, t.z, 2f)
        }
        if (sparkTimer >= .045f) {
            sparkTimer %= .045f
            for (a in mission.world.aaSites) {
                if (a.alive && a.flash > 0f) {
                    fx.spark(
                        a.x + sin(a.barrelYaw) * 8f, a.muzzleHeight,
                        a.z + cos(a.barrelYaw) * 8f, Theme.AMBER
                    )
                }
            }
        }
    }

    fun release() {
        fx.clear()
        scene.clear()
        art.clear()
    }

    private fun material(building: Boolean = true) {
        scene.wallTexture = Gfx.get(gfxContext, if (building) "material_wall" else "material_concrete")
        scene.roofTexture = Gfx.get(gfxContext, "material_roof")
    }

    private fun plain() {
        scene.wallTexture = null
        scene.roofTexture = null
    }

    fun draw(c: Canvas, mission: Mission, w: Float, h: Float) {
        val world = mission.world
        scene.fogStart = 300f
        scene.fogEnd = 1650f
        scene.begin()
        art.draw(c, scene, world, w, h)
        fx.drawGround(c, scene)
        art.roads(scene, world)
        plain()

        val camX = scene.cam.x
        val camZ = scene.cam.z
        val maxD2 = CULL * CULL

        // ----------------------------------------------------------- موانع صحنه
        for (p in world.props) {
            val dx = p.x - camX
            val dz = p.z - camZ
            val d2 = dx * dx + dz * dz
            if (d2 > maxD2) continue
            val detail = lod(d2)
            val shadowRange = if (p.kind == PropKind.BUILDING) 320f else 550f
            if (d2 < shadowRange * shadowRange && p.kind != PropKind.SHIP && p.kind != PropKind.FREIGHTER) {
                art.shadow(
                    c, scene, p.x - p.h * scene.sunX / scene.sunY, p.z - p.h * scene.sunZ / scene.sunY,
                    p.w * .8f, p.d * .75f, .65f
                )
            }
            when (p.kind) {
                PropKind.TREE, PropKind.PALM, PropKind.BUSH, PropKind.ROCK -> billboard(p)
                // ناوهای پس‌زمینه یک پله ساده‌تر از هدف اصلی رندر می‌شوند
                PropKind.SHIP -> hardware.warship(rig, p.x, p.z, p.rot, p.w, p.h, p.d, false,
                    (detail + 1).coerceAtMost(2))
                PropKind.FREIGHTER -> hardware.freighter(rig, p.x, p.z, p.rot, p.w, p.h, p.d)
                PropKind.TOWER_MAST -> {
                    plain()
                    scene.box(p.x, 0f, p.z, p.w * .28f, p.h, p.d * .28f, p.color, p.rot)
                }
                PropKind.BUILDING -> {
                    val bd = buildingLod(d2)
                    if (bd == 2) {
                        material()
                        scene.box(p.x, 0f, p.z, p.w, p.h, p.d, 0xFF9E9E90.toInt(), p.rot)
                        plain()
                    } else {
                        hardware.building(rig, p.x, p.z, p.w, p.h, p.d, p.rot, false, bd)
                    }
                }
            }
        }

        // ----------------------------------------------- مناطق رادار و اخلال
        for (r in world.radars) {
            zone(c, r.x, r.z, r.radius, Theme.BLUE)
            val dx = r.x - camX
            val dz = r.z - camZ
            val d2 = dx * dx + dz * dz
            // ایستگاه رادار بزرگ‌تر از مقیاس واقعی است تا از ارتفاع پرواز خوانا بماند
            if (d2 <= maxD2) hardware.radarStation(rig, r.x, r.z, r.spin, 2.2f, lod(d2))
        }
        for (e in world.ewZones) zone(c, e.x, e.z, e.radius, Theme.VIOLET)

        // ------------------------------------------------------ پدافند زمینی
        for (a in world.aaSites) {
            if (!a.alive) continue
            val dx = a.x - camX
            val dz = a.z - camZ
            val d2 = dx * dx + dz * dz
            if (d2 > maxD2) continue
            zone(c, a.x, a.z, a.range, Theme.RED)
            val detail = lod(d2)
            if (d2 < 550f * 550f) art.shadow(c, scene, a.x + 3f, a.z - 2f, 7f, 9f, .5f)
            if (a.kind == AASite.KIND_GUN) {
                hardware.aaGun(rig, a.x, a.z, a.bodyYaw, a.barrelYaw, a.barrelPitch, 1.2f, true, a.flash, detail)
            } else {
                hardware.samLauncher(rig, a.x, a.z, a.bodyYaw, a.barrelYaw, a.barrelPitch, 1.2f, true, detail)
            }
        }

        // ------------------------------------------------------------- اهداف
        for (t in world.targets) {
            val dx = t.x - camX
            val dz = t.z - camZ
            val d2 = dx * dx + dz * dz
            if (d2 > maxD2) continue
            target(t, lod(d2))
        }

        plain()
        for (b in world.bonuses) if (!b.collected) scene.box(b.x, b.y, b.z, 4.5f, 4.5f, 4.5f, Theme.AMBER, b.spin)
        for (m in world.missiles) missile(m)

        if (!mission.fpv && mission.phase != Mission.Phase.ENDED) {
            val alt = mission.y.coerceAtLeast(0f)
            if (alt < 220f) {
                art.shadow(
                    c, scene, mission.x - alt * scene.sunX / scene.sunY, mission.z - alt * scene.sunZ / scene.sunY,
                    7f + alt * .035f, 7f + alt * .035f, (.40f - alt * .0015f).coerceAtLeast(.06f)
                )
            }
            drone.draw(scene, mission)
        }
        scene.flush(c)
        fx.draw(c, scene, mission.fpv)
    }

    // ------------------------------------------------------------------ اجزا

    private fun billboard(p: Prop) {
        val file = when (p.kind) {
            PropKind.PALM -> "prop_palm"
            PropKind.BUSH -> "prop_bush"
            PropKind.TREE -> if (p.h % 2f < 1f) "prop_tree1" else "prop_tree2"
            else -> if (p.w % 2f < 1f) "prop_rock1" else "prop_rock2"
        }
        val bitmap = Gfx.get(gfxContext, file)
        if (bitmap != null) {
            val leafy = p.kind != PropKind.ROCK
            val width = if (leafy) max(p.w, p.h * .95f) else p.w
            scene.billboard(p.x, 0f, p.z, width, p.h, bitmap)
        } else {
            plain()
            scene.box(p.x, 0f, p.z, p.w, p.h, p.d, p.color, p.rot)
        }
    }

    /** موشک پدافند: بدنه‌ی استوانه‌ای با شعله‌ی موتور، جای مکعب قبلی. */
    private fun missile(m: Missile) {
        val vx = m.vx
        val vy = m.vy
        val vz = m.vz
        val len = Math.sqrt((vx * vx + vy * vy + vz * vz).toDouble()).toFloat().coerceAtLeast(1f)
        val ux = vx / len
        val uy = vy / len
        val uz = vz / len
        rig.place(m.x, m.y, m.z, 0f, 1f)
        rig.tube(-ux * 2.1f, -uy * 2.1f, -uz * 2.1f, ux * 1.5f, uy * 1.5f, uz * 1.5f, 0.34f, 6, 0xFFB9BCB4.toInt())
        rig.tube(-ux * 4.6f, -uy * 4.6f, -uz * 4.6f, -ux * 2.0f, -uy * 2.0f, -uz * 2.0f, 0.26f, 6, Theme.ORANGE, false)
    }

    private fun zone(c: Canvas, x: Float, z: Float, radius: Float, color: Int) {
        val segments = 48
        var px = x + radius
        var pz = z
        for (i in 1..segments) {
            val a = i * 2f * PI.toFloat() / segments
            val nx = x + cos(a) * radius
            val nz = z + sin(a) * radius
            scene.line(px, .6f, pz, nx, .6f, nz, c, Theme.withAlpha(color, .36f), 1.4f)
            px = nx
            pz = nz
        }
    }

    private fun target(t: Target, detail: Int) {
        when (t.shape) {
            TargetShape.BUNKER -> hardware.bunker(rig, t.x, t.z, t.w, t.h, t.d, t.heading, t.destroyed, detail)
            TargetShape.BUILDING -> hardware.building(rig, t.x, t.z, t.w, t.h, t.d, t.heading, t.destroyed, detail)
            TargetShape.TOWER -> hardware.gantry(rig, t.x, t.z, t.w, t.h, t.d, t.heading, t.destroyed, detail)
            TargetShape.SHIP -> hardware.warship(rig, t.x, t.z, t.heading, t.w, t.h, t.d, t.destroyed, detail)
            TargetShape.CARRIER -> hardware.carrier(rig, t.x, t.z, t.heading, t.w, t.h, t.d, t.destroyed, detail)
            TargetShape.VEHICLE -> convoy(t, detail)
            TargetShape.BRIDGE -> hardware.bridgeSpan(rig, t.x, t.z, t.w, t.h, t.d, t.heading, t.destroyed, detail)
            TargetShape.ANTENNA -> hardware.latticeMast(rig, t.x, t.z, t.h, t.destroyed, detail)
            TargetShape.TRAIN -> train(t, detail)
            TargetShape.TUNNEL -> hardware.tunnelMouth(rig, t.x, t.z, t.w, t.h, t.d, t.heading, t.destroyed, detail)
        }
    }

    /** کاروان زرهی: تانک اصلی به‌عنوان هدف، با نفربر و کامیون همراه. */
    private fun convoy(t: Target, detail: Int) {
        val s = sin(t.heading)
        val c = cos(t.heading)
        // تانک روی مرکز هدف قرار می‌گیرد تا نقطه‌ی برخورد با تصویر بخواند
        hardware.tank(rig, t.x, t.z, t.heading, 1.15f, t.heading - 0.35f, t.destroyed, detail)
        if (detail <= 1) {
            hardware.apc(rig, t.x + s * 14f, t.z + c * 14f, t.heading, 1.1f, t.destroyed, detail)
            hardware.truck(rig, t.x - s * 15f, t.z - c * 15f, t.heading, 1.1f, t.destroyed, detail)
        }
    }

    /** قطار: لکوموتیو، واگن مهمات هدف و واگن‌های همراه. */
    private fun train(t: Target, detail: Int) {
        val s = sin(t.heading)
        val c = cos(t.heading)
        hardware.locomotive(rig, t.x + s * 56f, t.z + c * 56f, t.heading, t.destroyed, detail)
        for (i in -2..1) {
            val off = i * 24f
            hardware.wagon(
                rig, t.x + s * off, t.z + c * off, t.heading,
                if (i == 0) 0 else (i + 3) % 3, t.destroyed, detail
            )
        }
    }

    /** مختصات مشترک و بدون چرخش: همان قرارداد قبلی HUD. */
    fun project(x: Float, y: Float, z: Float): FloatArray? =
        if (scene.cam.project(x, y, z, proj)) proj else null
}
