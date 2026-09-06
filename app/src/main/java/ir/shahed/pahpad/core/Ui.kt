package ir.shahed.pahpad.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** یک دکمه‌ی ساده‌ی رسم‌شده روی بوم */
class UiButton(
    var label: String,
    var accent: Int = Theme.MINT,
    var filled: Boolean = false,
    var onClick: () -> Unit = {}
) {
    val rect = RectF()
    var enabled = true
    var visible = true
    var pressed = false
    var subLabel: String? = null
    var icon: String? = null
    var tag: Any? = null

    fun set(x: Float, y: Float, w: Float, h: Float) {
        rect.set(x, y, x + w, y + h)
    }

    fun contains(px: Float, py: Float): Boolean =
        visible && enabled && rect.contains(px, py)
}

/** ابزار رسم رابط کاربری: متن فارسی، پنل، دکمه، نوار و ستاره */
class Ui(val context: Context, val density: Float) {

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.regular(context)
        textAlign = Paint.Align.CENTER
    }
    private val boldFace: Typeface = Fonts.bold(context)
    private val regularFace: Typeface = Fonts.regular(context)
    private val tmp = RectF()

    fun dp(v: Float): Float = v * density

    fun sp(v: Float): Float = v * density

    // --------------------------------------------------------------- متن

    fun text(
        c: Canvas,
        str: String,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.CENTER,
        alpha: Float = 1f
    ) {
        textPaint.typeface = if (bold) boldFace else regularFace
        textPaint.textSize = size
        textPaint.color = Theme.withAlpha(color, alpha)
        textPaint.textAlign = align
        c.drawText(str, cx, cy + size * 0.35f, textPaint)
    }

    fun measure(str: String, size: Float, bold: Boolean = false): Float {
        textPaint.typeface = if (bold) boldFace else regularFace
        textPaint.textSize = size
        return textPaint.measureText(str)
    }

    /** شکستن متن فارسی به چند خط با عرض مشخص */
    fun wrap(str: String, size: Float, maxWidth: Float, bold: Boolean = false): List<String> {
        textPaint.typeface = if (bold) boldFace else regularFace
        textPaint.textSize = size
        val words = str.split(' ')
        val lines = ArrayList<String>()
        var line = StringBuilder()
        for (w in words) {
            val candidate = if (line.isEmpty()) w else "$line $w"
            if (textPaint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = StringBuilder(candidate)
            } else {
                lines.add(line.toString())
                line = StringBuilder(w)
            }
        }
        if (line.isNotEmpty()) lines.add(line.toString())
        return lines
    }

    fun paragraph(
        c: Canvas,
        str: String,
        cx: Float,
        top: Float,
        size: Float,
        maxWidth: Float,
        color: Int,
        lineGap: Float = 1.55f,
        align: Paint.Align = Paint.Align.CENTER
    ): Float {
        var y = top
        for (line in wrap(str, size, maxWidth)) {
            text(c, line, cx, y, size, color, align = align)
            y += size * lineGap
        }
        return y
    }

    // --------------------------------------------------------------- سطوح

    fun background(c: Canvas, w: Float, h: Float, accent: Int = Theme.MINT) {
        fill.shader = LinearGradient(
            0f, 0f, w * 0.4f, h,
            intArrayOf(Theme.BG_DEEP, Theme.BG, 0xFF10161D.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        // شبکه تاکتیکی پس‌زمینه
        stroke.color = Theme.withAlpha(accent, 0.05f)
        stroke.strokeWidth = dp(1f)
        val step = dp(46f)
        var x = 0f
        while (x < w) { c.drawLine(x, 0f, x, h, stroke); x += step }
        var y = 0f
        while (y < h) { c.drawLine(0f, y, w, y, stroke); y += step }
        // درخشش گوشه
        fill.shader = android.graphics.RadialGradient(
            w * 0.5f, h * 1.15f, h * 0.9f,
            intArrayOf(Theme.withAlpha(accent, 0.13f), Theme.withAlpha(accent, 0f)),
            null, Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
    }

    fun panel(
        c: Canvas,
        r: RectF,
        fillColor: Int = Theme.PANEL,
        strokeColor: Int = Theme.LINE,
        radius: Float = dp(14f),
        strokeWidth: Float = dp(1.2f)
    ) {
        fill.color = fillColor
        fill.shader = null
        c.drawRoundRect(r, radius, radius, fill)
        if (strokeWidth > 0f) {
            stroke.color = strokeColor
            stroke.strokeWidth = strokeWidth
            c.drawRoundRect(r, radius, radius, stroke)
        }
    }

    fun panel(
        c: Canvas, x: Float, y: Float, w: Float, h: Float,
        fillColor: Int = Theme.PANEL, strokeColor: Int = Theme.LINE, radius: Float = dp(14f)
    ) {
        tmp.set(x, y, x + w, y + h)
        panel(c, tmp, fillColor, strokeColor, radius)
    }

    // --------------------------------------------------------------- تصویر
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val bmpCache = HashMap<String, Bitmap?>()

    /** تصویر از assets/gfx با حافظه‌ی نهان */
    fun image(context: Context, c: Canvas, name: String, x: Float, y: Float, w: Float, h: Float, radius: Float = dp(12f)) {
        val bmp = bmpCache.getOrPut(name) {
            try { context.assets.open("gfx/$name.png").use { BitmapFactory.decodeStream(it) } }
            catch (e: Exception) { null }
        } ?: return
        val save = c.save()
        val clip = Path()
        clip.addRoundRect(RectF(x, y, x + w, y + h), radius, radius, Path.Direction.CW)
        c.clipPath(clip)
        // پوشاندن کامل ناحیه (center-crop)
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        val scale = Math.max(w / bw, h / bh)
        val dw = bw * scale; val dh = bh * scale
        val dx = x + (w - dw) / 2f; val dy = y + (h - dh) / 2f
        c.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), bmpPaint)
        c.restoreToCount(save)
    }

    /** دسترسی خام به تصویر با حافظه‌ی نهان */
    fun imageGet(context: Context, name: String): Bitmap? = bmpCache.getOrPut(name) {
        try { context.assets.open("gfx/$name.png").use { BitmapFactory.decodeStream(it) } }
        catch (e: Exception) { null }
    }

    /** تصویر با حفظ تناسب داخل مستطیل (letterbox) */
    fun imageFit(context: Context, c: Canvas, name: String, x: Float, y: Float, w: Float, h: Float) {
        val bmp = bmpCache.getOrPut(name) {
            try { context.assets.open("gfx/$name.png").use { BitmapFactory.decodeStream(it) } }
            catch (e: Exception) { null }
        } ?: return
        val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
        val scale = Math.min(w / bw, h / bh)
        val dw = bw * scale; val dh = bh * scale
        c.drawBitmap(bmp, null, RectF(x + (w - dw) / 2f, y + (h - dh) / 2f, x + (w - dw) / 2f + dw, y + (h - dh) / 2f + dh), bmpPaint)
    }

    // --------------------------------------------------------------- دکمه

    fun button(c: Canvas, b: UiButton) {
        if (!b.visible) return
        val r = b.rect
        val radius = dp(12f)
        val alpha = if (b.enabled) 1f else 0.35f
        val press = if (b.pressed) 1f else 0f
        if (b.filled) {
            fill.color = Theme.withAlpha(Theme.mix(b.accent, Theme.TEXT, press * 0.25f), alpha)
            fill.shader = null
            c.drawRoundRect(r, radius, radius, fill)
            text(
                c, b.label, r.centerX(), r.centerY() - (if (b.subLabel != null) dp(7f) else 0f),
                dp(16f), Theme.BG_DEEP, bold = true, alpha = alpha
            )
        } else {
            fill.color = Theme.withAlpha(if (b.pressed) Theme.PANEL_HI else Theme.PANEL, alpha)
            fill.shader = null
            c.drawRoundRect(r, radius, radius, fill)
            stroke.color = Theme.withAlpha(b.accent, alpha * (if (b.pressed) 0.95f else 0.5f))
            stroke.strokeWidth = dp(1.3f)
            c.drawRoundRect(r, radius, radius, stroke)
            text(
                c, b.label, r.centerX(), r.centerY() - (if (b.subLabel != null) dp(7f) else 0f),
                dp(16f), if (b.enabled) Theme.TEXT else Theme.TEXT_FAINT, bold = true, alpha = alpha
            )
        }
        b.subLabel?.let {
            text(
                c, it, r.centerX(), r.centerY() + dp(12f), dp(11.5f),
                if (b.filled) Theme.BG_DEEP else Theme.TEXT_DIM, alpha = alpha * 0.9f
            )
        }
    }

    // --------------------------------------------------------------- اجزای بازی

    fun bar(
        c: Canvas, x: Float, y: Float, w: Float, h: Float, value01: Float,
        color: Int, bg: Int = Theme.withAlpha(0xFF000000.toInt(), 0.45f)
    ) {
        tmp.set(x, y, x + w, y + h)
        fill.color = bg
        fill.shader = null
        c.drawRoundRect(tmp, h / 2f, h / 2f, fill)
        val v = value01.coerceIn(0f, 1f)
        if (v > 0f) {
            tmp.set(x, y, x + w * v, y + h)
            fill.color = color
            c.drawRoundRect(tmp, h / 2f, h / 2f, fill)
        }
        stroke.color = Theme.withAlpha(Theme.TEXT, 0.18f)
        stroke.strokeWidth = dp(1f)
        tmp.set(x, y, x + w, y + h)
        c.drawRoundRect(tmp, h / 2f, h / 2f, stroke)
    }

    private val starPath = Path()

    fun star(c: Canvas, cx: Float, cy: Float, radius: Float, filled: Boolean, color: Int = Theme.AMBER) {
        starPath.reset()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) radius else radius * 0.46f
            val a = (-Math.PI / 2 + i * Math.PI / 5).toFloat()
            val px = cx + r * Math.cos(a.toDouble()).toFloat()
            val py = cy + r * Math.sin(a.toDouble()).toFloat()
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        if (filled) {
            fill.color = color
            fill.shader = null
            c.drawPath(starPath, fill)
        } else {
            stroke.color = Theme.withAlpha(color, 0.35f)
            stroke.strokeWidth = dp(1.4f)
            c.drawPath(starPath, stroke)
        }
    }

    fun stars(c: Canvas, cx: Float, cy: Float, radius: Float, earned: Int, total: Int = 3) {
        val gap = radius * 2.5f
        val start = cx + gap * (total - 1) / 2f
        for (i in 0 until total) {
            star(c, start - i * gap, cy, radius, i < earned)
        }
    }

    /** آیکون‌های ساده‌ی برداری */
    fun iconStar(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) = star(c, cx, cy, r, true, color)

    fun iconCoin(c: Canvas, cx: Float, cy: Float, r: Float) {
        fill.color = Theme.AMBER
        fill.shader = null
        c.drawCircle(cx, cy, r, fill)
        fill.color = Theme.withAlpha(Theme.BG_DEEP, 0.55f)
        c.drawCircle(cx, cy, r * 0.45f, fill)
    }

    fun iconLock(c: Canvas, cx: Float, cy: Float, r: Float, color: Int = Theme.TEXT_FAINT) {
        fill.color = color
        fill.shader = null
        tmp.set(cx - r * 0.75f, cy - r * 0.1f, cx + r * 0.75f, cy + r * 0.9f)
        c.drawRoundRect(tmp, r * 0.25f, r * 0.25f, fill)
        stroke.color = color
        stroke.strokeWidth = r * 0.3f
        stroke.style = Paint.Style.STROKE
        val arc = RectF(cx - r * 0.5f, cy - r * 0.85f, cx + r * 0.5f, cy + r * 0.15f)
        c.drawArc(arc, 180f, 180f, false, stroke)
    }

    fun chip(c: Canvas, cx: Float, cy: Float, label: String, color: Int, textSize: Float = dp(11.5f)) {
        val w = measure(label, textSize) + dp(18f)
        val h = textSize + dp(11f)
        tmp.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        fill.color = Theme.withAlpha(color, 0.16f)
        fill.shader = null
        c.drawRoundRect(tmp, h / 2f, h / 2f, fill)
        stroke.color = Theme.withAlpha(color, 0.5f)
        stroke.strokeWidth = dp(1f)
        c.drawRoundRect(tmp, h / 2f, h / 2f, stroke)
        text(c, label, cx, cy, textSize, color)
    }
}
