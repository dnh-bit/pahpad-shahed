package ir.shahed.pahpad.core

import android.app.Activity
import android.util.Log
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
    // ────────────────────────────────────────────────────────────────────────
    private const val APP_KEY = "alsoatsrtrotpqacegkehkaiieckldhrgsbspqtgqnbrsfasabstesbelligs"
    private const val ZONE_INTERSTITIAL = "5cfaa942e8d17f0001ffb292"
    private const val ZONE_REWARDED = "5cfaa802e8d17f0001ffb28e"

    private var initialized = false
    private var interstitialId: String? = null
    private var rewardedId: String? = null

    // سقفِ نمایش اینترستيشال: نه بعد از هر مرگ — هر N پایان ماموریت، با حداقل فاصله
    private const val INTERSTITIAL_EVERY_N_FINISHED = 2
    private const val INTERSTITIAL_MIN_GAP_MS = 60_000L
    private var finishCounter = 0
    private var lastInterstitialMs = 0L

    // ────────────────────────────────── init ───────────────────────────────

    /** یک بار در onCreate صدا زده می‌شود؛ پیش‌کَش بلافاصله بعد از init موفق. */
    fun setup(activity: Activity) {
        TapsellPlus.initialize(activity, APP_KEY, object : TapsellPlusInitListener {
            override fun onInitializeSuccess(adNetworks: AdNetworks) {
                initialized = true
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

        TapsellPlus.showInterstitialAd(activity, id, object : AdShowListener() {
            override fun onClosed(adModel: TapsellPlusAdModel) {
                preloadInterstitial(activity)
                onFinished()
            }

            override fun onError(errorModel: TapsellPlusErrorModel) {
                Log.e(TAG, "Interstitial error: ${errorModel.errorMessage}")
                preloadInterstitial(activity)
                onFinished()
            }
        })
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

        TapsellPlus.showRewardedVideoAd(activity, id, object : AdShowListener() {
            override fun onRewarded(adModel: TapsellPlusAdModel) {
                onRewardEarned()
            }

            override fun onClosed(adModel: TapsellPlusAdModel) {
                preloadRewarded(activity)
            }

            override fun onError(errorModel: TapsellPlusErrorModel) {
                Log.e(TAG, "Rewarded error: ${errorModel.errorMessage}")
                preloadRewarded(activity)
            }
        })
    }
}
