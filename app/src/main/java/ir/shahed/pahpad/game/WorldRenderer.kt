package ir.shahed.pahpad.game

import android.content.Context
import android.graphics.Canvas
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
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
    private var clock = 0f

    // Identity keys survive movement/rotation without sharing history between objects.
    private val lodStates = java.util.IdentityHashMap<Any, Int>()
    private var lodWorld: World? = null

    companion object {
        private const val NEAR_LOD = 250f
        private const val MID_LOD = 820f
        private const val BUILDING_NEAR_LOD = 175f
        private const val BUILDING_MID_LOD = 420f
        private const val LOD_HYSTERESIS = 0.10f
        private const val CULL = 1700f
    }

    /** Schmitt trigger: retain detail inside a +/-10% distance band. */
    private fun lod(key: Any, distance2: Float, building: Boolean = false): Int {
        val near = if (building) BUILDING_NEAR_LOD else NEAR_LOD
        val mid = if (building) BUILDING_MID_LOD else MID_LOD
        var state = lodStates[key] ?: when {
            distance2 < near * near -> 0
            distance2 < mid * mid -> 1
            else -> 2
        }
        // Loops also handle camera teleports across both thresholds in one frame.
        while (state < 2) {
            val boundary = (if (state == 0) near else mid) * (1f + LOD_HYSTERESIS)
            if (distance2 > boundary * boundary) state++ else break
        }
        while (state > 0) {
            val boundary = (if (state == 1) near else mid) * (1f - LOD_HYSTERESIS)
            if (distance2 < boundary * boundary) state-- else break
        }
        lodStates[key] = state
        return state
    }

    /**
     * وضعیت «پنجرهٔ مجزا» بر اساس اندازهٔ پنجره روی صفحه (پیکسل) با هیسترزیس.
     * پنجره‌های مجزا فقط وقتی بزرگ‌اند رسم می‌شوند؛ زیرشان همیشه نوار شیشه هست،
     * پس گذر از مرز هرگز پاپ‌کردن ایجاد نمی‌کند.
     */
    private val winStates = java.util.IdentityHashMap<Any, Int>()
    private val WIN_ENTER_PX = 9f
    private val WIN_EXIT_PX = 7f

    private fun windowLodEnabled(key: Any, windowPx: Float): Boolean {
        var s = winStates[key] ?: if (windowPx >= WIN_ENTER_PX) 1 else 0
        if (s == 0 && windowPx >= WIN_ENTER_PX) s = 1
        if (s == 1 && windowPx <= WIN_EXIT_PX) s = 0
        winStates[key] = s
        return s == 1
    }

    fun update(dt: Float, mission: Mission) {
        clock += dt
        art.advance(dt)
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
            for (t in mission.world.targets) {
                if (!t.destroyed) continue
                // Fresh wreck: flames + thick smoke, then a thinning plume for a while.
                if (t.burn < 7f) {
                    fx.smoke(t.x, t.h * .55f, t.z, 2.2f)
                    fx.flame(t.x, t.h * .35f, t.z, 1.6f)
                } else if (t.burn < 22f && (t.burn * 10f).toInt() % 3 == 0) {
                    fx.smoke(t.x, t.h * .5f, t.z, 1.4f)
                }
            }
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
        lodStates.clear()
        winStates.clear()
        lodWorld = null
    }

    private fun plain() {
        scene.wallTexture = null
        scene.roofTexture = null
    }

    fun draw(c: Canvas, mission: Mission, w: Float, h: Float) {
        val world = mission.world
        if (lodWorld !== world) {
            lodStates.clear()
            winStates.clear()
            lodWorld = world
        }
        look = EnvLook.of(world.level.env)
        scene.configure(look)
        scene.begin()
        art.draw(c, scene, world, w, h)
        // Ground layer first: roads are flushed on their own so that every ground
        // decal drawn afterwards (shadows, threat rings, blast rings) sits ON the
        // road instead of being painted over by the depth-sorted road quads.
        art.roads(scene, world)
        scene.flush(c)
        fx.drawGround(c, scene)
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
            val detail = lod(p, d2, p.kind == PropKind.BUILDING)
            val shadowRange = if (p.kind == PropKind.BUILDING) 380f else 520f
            if (d2 < shadowRange * shadowRange) shadowFor(c, p)
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
                PropKind.BUILDING -> building(p.x, p.z, p.w, p.h, p.d, p.rot, p.color, false, detail, p,
                    scene.cam.focal * 2.45f / Math.sqrt(d2.toDouble()).toFloat().coerceAtLeast(1f))
            }
        }

        // ----------------------------------------------- مناطق رادار و اخلال
        for (r in world.radars) {
            zone(c, r.x, r.z, r.radius, Theme.BLUE)
            if ((r.x - camX) * (r.x - camX) + (r.z - camZ) * (r.z - camZ) < 500f * 500f)
                art.castShadow(c, scene, r.x, r.z, 9f, 9f, 6f, 0f, .42f)
            val dx = r.x - camX
            val dz = r.z - camZ
            val d2 = dx * dx + dz * dz
            // ایستگاه رادار بزرگ‌تر از مقیاس واقعی است تا از ارتفاع پرواز خوانا بماند
            if (d2 <= maxD2) hardware.radarStation(rig, r.x, r.z, r.spin, 2.2f, lod(r, d2))
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
            val detail = lod(a, d2)
            if (a.y <= 0f && d2 < 550f * 550f) art.castShadow(c, scene, a.x, a.z, 6f, 8f, 3.2f, a.bodyYaw, .5f)
            if (a.kind == AASite.KIND_GUN) {
                hardware.aaGun(rig, a.x, a.z, a.bodyYaw, a.barrelYaw, a.barrelPitch, 1.2f, true, a.flash, detail, a.y)
            } else {
                hardware.samLauncher(rig, a.x, a.z, a.bodyYaw, a.barrelYaw, a.barrelPitch, 1.2f, true, detail, a.y)
            }
        }

        // ------------------------------------------------------------- اهداف
        for (t in world.targets) {
            val dx = t.x - camX
            val dz = t.z - camZ
            val d2 = dx * dx + dz * dz
            if (d2 > maxD2) continue
            if (!t.destroyed && d2 < 700f * 700f && t.shape != TargetShape.SHIP && t.shape != TargetShape.CARRIER)
                art.castShadow(c, scene, t.x, t.z, t.w, t.d, t.h * .8f, t.heading, .5f)
            target(t, lod(t, d2))
        }
        // Pulsing ground ring on the active target: readable from any distance.
        world.activeTarget()?.let { t ->
            val pulse = .5f + .5f * sin(clock * 5f)
            val r0 = t.hitRadius * 1.25f + 6f
            zone(c, t.x, t.z, r0 + pulse * 5f, Theme.RED, .35f + .45f * (1f - pulse), 2.2f)
            zone(c, t.x, t.z, r0 * .55f, Theme.RED, .25f, 1.4f)
        }

        plain()
        for (b in world.bonuses) {
            if (b.collected) continue
            val bob = sin(clock * 2.2f + b.x) * .8f
            scene.box(b.x, b.y + bob, b.z, 4.2f, 4.2f, 4.2f, Theme.AMBER, b.spin)
            fx.light(scene, b.x, b.y + bob + 2.1f, b.z, 7f + sin(clock * 4f) * 1.2f, fx.glowAmber, .55f, 3f)
        }
        for (m in world.missiles) missile(m)
        for (a in world.aaSites) {
            if (!a.alive || a.flash <= 0f) continue
            fx.light(scene, a.x + sin(a.barrelYaw) * 3f, a.muzzleHeight + 1f, a.z + cos(a.barrelYaw) * 3f,
                6f * (a.flash / .12f).coerceIn(0f, 1f) + 2f, fx.glowAmber, .9f)
        }

        if (!mission.fpv && mission.phase != Mission.Phase.ENDED) {
            val alt = mission.y.coerceAtLeast(0f)
            if (alt < 220f) {
                art.shadow(
                    c, scene, mission.x - alt * scene.sunX / scene.sunY, mission.z - alt * scene.sunZ / scene.sunY,
                    7f + alt * .035f, 7f + alt * .035f, (.40f - alt * .0015f).coerceAtLeast(.06f)
                )
            }
            drone.draw(scene, mission, fx)
        }
        fx.draw(c, scene, mission.fpv)
        scene.flush(c)
        art.lensFlare(c, scene, w, h)
    }

    private var look: EnvLook = EnvLook.DESERT

    private fun shadowFor(c: Canvas, p: Prop) {
        when (p.kind) {
            PropKind.SHIP, PropKind.FREIGHTER -> return
            PropKind.BUILDING -> {
                // contact occlusion + directional cast shadow
                art.castShadow(c, scene, p.x, p.z, p.w * 1.06f, p.d * 1.06f, 0f, p.rot, .30f)
                art.castShadow(c, scene, p.x, p.z, p.w, p.d, p.h, p.rot, .50f)
            }
            PropKind.ROCK -> art.castShadow(c, scene, p.x, p.z, p.w * .8f, p.d * .7f, p.h * .6f, p.rot, .45f)
            PropKind.TOWER_MAST -> art.castShadow(c, scene, p.x, p.z, p.w * .28f, p.d * .28f, p.h, p.rot, .45f)
            else -> art.castShadow(c, scene, p.x, p.z, p.w * .55f, p.w * .55f, p.h * .75f, 0f, .40f)
        }
    }

    // ------------------------------------------------------------------ اجزا

    /** Same footprint, height, roof cap and materials at every LOD; only facade decals drop out.
     *  پنجره‌ها در مقیاس واقعی (متر) روی هر ۴ رخ چیده می‌شوند. 0.0.9: شیشه رنگ آسمان همان
     *  محیط را می‌گیرد، پنجره‌های روشن در غروب/شب خودتابان‌اند و نوار تیرهٔ پای دیوار
     *  (اکلوژن تماسی) ساختمان را روی زمین می‌نشاند. منطق ویرانی نزدیک هم اصلاح شد
     *  (شاخهٔ قبلی هرگز اجرا نمی‌شد). */
    private fun building(
        x: Float, z: Float, w: Float, h: Float, d: Float, yaw: Float,
        baseColor: Int, destroyed: Boolean, detail: Int,
        winKey: Any? = null, windowPx: Float = 0f
    ) {
        rig.place(x, 0f, z, yaw, 1f)
        val color = if (destroyed) 0xFF393A36.toInt() else baseColor
        val wall = Gfx.get(gfxContext, if (destroyed) "material_rust" else "material_wall")
        val roof = Gfx.get(gfxContext, if (destroyed) "material_rust" else "material_roof")
        val cap = max(.5f, h * .04f).coerceAtMost(h * .2f)
        rig.box(0f, 0f, 0f, w, h - cap, d, color, wall, roof, bottom = false)
        // لبهٔ پشت‌بام (parapet)
        rig.box(0f, h - cap, 0f, w, cap, d, Theme.shade(color, 1.08f), wall, roof, bottom = false)
        val parapet = if (detail == 0 && !destroyed) min(1.1f, max(.35f, h * .018f)) else 0f
        if (parapet > 0f) {
            val pr = Theme.shade(color, 1.16f)
            val t = min(.7f, w * .05f)
            rig.box(0f, h, -d * .5f + t * .5f, w, parapet, t, pr, wall, roof, bottom = false)
            rig.box(0f, h, d * .5f - t * .5f, w, parapet, t, pr, wall, roof, bottom = false)
            rig.box(-w * .5f + t * .5f, h, 0f, t, parapet, d - t * 2f, pr, wall, roof, bottom = false)
            rig.box(w * .5f - t * .5f, h, 0f, t, parapet, d - t * 2f, pr, wall, roof, bottom = false)
            val seed = (x * 31f + z * 17f).toInt()
            val unitW = (w * .22f).coerceIn(1.6f, 5f)
            val unitD = (d * .26f).coerceIn(1.6f, 5f)
            val ux = (((seed % 5) + 5) % 5 - 2) * w * .11f
            val uz = ((((seed / 5) % 5) + 5) % 5 - 2) * d * .11f
            val unitH = parapet * 2.4f
            rig.box(ux, h, uz, unitW, unitH, unitD, Theme.shade(color, 0.92f), wall, roof, bottom = false)
            rig.tube(ux + unitW * .5f, h + unitH, uz, ux + unitW * .5f, h + unitH + 1.6f, uz,
                .16f, 6, Theme.shade(color, 0.8f), caps = true)
        }
        val usableH = h - cap
        if (detail <= 1 && !destroyed) {
            // اکلوژن تماسی: نوار تیرهٔ ۱٫۲ متری پای هر چهار دیوار
            val ao = Theme.shade(color, .52f)
            val band = 1.2f
            val ez = d * .5f + .012f; val ex = w * .5f + .012f
            rig.face(-w * .5f, 0f, -ez, w * .5f, 0f, -ez, w * .5f, band, -ez, -w * .5f, band, -ez, ao, cullBack = true)
            rig.face(-w * .5f, 0f, ez, w * .5f, 0f, ez, w * .5f, band, ez, -w * .5f, band, ez, ao, cullBack = true)
            rig.face(-ex, 0f, -d * .5f, -ex, 0f, d * .5f, -ex, band, d * .5f, -ex, band, -d * .5f, ao, cullBack = true)
            rig.face(ex, 0f, -d * .5f, ex, 0f, d * .5f, ex, band, d * .5f, ex, band, -d * .5f, ao, cullBack = true)
        }
        if (destroyed) {
            // ویرانی: فقط در نزدیک‌ترین LOD چند پنجرهٔ سوختهٔ تیره
            if (detail != 0) return
            val dark = 0xFF1F2322.toInt()
            val winW = 2.2f; val winH = 2.0f; val floorH = 3.4f; val gapX = 0.9f
            val floors = ((usableH - 1.6f) / floorH).toInt().coerceIn(1, 3)
            val bays = ((w - 2.2f) / (winW + gapX)).toInt().coerceIn(1, 2)
            val total = bays * winW + (bays - 1) * gapX; val sx = -total * 0.5f
            for (side in -1..1 step 2) {
                val zz = side.toFloat() * (d * 0.5f + 0.018f)
                for (b in 0 until bays) for (f in 0 until floors) {
                    val y0 = 1.2f + f * floorH; val y1 = y0 + winH
                    if (y1 > usableH - 0.5f) continue
                    val x0 = sx + b * (winW + gapX); val x1 = x0 + winW
                    rig.face(x0, y0, zz, x1, y0, zz, x1, y1, zz, x0, y1, zz, dark, cullBack = true)
                }
            }
            return
        }
        // ---------------------------------------------------- پنجره‌های ساختمان
        // راهکار ثبات: نوار شیشه در همهٔ فاصله‌ها رسم می‌شود (بدون درش/درش)،
        // و پنجرهٔ مجزا فقط وقتی روی صفحهٔ نمایش به اندازه کافی بزرگ است.
        // گذر از هر مرزی = پنجره روی نوار می‌نشیند یا بیرون می‌رود — نه پنجره صفر/صد.
        if (!destroyed && usableH >= 3.0f && w >= 4f && d >= 4f) {
            val glass = look.windowGlass
            val floorH = 3.55f
            val sill0 = 1.45f
            val floors = ((usableH - sill0 - 0.7f) / floorH).toInt().coerceIn(1, 5)
            // ۱) نوار شیشه همیشه (پشت پنجره‌های مجزا می‌نشیند)
            run {
                for (side in -1..1 step 2) {
                    val zz = side.toFloat() * (d * 0.5f + 0.014f)
                    for (f in 0 until floors) {
                        val y0 = sill0 + f * floorH; val y1 = y0 + 2.45f
                        if (y1 > usableH - 0.45f) continue
                        rig.face(-w * .4f, y0, zz, w * .4f, y0, zz, w * .4f, y1, zz, -w * .4f, y1, zz, glass, cullBack = true)
                    }
                }
                if (d >= 6f) {
                    for (side in -1..1 step 2) {
                        val xx = side.toFloat() * (w * 0.5f + 0.014f)
                        for (f in 0 until minOf(floors, 4)) {
                            val y0 = sill0 + f * floorH; val y1 = y0 + 2.45f
                            if (y1 > usableH - 0.45f) continue
                            rig.face(xx, y0, -d * .4f, xx, y0, d * .4f, xx, y1, d * .4f, xx, y1, -d * .4f, glass, cullBack = true)
                        }
                    }
                }
            }
            // ۲) پنجره‌های مجزا + قاب‌ها — فقط وقتی روی صفحه زیادند
            if (detail <= 1 && winKey != null && windowLodEnabled(winKey, windowPx)) {
                val winW = 2.15f
                val winH = 2.45f
                val floorH2 = 3.55f
                val gapX = 0.75f
                val sill0 = 1.45f
                val inset = 0.020f
                val seed = (x * 31f + z * 17f).toInt()
                val phase = ((seed % 7) + 7) % 7
                val glassHi = Theme.mix(glass, look.upper, .45f)
                fun litWindow(b: Int, f: Int): Boolean = ((b * 7 + f * 11 + phase) % look.litChance) == 0
                fun glassFor(b: Int, f: Int): Int =
                    if ((b + f + phase) % 3 == 0) glassHi else glass
                val baysZ = ((w - 2.0f) / (winW + gapX)).toInt().coerceIn(1, 4)
                run {
                    val total = baysZ * winW + (baysZ - 1) * gapX
                    val startX = -total * 0.5f
                    for (side in -1..1 step 2) {
                        val zz = side.toFloat() * (d * 0.5f + inset)
                        for (b in 0 until baysZ) {
                            val x0 = startX + b * (winW + gapX); val x1 = x0 + winW
                            for (f in 0 until floors) {
                                val y0 = sill0 + f * floorH2; val y1 = y0 + winH
                                if (y1 > usableH - 0.45f) continue
                                val bi = b + if (side > 0) 10 else 0
                                if (litWindow(bi, f)) {
                                    rig.face(x0, y0, zz, x1, y0, zz, x1, y1, zz, x0, y1, zz, look.windowLit, emissive = true, cullBack = true)
                                } else {
                                    rig.face(x0, y0, zz, x1, y0, zz, x1, y1, zz, x0, y1, zz, glassFor(bi, f))
                                }
                                // قاب؛ آفست هم‌جهت با بیرونِ همان رخ
                                val fz = zz + side.toFloat() * 0.004f
                                val frame = Theme.shade(color, 0.62f)
                                val fh = 0.09f
                                rig.face(x0, y1 - fh, fz, x1, y1 - fh, fz, x1, y1, fz, x0, y1, fz, frame, cullBack = true)
                                rig.face(x0, y0, fz, x1, y0, fz, x1, y0 + fh * 1.6f, fz, x0, y0 + fh * 1.6f, fz, frame, cullBack = true)
                            }
                        }
                    }
                }
                if (d >= 6f) {
                    val baysX = ((d - 2.0f) / (winW + gapX)).toInt().coerceIn(1, 3)
                    val totalZ = baysX * winW + (baysX - 1) * gapX
                    val startZ = -totalZ * 0.5f
                    val floorsX = minOf(floors, 4)
                    for (side in -1..1 step 2) {
                        val xx = side.toFloat() * (w * 0.5f + inset)
                        val fx0 = xx + side.toFloat() * 0.004f
                        for (b in 0 until baysX) {
                            val z0 = startZ + b * (winW + gapX); val z1 = z0 + winW
                            for (f in 0 until floorsX) {
                                val y0 = sill0 + f * floorH2; val y1 = y0 + winH
                                if (y1 > usableH - 0.45f) continue
                                val bi = b + if (side > 0) 30 else 20
                                if (litWindow(bi, f)) {
                                    rig.face(xx, y0, z0, xx, y0, z1, xx, y1, z1, xx, y1, z0, look.windowLit, emissive = true, cullBack = true)
                                } else {
                                    rig.face(xx, y0, z0, xx, y0, z1, xx, y1, z1, xx, y1, z0, glassFor(bi, f))
                                }
                                val frame = Theme.shade(color, 0.62f)
                                val fh = 0.09f
                                rig.face(fx0, y1 - fh, z0, fx0, y1 - fh, z1, fx0, y1, z1, fx0, y1, z0, frame, cullBack = true)
                                rig.face(fx0, y0, z0, fx0, y0, z1, fx0, y0 + fh * 1.6f, z1, fx0, y0 + fh * 1.6f, z0, frame, cullBack = true)
                            }
                        }
                    }
                }
            }
        }
    }

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
        fx.light(scene, m.x - ux * 2.6f, m.y - uy * 2.6f, m.z - uz * 2.6f, 2.8f, fx.glowAmber, .95f, 3f)
    }

    private fun zone(c: Canvas, x: Float, z: Float, radius: Float, color: Int, alpha: Float = .36f, width: Float = 1.4f) {
        val segments = 48
        var px = x + radius
        var pz = z
        for (i in 1..segments) {
            val a = i * 2f * PI.toFloat() / segments
            val nx = x + cos(a) * radius
            val nz = z + sin(a) * radius
            scene.line(px, .6f, pz, nx, .6f, nz, c, Theme.withAlpha(color, alpha), width)
            px = nx
            pz = nz
        }
    }

    private fun target(t: Target, detail: Int) {
        when (t.shape) {
            TargetShape.BUNKER -> hardware.bunker(rig, t.x, t.z, t.w, t.h, t.d, t.heading, t.destroyed, detail)
            TargetShape.BUILDING -> building(t.x, t.z, t.w, t.h, t.d, t.heading, 0xFFA8A492.toInt(), t.destroyed, detail)
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
