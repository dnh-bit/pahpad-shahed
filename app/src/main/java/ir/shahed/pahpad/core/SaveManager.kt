package ir.shahed.pahpad.core

import android.content.Context
import android.content.SharedPreferences
import ir.shahed.pahpad.data.DroneModels
import ir.shahed.pahpad.data.Levels
import ir.shahed.pahpad.data.Ranks
import ir.shahed.pahpad.data.UpgradeKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ذخیره و بازیابی پیشرفت بازیکن: ستاره‌ها، سکه، تجربه، ارتقاها و تنظیمات.
 */
class SaveManager(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("pahpad_shahed_save", Context.MODE_PRIVATE)

    // ------------------------------------------------------------ منابع بازیکن

    var coins: Int
        get() = sp.getInt("coins", 250)
        set(v) = sp.edit().putInt("coins", v.coerceAtLeast(0)).apply()

    var xp: Int
        get() = sp.getInt("xp", 0)
        set(v) = sp.edit().putInt("xp", v.coerceAtLeast(0)).apply()

    val rank: Ranks.Rank get() = Ranks.forXp(xp)

    /** جمع در Long انجام می‌شود تا در مقادیر بزرگ سرریز Int رخ ندهد. */
    fun addCoins(amount: Int) {
        if (amount <= 0) return
        coins = (coins.toLong() + amount.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun addXp(amount: Int) {
        if (amount <= 0) return
        xp = (xp.toLong() + amount.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun spendCoins(amount: Int): Boolean {
        if (coins < amount) return false
        coins -= amount
        return true
    }

    // ------------------------------------------------------------ گزارش کرش

    /**
     * آخرین کرش را در ذخیره‌ساز می‌نویسد تا کاربر بتواند در تنظیمات ببیند و
     * اسکرین‌شات بفرستد (این بازی اینترنت گزارش-خطا ندارد، پس خودکار انجام می‌شود).
     */
    fun reportCrash(thread: Thread, error: Throwable) {
        try {
            val sb = StringBuilder()
            sb.append(error.javaClass.name).append(": ").append(error.message ?: "").append('\n')
            for (s in error.stackTrace.take(10)) {
                sb.append("  at ").append(s.className)
                    .append('.').append(s.methodName)
                    .append(':').append(s.lineNumber).append('\n')
            }
            var cause = error.cause
            var depth = 0
            while (cause != null && depth < 2) {
                sb.append("Caused by: ").append(cause.javaClass.name).append(": ")
                    .append(cause.message ?: "").append('\n')
                for (s in cause.stackTrace.take(5)) {
                    sb.append("  at ").append(s.className)
                        .append('.').append(s.methodName)
                        .append(':').append(s.lineNumber).append('\n')
                }
                cause = cause.cause
                depth++
            }
            sp.edit().putString("last_crash", sb.toString().take(1200)).apply()
        } catch (_: Throwable) {
            // گزارش‌گیری هرگز نباید جای crash handler اصلی را بگیرد
        }
    }

    val lastCrash: String
        get() = sp.getString("last_crash", "") ?: ""

    fun clearLastCrash() {
        sp.edit().remove("last_crash").apply()
    }

    // ------------------------------------------------------------ مراحل

    fun starsOf(levelId: String): Int = sp.getInt("stars_$levelId", 0)

    fun bestScoreOf(levelId: String): Int = sp.getInt("best_$levelId", 0)

    fun bestTimeOf(levelId: String): Float = sp.getFloat("time_$levelId", 0f)

    /** ثبت نتیجه یک ماموریت. مقدار بازگشتی: تعداد ستاره‌های تازه کسب‌شده */
    fun recordResult(levelId: String, stars: Int, score: Int, seconds: Float): Int {
        val e = sp.edit()
        val oldStars = starsOf(levelId)
        val newStars = maxOf(oldStars, stars)
        e.putInt("stars_$levelId", newStars)
        if (score > bestScoreOf(levelId)) e.putInt("best_$levelId", score)
        val oldTime = bestTimeOf(levelId)
        if (oldTime <= 0f || (seconds > 0f && seconds < oldTime)) e.putFloat("time_$levelId", seconds)
        e.apply()
        return (newStars - oldStars).coerceAtLeast(0)
    }

    val totalStars: Int
        get() {
            var sum = 0
            for (l in Levels.all) sum += starsOf(l.id)
            return sum
        }

    fun isLevelUnlocked(levelId: String): Boolean {
        val level = Levels.byId(levelId) ?: return false
        val chapter = Levels.chapter(level.chapter) ?: return false
        if (totalStars < chapter.requiredStars) return false
        if (level.index == 1) return true
        val prev = Levels.all.firstOrNull { it.chapter == level.chapter && it.index == level.index - 1 }
            ?: return true
        return starsOf(prev.id) > 0
    }

    fun isChapterUnlocked(chapterNumber: Int): Boolean {
        val chapter = Levels.chapter(chapterNumber) ?: return false
        return totalStars >= chapter.requiredStars
    }

    /** آخرین مرحله‌ای که باید ادامه داده شود */
    fun nextPlayableLevelId(): String {
        for (l in Levels.all) {
            if (isLevelUnlocked(l.id) && starsOf(l.id) == 0) return l.id
        }
        return Levels.all.first().id
    }

    fun hasProgress(): Boolean = totalStars > 0 || xp > 0

    // ------------------------------------------------------------ پهباد و ارتقا

    var selectedDroneId: String
        get() = sp.getString("drone", DroneModels.all.first().id) ?: DroneModels.all.first().id
        set(v) = sp.edit().putString("drone", v).apply()

    fun isDroneUnlocked(droneId: String): Boolean {
        val d = DroneModels.byId(droneId) ?: return false
        return totalStars >= d.requiredStars
    }

    fun upgradeLevel(droneId: String, kind: UpgradeKind): Int {
        val cap = DroneModels.byId(droneId)?.maxLevel(kind) ?: 1
        return sp.getInt("up_${droneId}_${kind.key}", 1).coerceIn(1, cap)
    }

    fun setUpgradeLevel(droneId: String, kind: UpgradeKind, level: Int) {
        val cap = DroneModels.byId(droneId)?.maxLevel(kind) ?: 1
        sp.edit().putInt("up_${droneId}_${kind.key}", level.coerceIn(1, cap)).apply()
    }

    // ------------------------------------------------------------ حالت‌های ویژه

    var endlessBest: Int
        get() = sp.getInt("endless_best", 0)
        set(v) = sp.edit().putInt("endless_best", v).apply()

    var dailyBest: Int
        get() = if (sp.getString("daily_day", "") == today()) sp.getInt("daily_best", 0) else 0
        set(v) = sp.edit().putString("daily_day", today()).putInt("daily_best", v).apply()

    val dailyDone: Boolean get() = sp.getString("daily_day", "") == today()

    fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** بذر تصادفی چالش روزانه؛ هر روز یک ماموریت متفاوت */
    fun dailySeed(): Long = today().toLong()

    // ------------------------------------------------------------ تنظیمات

    var soundOn: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()

    var vibrateOn: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

    /** true = ژیروسکوپ / تیلت گوشی ، false = جوی‌استیک مجازی — پیش‌فرض جوی‌استیک */
    var tiltControl: Boolean
        get() = sp.getBoolean("tilt", false)
        set(v) = sp.edit().putBoolean("tilt", v).apply()

    var showHints: Boolean
        get() = sp.getBoolean("hints", true)
        set(v) = sp.edit().putBoolean("hints", v).apply()

    var invertPitch: Boolean
        get() = sp.getBoolean("invert_pitch", false)
        set(v) = sp.edit().putBoolean("invert_pitch", v).apply()

    var callSign: String
        get() = sp.getString("callsign", "اپراتور") ?: "اپراتور"
        set(v) = sp.edit().putString("callsign", v).apply()

    fun resetAll() {
        sp.edit().clear().apply()
    }
}
