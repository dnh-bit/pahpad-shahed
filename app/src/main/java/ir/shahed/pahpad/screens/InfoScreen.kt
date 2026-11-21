package ir.shahed.pahpad.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import ir.shahed.pahpad.BuildConfig
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.Fa
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.core.UiButton
import kotlin.math.abs

/**
 * صفحهٔ متنی اسکرول‌شونده — برای «درباره» و «حریم خصوصی».
 *
 * چرا یک کلاس برای هر دو: متن‌ها بلندند و در گوشی کوتاه از کادر بیرون می‌زنند،
 * پس هر دو به یک چیدمان اسکرول‌شونده نیاز دارند. اگر دو صفحهٔ جدا می‌ساختیم،
 * باگ اسکرول/بریدگی باید دو بار رفع می‌شد.
 *
 * متن داخل خود APK است (نه از وب)، پس صفحه آفلاین هم خوانده می‌شود — شرط
 * بازبینی مایکت و بازار.
 */
class InfoScreen(
    game: MainActivity,
    private val title: String,
    private val subtitle: String?,
    private val blocks: List<Block>,
) : BaseScreen(game) {

    data class Block(val head: String, val body: String, val accent: Int = Theme.MINT)

    private val back = UiButton("بازگشت", Theme.TEXT_DIM) { game.show(SettingsScreen(game)) }

    private val headSizeDp = 13f
    private val bodySizeDp = 12f
    private val lineGap = 1.75f
    private val headGapTopDp = 16f
    private val headGapBottomDp = 7f

    private val viewport = RectF()
    private var scrollY = 0f
    private var maxScroll = 0f

    private var laid: List<Laid> = emptyList()

    private data class Laid(val head: String, val accent: Int, val lines: List<String>)

    init {
        buttons.add(back)
    }

    override fun onBack(): Boolean {
        game.show(SettingsScreen(game))
        return true
    }

    // ------------------------------------------------------------ چیدمان

    override fun layoutUi(w: Float, h: Float) {
        val top = dp(88f)
        val bottom = h - dp(58f)
        viewport.set(dp(20f), top, w - dp(20f), bottom)

        val maxW = viewport.width().coerceAtLeast(1f)
        val bs = dp(bodySizeDp)
        laid = blocks.map { b ->
            Laid(b.head, b.accent, ui.wrap(b.body, bs, maxW))
        }

        var contentH = 0f
        for (b in laid) {
            contentH += dp(headGapTopDp) + dp(headSizeDp) + dp(headGapBottomDp)
            contentH += b.lines.size * bs * lineGap
        }

        maxScroll = (contentH - viewport.height()).coerceAtLeast(0f)
        scrollY = scrollY.coerceIn(0f, maxScroll)

        back.set(dp(20f), h - dp(50f), dp(112f), dp(38f))
    }

    /** برای تست: خطوط آماده‌شده در اندازهٔ فعلی */
    fun allLines(): List<String> = laid.flatMap { it.lines }

    /** برای تست: چقدر می‌شود اسکرول کرد (۰ = همه‌چیز جا شده) */
    fun scrollRange(): Float = maxScroll

    /** برای تست: عرض مفید متن */
    fun contentWidth(): Float = viewport.width()

    /** برای تست: اندازهٔ فونت متن بدنه (px) */
    fun bodySizePx(): Float = dp(bodySizeDp)

    /** برای تست: ناحیهٔ متن روی صفحه */
    fun contentRect(): RectF = RectF(viewport)

    // ------------------------------------------------------------ لمس

    override fun claimsTouch(x: Float, y: Float): Boolean = viewport.contains(x, y)

    private var dragPointer = -1
    private var downY = 0f
    private var lastY = 0f
    private var moved = false

    override fun onTouchDown(x: Float, y: Float, pointerId: Int): Boolean {
        if (dragPointer == -1 && viewport.contains(x, y)) {
            dragPointer = pointerId
            downY = y
            lastY = y
            moved = false
        }
        return true
    }

    override fun onTouchMove(x: Float, y: Float, pointerId: Int) {
        if (pointerId != dragPointer) return
        val dy = y - lastY
        lastY = y
        if (!moved && maxScroll > 0f && abs(y - downY) >= dp(8f)) moved = true
        if (moved && maxScroll > 0f) {
            scrollY = (scrollY - dy).coerceIn(0f, maxScroll)
            layoutUi(vw, vh)
        }
    }

    override fun onTouchUp(x: Float, y: Float, pointerId: Int) {
        if (pointerId != dragPointer) return
        dragPointer = -1
        moved = false
    }

    override fun onTouchCancel() {
        dragPointer = -1
        moved = false
    }

    // ------------------------------------------------------------ رسم

    override fun render(c: Canvas) {
        ui.background(c, vw, vh)
        Deco.header(this, c, title, subtitle)
        Deco.resources(this, c)

        c.save()
        c.clipRect(viewport)

        val bs = dp(bodySizeDp)
        val hs = dp(headSizeDp)
        val lh = bs * lineGap
        var y = viewport.top - scrollY

        for (b in laid) {
            y += dp(headGapTopDp)
            ui.text(c, b.head, viewport.right, y, hs, b.accent, bold = true, align = Paint.Align.RIGHT)
            y += hs + dp(headGapBottomDp)
            for (line in b.lines) {
                ui.text(c, line, viewport.right, y, bs, Theme.TEXT_DIM, align = Paint.Align.RIGHT)
                y += lh
            }
        }

        c.restore()

        if (maxScroll > 0f) {
            val trackH = viewport.height()
            val thumbH = (trackH * (trackH / (trackH + maxScroll))).coerceAtLeast(dp(26f))
            val t = scrollY / maxScroll
            val thumbY = viewport.top + t * (trackH - thumbH)
            ui.fill.color = Theme.withAlpha(Theme.BLUE, 0.32f)
            ui.fill.shader = null
            c.drawRoundRect(viewport.right + dp(3f), thumbY, viewport.right + dp(6f),
                thumbY + thumbH, dp(2f), dp(2f), ui.fill)
        }

        ui.button(c, back)
        if (maxScroll > 0f) {
            ui.text(c, "برای دیدن بقیه، انگشت را بکشید", vw / 2f, vh - dp(42f), dp(10f), Theme.TEXT_FAINT)
        }
    }
}

/**
 * متن صفحه‌های اطلاعاتی. یک جا تعریف شده تا «درباره» و «حریم خصوصی» و متن
 * صفحهٔ فروشگاه از هم جدا نیفتند.
 */
object InfoPages {

    const val EMAIL = "AlirezaOudi@iran.ir"
    const val MAKER = "جمعی از جوانان سرزمین ایران"

    fun about(game: MainActivity): InfoScreen = InfoScreen(
        game,
        title = "درباره",
        subtitle = "پهپاد شاهد — شبیه‌ساز پرواز",
        blocks = listOf(
            InfoScreen.Block(
                "این برنامه",
                "پهپاد شاهد یک بازی شبیه‌ساز پرواز است. ماموریت‌ها، پهپادها و نقشه‌ها همه " +
                    "خیالی‌اند و برنامه هیچ ارتباطی با سامانه‌های واقعی ندارد.",
            ),
            InfoScreen.Block(
                "سازنده",
                MAKER,
                Theme.MINT,
            ),
            InfoScreen.Block(
                "ارتباط",
                "ایمیل: $EMAIL",
                Theme.BLUE,
            ),
            InfoScreen.Block(
                "نسخه",
                Fa.num(BuildConfig.VERSION_NAME) + " (" + Fa.num(BuildConfig.VERSION_CODE) + ")",
                Theme.VIOLET,
            ),
            InfoScreen.Block(
                "سپاس",
                "از همهٔ کسانی که این بازی را آزمودند و ایرادهایش را گفتند.",
                Theme.AMBER,
            ),
        ),
    )

    fun privacy(game: MainActivity): InfoScreen = InfoScreen(
        game,
        title = "حریم خصوصی",
        subtitle = "چه چیزی ذخیره می‌شود و چه چیزی نه",
        blocks = listOf(
            InfoScreen.Block(
                "چه چیزی جمع نمی‌کنیم",
                "نام، ایمیل، شماره تلفن، مخاطبین، موقعیت مکانی، دوربین، میکروفون و فایل‌های " +
                    "گوشی — هیچ‌کدام خوانده یا فرستاده نمی‌شوند. برنامه هیچ سروری برای جمع‌آوری " +
                    "اطلاعات کاربران ندارد.",
            ),
            InfoScreen.Block(
                "چه چیزی ذخیره می‌شود",
                "پیشرفت بازی (ستاره‌ها، سکه‌ها، پهپادهای باز‌شده) و تنظیمات صدا و گرافیک، فقط " +
                    "در حافظهٔ داخلی همین گوشی. با حذف برنامه پاک می‌شود.",
            ),
            InfoScreen.Block(
                "مجوزهای برنامه",
                "اینترنت و وضعیت شبکه فقط برای نمایش تبلیغ؛ لرزش برای بازخورد لمسی. برنامه به " +
                    "حافظهٔ بیرونی، موقعیت مکانی، دوربین و مخاطبین دسترسی ندارد.",
                Theme.AMBER,
            ),
            InfoScreen.Block(
                "تبلیغات",
                "هزینهٔ توسعه از شبکهٔ تبلیغاتی تپسل‌پلاس تأمین می‌شود. این شبکه ممکن است شناسهٔ " +
                    "تبلیغاتی دستگاه و اطلاعات فنی آن را برای نمایش و اندازه‌گیری تبلیغ دریافت " +
                    "کند (tapsell.ir/privacy). بازی آفلاین هم اجرا می‌شود؛ بدون اینترنت فقط " +
                    "تبلیغ نمایش داده نمی‌شود.",
                Theme.VIOLET,
            ),
            InfoScreen.Block(
                "کودکان",
                "برنامه اطلاعاتی از هیچ کاربری، از جمله کودکان، جمع‌آوری نمی‌کند و محتوای " +
                    "نامناسب ندارد.",
            ),
            InfoScreen.Block(
                "تماس",
                "پرسش دربارهٔ حریم خصوصی: $EMAIL",
                Theme.BLUE,
            ),
            InfoScreen.Block(
                "سازنده",
                MAKER,
                Theme.MINT,
            ),
        ),
    )
}
