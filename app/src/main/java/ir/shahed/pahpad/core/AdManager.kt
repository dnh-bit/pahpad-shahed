package ir.shahed.pahpad.core

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.AdShowListener
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import ir.tapsell.plus.model.TapsellPlusErrorModel

/**
 * پوشش تمامِ تبلیغات بازی (تپسل‌پلاس).
 *
 * قواعد معماری که این کلاس تضمین می‌کند:
 * - هیچ درخواست شبکه‌ای در مسیر رسم/حلقهٔ بازی نیست؛ همه‌چیز async است.
 * - درخواست/نمایش فقط بعد از init موفق انجام می‌شود؛ آفلاین چیزی نمی‌شکند
 *   (init fail ساکن است و کال‌بک‌ها همیشه به کاربر برمی‌گردند).
 * - تولید اعلان با کلید تست تا پیش از انتشار؛ قبل از ریلیز نهایی سه ثابت
 *   [APP_KEY]، [ZONE_INTERSTITIAL] و [ZONE_REWARDED] با کلیدهای واقعی
 *   پنل تپسل‌پلاس جایگزین شوند (TODO پایین).
 *
 * صفحه‌های بازی فقط با لامبداها با این شیء کار می‌کنند.
 */
object AdManager {
    private const val TAG = "AdManager"

    // ────────────────────────────────────────────────────────────────────────
    // ⚠️ TODO(Release): این سه مقدار «کلید تست رسمی تپسل» هستند. قبل از ریلیز
    //    سری تولیدی، کلیدهای اپ و زون‌های واقعی از پنل app.tapsell.ir بگذار.
    //    (کلید تست = کلید رسمی نمونهٔ گیت‌هاب تپسل‌پلاس؛ زون‌های تست هم همان‌اند)
    // ────────────────────────────────────────────────────────────────────────
    private const val APP_KEY = "alsoatsrtrotpqacegkehkaiieckldhrgsbspqtgqnbrrfccrtbdomgjtahflchkqtqosa"
    private const val ZONE_INTERSTITIAL = "5cfaa942e8d17f0001ffb292"
    private const val ZONE_REWARDED = "5cfaa802e8d17f0001ffb28e"

    @Volatile private var initialized = false
    @Volatile private var initAttempted = false
    @Volatile private var interstitialId: String? = null
    @Volatile private var rewardedId: String? = null

    // سقفِ نمایش اینترستيشال: نه بعد از هر مرگ — هر N پایان ماموریت، با حداقل فاصله
    private const val INTERSTITIAL_EVERY_N_FINISHED = 2
    private const val INTERSTITIAL_MIN_GAP_MS = 60_000L
    /** اگر SDK هیچ کالبکی نداد، بعد از این مدت ناوبری به‌زور ادامه می‌یابد. */
    private const val INTERSTITIAL_WATCHDOG_MS = 4_000L
    private var finishCounter = 0
    private var lastInterstitialMs = 0L

    // ────────────────────────────────── init ───────────────────────────────

    /** یک بار در onCreate صدا زده می‌شود؛ پیش‌کَش بلافاصله بعد از init موفق. */
    fun setup(activity: Activity) {
        if (initialized) return
        initAttempted = true
        TapsellPlus.initialize(activity, APP_KEY, object : TapsellPlusInitListener {
            override fun onInitializeSuccess(adNetworks: AdNetworks) {
                initialized = true
                if (ir.shahed.pahpad.BuildConfig.DEBUG) {
                    TapsellPlus.setDebugMode(Log.DEBUG)
                }
                Log.d(TAG, "Tapsell initialized (${adNetworks.name})")
                // پیش‌کش در پس‌زمینه — خارج از مسیر رندر
                preloadInterstitial(activity)
                preloadRewarded(activity)
            }

            override fun onInitializeFailed(adNetworks: AdNetworks, error: AdNetworkError) {
                // آفلاین/خطا → بازی تبلیغ نشان نمی‌دهد و همان‌طور عادی ادامه می‌دهد
                Log.e(TAG, "Tapsell init failed: ${error.errorMessage}")
            }
        })
    }

    fun isRewardedAdReady(): Boolean = initialized && rewardedId != null

    /** وضعیت مختصر برای خطِ «شبکهٔ تبلیغ» در تنظیمات */
    fun status(): String = when {
        initialized && rewardedId != null && interstitialId != null -> "آماده ✓"
        initialized && (rewardedId != null || interstitialId != null) -> "اتصال موفق — کش شدن…"
        initialized -> "اتصال موفق — هیچ ویدیویی کش نشد"
        initAttempted -> "اتصال ناموفق (اینترنت؟)"
        else -> "در حال اتصال…"
    }

    // ───────────────────────────── پیش‌کش ──────────────────────────────────

    private fun preloadInterstitial(activity: Activity) {
        if (!initialized || interstitialId != null) return
        TapsellPlus.requestInterstitialAd(activity, ZONE_INTERSTITIAL, object : AdRequestCallback() {
            override fun response(adModel: TapsellPlusAdModel) {
                interstitialId = adModel.responseId
                Log.d(TAG, "Intersteshial cached")
            }

            override fun error(message: String) {
                Log.w(TAG, "Interstitial preload failed: $message")
            }
        })
    }

    private fun preloadRewarded(activity: Activity) {
        if (!initialized || rewardedId != null) return
        TapsellPlus.requestRewardedVideoAd(activity, ZONE_REWARDED, object : AdRequestCallback() {
            override fun response(adModel: TapsellPlusAdModel) {
                rewardedId = adModel.responseId
                Log.d(TAG, "Rewarded cached")
            }

            override fun error(message: String) {
                Log.w(TAG, "Rewarded preload failed: $message")
            }
        })
    }

    // ───────────────────────────── اینترستيشال ─────────────────────────────

    /**
     * پایانِ هر ماموریت (ناموفق/موفق) صدا زده می‌شود؛ بلافاصله قبل از رفتن به منو
     * یا ری‌استارت. فقط طبق سقف نمایش می‌دهد؛ اگر نمایش داده نشود [onFinished]
     * بلافاصله اجرا می‌شود تا جریان صفحه‌ها بدون وقفه ادامه یابد.
     *
     * توقف حلقه/صدا را lifecycle اکتیویتی انجام می‌دهد (رَندر و صدا در onPause
     * دست‌سازِ بازی خودشان متوقف می‌شوند)؛ اینجا فقط همگام‌سازی تضمین می‌شود.
     */
    fun maybeShowInterstitial(activity: Activity, onFinished: () -> Unit) {
        val id = interstitialId
        finishCounter++
        val due = finishCounter >= INTERSTITIAL_EVERY_N_FINISHED &&
            (System.currentTimeMillis() - lastInterstitialMs) >= INTERSTITIAL_MIN_GAP_MS

        if (!initialized || id == null || !due) {
            if (initialized) preloadInterstitial(activity)
            onFinished()
            return
        }

        finishCounter = 0
        lastInterstitialMs = System.currentTimeMillis()
        interstitialId = null

        // پایان دقیقاً یک بار تضمین می‌شود: اگر show پرتاب کند یا SDK هیچ
        // کالبکی ندهد (بسته/خطا)، ناوبری صفحه‌ها نباید قورت داده شود و
        // بازیکن روی صفحهٔ نتیجه گیر نمی‌کند.
        val done = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        lateinit var watchdog: Runnable
        fun finishOnce() {
            if (!done.compareAndSet(false, true)) return
            handler.removeCallbacks(watchdog)
            if (Looper.myLooper() == Looper.getMainLooper()) onFinished() else handler.post { onFinished() }
        }
        watchdog = Runnable { finishOnce() }
        handler.postDelayed(watchdog, INTERSTITIAL_WATCHDOG_MS)

        try {
            TapsellPlus.showInterstitialAd(activity, id, object : AdShowListener() {
                override fun onOpened(adModel: TapsellPlusAdModel) {
                    handler.removeCallbacks(watchdog)
                }

                override fun onClosed(adModel: TapsellPlusAdModel) {
                    preloadInterstitial(activity)
                    finishOnce()
                }

                override fun onError(errorModel: TapsellPlusErrorModel) {
                    Log.e(TAG, "Interstitial error: ${errorModel.errorMessage}")
                    preloadInterstitial(activity)
                    finishOnce()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Interstitial show failed: ${e.message}")
            finishOnce()
        }
    }

    // ───────────────────────────── جایزه‌ای ────────────────────────────────

    /**
     * ویدیوی جایزه‌ای. پاداش فقط در [AdShowListener.onRewarded] داده می‌شود؛
     * در بسته شدن/خطا فقط پیش‌کش بعدی چیده می‌شود. فراخوانی‌کننده باید
     * [isRewardedAdReady] را چک کرده باشد.
     */
    fun showRewarded(activity: Activity, onRewardEarned: () -> Unit) {
        val id = rewardedId
        if (id == null) return
        rewardedId = null

        val mainHandler = Handler(Looper.getMainLooper())
        try {
            TapsellPlus.showRewardedVideoAd(activity, id, object : AdShowListener() {
                override fun onRewarded(adModel: TapsellPlusAdModel) {
                    if (Looper.myLooper() == Looper.getMainLooper()) onRewardEarned()
                    else mainHandler.post { onRewardEarned() }
                }

                override fun onClosed(adModel: TapsellPlusAdModel) {
                    preloadRewarded(activity)
                }

                override fun onError(errorModel: TapsellPlusErrorModel) {
                    Log.e(TAG, "Rewarded error: ${errorModel.errorMessage}")
                    preloadRewarded(activity)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Rewarded show failed: ${e.message}")
            preloadRewarded(activity)
        }
    }
}
