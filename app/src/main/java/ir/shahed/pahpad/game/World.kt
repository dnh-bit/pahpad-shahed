package ir.shahed.pahpad.game

import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.data.Env
import ir.shahed.pahpad.data.LevelDef
import java.util.Random
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class TargetShape { BUNKER, BUILDING, TOWER, SHIP, CARRIER, VEHICLE, BRIDGE, ANTENNA, TRAIN, TUNNEL }

class Target(
    var x: Float,
    var z: Float,
    val shape: TargetShape,
    val w: Float,
    val h: Float,
    val d: Float,
    val hitRadius: Float,
    val moving: Boolean,
    var heading: Float,
    var speed: Float,
    val label: String
) {
    var destroyed = false
    var burn = 0f
    val centerY: Float get() = h * 0.5f
}

/**
 * سامانه‌ی پدافند زمینی.
 * kind نوع سامانه را مشخص می‌کند و رندرکننده بر همان اساس مدل سه‌بعدی می‌سازد.
 */
class AASite(
    val range: Float,
    val reload: Float,
    val kind: Int = KIND_SAM,
    /** جهت ثابت شاسی؛ فقط برجک/لانچر می‌چرخد */
    val bodyYaw: Float = 0f
) {
    /** x و z متغیرند تا پدافند روی پشت‌بام ساختمان جابه‌جا شود */
    var x: Float = 0f
    var z: Float = 0f
    var cooldown = 1.2f
    var alive = true
    var barrelYaw = 0f
    /** زاویه‌ی ارتفاع لوله یا لانچر بر حسب رادیان */
    var barrelPitch = 0.45f
    var flash = 0f
    /** ارتفاع پایه؛ روی سقف ساختمان‌های شهری صفر نیست */
    var y: Float = 0f

    /** هدفِ میزبان در مراحل دریایی (ناو/ناوشکنِ هدف)؛ سامانه روی عرشه‌ی
     *  آن سوار است و هر فریم همراهش حرکت می‌کند. null یعنی سامانه ثابت است. */
    var hostTarget: Target? = null
    /** آفست محلی سامانه روی عرشه‌ی میزبان (دستگاه محلی بدنه، متر) */
    var hostLX: Float = 0f
    var hostLZ: Float = 0f
    /** ارتفاع عرشه‌ی میزبان؛ وقتی hostTarget ست شده y از این مقدار می‌آید */
    var hostDeckY: Float = 0f
    /** پراپِ میزبان (کشتی اسکورت/جزیره/ساختمان) اگر سامانه روی یک پراپ
     *  نشسته باشد؛ موشکِ همین سامانه نباید با میزبان خودش برخورد کند. */
    var hostProp: Prop? = null

    /** ارتفاع دهانه‌ی لوله از زمین؛ برای محاسبه‌ی زاویه‌ی نشانه‌روی */
    val muzzleHeight: Float get() = y + if (kind == KIND_SAM) 2.6f else 2.1f

    companion object {
        /** پرتابگر موشک زمین‌به‌هوا */
        const val KIND_SAM = 0
        /** توپ پدافند خودکشیده */
        const val KIND_GUN = 1
    }
}

class Missile(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float
) {
    var life = 6f
    var alive = true
    /** پراپی که این موشک از روی آن شلیک شده (کشتی/جزیره/بام میزبان)؛
     *  موشک با سکوی پرتاب خودش برخورد نمی‌کند. */
    var ignoreProp: Prop? = null
}

class RadarZone(val x: Float, val z: Float, val radius: Float, val ceiling: Float) {
    /** زاویه‌ی آرایه‌ی چرخان رادار */
    var spin = 0f
    /** ارتفاع سکوی زیر ایستگاه (جزیره/عرشه)؛ روی زمین/آب صفر است */
    var y: Float = 0f
}

class EwZone(val x: Float, val z: Float, val radius: Float)

enum class PropKind { BUILDING, ROCK, TREE, PALM, BUSH, SHIP, FREIGHTER, TOWER_MAST, ISLAND }

class Prop(
    val x: Float, val z: Float,
    val w: Float, val h: Float, val d: Float,
    val color: Int, val rot: Float, val kind: PropKind
)

class Bonus(val x: Float, val y: Float, val z: Float, val value: Int) {
    var collected = false
    var spin = 0f
}

/**
 * شبکهٔ مشترک شهر: مراکز ردیف‌ها و خیابان‌های عرضی یک‌جا تعریف می‌شوند تا
 * چیدمان ساختمان‌ها و نقشهٔ خیابان‌ها هرگز روی هم نیفتند. خیابان عرضی دقیقاً
 * میانهٔ فاصلهٔ دو ردیف است و لرزش جانبی ساختمان با ضخامت خودش محدود می‌شود
 * تا هیچ پیهایی تا لبهٔ خیابان نرسد.
 */
object CityLayout {
    /** z مرکز نخستین ردیف ساختمان‌ها */
    const val START_Z = 220f
    /** فاصلهٔ مراکز ستون‌ها بر محور x */
    const val COLUMN_SPACING = 78f
    /** فاصلهٔ مراکز ردیف‌ها بر محور z */
    const val ROW_SPACING = 95f
    /** نصف عرض خیابان */
    const val HALF_ROAD = 8f
    /** حداقل فاصلهٔ آزاد پی ساختمان تا لبهٔ خیابان */
    const val CLEARANCE = 3f
    fun rowZ(row: Int): Float = START_Z + row * ROW_SPACING
    /** خیابان عرضی، میانهٔ فاصلهٔ ردیف row و ردیف row+1 */
    fun crossZ(row: Int): Float = START_Z + (row + 0.5f) * ROW_SPACING
    fun jitterLimit(width: Float): Float =
        min(7f, 39f - HALF_ROAD - CLEARANCE - width * 0.5f).coerceAtLeast(0f)
    fun blocksRoad(p: Prop): Boolean {
        val hx = abs(cos(p.rot)) * p.w * .5f + abs(sin(p.rot)) * p.d * .5f
        val hz = abs(sin(p.rot)) * p.w * .5f + abs(cos(p.rot)) * p.d * .5f
        if (abs(p.x) - hx <= 400f && blocksRoad(p.z, hz * 2f)) return true
        for (col in -5..4) {
            val road = col * COLUMN_SPACING + COLUMN_SPACING * .5f
            if (abs(p.x - road) - hx < HALF_ROAD + CLEARANCE) return true
        }
        return false
    }
    fun blocksRoad(z: Float, depth: Float): Boolean {
        val n = Math.round((z - START_Z) / ROW_SPACING - 0.5f)
        val road = crossZ(n)
        return Math.abs(z - road) - depth * 0.5f < HALF_ROAD + CLEARANCE
    }
}

/**
 * دنیای یک ماموریت؛ به صورت رویه‌ای و با بذر ثابت ساخته می‌شود
 * تا هر مرحله همیشه یکسان باشد (و چالش روزانه هر روز متفاوت).
 */
class World(val level: LevelDef, seed: Long, private val difficultyScale: Float = 1f) {

    companion object {
        /** سقف تعداد سامانه‌های پدافندی در یک صحنه */
        const val MAX_AA_SITES = 12
        private const val TWO_PI = (Math.PI * 2).toFloat()

        /** پروفایل نیم‌عرض بدنه‌ی ناوشکن در طول بدنه: جفت‌های (z نرمال، ضریب
         *  نیم‌عرض) از پاشنه (۱-) تا دماغه (۱) — همان اعداد مقاطع سازنده‌ی
         *  بدنه در Vehicles.warship. */
        private val WARSHIP_PROFILE = floatArrayOf(
            -1f, .80f, -.70f, .97f, -.25f, 1f, .22f, .96f, .62f, .74f, .88f, .40f, 1f, .16f
        )
        /** پروفایل نیم‌عرض کشتی باری (Vehicles.freighter). */
        private val FREIGHTER_PROFILE = floatArrayOf(
            -1f, .86f, -.4f, 1f, .5f, .94f, .86f, .60f, 1f, .16f
        )
    }

    /** ضریب نیم‌عرض مؤثر بدنه‌ی کشتی در موقعیت طولی [lzNorm] (۱- تا ۱). */
    private fun shipBeamFactor(kind: PropKind, lzNorm: Float): Float {
        val pts = if (kind == PropKind.FREIGHTER) FREIGHTER_PROFILE else WARSHIP_PROFILE
        val t = lzNorm.coerceIn(-1f, 1f)
        var i = 0
        while (i < pts.size - 2) {
            val z0 = pts[i]; val f0 = pts[i + 1]
            val z1 = pts[i + 2]; val f1 = pts[i + 3]
            if (t <= z1) {
                val u = if (z1 > z0) (t - z0) / (z1 - z0) else 0f
                return (f0 + (f1 - f0) * u).coerceAtLeast(0.16f)
            }
            i += 2
        }
        return pts[pts.size - 1].coerceAtLeast(0.16f)
    }

    /**
     * آیا نقطه داخل حجمِ واقعیِ کشتی است — با بدنه‌ی باریک‌شونده.
     * قبلاً برخورد کشتی‌ها جعبه‌ی مستطیل کامل بود: دور دماغه و پاشنه،
     * جایی که بدنه باریک شده و آب دیده می‌شود، «دیوار نامرئی» می‌ایستاد
     * و پهباد در هوای خالی منفجر می‌شد (فیلم کاربر، انتهای مرحله‌ی ناو).
     */
    private fun pointInShip(p: Prop, px: Float, py: Float, pz: Float): Boolean {
        if (py < 0f || py > p.h) return false
        val s = sin(p.rot); val c = cos(p.rot)
        val dx = px - p.x; val dz = pz - p.z
        val lx = dx * c + dz * s
        val lz = -dx * s + dz * c
        if (abs(lz) >= p.d * .5f) return false
        return abs(lx) < p.w * .5f * shipBeamFactor(p.kind, lz / (p.d * .5f))
    }

    private val rnd = Random(seed)
    val palette = level.env.palette

    val targets = ArrayList<Target>()
    val aaSites = ArrayList<AASite>()
    val missiles = ArrayList<Missile>()
    val radars = ArrayList<RadarZone>()
    val ewZones = ArrayList<EwZone>()
    val props = ArrayList<Prop>()

    /** ساختمان‌هایی که پدافند روی پشت‌بامشان نشسته و باید حفظ شوند */
    private val hostedRoofDefenses = HashSet<Prop>()
    val bonuses = ArrayList<Bonus>()

    val launchHeight = 80f
    val distance = level.distance

    /** جهت و شدت باد */
    val windDir: Float = rnd.nextFloat() * (Math.PI * 2).toFloat()
    val windSpeed: Float = level.wind * 13f

    init {
        buildTargets()
        buildDefenses()
        buildScenery()
        clearObjectiveFootprints()
        placeRoofDefenses()
        placeNavalDefenses()
        clearObjectiveFootprints()
        buildBonuses()
    }

    /**
     * Scenery is scattered independently of targets and air defence, so a city
     * block or rock could be generated right on top of a secondary target or an
     * AA site: the target was then hidden inside a building and the drone died on
     * the building before it could reach it. Remove any prop whose footprint
     * overlaps an objective (plus a small clearance). Removal consumes no RNG, so
     * every seed still produces the same world otherwise.
     */
    private fun clearObjectiveFootprints() {
        fun overlaps(p: Prop, x: Float, z: Float, r: Float): Boolean {
            val s = sin(p.rot); val c = cos(p.rot)
            val dx = x - p.x; val dz = z - p.z
            val lx = dx * c + dz * s
            val lz = -dx * s + dz * c
            val qx = (abs(lx) - p.w * .5f).coerceAtLeast(0f)
            val qz = (abs(lz) - p.d * .5f).coerceAtLeast(0f)
            return qx * qx + qz * qz < r * r
        }
        props.removeAll { p ->
            // ساختمانی که پدافند روی پشت‌بامش نشسته حفظ می‌شود — پدافند بالای آن است
            if (hostedRoofDefenses.contains(p)) return@removeAll false
            targets.any { t ->
                val r = kotlin.math.max(t.w, t.d) * .5f + 10f
                // moving targets sweep along their heading: keep their lane clear too
                if (t.moving) {
                    val hz = abs(sin(p.rot)) * p.w * .5f + abs(cos(p.rot)) * p.d * .5f
                    val hx = abs(cos(p.rot)) * p.w * .5f + abs(sin(p.rot)) * p.d * .5f
                    val targetHalfZ = abs(sin(t.heading)) * t.w * .5f + abs(cos(t.heading)) * t.d * .5f
                    abs(p.z - t.z) < hz + targetHalfZ + 12f && abs(p.x) - hx < 380f + kotlin.math.max(t.w, t.d) * .5f
                } else overlaps(p, t.x, t.z, r)
            } || aaSites.any { a -> a.y <= 0f && overlaps(p, a.x, a.z, 12f) } ||
                radars.any { r -> overlaps(p, r.x, r.z, 14f) }
        }
    }

    /** در ماموریت‌های شهریِ سخت، برخی پدافندها روی پشت‌بام بلندترین ساختمانِ
     * نزدیک می‌نشینند (از دستور کاربر برای مراحل آخر). ساختمان میزبان در
     * [hostedRoofDefenses] نگه داشته می‌شود تا پاک‌سازی اهداف آن را نسوزاند. */
    private fun placeRoofDefenses() {
        if (level.difficulty < 4) return
        if (level.env != Env.URBAN && level.env != Env.SPECIAL) return
        for (a in aaSites) {
            if (rnd.nextFloat() >= 0.65f) continue
            var best: Prop? = null
            var bestDist = 60f
            for (p in props) {
                if (p.kind != PropKind.BUILDING || p.h < 30f || p.w < 14f) continue
                val dx = p.x - a.x; val dz = p.z - a.z
                val d = Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
                if (d < bestDist) { bestDist = d; best = p }
            }
            best?.let { p ->
                a.x = p.x; a.z = p.z; a.y = p.h + 0.4f
                a.hostProp = p
                hostedRoofDefenses.add(p)
            }
        }
    }

    /** جزیره‌ی کوچک زیر یک سامانه‌ی دریایی می‌سازد و آن را در برابر
     *  پاک‌سازی ردپای اهداف حفظ می‌کند. ارتفاع پراپ = ارتفاع سکوی جزیره. */
    private fun addIsland(x: Float, z: Float, w: Float, h: Float): Prop {
        val p = Prop(x, z, w, h, w, Theme.shade(palette.prop, 1.0f), rnd.nextFloat() * 3f, PropKind.ISLAND)
        props.add(p)
        hostedRoofDefenses.add(p)
        return p
    }

    /**
     * در مراحل دریایی هیچ پدافند و راداری روی آب نمی‌نشیند (دستور کاربر) —
     * و سامانه‌ها هم یک‌جا متمرکز نمی‌شوند؛ در کل صحنه پخش‌اند:
     * ۱) فقط نزدیک‌ترین سامانه به هدف، روی عرشه‌ی خودِ هدف اصلی سوار
     * می‌شود (ناو هواپیمابر/ناوشکن) و با کشتی حرکت می‌کند؛ با غرق شدن
     * کشتی، سامانه‌اش هم از کار می‌افتد.
     * ۲) هر سامانه‌ی دیگر روی یک ناو اسکورت/کشتی باریِ **جدا** می‌نشیند
     * (نزدیک‌ترین کشتیِ هنوز خالی به موقعیت مسیرش).
     * ۳) اگر کشتی آزادی نزدیک نبود، زیر همان سامانه جزیره‌ی کوچک ساخته
     * می‌شود. رادارها هم هر کدام جزیره‌ی خودشان را دارند.
     */
    private fun placeNavalDefenses() {
        if (level.env != Env.NAVAL) return
        val primary = targets.firstOrNull()
        // نزدیک‌ترین سامانه به هدف (بیشترین z) تنها سامانه‌ی سوار بر هدف است
        var targetHostIdx = -1
        if (primary != null &&
            (primary.shape == TargetShape.CARRIER || primary.shape == TargetShape.SHIP)) {
            var bestZ = Float.NEGATIVE_INFINITY
            for ((i, a) in aaSites.withIndex()) {
                if (a.z > bestZ) { bestZ = a.z; targetHostIdx = i }
            }
        }
        val usedShips = HashSet<Prop>()
        for ((idx, a) in aaSites.withIndex()) {
            if (idx == targetHostIdx && primary != null) {
                val carrier = primary.shape == TargetShape.CARRIER
                a.hostTarget = primary
                a.hostLX = if (carrier) 13f else 0f
                a.hostLZ = if (carrier) 50f else -52f
                a.hostDeckY = if (carrier) primary.h * 0.68f else primary.h * 0.55f + 0.34f
                continue
            }
            var best: Prop? = null
            var bestD = 500f
            for (p in props) {
                if (p.kind != PropKind.SHIP && p.kind != PropKind.FREIGHTER) continue
                if (usedShips.contains(p)) continue
                val dx = p.x - a.x; val dz = p.z - a.z
                val d = Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
                if (d < bestD) { bestD = d; best = p }
            }
            val host = best
            if (host != null) {
                usedShips.add(host)
                hostedRoofDefenses.add(host)
                a.hostProp = host
                // نقطه‌ی نشستن روی عرشه‌ی میزبان باید خالی باشد:
                // ناوچه: روی صفحه‌ی پد هلی در عقب (بالای صفحه، نه توی آن).
                // کشتی باری: شکاف خالی بین خانه‌ی سکان و اولین ردیف کانتینر —
                // نقطه‌ی قبلی (+۰٫۳۶ طول) دقیقاً توی ردیف چهارم کانتینرها بود.
                val lz = if (host.kind == PropKind.SHIP) -host.d * 0.38f else -host.d * 0.23f
                a.x = host.x - lz * sin(host.rot)
                a.z = host.z + lz * cos(host.rot)
                a.y = if (host.kind == PropKind.SHIP) host.h * 0.55f + 0.34f else host.h * 0.6f + 0.2f
            } else {
                val island = addIsland(a.x, a.z, 38f, 7f)
                a.hostProp = island
                a.y = island.h
            }
        }
        for (r in radars) {
            r.y = addIsland(r.x, r.z, 64f, 9f).h
        }
        // موقعیت سامانه‌ی سوار بر هدف از همان فریم اول درست باشد
        updateHostedDefenses()
    }

    /** سامانه‌های سوار بر هدف متحرک را به عرشه‌ی میزبانشان می‌چسباند. */
    private fun updateHostedDefenses() {
        for (a in aaSites) {
            val t = a.hostTarget ?: continue
            if (t.destroyed) {
                // سامانه با کشتیِ میزبانش غرق می‌شود
                a.alive = false
                continue
            }
            val s = sin(t.heading); val c = cos(t.heading)
            a.x = t.x + a.hostLX * c - a.hostLZ * s
            a.z = t.z + a.hostLX * s + a.hostLZ * c
            a.y = a.hostDeckY
        }
    }

    // ------------------------------------------------------------- ساخت اهداف

    private fun shapeFor(): TargetShape = when (level.id) {
        "L1_1" -> TargetShape.BUNKER
        "L1_2" -> TargetShape.BUILDING
        "L1_3" -> TargetShape.BRIDGE
        "L2_1" -> TargetShape.VEHICLE
        "L2_2" -> TargetShape.TOWER
        "L2_3" -> TargetShape.BUILDING
        "L2_4" -> TargetShape.BUILDING
        "L2_5" -> TargetShape.SHIP
        "L3_1" -> TargetShape.BUILDING
        "L3_2" -> TargetShape.ANTENNA
        "L3_3" -> TargetShape.BUILDING
        "L3_4" -> TargetShape.BUILDING
        "L3_5" -> TargetShape.TOWER
        "L4_1" -> TargetShape.TRAIN
        "L4_2" -> TargetShape.TUNNEL
        "L4_3" -> TargetShape.ANTENNA
        "L4_4" -> TargetShape.CARRIER
        "L4_5" -> TargetShape.BUILDING
        else -> when {
            level.env == Env.NAVAL -> TargetShape.SHIP
            level.movingTarget -> if (rnd.nextBoolean()) TargetShape.VEHICLE else TargetShape.TRAIN
            else -> when (rnd.nextInt(4)) {
                0 -> TargetShape.BUNKER
                1 -> TargetShape.TOWER
                2 -> TargetShape.ANTENNA
                else -> TargetShape.BUILDING
            }
        }
    }

    private fun buildTargets() {
        val shape = shapeFor()
        val count = level.targets.coerceAtLeast(1)
        for (i in 0 until count) {
            val lateral = if (i == 0) 0f else (if (i % 2 == 0) 1f else -1f) * (70f + rnd.nextFloat() * 120f)
            val depth = distance + (if (i == 0) 0f else (rnd.nextFloat() * 220f - 110f))
            val s = if (i == 0) shape else secondaryShape(shape)
            targets.add(makeTarget(s, lateral, depth, i))
        }
    }

    private fun secondaryShape(main: TargetShape): TargetShape = when (main) {
        TargetShape.SHIP, TargetShape.CARRIER -> TargetShape.SHIP
        TargetShape.ANTENNA -> TargetShape.ANTENNA
        TargetShape.TRAIN -> TargetShape.TRAIN
        else -> if (rnd.nextBoolean()) TargetShape.BUILDING else TargetShape.TOWER
    }

    private fun makeTarget(shape: TargetShape, x: Float, z: Float, index: Int): Target {
        val moving = level.movingTarget && (shape == TargetShape.VEHICLE ||
            shape == TargetShape.SHIP || shape == TargetShape.CARRIER || shape == TargetShape.TRAIN)
        val heading = when (shape) {
            TargetShape.SHIP, TargetShape.CARRIER -> (Math.PI * 0.5).toFloat()
            TargetShape.TRAIN -> (Math.PI * 0.5).toFloat()
            else -> if (rnd.nextBoolean()) (Math.PI * 0.5).toFloat() else (-Math.PI * 0.5).toFloat()
        }
        val speed = when (shape) {
            TargetShape.VEHICLE -> 11f + rnd.nextFloat() * 7f
            TargetShape.SHIP -> 7f + rnd.nextFloat() * 4f
            TargetShape.CARRIER -> 6f + rnd.nextFloat() * 3f
            TargetShape.TRAIN -> 24f + rnd.nextFloat() * 8f
            else -> 0f
        }
        val label = if (index == 0) level.targetName else {
            val faction = when {
                "آمریکا" in level.targetName && "اسرائیل" in level.targetName -> " ارتش‌های تروریستی آمریکا و اسرائیل"
                "اسرائیل" in level.targetName -> " ارتش تروریستی اسرائیل"
                "آمریکا" in level.targetName -> " ارتش تروریستی آمریکا"
                else -> ""
            }
            "هدف فرعی " + Fa.num(index + 1) + faction
        }
        return when (shape) {
            TargetShape.BUNKER -> Target(x, z, shape, 26f, 9f, 26f, 15f, false, 0f, 0f, label)
            TargetShape.BUILDING -> Target(x, z, shape, 40f, 26f, 34f, 22f, false, 0f, 0f, label)
            TargetShape.TOWER -> Target(x, z, shape, 22f, 48f, 22f, 18f, false, 0f, 0f, label)
            TargetShape.SHIP -> Target(x, z, shape, 22f, 20f, 132f, 26f, moving, heading, speed, label)
            TargetShape.CARRIER -> Target(x, z, shape, 52f, 30f, 268f, 44f, moving, heading, speed, label)
            TargetShape.VEHICLE -> Target(x, z, shape, 10f, 7f, 22f, 11f, moving, heading, speed, label)
            TargetShape.BRIDGE -> Target(x, z, shape, 140f, 16f, 22f, 18f, false, 0f, 0f, label)
            TargetShape.ANTENNA -> Target(x, z, shape, 14f, 72f, 14f, 14f, false, 0f, 0f, label)
            TargetShape.TRAIN -> Target(x, z, shape, 12f, 10f, 160f, 16f, moving, heading, speed, label)
            TargetShape.TUNNEL -> Target(x, z, shape, 30f, 14f, 20f, 12f, false, 0f, 0f, label)
        }
    }

    // ------------------------------------------------------------- پدافند

    private fun buildDefenses() {
        // سقف تعداد سامانه‌ها تا در موج‌های بالای حالت بی‌نهایت رشد بی‌پایان نکند
        val count = Math.round(level.aaSites * difficultyScale).coerceIn(0, MAX_AA_SITES)
        for (i in 0 until count) {
            val t = 0.35f + 0.6f * (i.toFloat() / Math.max(1, count - 1).toFloat())
            val z = distance * t.coerceAtMost(0.98f) + (rnd.nextFloat() * 60f - 30f)
            val side = if (i % 2 == 0) 1f else -1f
            val x = side * (50f + rnd.nextFloat() * 190f)
            // پرتابگر موشک برد بلندتر و آتش کندتر دارد، توپ برد کوتاه و آتش تند
            val sam = i % 3 != 1
            val kind = if (sam) AASite.KIND_SAM else AASite.KIND_GUN
            val range = if (sam) 360f + rnd.nextFloat() * 170f else 240f + rnd.nextFloat() * 110f
            val reload = ((if (sam) 2.6f else 1.9f) - 0.16f * level.difficulty).coerceAtLeast(1.0f)
            val bodyYaw = Math.atan2(-x.toDouble(), (distance * 0.2 - z).toDouble()).toFloat() +
                (rnd.nextFloat() - 0.5f) * 0.7f
            val site = AASite(range, reload, kind, bodyYaw)
            site.x = x; site.z = z
            aaSites.add(site)
        }
        for (i in 0 until level.radarZones) {
            val frac = if (level.radarZones == 1) 0.55f else 0.45f + 0.45f * i / (level.radarZones - 1).toFloat()
            val z = distance * frac
            radars.add(RadarZone(rnd.nextFloat() * 120f - 60f, z, 420f, 55f + rnd.nextFloat() * 25f))
        }
        for (i in 0 until level.ewZones) {
            val z = distance * (0.3f + 0.6f * (i.toFloat() / Math.max(1, level.ewZones).toFloat()))
            ewZones.add(EwZone(rnd.nextFloat() * 200f - 100f, z, 170f + rnd.nextFloat() * 90f))
        }
    }

    // ------------------------------------------------------------- محیط

    private fun buildScenery() {
        when (level.env) {
            Env.URBAN -> buildCity()
            Env.NAVAL -> buildSea()
            Env.SPECIAL -> {
                buildDesert()
                // Scattered rotated props must respect the same road footprint as city blocks.
                props.removeAll { CityLayout.blocksRoad(it) }
                buildCity()
            }
            else -> buildDesert()
        }
    }

    private fun buildDesert() {
        val count = 90
        for (i in 0 until count) {
            val x = rnd.nextFloat() * 1400f - 700f
            val z = rnd.nextFloat() * (distance + 500f) - 150f
            if (Math.abs(x) < 26f && Math.abs(z - distance) < 60f) continue
            val roll = rnd.nextFloat()
            val kind = when {
                roll < 0.50f -> PropKind.ROCK
                roll < 0.74f -> PropKind.BUSH
                roll < 0.90f -> PropKind.TREE
                else -> PropKind.PALM
            }
            val h = when (kind) {
                PropKind.ROCK -> 3f + rnd.nextFloat() * 9f
                PropKind.BUSH -> 1.4f + rnd.nextFloat() * 1.6f
                PropKind.PALM -> 9f + rnd.nextFloat() * 6f
                else -> 6f + rnd.nextFloat() * 5f
            }
            val w = when (kind) {
                PropKind.ROCK -> 6f + rnd.nextFloat() * 16f
                PropKind.BUSH -> 2.2f + rnd.nextFloat() * 2.0f
                PropKind.PALM -> 5f + rnd.nextFloat() * 2f
                else -> 4f + rnd.nextFloat() * 3f
            }
            val color = when (kind) {
                PropKind.ROCK -> Theme.shade(palette.prop, 0.9f + rnd.nextFloat() * 0.3f)
                PropKind.BUSH -> 0xFF6A6A44.toInt()
                else -> 0xFF4E6B3E.toInt()
            }
            props.add(
                Prop(x, z, w, h, w * (0.7f + rnd.nextFloat() * 0.6f), color, rnd.nextFloat() * 3f, kind)
            )
        }
        // چند سازه‌ی نظامی برای فضاسازی
        for (i in 0 until 8) {
            val x = (if (rnd.nextBoolean()) 1f else -1f) * (120f + rnd.nextFloat() * 320f)
            val z = rnd.nextFloat() * distance
            props.add(
                Prop(x, z, 14f + rnd.nextFloat() * 12f, 6f + rnd.nextFloat() * 8f,
                    14f + rnd.nextFloat() * 10f, Theme.shade(palette.prop, 1.15f),
                    rnd.nextFloat(), PropKind.BUILDING)
            )
        }
    }

    private fun buildCity() {
        var row = 0
        while (CityLayout.rowZ(row) < distance - 80f) {
            val z = CityLayout.rowZ(row)
            var col = -4
            while (col <= 4) {
                val w = 34f + rnd.nextFloat() * 22f
                val limit = CityLayout.jitterLimit(w)
                val x = col * CityLayout.COLUMN_SPACING + (rnd.nextFloat() * 2f - 1f) * limit
                val central = Math.abs(x) < 30f
                val skip = rnd.nextFloat() < (if (central) 0.62f else 0.24f)
                if (!skip && Math.abs(z - distance) > 70f) {
                    val h = if (central) 14f + rnd.nextFloat() * 26f else 22f + rnd.nextFloat() * 70f
                    // عمق ساختمان هم با کف شبکه محدود می‌شود تا از خیابان عرضی رد نشود
                    val dMax = (CityLayout.ROW_SPACING * 0.5f - CityLayout.HALF_ROAD -
                        CityLayout.CLEARANCE) * 2f
                    props.add(
                        Prop(
                            x, z, w, h, min(30f + rnd.nextFloat() * 22f, dMax),
                            Theme.shade(palette.prop, 0.75f + rnd.nextFloat() * 0.5f),
                            0f, PropKind.BUILDING
                        )
                    )
                }
                col++
            }
            row++
        }
    }

    private fun buildSea() {
        for (i in 0 until 10) {
            val x = (if (rnd.nextBoolean()) 1f else -1f) * (110f + rnd.nextFloat() * 420f)
            val z = 250f + rnd.nextFloat() * (distance - 150f)
            // ناوهای اسکورت باریک‌تر و کشتی‌های باری پهن‌ترند
            val escort = rnd.nextFloat() < 0.6f
            val kind = if (escort) PropKind.SHIP else PropKind.FREIGHTER
            val beam = if (escort) 15f + rnd.nextFloat() * 6f else 22f + rnd.nextFloat() * 10f
            val len = if (escort) 78f + rnd.nextFloat() * 46f else 120f + rnd.nextFloat() * 90f
            props.add(
                Prop(x, z, beam, 10f + rnd.nextFloat() * 8f, len,
                    Theme.shade(palette.prop, 1.0f),
                    (rnd.nextFloat() - 0.5f) * 0.6f + (Math.PI * 0.5).toFloat(), kind)
            )
        }
    }

    private fun buildBonuses() {
        val n = level.bonuses
        for (i in 0 until n) {
            val t = (i + 1f) / (n + 1f)
            val z = distance * t
            val x = rnd.nextFloat() * 240f - 120f
            var y = 25f + rnd.nextFloat() * 110f
            // A bonus generated inside a tall building could only be "collected" by
            // crashing into it, making the all-bonuses star impossible on city maps.
            // Lift it clear of any roof within the 22 m pickup radius.
            for (p in props) {
                if (p.kind == PropKind.BUSH || p.kind == PropKind.TREE || p.kind == PropKind.PALM) continue
                val reach = kotlin.math.max(p.w, p.d) * .75f + 22f
                val dx = p.x - x; val dz = p.z - z
                if (dx * dx + dz * dz < reach * reach) y = kotlin.math.max(y, p.h + 16f)
            }
            bonuses.add(Bonus(x, y, z, 100))
        }
    }

    // ------------------------------------------------------------- به‌روزرسانی

    fun update(dt: Float) {
        for (t in targets) {
            if (t.destroyed) {
                t.burn += dt
                continue
            }
            if (t.moving && t.speed > 0f) {
                val vx = -Math.sin(t.heading.toDouble()).toFloat()
                val vz = Math.cos(t.heading.toDouble()).toFloat()
                t.x += vx * t.speed * dt
                t.z += vz * t.speed * dt
                // برگشت در محدوده؛ هم‌جهت با محور جلوی مدل (-sin(heading), +cos(heading))
                if (Math.abs(t.x) > 380f) {
                    val sx = Math.signum(t.x)
                    t.x = 380f * sx
                    if (vx * sx > 0f) t.heading = (t.heading + Math.PI.toFloat()) % TWO_PI
                }
            }
        }
        updateHostedDefenses()
        for (b in bonuses) {
            b.spin += dt * 2.4f
            if (b.spin > TWO_PI) b.spin -= TWO_PI
        }
        for (r in radars) {
            r.spin += dt * 0.62f
            if (r.spin > TWO_PI) r.spin -= TWO_PI
        }
        for (a in aaSites) {
            if (a.flash > 0f) a.flash -= dt
        }
    }

    fun activeTarget(): Target? = targets.firstOrNull { !it.destroyed }

    fun allDestroyed(): Boolean = targets.all { it.destroyed }

    /** Segment/rotated-box test for solid scenery; does not skip thin walls. */
    fun projectileHitsProp(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Boolean =
        projectileHitsPropExcept(ax, ay, az, bx, by, bz, null)

    /**
     * مثل [projectileHitsProp] ولی یک پراپ (سکوی پرتاب خود موشک) نادیده
     * گرفته می‌شود: دهانه‌ی سامانه‌های روی عرشه **داخل** حجم برخوردِ
     * کشتیِ میزبان است (عرشه در ۰٫۶ ارتفاع، سقف حجم تا خود ارتفاع) و
     * موشک در همان گام اول به میزبان خودش می‌خورد و بی‌صدا حذف می‌شد —
     * سامانه فلش می‌زد ولی هیچ موشکی پرواز نمی‌کرد (گزارش کاربر).
     */
    fun projectileHitsPropExcept(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, ignore: Prop?
    ): Boolean {
        for (p in props) {
            if (p === ignore) continue
            if (p.kind == PropKind.BUSH || p.kind == PropKind.TREE || p.kind == PropKind.PALM) continue
            // کشتی: حجم باریک‌شونده — نمونه‌برداری روی پاره‌خط با همان تست نقطه‌ای
            if (p.kind == PropKind.SHIP || p.kind == PropKind.FREIGHTER) {
                var i = 0
                while (i <= 6) {
                    val t = i / 6f
                    if (pointInShip(p, ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t)) return true
                    i++
                }
                continue
            }
            // صخره — مثل برخورد خودِ پهباد — حجم استوانه‌ای دارد، نه جعبهٔ
            // کامل؛ وگرنه موشک به گوشه‌های نامرئیِ جعبه می‌خورد و در هوا
            // منفجر می‌شود، جایی که پهباد آزادانه رد می‌شود.
            if (p.kind == PropKind.ROCK) {
                if (segmentHitsCylinder(p.x, p.z, p.w * 0.42f, p.h, ax, ay, az, bx, by, bz)) return true
                continue
            }
            if (segmentHitsBox(p, ax, ay, az, bx, by, bz)) return true
        }
        return false
    }

    /** آیا این نقطه داخل یکی از موانع است */
    fun hitsProp(px: Float, py: Float, pz: Float): Boolean = pointInProp(px, py, pz)

    /**
     * برخورد جاروب‌شده بین موقعیت فریم قبل و فریم فعلی.
     * قبلاً فقط نقطه‌ی پایانی آزمایش می‌شد، پس پهباد پرسرعت می‌توانست از دیوار عبور کند.
     */
    fun hitsPropSwept(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float
    ): Boolean {
        // Exact segment tests. The old version sampled at most 12 points 4 m apart,
        // so a long frame (low FPS or boost) could step straight through a wall.
        for (p in props) {
            if (ay > p.h && by > p.h) continue
            when (p.kind) {
                PropKind.TREE, PropKind.PALM, PropKind.BUSH, PropKind.ROCK -> {
                    if (segmentHitsCylinder(p.x, p.z, p.w * 0.42f, p.h, ax, ay, az, bx, by, bz)) return true
                }
                else -> if (p.kind == PropKind.SHIP || p.kind == PropKind.FREIGHTER) {
                    // بدنه‌ی باریک‌شونده جعبه نیست؛ پاره‌خط نمونه‌برداری می‌شود
                    var i = 0
                    var shipHit = false
                    while (i <= 8 && !shipHit) {
                        val t = i / 8f
                        shipHit = pointInShip(p, ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t)
                        i++
                    }
                    if (shipHit) return true
                } else if (segmentHitsBox(p, ax, ay, az, bx, by, bz)) return true
            }
        }
        return false
    }

    private fun segmentHitsCylinder(
        cx: Float, cz: Float, r: Float, h: Float,
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float
    ): Boolean {
        var t0 = 0f
        var t1 = 1f
        val dy = by - ay
        if (abs(dy) < 1e-6f) {
            if (ay < 0f || ay > h) return false
        } else {
            val a = (0f - ay) / dy
            val b = (h - ay) / dy
            t0 = maxOf(t0, minOf(a, b))
            t1 = minOf(t1, maxOf(a, b))
            if (t0 > t1) return false
        }
        val sx = bx - ax
        val sz = bz - az
        val len2 = sx * sx + sz * sz
        val t = if (len2 < 1e-6f) t0 else (((cx - ax) * sx + (cz - az) * sz) / len2).coerceIn(t0, t1)
        val qx = ax + sx * t - cx
        val qz = az + sz * t - cz
        return qx * qx + qz * qz < r * r
    }

    private fun segmentHitsBox(p: Prop, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Boolean {
        val maxHalf = kotlin.math.max(p.w, p.d) * 0.72f
        if (maxOf(ax, bx) < p.x - maxHalf || minOf(ax, bx) > p.x + maxHalf) return false
        if (maxOf(az, bz) < p.z - maxHalf || minOf(az, bz) > p.z + maxHalf) return false
        val s = sin(p.rot); val c = cos(p.rot)
        val x0 = (ax - p.x) * c + (az - p.z) * s
        val z0 = -(ax - p.x) * s + (az - p.z) * c
        val x1 = (bx - p.x) * c + (bz - p.z) * s
        val z1 = -(bx - p.x) * s + (bz - p.z) * c
        var enter = 0f
        var leave = 1f
        val hw = p.w * .5f
        val hd = p.d * .5f

        val dx = x1 - x0
        if (abs(dx) < .000001f) {
            if (x0 < -hw || x0 > hw) return false
        } else {
            val a = (-hw - x0) / dx
            val b = (hw - x0) / dx
            enter = maxOf(enter, minOf(a, b))
            leave = minOf(leave, maxOf(a, b))
            if (enter > leave) return false
        }
        val dy = by - ay
        if (abs(dy) < .000001f) {
            if (ay < 0f || ay > p.h) return false
        } else {
            val a = (0f - ay) / dy
            val b = (p.h - ay) / dy
            enter = maxOf(enter, minOf(a, b))
            leave = minOf(leave, maxOf(a, b))
            if (enter > leave) return false
        }
        val dz = z1 - z0
        if (abs(dz) < .000001f) {
            if (z0 < -hd || z0 > hd) return false
        } else {
            val a = (-hd - z0) / dz
            val b = (hd - z0) / dz
            enter = maxOf(enter, minOf(a, b))
            leave = minOf(leave, maxOf(a, b))
            if (enter > leave) return false
        }
        return true
    }

    private fun pointInProp(px: Float, py: Float, pz: Float): Boolean {
        for (p in props) {
            if (py > p.h) continue
            when (p.kind) {
                // کشتی‌ها بدنه‌ی باریک‌شونده دارند، نه جعبه‌ی کامل
                PropKind.SHIP, PropKind.FREIGHTER -> {
                    if (pointInShip(p, px, py, pz)) return true
                }
                // پوشش گیاهی و صخره حجم استوانه‌ای دارند، نه جعبه‌ی کامل
                PropKind.TREE, PropKind.PALM, PropKind.BUSH, PropKind.ROCK -> {
                    val r = p.w * 0.42f
                    val ddx = px - p.x
                    val ddz = pz - p.z
                    if (ddx * ddx + ddz * ddz < r * r) return true
                }
                else -> {
                    // چرخش مانع لحاظ می‌شود: نقطه به دستگاه محلی جعبه برده می‌شود
                    val s = Math.sin(p.rot.toDouble()).toFloat()
                    val c = Math.cos(p.rot.toDouble()).toFloat()
                    val ddx = px - p.x
                    val ddz = pz - p.z
                    val lx = ddx * c + ddz * s
                    val lz = -ddx * s + ddz * c
                    if (Math.abs(lx) < p.w * 0.5f && Math.abs(lz) < p.d * 0.5f) return true
                }
            }
        }
        return false
    }

    fun insideRadar(px: Float, pz: Float, py: Float): Boolean {
        for (r in radars) {
            val dx = px - r.x
            val dz = pz - r.z
            if (dx * dx + dz * dz < r.radius * r.radius && py > r.ceiling) return true
        }
        return false
    }

    fun insideEw(px: Float, pz: Float): Boolean {
        for (e in ewZones) {
            val dx = px - e.x
            val dz = pz - e.z
            if (dx * dx + dz * dz < e.radius * e.radius) return true
        }
        return false
    }
}
