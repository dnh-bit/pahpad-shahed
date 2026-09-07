package ir.shahed.pahpad.game

import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.DroneModels
import ir.shahed.pahpad.data.LevelDef
import ir.shahed.pahpad.data.StarRules
import ir.shahed.pahpad.data.UpgradeKind
import java.util.Random

enum class MissionMode { STORY, ENDLESS, DAILY }

/** نتیجه نهایی ماموریت */
class MissionResult(
    val success: Boolean,
    /** ماموریت با فرمان خود بازیکن لغو شد؛ در این حالت پاداشی ثبت نمی‌شود */
    val aborted: Boolean,
    val reason: String,
    val accuracy: Int,
    val timeScore: Int,
    val defense: Int,
    val fuelScore: Int,
    val bonusScore: Int,
    val elapsed: Float,
    val stars: Int,
    val coins: Int,
    val xp: Int,
    val targetsDestroyed: Int,
    val targetsTotal: Int,
    /** میانگین دقت برخورد بین ۰ و ۱؛ مبنای ستاره‌ی دوم */
    val avgAccuracy: Float,
    /** تعداد آسیب‌های پدافندی؛ مبنای ستاره‌ی سوم */
    val damage: Int,
    /** زمان مرجع ماموریت بر حسب ثانیه */
    val parTime: Float,
    /**
     * امتیاز نهایی. صریح ذخیره می‌شود تا مقدار نمایش‌داده‌شده با مقدار ثبت‌شده در
     * رکوردها یکی باشد (در حالت بی‌نهایت قبلاً دو عدد متفاوت بودند).
     */
    val scoreTotal: Int
) {
    val total: Int get() = scoreTotal

    /** قوانین ستاره با اندازه‌ی واقعی این ماموریت */
    fun starRules(): List<StarRules.Rule> = StarRules.evaluate(
        success, targetsDestroyed, targetsTotal, avgAccuracy, damage, elapsed, parTime
    )
}

/**
 * منطق کامل یک ماموریت: فیزیک پهباد، پدافند دشمن، رادار، جنگ الکترونیک،
 * سوخت، برخورد و محاسبه امتیاز.
 */
class Mission(
    val level: LevelDef,
    private val save: SaveManager,
    val mode: MissionMode,
    seed: Long
) {

    enum class Phase { READY, FLYING, ENDED }

    // ------------------------------------------------------------ مشخصات پهباد
    val model: DroneModels.Model =
        DroneModels.byId(save.selectedDroneId) ?: DroneModels.all.first()

    private val lvlRange = save.upgradeLevel(model.id, UpgradeKind.RANGE)
    private val lvlSpeed = save.upgradeLevel(model.id, UpgradeKind.SPEED)
    private val lvlGuide = save.upgradeLevel(model.id, UpgradeKind.GUIDANCE)
    private val lvlBlast = save.upgradeLevel(model.id, UpgradeKind.BLAST)
    private val lvlArmor = save.upgradeLevel(model.id, UpgradeKind.ARMOR)

    val maxRange: Float = model.rangeAt(lvlRange)
    val maxSpeedKmh: Float = model.speedAt(lvlSpeed)
    val guidance: Float = model.guidanceAt(lvlGuide)
    val blastRadius: Float = model.blastAt(lvlBlast)
    val armor: Int = model.armorAt(lvlArmor)

    private val minSpeedKmh = maxSpeedKmh * 0.42f

    // ------------------------------------------------------------ وضعیت
    var world: World = World(level, seed)
        private set
    var phase = Phase.READY
        private set
    var wave = 1
        private set

    private val rnd = Random(seed xor 0x5DEECE66L)
    private var baseSeed = seed

    // پهباد
    var x = 0f; var y = 0f; var z = 0f
    var prevX = 0f; var prevY = 0f; var prevZ = 0f
    /** سرعت لحظه‌ای بر حسب متر بر ثانیه؛ مستقل از نرخ فریم */
    private var velX = 0f
    private var velY = 0f
    private var velZ = 0f
    var yaw = 0f
    var pitch = 0f
    var roll = 0f
    var speedMps = 0f
    var throttle = 0.7f
    var boost = false
    var fpv = false

    // ورودی کاربر
    var steerX = 0f
    var steerY = 0f

    // منابع و وضعیت
    var fuelLeft = 0f
        private set
    var damage = 0
        private set
    var elapsed = 0f
        private set
    var timeLeft = 0f
        private set
    var dronesLeft = 1
        private set
    var signalLoss = 0f
        private set
    var radarLock = false
        private set
    var ewActive = false
        private set
    var shake = 0f
    var flash = 0f
    var lastImpactAccuracy = 0f
        private set

    private var accuracyPoints = 0f
    /** جمع دقت خام برخوردها برای محاسبه‌ی میانگین دقت (مبنای ستاره‌ی دوم) */
    private var precisionSum = 0f
    private var fuelRatioSum = 0f
    private var impacts = 0
    private var bonusesCollected = 0
    private var totalBonuses = 0
    private var targetsDestroyed = 0
    private var alarmTimer = 0f

    var result: MissionResult? = null
        private set

    /** پیام‌های کوتاه روی صفحه: متن و زمان باقی‌مانده */
    val toasts = ArrayList<Toast>()

    class Toast(val text: String, var life: Float, val color: Int)

    // رویدادها برای لایه‌ی نمایش
    var onExplosion: ((Float, Float, Float, Float) -> Unit)? = null
    var onSmoke: ((Float, Float, Float) -> Unit)? = null
    var onTracer: ((Float, Float, Float) -> Unit)? = null
    var onLaunch: (() -> Unit)? = null
    var onHit: (() -> Unit)? = null
    var onAlarm: (() -> Unit)? = null
    var onCollect: (() -> Unit)? = null
    var onFinish: ((MissionResult) -> Unit)? = null

    init {
        dronesLeft = when (mode) {
            MissionMode.ENDLESS -> 999
            else -> level.targets.coerceAtLeast(1)
        }
        totalBonuses = world.bonuses.size
        timeLeft = level.timeLimit
        resetDrone()
    }

    // ------------------------------------------------------------ آماده‌سازی

    private fun resetDrone() {
        x = 0f
        y = world.launchHeight
        z = 0f
        prevX = x; prevY = y; prevZ = z
        val t = world.activeTarget()
        yaw = if (t != null) Math.atan2((t.x - x).toDouble(), (t.z - z).toDouble()).toFloat() else 0f
        pitch = 0f
        roll = 0f
        speedMps = 0f
        velX = 0f; velY = 0f; velZ = 0f
        throttle = 0.72f
        boost = false
        // سوخت تضمینی: حتی با پهپاد بی‌ارتقا، برد کافی برای رسیدن به هدف وجود دارد
        // (بیشینهٔ برد مدل یا ۱٫۴۵ برابر فاصلهٔ ماموریت) + ۱۵٪ ذخیره برای پیچ و مانور
        fuelLeft = Math.max(maxRange, level.distance * 1.45f) * 1.15f
        signalLoss = 0f
        phase = Phase.READY
    }

    fun launch() {
        if (phase != Phase.READY) return
        phase = Phase.FLYING
        speedMps = minSpeedKmh / 3.6f
        onLaunch?.invoke()
        toast("پرتاب انجام شد", 1.6f, ir.shahed.pahpad.core.Theme.MINT)
    }

    fun toast(text: String, life: Float = 2.2f, color: Int = ir.shahed.pahpad.core.Theme.TEXT) {
        toasts.add(Toast(text, life, color))
        if (toasts.size > 4) toasts.removeAt(0)
    }

    // ------------------------------------------------------------ حلقه اصلی

    fun update(dt: Float) {
        for (i in toasts.indices.reversed()) {
            toasts[i].life -= dt
            if (toasts[i].life <= 0f) toasts.removeAt(i)
        }
        if (shake > 0f) shake = (shake - dt * 2.4f).coerceAtLeast(0f)
        if (flash > 0f) flash = (flash - dt * 3.2f).coerceAtLeast(0f)

        world.update(dt)
        if (phase != Phase.FLYING) {
            updateMissiles(dt)
            return
        }

        elapsed += dt
        if (level.timeLimit > 0f) {
            timeLeft -= dt
            if (timeLeft <= 0f) {
                finish(false, "زمان ماموریت به پایان رسید")
                return
            }
        }

        // ---- جنگ الکترونیک و رادار
        ewActive = world.insideEw(x, z)
        if (ewActive) {
            val resist = if (model.hasEcm) 0.4f else 1f
            signalLoss = (signalLoss + dt * 1.5f * resist).coerceAtMost(1f)
        } else {
            signalLoss = (signalLoss - dt * 1.1f).coerceAtLeast(0f)
        }
        radarLock = world.insideRadar(x, z, y)

        // ---- فرمان‌پذیری
        val jitter = if (signalLoss > 0.15f) (rnd.nextFloat() * 2f - 1f) * signalLoss * 1.1f else 0f
        val control = (1f - signalLoss * 0.55f)
        val turnRate = 0.62f * guidance
        val pitchRate = 0.5f * guidance
        yaw += (steerX * turnRate * control + jitter * 0.5f) * dt
        val pitchInput = if (save.invertPitch) -steerY else steerY
        pitch += (pitchInput * pitchRate * control + jitter * 0.25f) * dt
        pitch = pitch.coerceIn(-0.95f, 0.75f)
        roll += ((-steerX * 0.55f) - roll) * Math.min(1f, dt * 5f)

        // ---- سرعت
        val boostFactor = if (boost) 1.28f else 1f
        val targetSpeed = (minSpeedKmh + (maxSpeedKmh - minSpeedKmh) * throttle) * boostFactor / 3.6f
        speedMps += (targetSpeed - speedMps) * Math.min(1f, dt * 1.6f)

        // ---- حرکت
        prevX = x; prevY = y; prevZ = z
        val cp = Math.cos(pitch.toDouble()).toFloat()
        val dx = Math.sin(yaw.toDouble()).toFloat() * cp
        val dy = Math.sin(pitch.toDouble()).toFloat()
        val dz = Math.cos(yaw.toDouble()).toFloat() * cp
        val wx = Math.sin(world.windDir.toDouble()).toFloat() * world.windSpeed
        val wz = Math.cos(world.windDir.toDouble()).toFloat() * world.windSpeed
        val moveX = dx * speedMps + wx
        val moveY = dy * speedMps
        val moveZ = dz * speedMps + wz
        x += moveX * dt
        y += moveY * dt
        z += moveZ * dt
        velX = moveX; velY = moveY; velZ = moveZ

        // ---- سوخت (فقط مسافت پیموده‌شده توسط خود پهباد؛ باد سوخت نمی‌سوزاند)
        val used = Math.sqrt(
            ((x - prevX - wx * dt) * (x - prevX - wx * dt) +
             (y - prevY) * (y - prevY) +
             (z - prevZ - wz * dt) * (z - prevZ - wz * dt)).toDouble()
        ).toFloat() * (if (boost) 1.25f else 1f)
        fuelLeft -= used
        if (fuelLeft <= 0f) {
            fuelLeft = 0f
            explodeDrone("سوخت پهباد تمام شد")
            return
        }

        // ---- دود موتور
        if (rnd.nextFloat() < 0.55f) onSmoke?.invoke(x, y, z)

        // ---- برخورد با زمین و ساختمان
        if (y <= 1.5f) {
            if (!checkTargetImpact(true)) explodeDrone("پهباد به زمین برخورد کرد")
            return
        }
        if (world.hitsPropSwept(prevX, prevY, prevZ, x, y, z)) {
            if (!checkTargetImpact(true)) explodeDrone("برخورد با مانع")
            return
        }

        // ---- برخورد با هدف
        if (checkTargetImpact(false)) return

        // ---- جایزه‌های مخفی
        for (b in world.bonuses) {
            if (b.collected) continue
            val ddx = b.x - x; val ddy = b.y - y; val ddz = b.z - z
            if (ddx * ddx + ddy * ddy + ddz * ddz < 22f * 22f) {
                b.collected = true
                bonusesCollected++
                onCollect?.invoke()
                toast("بونوس مخفی پیدا شد", 1.4f, ir.shahed.pahpad.core.Theme.AMBER)
            }
        }

        // ---- پدافند
        updateAa(dt)
        updateMissiles(dt)
    }

    // ------------------------------------------------------------ پدافند هوایی

    private fun updateAa(dt: Float) {
        var underThreat = false
        for (a in world.aaSites) {
            if (!a.alive) continue
            val ddx = x - a.x
            val ddz = z - a.z
            val dist = Math.sqrt((ddx * ddx + ddz * ddz).toDouble()).toFloat()
            if (dist > a.range) continue
            underThreat = true
            a.barrelYaw = Math.atan2(ddx.toDouble(), ddz.toDouble()).toFloat()
            a.barrelPitch = Math.atan2((y - a.muzzleHeight).toDouble(), dist.toDouble()).toFloat()
            val rate = if (radarLock) 2.0f else 1f
            a.cooldown -= dt * rate
            if (a.cooldown <= 0f) {
                a.cooldown = a.reload * (0.75f + rnd.nextFloat() * 0.5f)
                a.flash = 0.12f
                fireMissile(a, dist)
            }
        }
        if (underThreat) {
            alarmTimer -= dt
            if (alarmTimer <= 0f) {
                alarmTimer = 1.5f
                onAlarm?.invoke()
            }
        }
    }

    private fun fireMissile(a: AASite, dist: Float) {
        // دقت پدافند: ارتفاع کم و سرعت بالا شانس خطا را زیاد می‌کند
        val altFactor = ((y - 25f) / 120f).coerceIn(0f, 1f)
        val speedFactor = 1f - (speedMps / (maxSpeedKmh / 3.6f)).coerceIn(0f, 1f) * 0.35f
        val lockBonus = if (radarLock) 1.35f else 1f
        val accuracy = (0.25f + 0.6f * altFactor) * speedFactor * lockBonus
        val flightTime = dist / 230f
        // پیش‌بینی از سرعت واقعی (متر بر ثانیه) محاسبه می‌شود، نه از اختلاف دو فریم
        // با گام ثابت ۰٫۰۱۶؛ رفتار پدافند دیگر به نرخ فریم دستگاه وابسته نیست.
        val leadX = x + velX * flightTime * 0.35f
        val leadY = y + velY * flightTime * 0.35f
        val leadZ = z + velZ * flightTime * 0.35f
        val errScale = (1f - accuracy.coerceIn(0f, 0.95f)) * 130f
        val tx = leadX + (rnd.nextFloat() * 2f - 1f) * errScale
        val ty = (leadY + (rnd.nextFloat() * 2f - 1f) * errScale * 0.6f).coerceAtLeast(4f)
        val tz = leadZ + (rnd.nextFloat() * 2f - 1f) * errScale
        val sx = a.x; val sy = 6f; val sz = a.z
        val len = Math.sqrt(
            ((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy) + (tz - sz) * (tz - sz)).toDouble()
        ).toFloat().coerceAtLeast(1f)
        val sp = 235f
        world.missiles.add(
            Missile(sx, sy, sz, (tx - sx) / len * sp, (ty - sy) / len * sp, (tz - sz) / len * sp)
        )
    }

    private fun updateMissiles(dt: Float) {
        val it = world.missiles.iterator()
        while (it.hasNext()) {
            val m = it.next()
            val px = m.x; val py = m.y; val pz = m.z
            m.x += m.vx * dt
            m.y += m.vy * dt
            m.z += m.vz * dt
            m.life -= dt
            onTracer?.invoke(m.x, m.y, m.z)
            if (phase == Phase.FLYING) {
                val d = distancePointSegment(x, y, z, px, py, pz, m.x, m.y, m.z)
                if (d < 12f) {
                    m.alive = false
                    takeDamage()
                }
            }
            if (m.life <= 0f || m.y < 0f || !m.alive) it.remove()
        }
    }

    private fun takeDamage() {
        damage++
        shake = 1f
        onHit?.invoke()
        if (damage >= armor) {
            explodeDrone("پهباد توسط پدافند دشمن ساقط شد")
        } else {
            toast("آسیب پدافندی: ${ir.shahed.pahpad.core.Fa.num(damage)} از ${ir.shahed.pahpad.core.Fa.num(armor)}",
                1.8f, ir.shahed.pahpad.core.Theme.RED)
        }
    }

    // ------------------------------------------------------------ برخورد با هدف

    private fun checkTargetImpact(forced: Boolean): Boolean {
        var best: Target? = null
        var bestDist = Float.MAX_VALUE
        for (t in world.targets) {
            if (t.destroyed) continue
            val d = distancePointSegment(
                t.x, t.centerY, t.z, prevX, prevY, prevZ, x, y, z
            )
            if (d < bestDist) {
                bestDist = d
                best = t
            }
        }
        val t = best ?: return false
        val effective = t.hitRadius + blastRadius * 0.35f
        if (bestDist <= effective || (forced && bestDist <= effective * 1.6f)) {
            impact(t, bestDist)
            return true
        }
        return false
    }

    private fun impact(t: Target, dist: Float) {
        t.destroyed = true
        targetsDestroyed++
        impacts++
        val perTarget = 1000f / level.targets.coerceAtLeast(1).toFloat()
        val precision = (1f - (dist / (t.hitRadius + blastRadius * 0.35f)).coerceIn(0f, 1f))
        lastImpactAccuracy = precision
        precisionSum += precision
        accuracyPoints += perTarget * (0.2f + 0.8f * Math.pow(precision.toDouble(), 1.15).toFloat())
        fuelRatioSum += (fuelLeft / maxRange).coerceIn(0f, 1f)
        flash = 1f
        shake = 1.4f
        onExplosion?.invoke(t.x, t.centerY, t.z, 1f + blastRadius / 45f)
        val quality = when {
            precision > 0.85f -> "برخورد مستقیم و دقیق"
            precision > 0.5f -> "هدف منهدم شد"
            else -> "هدف آسیب دید"
        }
        toast(quality, 2f, ir.shahed.pahpad.core.Theme.MINT)

        if (mode == MissionMode.ENDLESS) {
            nextWave()
            return
        }
        if (world.allDestroyed()) {
            finish(true, "همه اهداف منهدم شدند")
        } else {
            dronesLeft--
            if (dronesLeft <= 0) {
                finish(false, "پهبادی برای اهداف باقی‌مانده نماند")
            } else {
                toast("پهباد بعدی آماده پرتاب است", 2.4f, ir.shahed.pahpad.core.Theme.AMBER)
                resetDrone()
            }
        }
    }

    private fun explodeDrone(reason: String) {
        flash = 0.8f
        shake = 1.2f
        onExplosion?.invoke(x, y.coerceAtLeast(2f), z, 0.8f)
        if (mode == MissionMode.ENDLESS) {
            finish(targetsDestroyed > 0, reason)
            return
        }
        finish(false, reason)
    }

    // ------------------------------------------------------------ حالت بی‌نهایت

    private fun nextWave() {
        wave++
        baseSeed = baseSeed * 31 + wave
        val scale = 1f + wave * 0.35f
        world = World(level, baseSeed, scale)
        totalBonuses += world.bonuses.size
        resetDrone()
        phase = Phase.FLYING
        speedMps = minSpeedKmh / 3.6f
        toast("موج ${ir.shahed.pahpad.core.Fa.num(wave)} آغاز شد", 2f, ir.shahed.pahpad.core.Theme.AMBER)
    }

    // ------------------------------------------------------------ پایان و امتیاز

    private fun finish(success: Boolean, reason: String, aborted: Boolean = false) {
        if (phase == Phase.ENDED) return
        phase = Phase.ENDED

        val targetsTotal = if (mode == MissionMode.ENDLESS) targetsDestroyed.coerceAtLeast(1)
        else level.targets.coerceAtLeast(1)
        val avgAccuracy = if (impacts > 0) (precisionSum / impacts).coerceIn(0f, 1f) else 0f
        val cruise = maxSpeedKmh * 0.8f / 3.6f
        val par = StarRules.parTime(level.distance, cruise, level.targets)

        val acc = Math.round(accuracyPoints).coerceIn(0, 1000)
        val timeScore = if (!success || par <= 0f) 0
        else Math.round(500f * (1f - (elapsed / par).coerceIn(0f, 1f))).coerceIn(0, 500)
        val defense = if (success && damage == 0) 300 else 0
        val fuelAvg = if (impacts > 0) fuelRatioSum / impacts else 0f
        val fuelScore = if (!success) 0 else Math.round(200f * fuelAvg).coerceIn(0, 200)
        val bonusScore = if (totalBonuses <= 0) 0
        else Math.round(500f * bonusesCollected.toFloat() / totalBonuses.toFloat()).coerceIn(0, 500)

        // امتیاز نهایی یک عدد واحد است و همان عدد هم نمایش داده و هم ذخیره می‌شود
        val scoreTotal = if (mode == MissionMode.ENDLESS)
            Math.round(accuracyPoints) + bonusScore + targetsDestroyed * 150
        else acc + timeScore + defense + fuelScore + bonusScore

        val stars = StarRules.stars(success, avgAccuracy, damage, elapsed, par)

        val coinMul = if (mode == MissionMode.DAILY) 2f else 1f
        // لغو ماموریت هیچ پاداشی ندارد؛ شکست فقط پاداش تسلی‌بخش می‌گیرد
        val coins = when {
            aborted -> 0
            success -> Math.round((40f + scoreTotal / 12f + stars * 25f) * coinMul)
            else -> Math.round((10f + scoreTotal / 40f) * coinMul)
        }
        val xp = when {
            aborted -> 0
            success -> Math.round(scoreTotal / 4f + stars * 40f)
            else -> Math.round(scoreTotal / 16f)
        }

        val res = MissionResult(
            success = success,
            aborted = aborted,
            reason = reason,
            accuracy = acc,
            timeScore = timeScore,
            defense = defense,
            fuelScore = fuelScore,
            bonusScore = bonusScore,
            elapsed = elapsed,
            stars = stars,
            coins = coins,
            xp = xp,
            targetsDestroyed = targetsDestroyed,
            targetsTotal = targetsTotal,
            avgAccuracy = avgAccuracy,
            damage = damage,
            parTime = par,
            scoreTotal = scoreTotal
        )
        result = res

        // ثبت در حافظه
        if (coins > 0) save.addCoins(coins)
        if (xp > 0) save.addXp(xp)
        if (!aborted) {
            when (mode) {
                // فقط ماموریت موفق در رکورد مرحله ثبت می‌شود تا زمان بهترین رکورد
                // با یک شکست سریع خراب نشود
                MissionMode.STORY -> if (success) save.recordResult(level.id, stars, scoreTotal, elapsed)
                MissionMode.ENDLESS -> if (scoreTotal > save.endlessBest) save.endlessBest = scoreTotal
                MissionMode.DAILY -> if (scoreTotal > save.dailyBest) save.dailyBest = scoreTotal
            }
        }
        onFinish?.invoke(res)
    }

    fun abort() {
        if (phase == Phase.ENDED) return
        finish(false, "ماموریت لغو شد", aborted = true)
    }

    // ------------------------------------------------------------ اطلاعات HUD

    /** میانگین دقت برخوردهای انجام‌شده تا این لحظه */
    val averageAccuracy: Float
        get() = if (impacts > 0) (precisionSum / impacts).coerceIn(0f, 1f) else 0f

    /** زمان مرجع ماموریت؛ همان عددی که در بریفینگ نشان داده می‌شود */
    val parTime: Float
        get() = StarRules.parTime(level.distance, maxSpeedKmh * 0.8f / 3.6f, level.targets)

    val damageTaken: Int get() = damage

    val altitude: Float get() = y
    val speedKmh: Float get() = speedMps * 3.6f
    val fuel01: Float get() = (fuelLeft / maxRange).coerceIn(0f, 1f)

    fun distanceToTarget(): Float {
        val t = world.activeTarget() ?: return 0f
        val dx = t.x - x
        val dz = t.z - z
        val dy = t.centerY - y
        return Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }

    /** زاویه هدف نسبت به جهت پرواز، برای نشانگر جهت */
    fun bearingToTarget(): Float {
        val t = world.activeTarget() ?: return 0f
        val ang = Math.atan2((t.x - x).toDouble(), (t.z - z).toDouble()).toFloat()
        var diff = ang - yaw
        while (diff > Math.PI) diff -= (Math.PI * 2).toFloat()
        while (diff < -Math.PI) diff += (Math.PI * 2).toFloat()
        return diff
    }

    val bonusInfo: String
        get() = "${ir.shahed.pahpad.core.Fa.num(bonusesCollected)}/${ir.shahed.pahpad.core.Fa.num(totalBonuses)}"

    val targetsInfo: String
        get() {
            val done = world.targets.count { it.destroyed }
            return "${ir.shahed.pahpad.core.Fa.num(done)}/${ir.shahed.pahpad.core.Fa.num(world.targets.size)}"
        }

    companion object {
        /** فاصله‌ی نقطه از پاره‌خط؛ برای برخورد در سرعت بالا */
        fun distancePointSegment(
            px: Float, py: Float, pz: Float,
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float
        ): Float {
            val abx = bx - ax; val aby = by - ay; val abz = bz - az
            val apx = px - ax; val apy = py - ay; val apz = pz - az
            val len2 = abx * abx + aby * aby + abz * abz
            val t = if (len2 <= 0.00001f) 0f
            else ((apx * abx + apy * aby + apz * abz) / len2).coerceIn(0f, 1f)
            val cx = ax + abx * t - px
            val cy = ay + aby * t - py
            val cz = az + abz * t - pz
            return Math.sqrt((cx * cx + cy * cy + cz * cz).toDouble()).toFloat()
        }
    }
}
