package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.data.LevelDef
import ir.shahed.pahpad.game.Mission
import ir.shahed.pahpad.game.MissionMode
import ir.shahed.pahpad.game.WorldRenderer
import kotlin.math.cos
import kotlin.math.sin

/**
 * صفحهٔ «آماده‌سازی ماموریت» — بین بریفینگ و شروع بازی.
 *
 * شکایت کاربر: «اول که بازی می‌خواهد شروع بشود، کمی لگ می‌زند و بعد اوکی
 * می‌شود». علتش این بود که سنگین‌ترین کارها **در اولین فریم بازی** انجام
 * می‌شدند:
 *
 *   ۱. ساخت `Mission` و `World` (تولید کل شهر، پدافند، اهداف)
 *   ۲. ساخت `WorldRenderer` و بارگذاری همهٔ بافت‌ها (Gfx.get)
 *   ۳. اولین رندر کامل، که همهٔ وجه‌ها و استخرها را برای اولین بار پر می‌کند
 *
 * حالا همهٔ این‌ها در همین صفحه انجام می‌شوند، پشت یک نوار پیشرفت. بازیکن
 * دکمهٔ «شروع» را وقتی می‌زند که کار تمام شده است — پس فریم اول بازی مثل
 * فریم دوم است و لگی دیده نمی‌شود.
 *
 * پیاده‌سازی: هر `update` یک گام انجام می‌دهد و بعد یک فریم رسم می‌شود. به
 * این ترتیب رابط کاربری هیچ‌وقت freeze نمی‌شود و نوار پیشرفت واقعاً پیش
 * می‌رود (نه یک نوار تقلبی). رندر آماده‌سازی **روی بیت‌مپ دور ریختنی**
 * انجام می‌شود تا همان مسیر کدِ بازی گرم شود، ولی چیزی از آن روی صفحه
 * نیاید.
 */
class LoadingScreen(
    game: MainActivity,
    private val level: LevelDef,
    private val mode: MissionMode,
    private val seed: Long
) : BaseScreen(game) {

    /** گام‌های آماده‌سازی، به ترتیب. عدد = وزن نسبی کار. */
    private val steps = listOf(
        "ساخت نقشهٔ منطقه" to 3,
        "چیدمان پدافند و اهداف" to 2,
        "بارگذاری بافت‌ها" to 4,
        "آماده‌سازی موتور گرافیک" to 5,
        "گرم‌کردن رندر" to 8
    )
    private val totalWeight = steps.sumOf { it.second }

    private var stepIndex = 0
    private var doneWeight = 0
    private var progress = 0f

    private var mission: Mission? = null
    private var renderer: WorldRenderer? = null
    private var ready = false

    /** بیت‌مپ دور ریختنی: رندر گرم‌کردن روی آن انجام می‌شود، نه روی صفحه. */
    private var warm: android.graphics.Bitmap? = null
    private var warmCanvas: Canvas? = null

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barBg = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var glow: Shader? = null

    /** چند موقعیت پرواز برای گرم‌کردن رندر (پوشش استخر وجه‌ها در وضوح واقعی) */
    private var warmFrames = 0

    /**
     * فریم‌های گرم‌کردن روی **بوم سخت‌افزاری خود ویو**.
     *
     * چرا جدا از گرم‌کردن نرم‌افزاری: تا این نسخه، گرم‌کردن روی یک Bitmap
     * (Canvas نرم‌افزاری) انجام می‌شد. بازی ولی با `hardwareAccelerated="true"`
     * روی Canvas سخت‌افزاری رندر می‌شود. این دو مسیر متفاوت‌اند: آپلود
     * بافت‌ها به حافظهٔ GPU، کامپایل شیدرها و ساختن لایه‌ها فقط در مسیر
     * سخت‌افزاری رخ می‌دهد. پس هر چه نرم‌افزاری «گرم» می‌کردیم در بازی
     * دوباره از صفر انجام می‌شد و چند ثانیهٔ اول همان افت فریم را داشت.
     *
     * حالا در دو فریم آخر، رندر روی Canvas واقعی صفحه انجام می‌شود. خروجی
     * دور ریخته می‌شود (روی آن رابط کاربری کشیده می‌شود)، ولی GPU همان کار
     * واقعی را انجام داده است.
     */
    private var hwWarm = 0
    private val hwWarmTarget = 2

    private var appear = 0f

    override fun onEnter() {
        appear = 0f
    }

    override fun layoutUi(w: Float, h: Float) {
        val r = Math.hypot((w / 2f).toDouble(), (h / 2f).toDouble()).toFloat().coerceAtLeast(1f)
        glow = RadialGradient(
            w / 2f, h * 0.42f, r * 0.9f,
            intArrayOf(Theme.withAlpha(Theme.MINT, 0.10f), 0x00000000),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
        )
    }

    // ------------------------------------------------------------ گام‌ها

    override fun update(dt: Float) {
        appear = (appear + dt * 3f).coerceAtMost(1f)
        if (ready) return

        when (stepIndex) {
            0 -> {
                // سنگین‌ترین گام: تولید کل دنیا
                if (mission == null) {
                    mission = Mission(level, save, mode, seed)
                }
                finishStep()
            }
            1 -> {
                // دسترسی به دنیا آن را کاملاً می‌سازد (اهداف/پدافند/موانع)
                val m = mission ?: return
                @Suppress("UNUSED_VARIABLE") val n = m.world.props.size + m.world.targets.size
                finishStep()
            }
            2 -> {
                // همهٔ بافت‌ها یک‌بار لود شوند تا اولین فریم بازی منتظر دیسک نماند
                val names = listOf(
                    "material_wall", "material_roof", "material_concrete", "material_metal",
                    "material_camo", "material_rust", "material_track", "material_deck",
                    "shadow_box", "shadow_soft", "sky_clouds", "terrain_macro",
                    "drone_131", "drone_136", "drone_238", "drone_x",
                    "drone_131_side", "drone_136_side", "drone_238_side", "drone_x_side",
                    "prop_tree1", "prop_tree2", "prop_palm", "prop_bush",
                    "prop_rock1", "prop_rock2", "hud_target"
                )
                for (n in names) ir.shahed.pahpad.core.Gfx.get(game, n)
                ir.shahed.pahpad.core.Gfx.get(game, "env_" + level.env.name.lowercase())
                ir.shahed.pahpad.core.Gfx.get(game, "horizon_" + level.env.name.lowercase())
                ir.shahed.pahpad.core.Gfx.get(game, "terrain_" + level.env.name.lowercase())
                finishStep()
            }
            3 -> {
                // ساخت رندرر: تهیهٔ Paintها، تایلیng، ضریب کیفیت
                if (renderer == null) {
                    renderer = WorldRenderer(game).also {
                        it.applyQuality(save.graphicsQuality)
                        it.effectsEnabled = save.effectsOn
                    }
                }
                finishStep()
            }
            4 -> {
                // گرم‌کردن: کامل، با وضوح واقعی صفحه و از چند موقعیت پرواز.
                //
                // چرا وضوح واقعی: نسخهٔ قبلی روی ۴۸۰×۲۷۰ گرم می‌کرد، ولی بازی
                // در وضوح کامل رندر می‌شود. در وضوح بالاتر تعداد وجه‌ها و
                // برش‌های بیشتری در کادر می‌مانند، پس استخر وجه‌ها دوباره رشد
                // می‌کرد و چند فریم اول بازی صرف رشد/کپی و GC می‌شد — همان
                // «افت فریم در چند ثانیهٔ اول، مخصوصاً مراحل شهری».
                //
                // چرا چند موقعیت: اگر فقط از نقطهٔ شروع گرم شود، استخر فقط
                // برای همان نمای باریک پر می‌شود. با چند موقعیت (شامل وسط
                // شهر و ارتفاع بالا) بزرگ‌ترین نیاز واقعی پوشش داده می‌شود.
                val m = mission ?: return
                val r = renderer ?: return
                val w = if (vw > 0f) vw else 1920f
                val h = if (vh > 0f) vh else 1080f
                val bmp = warm ?: android.graphics.Bitmap.createBitmap(
                    w.toInt().coerceIn(320, 2600),
                    h.toInt().coerceIn(240, 1600),
                    android.graphics.Bitmap.Config.ARGB_8888
                ).also {
                    warm = it
                    warmCanvas = Canvas(it)
                }
                val cw = bmp.width.toFloat()
                val ch = bmp.height.toFloat()
                val cam = r.scene.cam
                val cp = cos(m.pitch.toDouble()).toFloat()
                val warmSpots = 6
                if (warmFrames < warmSpots) {
                    // موقعیت‌های گرم‌کردن: شروع، وسط مسیر، ارتفاع بالا،
                    // نزدیک شهر، و دو نمای چرخیده.
                    val t = warmFrames / (warmSpots - 1f)
                    val distAlong = m.world.distance * t
                    cam.x = m.x + (sin(m.yaw) * distAlong)
                    cam.z = m.z - cp * 26f + (cos(m.yaw) * distAlong)
                    cam.y = m.y + 3f + t * 60f
                    cam.yaw = m.yaw + t * 0.55f
                    cam.pitch = m.pitch - t * 0.06f
                    cam.roll = t * 0.05f
                    cam.fov = 74f
                    cam.viewport(cw, ch)
                    // یک‌بار ظرفیت استخر را برای این وضوح رزرو کن
                    r.scene.reserveFaces(2600)
                    r.draw(warmCanvas!!, m, cw, ch)
                    warmFrames++
                    progress = ((doneWeight + steps[stepIndex].second * warmFrames / warmSpots.toFloat())
                        / totalWeight).coerceIn(0f, 1f)
                } else if (hwWarm < hwWarmTarget) {
                    // هنوز تمام نشده — فریم سخت‌افزاری در render() زده می‌شود.
                    hwWarm++
                    if (hwWarm >= hwWarmTarget) {
                        cam.x = m.x
                        cam.z = m.z - cp * 26f
                        cam.y = m.y + 3f
                        cam.yaw = m.yaw
                        cam.pitch = m.pitch
                        cam.roll = 0f
                        cam.viewport(vw, vh)
                        finishStep()
                    }
                } else {
                    finishStep()
                }
            }
        }
    }

    private fun finishStep() {
        doneWeight += steps[stepIndex].second
        stepIndex++
        progress = (doneWeight.toFloat() / totalWeight).coerceIn(0f, 1f)
        if (stepIndex >= steps.size) {
            ready = true
            progress = 1f
            // آماده‌ایم — مستقیم به بازی برو، ولی فریم «۱۰۰٪» را هم نشان بده
            holdFrames = 0
        }
    }

    private var holdFrames = 0

    override fun render(c: Canvas) {
        // گرم‌کردن روی بوم سخت‌افزاری خود ویو — پیش از کشیدن رابط کاربری،
        // چون UI باید روی آن بیاید. خروجی دور ریخته می‌شود ولی GPU همان
        // آپلود بافت و کامپایل شیدر مسیر واقعی بازی را انجام داده است.
        if (!ready && stepIndex == 4 && warmFrames >= 6 && hwWarm > 0) {
            val m = mission
            val r = renderer
            if (m != null && r != null && vw > 0f && vh > 0f) {
                r.draw(c, m, vw, vh)
            }
        }
        ui.fill.shader = glow
        ui.fill.alpha = 255
        c.drawRect(0f, 0f, vw, vh, ui.fill)

        val a = appear
        val titleY = vh * 0.34f
        ui.text(c, level.title, vw / 2f, titleY, dp(21f), Theme.TEXT, bold = true, alpha = a)
        ui.text(c, "آماده‌سازی ماموریت", vw / 2f, titleY + dp(24f), dp(13f), Theme.MINT, alpha = a)

        // نوار پیشرفت
        val bw = minOf(vw * 0.62f, dp(320f))
        val bh = dp(6f)
        val bx = (vw - bw) / 2f
        val by = vh * 0.52f
        barBg.color = Theme.withAlpha(Theme.TEXT, 0.14f)
        val rr = bh / 2f
        c.drawRoundRect(bx, by, bx + bw, by + bh, rr, rr, barBg)
        barFill.color = Theme.MINT
        val fw = bw * progress
        if (fw > 0f) c.drawRoundRect(bx, by, bx + fw, by + bh, rr, rr, barFill)

        // گام فعلی + درصد
        val label = if (ready) "آماده" else steps.getOrNull(stepIndex)?.first ?: "آماده"
        ui.text(c, label, vw / 2f, by + dp(24f), dp(12f), Theme.TEXT_DIM, alpha = a)
        ui.text(
            c, Fa.num((progress * 100).toInt()) + "٪",
            vw / 2f, by + dp(44f), dp(11f), Theme.withAlpha(Theme.MINT, 0.85f), alpha = a
        )

        if (ready) {
            val pulse = 0.55f + 0.45f * ((sin(time * 4f) + 1f) / 2f)
            ui.text(
                c, "برای شروع ضربه بزنید", vw / 2f, vh * 0.72f,
                dp(15f), Theme.AMBER, bold = true, alpha = pulse
            )
        }
    }

    override fun onTouchDown(x: Float, y: Float, pointerId: Int): Boolean {
        if (!ready) return true
        val m = mission ?: return true
        // رفتن به بازی — از همان mission و renderer آماده‌شده استفاده می‌شود،
        // پس فریم اول بازی هیچ کاری جز رسم ندارد.
        game.show(GameScreen(game, level, mode, seed, m, renderer))
        return true
    }

    override fun onExit() {
        // بیت‌مپ دور ریختنی حتماً آزاد شود
        warm?.recycle()
        warm = null
        warmCanvas = null
    }

    /** بازگشت: تا وقتی آماده نشده، دکمهٔ بازگشت بی‌اثر است. */
    override fun onBack(): Boolean {
        if (!ready) return true
        game.show(BriefingScreen(game, level, mode))
        return true
    }
}
