package ir.shahed.pahpad.game

import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.data.Env
import ir.shahed.pahpad.data.LevelDef
import java.util.Random

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
    val x: Float,
    val z: Float,
    val range: Float,
    val reload: Float,
    val kind: Int = KIND_SAM,
    /** جهت ثابت شاسی؛ فقط برجک/لانچر می‌چرخد */
    val bodyYaw: Float = 0f
) {
    var cooldown = 1.2f
    var alive = true
    var barrelYaw = 0f
    /** زاویه‌ی ارتفاع لوله یا لانچر بر حسب رادیان */
    var barrelPitch = 0.45f
    var flash = 0f

    /** ارتفاع دهانه‌ی لوله از زمین؛ برای محاسبه‌ی زاویه‌ی نشانه‌روی */
    val muzzleHeight: Float get() = if (kind == KIND_SAM) 2.6f else 2.1f

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
}

class RadarZone(val x: Float, val z: Float, val radius: Float, val ceiling: Float) {
    /** زاویه‌ی آرایه‌ی چرخان رادار */
    var spin = 0f
}

class EwZone(val x: Float, val z: Float, val radius: Float)

enum class PropKind { BUILDING, ROCK, TREE, PALM, BUSH, SHIP, FREIGHTER, TOWER_MAST }

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
 * دنیای یک ماموریت؛ به صورت رویه‌ای و با بذر ثابت ساخته می‌شود
 * تا هر مرحله همیشه یکسان باشد (و چالش روزانه هر روز متفاوت).
 */
class World(val level: LevelDef, seed: Long, private val difficultyScale: Float = 1f) {

    companion object {
        /** سقف تعداد سامانه‌های پدافندی در یک صحنه */
        const val MAX_AA_SITES = 12
        private const val TWO_PI = (Math.PI * 2).toFloat()
    }

    private val rnd = Random(seed)
    val palette = level.env.palette

    val targets = ArrayList<Target>()
    val aaSites = ArrayList<AASite>()
    val missiles = ArrayList<Missile>()
    val radars = ArrayList<RadarZone>()
    val ewZones = ArrayList<EwZone>()
    val props = ArrayList<Prop>()
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
        buildBonuses()
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
        else -> if (level.env == Env.NAVAL) TargetShape.SHIP else TargetShape.BUILDING
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
        val label = if (index == 0) level.targetName else "هدف فرعی " + Fa.num(index + 1)
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
            aaSites.add(AASite(x, z, range, reload, kind, bodyYaw))
        }
        for (i in 0 until level.radarZones) {
            val z = distance * (0.55f + 0.35f * i)
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
            Env.SPECIAL -> { buildDesert(); buildCity() }
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
        val blockZ = 95f
        var z = 220f
        while (z < distance - 80f) {
            var col = -4
            while (col <= 4) {
                val x = col * 78f + (rnd.nextFloat() * 14f - 7f)
                val central = Math.abs(x) < 30f
                val skip = rnd.nextFloat() < (if (central) 0.62f else 0.24f)
                if (!skip && Math.abs(z - distance) > 70f) {
                    val h = if (central) 14f + rnd.nextFloat() * 26f else 22f + rnd.nextFloat() * 70f
                    val w = 34f + rnd.nextFloat() * 22f
                    props.add(
                        Prop(
                            x, z, w, h, 30f + rnd.nextFloat() * 22f,
                            Theme.shade(palette.prop, 0.75f + rnd.nextFloat() * 0.5f),
                            0f, PropKind.BUILDING
                        )
                    )
                }
                col++
            }
            z += blockZ
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
            val y = 25f + rnd.nextFloat() * 110f
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
                t.x += Math.sin(t.heading.toDouble()).toFloat() * t.speed * dt
                t.z += Math.cos(t.heading.toDouble()).toFloat() * t.speed * dt
                // برگشت در محدوده
                if (Math.abs(t.x) > 380f) t.heading += Math.PI.toFloat()
            }
        }
        for (b in bonuses) b.spin += dt * 2.4f
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
        val dx = bx - ax
        val dy = by - ay
        val dz = bz - az
        val span = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        val steps = Math.min(12, Math.max(1, Math.ceil((span / 4f).toDouble()).toInt()))
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            if (pointInProp(ax + dx * t, ay + dy * t, az + dz * t)) return true
        }
        return false
    }

    private fun pointInProp(px: Float, py: Float, pz: Float): Boolean {
        for (p in props) {
            if (py > p.h) continue
            when (p.kind) {
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
