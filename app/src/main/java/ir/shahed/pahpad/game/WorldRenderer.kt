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

    /**
     * ضریب کیفیت از تنظیمات کاربر: ۰ = سبک، ۱ = متعادل، ۲ = بالا.
     *
     * با کیفیت پایین‌تر، آستانه‌های جزئیات نزدیک‌تر می‌شوند (ساختمان‌های
     * کم‌جزئیات زودتر فعال می‌شوند) و شعاع دید کوتاه‌تر می‌شود. این تنها
     * جایی است که «سنگینی» بازی به آن حساس است.
     */
    init { setupWallTiling() }

    private var qualityMul = 1f

    fun applyQuality(level: Int) {
        qualityMul = when (level) {
            0 -> 0.55f
            2 -> 1.35f
            else -> 1f
        }
        lodStates.clear()
    }

    /** افکت‌های دود/جرقه خاموش شده‌اند؟ */
    var effectsEnabled = true

    /**
     * نمایش «پنجرهٔ مجزا» — کاملاً مستقل از دوربین.
     *
     * تاریخچهٔ این باگ (سه تلاش):
     *   ۱) آستانهٔ ۹/۷ پیکسل با هیسترزیس — ورودی اشتباه بود (عرض ساختمان
     *      به‌جای عرض پنجره) و فاصلهٔ مرده ۰٫۹ پیکسل شد.
     *   ۲) همان را پیوسته (smoothstep) کردم — ولی همچنان تابعی از فاصله تا
     *      دوربین بود. هر بار پهباد جلو/عقب می‌رود یا دوربین می‌چرخد، عرض
     *      پیکسلی پنجره عوض می‌شود و الگوی پنجره‌ها «می‌جوشد».
     *
     * نتیجهٔ واقعی: هر معیاری که به دوربین وابسته باشد، در پرواز ناپایدار
     * *دیده می‌شود* — حتی اگر پیوسته باشد، چون کل شبکهٔ پنجره با حرکت
     * دوربین نرم جابه‌جا/محو می‌شود و چشم آن را «لرزش» می‌بیند.
     *
     * راه‌حل نهایی: تصمیم بر اساس **فاصلهٔ واقعی ساختمان تا مرکز صحنه**
     * گرفته می‌شود، نه دوربین. پس برای هر ساختمان ثابت است و در تمام طول
     * مأموریت هرگز عوض نمی‌شود. تنها چیزی که به دوربین بستگی دارد
     * بزرگ‌نمایی طبیعی پرسپکتیو است — که همان چیزی است که چشم انتظار دارد.
     *
     * این تابع هیچ‌جا با فاصلهٔ دوربین صدا زده نمی‌شود.
     */
    private val WIN_DETAIL_NEAR = 260f   // نزدیک‌تر از این: جزئیات کامل
    private val WIN_DETAIL_FAR = 520f    // دورتر از این: فقط نوار شیشه

    private fun windowDetail(worldDist: Float): Float {
        if (worldDist <= WIN_DETAIL_NEAR) return 1f
        if (worldDist >= WIN_DETAIL_FAR) return 0f
        val t = (worldDist - WIN_DETAIL_NEAR) / (WIN_DETAIL_FAR - WIN_DETAIL_NEAR)
        val u = 1f - t
        return u * u * (3f - 2f * u)
    }

    /** Schmitt trigger: retain detail inside a +/-10% distance band. */
    private fun lod(key: Any, distance2: Float, building: Boolean = false): Int {
        val near = (if (building) BUILDING_NEAR_LOD else NEAR_LOD) * qualityMul
        val mid = (if (building) BUILDING_MID_LOD else MID_LOD) * qualityMul
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

    fun update(dt: Float, mission: Mission) {
        clock += dt
        art.advance(dt)
        drone.update(dt, mission)
        fx.update(dt)
        if (mission.fpv) fx.clearEngineSmoke()
        if (mission.phase == Mission.Phase.FLYING && !mission.fpv && effectsEnabled) {
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
        lodWorld = null
    }

    private fun plain() {
        scene.wallTexture = null
        scene.roofTexture = null
    }

    /**
     * تکرار بافت دیوار در فضای جهانی.
     *
     * همیشه روشن است ولی فقط روی `rig.box` دیوارها اثر دارد: آن‌جا طول
     * واقعی وجه با متر اندازه‌گیری می‌شود. اسپرایت‌ها و بیلبوردها از مسیر
     * `imagePlane` می‌آیند و tileMode روی آن‌ها اثری ندارد.
     */
    private fun setupWallTiling() {
        scene.tileMode = android.graphics.Shader.TileMode.REPEAT
        scene.textureMeters = 7f   // فقط روی وجه‌هایی که tiledFaces دارند
    }

    fun draw(c: Canvas, mission: Mission, w: Float, h: Float) {
        val world = mission.world
        if (lodWorld !== world) {
            lodStates.clear()
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
                    // فاصلهٔ ساختمان از مرکز صحنه — ثابت در تمام مأموریت،
                    // مستقل از دوربین. (پیش‌تر فاصله تا دوربین بود و با هر
                    // حرکت پهباد الگوی پنجره‌ها می‌جوشید.)
                    Math.sqrt((p.x * p.x + p.z * p.z).toDouble()).toFloat())
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
        winKey: Any? = null,
        /** فاصلهٔ ساختمان از مرکز صحنه (نه از دوربین) — برای پایداری پنجره‌ها */
        worldDist: Float = 0f
    ) {
        rig.place(x, 0f, z, yaw, 1f)
        val color = if (destroyed) 0xFF393A36.toInt() else baseColor
        val wall = Gfx.get(gfxContext, if (destroyed) "material_rust" else "material_wall")
        val roof = Gfx.get(gfxContext, if (destroyed) "material_rust" else "material_roof")
        val cap = max(.5f, h * .04f).coerceAtMost(h * .2f)
        // اینجا (و فقط اینجا) بافت دیوار با مقیاس متری تکرار می‌شود.
        // بدنهٔ پهپاد/تانک/کشتی از این مسیر نمی‌گذرند یا tiledFaces ندارند.
        //
        // وجه بالای بدنه رسم نمی‌شود: حجمِ «لبهٔ پشت‌بام» که دقیقاً رویش
        // می‌نشیند همان سطح را می‌پوشاند. قبلاً هر دو وجه هم‌سطح و هم‌اندازه
        // رسم می‌شدند و مرتب‌ساز نقاش هر فریم یکی را جلو می‌آورد → سقف
        // سوسو می‌زد و لکه‌لکه دیده می‌شد.
        rig.box(0f, 0f, 0f, w, h - cap, d, color, wall, roof, bottom = false,
            tiledFaces = true, topFace = false)
        // لبهٔ پشت‌بام: تنها وجه بالایی بام، با همان تکرار متری بافت
        rig.box(0f, h - cap, 0f, w, cap, d, Theme.shade(color, 1.08f), wall, roof, bottom = false,
            tiledFaces = true)
        // پشت‌بام و واحدهایش **نباید با فاصله ظاهر/غیب شوند**.
        //
        // قبلاً `detail == 0` بود، یعنی فقط تا ۱۷۵ متر رسم می‌شد. وقتی
        // دوربین جلو/عقب می‌رفت، کل پشت‌بام یک‌جا می‌پرید — همان «چیزی
        // روی سقف ظاهر و غیب می‌شود».
        //
        // حالا تا ۴۲۰ متر (MID_LOD) می‌مانَد و آن‌طرف‌تر فقط واحدهای
        // تزئینی (جعبه و آنتن) حذف می‌شوند، نه خودِ لبه.
        val parapet = if (!destroyed) min(1.1f, max(.35f, h * .018f)) else 0f
        if (parapet > 0f) {
            val pr = Theme.shade(color, 1.16f)
            val t = min(.7f, w * .05f)
            rig.box(0f, h, -d * .5f + t * .5f, w, parapet, t, pr, wall, roof, bottom = false)
            rig.box(0f, h, d * .5f - t * .5f, w, parapet, t, pr, wall, roof, bottom = false)
            rig.box(-w * .5f + t * .5f, h, 0f, t, parapet, d - t * 2f, pr, wall, roof, bottom = false)
            rig.box(w * .5f - t * .5f, h, 0f, t, parapet, d - t * 2f, pr, wall, roof, bottom = false)
            // توجه: همهٔ جعبه‌های پشت‌بام `bottom = false` هستند. وجه زیرینشان
            // دقیقاً روی سطح بام می‌نشیند و در مرتب‌ساز نقاش با آن هم‌سطح
            // می‌شود → لکهٔ تیره‌ای که هر فریم ظاهر/غیب می‌شد.
            // واحدهای پشت‌بام فقط نزدیک — حذف‌شدنشان در فاصله طبیعی است
            // چون کوچک‌اند و چشم متوجه نمی‌شود. لبهٔ پشت‌بام (بالا) همیشه
            // می‌مانَد.
            if (detail <= 1) {
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
        }
        val usableH = h - cap
        if (detail <= 1 && !destroyed) {
            // اکلوژن تماسی: نوار تیرهٔ ۱٫۲ متری پای هر چهار دیوار
            val ao = Theme.shade(color, .52f)
            val band = 1.2f
            val ez = d * .5f + .012f; val ex = w * .5f + .012f
            rig.face(-w * .5f, 0f, -ez, w * .5f, 0f, -ez, w * .5f, band, -ez, -w * .5f, band, -ez, ao, cullBack = true, surfaceBias = 0.15f)
            rig.face(-w * .5f, 0f, ez, w * .5f, 0f, ez, w * .5f, band, ez, -w * .5f, band, ez, ao, cullBack = true, surfaceBias = 0.15f)
            rig.face(-ex, 0f, -d * .5f, -ex, 0f, d * .5f, -ex, band, d * .5f, -ex, band, -d * .5f, ao, cullBack = true, surfaceBias = 0.15f)
            rig.face(ex, 0f, -d * .5f, ex, 0f, d * .5f, ex, band, d * .5f, ex, band, -d * .5f, ao, cullBack = true, surfaceBias = 0.15f)
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
                    rig.face(x0, y0, zz, x1, y0, zz, x1, y1, zz, x0, y1, zz, dark,
                        cullBack = true, surfaceBias = 0.2f)
                }
            }
            return
        }
        // ---------------------------------------------------- پنجره‌های ساختمان
        // راهکار ثبات: نوار شیشه در همهٔ فاصله‌ها رسم می‌شود (بدون درش/درش)،
        // و پنجرهٔ مجزا فقط وقتی روی صفحهٔ نمایش به اندازه کافی بزرگ است.
        // گذر از هر مرزی = پنجره روی نوار می‌نشیند یا بیرون می‌رود — نه پنجره صفر/صد.
        if (!destroyed && usableH >= 3.0f && w >= 4f && d >= 4f) {
            // نوار پشت (زمینهٔ طبقه) — تیره‌تر از پنجره‌های مجزا
            // شیشهٔ پنجره — روشن‌تر از دیوار، تا شبکهٔ پنجره دیده شود
            val paneGlass = look.windowGlass
            val floorH = 3.55f
            val sill0 = 1.45f
            // طبقات از ارتفاع واقعی ساختمان — سقف ۵ باعث می‌شد برج ۴۹ متری
            // فقط ۵ ردیف پنجره داشته باشد در حالی که جا برای ۱۲ ردیف هست.
            val floors = ((usableH - sill0 - 0.7f) / floorH).toInt().coerceIn(1, 12)
                // ابعاد نامی پنجره — مبنای محاسبهٔ تعداد و گام
                val winW = 2.15f
                val gapX = 0.75f
                val winH = 2.45f
                val floorH2 = 3.55f

                // بودجه بر اساس **وجه** نه پنجره: هر پنجره ۱ شیشه + ۲ قاب
                // روی ۲ رخ، و رخ‌های کناری هم سهم دارند. با ۵۶ پنجره یک
                // ساختمان نزدیک ~۴۵۰ وجه می‌ساخت که روی Canvas موبایل زیاد
                // است. ۳۲ پنجره ≈ ۲۶۰ وجه، که با چند ساختمان در کادر
                // هنوز زیر بودجهٔ فریم می‌ماند.
                val maxWindows = 32
                val slotWish = winW + gapX          // ۲.۹ متر
                var baysZraw = ((w - 1.2f) / slotWish).toInt().coerceIn(1, 16)
                var floorsRaw = floors
                val total = baysZraw * floorsRaw
                if (total > maxWindows) {
                    val k = Math.sqrt(maxWindows.toDouble() / total).toFloat()
                    baysZraw = (baysZraw * k).toInt().coerceIn(2, 16)
                    floorsRaw = (floorsRaw * k).toInt().coerceIn(2, 12)
                }
                val baysZ = baysZraw
                val floorsEff = floorsRaw

                // ارتفاع هر طبقه را از ارتفاع واقعی دیوار بازمحاسبه کن تا
                // شبکه از پایین تا بالای دیوار پخش شود. (قبلاً طبقات کم
                // می‌شدند ولی گام ثابت ۳.۵۵ متر می‌ماند → بالای ساختمان
                // بی‌پنجره می‌ماند؛ روی برج ۴۹ متری ۱۳.۶ متر خالی.)
                val bandTop = usableH - 0.45f
                val availV = (bandTop - sill0).coerceAtLeast(1f)
                val slotH = (availV / floorsEff).coerceAtLeast(0.6f)
                // پنجره ۶۶٪ گام ارتفاع را می‌گیرد؛ بقیه فاصلهٔ بین طبقات
                val pwH = (slotH * 0.62f).coerceAtLeast(0.4f)
                // گام و عرض پنجره را از عرض دیوار بازمحاسبه کن تا شبکه
                // دقیقاً از لبه تا لبه پخش شود.
                val usableW = (w * 0.90f).coerceAtLeast(2f)
                val slotW = (usableW / baysZ).coerceAtLeast(0.9f)
                val pwW = (slotW * 0.66f).coerceAtLeast(0.5f)
            // نوار شیشهٔ تمام‌عرض حذف شد.
            //
            // چرا: آن نوار یک مستطیل به عرض کل دیوار و ارتفاع هر طبقه بود.
            // در نمای مایل، لبهٔ بالایی و پایینی‌اش با پرسپکتیو دیوار هم‌راستا
            // نمی‌شد و مثل «شکاف افقی تخت» دیده می‌شد — همان چیزی که کاربر
            // به‌عنوان پنجرهٔ خراب می‌دید. حالا فقط پنجره‌های مجزا (که هر
            // کدام با چهار گوشهٔ خودشان تصویر می‌شوند) رسم می‌شوند.

            // ۲) پنجره‌های مجزا + قاب‌ها — با شدت پیوسته، نه روشن/خاموش.
            //    زیر آستانه، پنجره‌ها رنگِ خود نوار شیشه را می‌گیرند و دیده
            //    نمی‌شوند؛ بالای آستانه رنگ کامل خودشان را. بین این دو،
            //    رنگ در هم می‌آمیزد.
            // از وقتی نوار پشتی حذف شد، پنجره‌ها باید همیشه رسم شوند؛
            // «محو تدریجی» فقط برای گذار نرم بین LOD است، نه برای حذف کامل.
            val winFade = if (winKey != null) {
                val f = windowDetail(worldDist)
                if (f < 0.05f) 0.05f else f
            } else 0f
            // LOD دور (detail > 1): پنجره‌ها بمانند ولی قاب‌ها حذف شوند تا
            // وجه‌ها نصف شوند و فریم نیفتد.
            val drawFrames = detail <= 1
            if (winFade > 0.001f) {
                val inset = 0.06f     // بیرون‌زدگی پنجره از دیوار (متر)
                // پنجره‌ها با بایاس عمق مرتب می‌شوند، پس لازم نیست چندان
                // بیرون بزنند؛ ۶ سانتی‌متر برای جلوگیری از z-fight کافی است.
                val winBias = 0.35f   // آفست سطحی؛ در Rig.face با زاویهٔ دید مقیاس می‌شود
                val seed = (x * 31f + z * 17f).toInt()
                val phase = ((seed % 7) + 7) % 7
                // رنگ پنجره با شدت winFade از رنگ نوار به رنگ کامل می‌رود
                // محو کردن رنگ دیگر معنا ندارد؛ نوار پشتی نیست که پنجره در
                // آن حل شود. پنجره همیشه رنگ کامل خودش را دارد.
                val litWin = look.windowLit
                // قاب: روشن‌تر از دیوار تا مرز پنجره‌ها دیده شود
                val frameBase = Theme.shade(color, 1.9f)
                val frameCol = frameBase
                // فقط حدود ۱ از ۵ پنجره روشن — بیشتر از این، شبکه شطرنجی به‌نظر می‌رسد
                fun litWindow(b: Int, f: Int): Boolean = ((b * 7 + f * 11 + phase) % (look.litChance * 2)) == 0
                // تعداد پنجره از عرض واقعی دیوار حساب می‌شود (نه سقف ثابت ۴).
                // با سقف ۴، ساختمان ۵۱ متری فقط ۴ پنجره می‌گرفت و دیوار خالی می‌ماند.
                // سقف بالا فقط برای جلوگیری از انفجار تعداد وجه در ساختمان‌های غول‌پیکر است.
                // سقف کل پنجره‌های هر ساختمان: هر پنجره ۶ وجه (شیشه + ۲ قاب،
                // روی ۲ رخ) است. با ۱۴×۱۲ = ۱۶۸ پنجره، یک ساختمان تنها هزار
                // وجه می‌ساخت و روی Canvas موبایل فریم می‌افتاد.
                // به‌جای کم‌کردن یک‌طرفه (که برج را با ۳ ردیف پنجره رها می‌کرد)،
                // هر دو بُعد را با یک ضریب متناسب کوچک می‌کنیم تا نسبت شبکه
                // حفظ شود و برج همان‌قدر پنجره بگیرد که ساختمان کوتاه.
                // تعداد پنجره در عرض: هم از عرض واقعی دیوار، هم با بودجهٔ
                // کلی وجه. مهم: بعد از کم‌کردن تعداد، عرض پنجره و گام هم
                // باید بازمحاسبه شوند تا شبکه **تمام عرض** را بپوشاند؛
                // وگرنه چند پنجره ریز در گوشه می‌ماند و بقیهٔ دیوار خالی.
                // نقطهٔ مرجع هر دیوار در دستگاه محلی: مرکز همان دیوار.
                // دیوارهای Z (جلو/عقب) در zz، دیوارهای X در xx.
                run {
                    val total = baysZ * slotW
                    val startX = -total * 0.5f + (slotW - pwW) * 0.5f
                    for (side in -1..1 step 2) {
                        val zz = side.toFloat() * (d * 0.5f + inset)
                        // مرکز همان دیوار: عمق zz، ارتفاع وسط ناحیهٔ پنجره
                        val wallRefZ = floatArrayOf(0f, (sill0 + bandTop) * 0.5f, zz)
                        for (b in 0 until baysZ) {
                            val x0 = startX + b * slotW; val x1 = x0 + pwW
                            for (f in 0 until floorsEff) {
                                val y0 = sill0 + f * slotH; val y1 = y0 + pwH
                                val bi = b + if (side > 0) 10 else 0
                                if (litWindow(bi, f)) {
                                    // در شدت کم، پنجرهٔ روشن هم نباید چشمک بزند
                                    rig.face(x0, y0, zz, x1, y0, zz, x1, y1, zz, x0, y1, zz, litWin,
                                        emissive = winFade > 0.85f, cullBack = true, surfaceBias = winBias, biasRefLocal = wallRefZ)
                                } else {
                                    rig.face(x0, y0, zz, x1, y0, zz, x1, y1, zz, x0, y1, zz, paneGlass,
                                        cullBack = true, surfaceBias = winBias, biasRefLocal = wallRefZ)
                                }
                                // قاب؛ آفست هم‌جهت با بیرونِ همان رخ
                                if (drawFrames) {
                                    val fz = zz + side.toFloat() * 0.004f
                                    val frame = frameCol
                                    val fh = 0.09f
                                    rig.face(x0, y1 - fh, fz, x1, y1 - fh, fz, x1, y1, fz, x0, y1, fz, frame, cullBack = true, surfaceBias = winBias, biasRefLocal = wallRefZ)
                                    rig.face(x0, y0, fz, x1, y0, fz, x1, y0 + fh * 1.6f, fz, x0, y0 + fh * 1.6f, fz, frame, cullBack = true, surfaceBias = winBias, biasRefLocal = wallRefZ)
                                }
                            }
                        }
                    }
                }
                if (d >= 6f) {
                    val baysX = ((baysZ.toFloat() * d / w).toInt()).coerceIn(2, 10)
                    val usableD = (d * 0.90f).coerceAtLeast(2f)
                    val slotD = (usableD / baysX).coerceAtLeast(0.9f)
                    val pwD = (slotD * 0.66f).coerceAtLeast(0.5f)
                    val totalZ = baysX * slotD
                    val startZ = -totalZ * 0.5f + (slotD - pwD) * 0.5f
                    val floorsX = floorsEff
                    for (side in -1..1 step 2) {
                        val xx = side.toFloat() * (w * 0.5f + inset)
                        val wallRefX = floatArrayOf(xx, (sill0 + bandTop) * 0.5f, 0f)
                        val fx0 = xx + side.toFloat() * 0.004f
                        for (b in 0 until baysX) {
                            val z0 = startZ + b * slotD; val z1 = z0 + pwD
                            for (f in 0 until floorsX) {
                                val y0 = sill0 + f * slotH; val y1 = y0 + pwH
                                val bi = b + if (side > 0) 30 else 20
                                if (litWindow(bi, f)) {
                                    rig.face(xx, y0, z0, xx, y0, z1, xx, y1, z1, xx, y1, z0, litWin,
                                        emissive = winFade > 0.85f, cullBack = true, surfaceBias = winBias, biasRefLocal = wallRefX)
                                } else {
                                    rig.face(xx, y0, z0, xx, y0, z1, xx, y1, z1, xx, y1, z0, paneGlass,
                                        cullBack = true, surfaceBias = winBias, biasRefLocal = wallRefX)
                                }
                                if (drawFrames) {
                                    val fh = 0.09f
                                    rig.face(fx0, y1 - fh, z0, fx0, y1 - fh, z1, fx0, y1, z1, fx0, y1, z0, frameCol, cullBack = true, surfaceBias = winBias, biasRefLocal = wallRefX)
                                    rig.face(fx0, y0, z0, fx0, y0, z1, fx0, y0 + fh * 1.6f, z1, fx0, y0 + fh * 1.6f, z0, frameCol, cullBack = true, surfaceBias = winBias, biasRefLocal = wallRefX)
                                }
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
