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
 * - کلید اپ و هر دو زون از پنل واقعی همین برنامه می‌آیند؛ اگر زون‌ها جابه‌جا
 *   شوند SDK خطا می‌دهد و هیچ تبلیغی کش نمی‌شود.
 *
 * صفحه‌های بازی فقط با لامبداها با این شیء کار می‌کنند.
 */
object AdManager {
    private const val TAG = "AdManager"

    // ────────────────────────────────────────────────────────────────────────
    // کلید و هر دو زون از پنل app.tapsell.ir همین برنامه (ir.shahed.pahpad).
    //   rewarded_main     → «ویدیو جایزه‌ای (Reward based)»      = ZONE_REWARDED
    //   interstitial_main → «آنی ویدیو (Interstitial video)»      = ZONE_INTERSTITIAL
    // اگر این دو جابه‌جا شوند، SDK خطای «zone type mismatch» می‌دهد و هیچ
    // تبلیغی کش نمی‌شود؛ پس نگاشت بالا مرجع است، نه حدس.
    // ────────────────────────────────────────────────────────────────────────
    private const val APP_KEY = "shfqoffnjakntlkglqrmrtfagmfbmccjdmpkreqdlipnkimlohabbkjsttmbjeecbodhgb"
    private const val ZONE_REWARDED = "6b028cfa6cbc4169d6c38649"
    private const val ZONE_INTERSTITIAL = "6b028db86cbc4169d6c3864a"
    /** «بنر آنی» (Interstitial banner) — تصویر تمام‌صفحه، کوتاه، عمدتاً بعد از باخت */
    private const val ZONE_INTERSTITIAL_BANNER = "6b0294b8aeabeb750feb8fe9"

    @Volatile private var initialized = false
    @Volatile private var initAttempted = false
    @Volatile private var interstitialId: String? = null
    @Volatile private var rewardedId: String? = null
    @Volatile private var bannerId: String? = null


    /** اگر SDK هیچ کال‌بکی نداد، بعد از این مدت ناوبری به‌زور ادامه می‌یابد. */
    private const val INTERSTITIAL_WATCHDOG_MS = 4_000L
    /**
     * تصمیم «کدام تبلیغ، کِی» در [AdPolicy] است (خالص و تست‌شده): باخت → بنر
     * آنی، هر ۳ برد → آنی ویدیو. این‌جا فقط اجرا می‌شود.
     */
    private val policy = AdPolicy()
    /** آخرین باری که هر نوع تبلیغ واقعاً نمایش داده شد (برای گزارش) */
    @Volatile private var shownInterstitialMs = 0L

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
                preloadInterstitialBanner(activity)
                preloadRewarded(activity)
            }

            override fun onInitializeFailed(adNetworks: AdNetworks, error: AdNetworkError) {
                // آفلاین/خطا → بازی تبلیغ نشان نمی‌دهد و همان‌طور عادی ادامه می‌دهد
                Log.e(TAG, "Tapsell init failed: ${error.errorMessage}")
            }
        })
    }

    fun isRewardedAdReady(): Boolean = initialized && rewardedId != null

    fun isInterstitialBannerReady(): Boolean = initialized && bannerId != null

    fun isInterstitialVideoReady(): Boolean = initialized && interstitialId != null

    /**
     * نمایشِ «آنی» (تمام‌صفحه) با اولویتِ بنر. اگر بنر کش نشده باشد و ویدیو
     * کش شده باشد، ویدیو نمایش داده می‌شود؛ اگر هیچ‌کدام نبود [onFinished]
     * بلافاصله اجرا می‌شود تا جریان صفحه‌ها قطع نشود.
     */
    fun showInterstitial(activity: Activity, preferBanner: Boolean, onFinished: () -> Unit) {
        val banner = bannerId
        val video = interstitialId
        val useBanner = if (preferBanner) (banner != null || video == null) else (video == null && banner != null)
        val id = if (useBanner) banner else video

        if (!initialized || id == null) {
            if (initialized) {
                preloadInterstitial(activity)
                preloadInterstitialBanner(activity)
            }
            onFinished()
            return
        }

        if (useBanner) bannerId = null else interstitialId = null
        shownInterstitialMs = System.currentTimeMillis()

        showFullScreen(activity, id, onFinished) {
            if (useBanner) preloadInterstitialBanner(activity) else preloadInterstitial(activity)
        }
    }

    /** وضعیت مختصر برای خطِ «شبکهٔ تبلیغ» در تنظیمات */
    fun status(): String = when {
        initialized && rewardedId != null && (interstitialId != null || bannerId != null) -> "آماده ✓"
        initialized && (rewardedId != null || interstitialId != null || bannerId != null) -> "اتصال موفق — کش شدن…"
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

    /**
     * «بنر آنی» تپسل یک زونِ Interstitial است که محتوایش تصویر است؛ SDK همین
     * متد را برایش صدا می‌زند. فقط زونش جداست.
     */
    private fun preloadInterstitialBanner(activity: Activity) {
        if (!initialized || bannerId != null) return
        TapsellPlus.requestInterstitialAd(activity, ZONE_INTERSTITIAL_BANNER, object : AdRequestCallback() {
            override fun response(adModel: TapsellPlusAdModel) {
                bannerId = adModel.responseId
                Log.d(TAG, "Interstitial banner cached")
            }

            override fun error(message: String) {
                Log.w(TAG, "Interstitial banner preload failed: $message")
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

    // ──────────────────────────── پایان ماموریت ────────────────────────────

    /**
     * بعد از پایان ماموریت صدا زده می‌شود (قبل از رفتن به منو یا تلاش مجدد).
     *
     * سیاست در [AdPolicy]: باخت → بنر آنی، هر چند برد → آنی ویدیو. اگر کش
     * آماده نبود یا فاصلهٔ زمانی نگذشته بود، [onFinished] بی‌درنگ اجرا می‌شود
     * تا جریان صفحه‌ها هیچ‌وقت روی یک تبلیغ نماند.
     *
     * @param aborted لغو توسط خود بازیکن؛ هیچ تبلیغی ندارد (خواستهٔ او بوده).
     */
    fun onMissionFinished(activity: Activity, lost: Boolean, aborted: Boolean, onFinished: () -> Unit) {
        if (aborted) {
            onFinished()
            return
        }
        val choice = policy.onMissionEnd(
            lost = lost,
            nowMs = System.currentTimeMillis(),
            bannerReady = bannerId != null,
            videoReady = interstitialId != null,
        )
        when (choice) {
            AdChoice.NONE -> {
                if (initialized) {
                    preloadInterstitial(activity)
                    preloadInterstitialBanner(activity)
                }
                onFinished()
            }
            AdChoice.BANNER -> showInterstitial(activity, preferBanner = true, onFinished = onFinished)
            AdChoice.VIDEO -> showInterstitial(activity, preferBanner = false, onFinished = onFinished)
        }
    }

    /**
     * نمایش اینترستیشال ویدیویی صرف‌نظر از سیاست (برای مسیرهایی که خودشان
     * تصمیم گرفته‌اند). اگر کش نبود، بلافاصله ادامه می‌دهد.
     */
    fun maybeShowInterstitial(activity: Activity, onFinished: () -> Unit) {
        showInterstitial(activity, preferBanner = false, onFinished = onFinished)
    }

    // ───────────────────────────── اینترستيشال ─────────────────────────────

    /**
     * نمایش تمام‌صفحه با نگهبانِ ۴ ثانیه‌ای: اگر SDK هیچ کال‌بکی نداد، ناوبری
     * صفحه‌ها نباید قورت داده شود. [onDone] یک بار اجرا می‌شود.
     */
    private fun showFullScreen(activity: Activity, id: String, onDone: () -> Unit, onClosed: () -> Unit) {

        // پایان دقیقاً یک بار تضمین می‌شود: اگر show پرتاب کند یا SDK هیچ
        // کالبکی ندهد (بسته/خطا)، ناوبری صفحه‌ها نباید قورت داده شود و
        // بازیکن روی صفحهٔ نتیجه گیر نمی‌کند.
        val done = AtomicBoolean(false)
        val handler = Handler(Looper.getMainLooper())
        lateinit var watchdog: Runnable
        fun finishOnce() {
            if (!done.compareAndSet(false, true)) return
            handler.removeCallbacks(watchdog)
            if (Looper.myLooper() == Looper.getMainLooper()) onDone() else handler.post { onDone() }
        }
        watchdog = Runnable { finishOnce() }
        handler.postDelayed(watchdog, INTERSTITIAL_WATCHDOG_MS)

        try {
            TapsellPlus.showInterstitialAd(activity, id, object : AdShowListener() {
                override fun onOpened(adModel: TapsellPlusAdModel) {
                    handler.removeCallbacks(watchdog)
                }

                override fun onClosed(adModel: TapsellPlusAdModel) {
                    onClosed()
                    finishOnce()
                }

                override fun onError(errorModel: TapsellPlusErrorModel) {
                    Log.e(TAG, "Interstitial error: ${errorModel.errorMessage}")
                    // جای خالی دوباره پر شود، وگرنه تا ری‌استارت بعدی هیچ تبلیغی نیست
                    onClosed()
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
