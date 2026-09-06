package ir.shahed.pahpad.core

import android.content.Context
import android.graphics.Typeface

/**
 * فونت فارسی بازی.
 * اگر فایل «assets/fonts/vazirmatn.ttf» موجود باشد از آن استفاده می‌شود،
 * در غیر این صورت فونت پیش‌فرض سیستم که از فارسی پشتیبانی می‌کند به کار می‌رود.
 */
object Fonts {

    private var regular: Typeface? = null
    private var bold: Typeface? = null

    fun regular(context: Context): Typeface {
        regular?.let { return it }
        val tf = load(context, "fonts/vazirmatn.ttf") ?: Typeface.create("sans-serif", Typeface.NORMAL)
        regular = tf
        return tf
    }

    fun bold(context: Context): Typeface {
        bold?.let { return it }
        val tf = load(context, "fonts/vazirmatn-bold.ttf")
            ?: Typeface.create(regular(context), Typeface.BOLD)
        bold = tf
        return tf
    }

    private fun load(context: Context, path: String): Typeface? = try {
        Typeface.createFromAsset(context.assets, path)
    } catch (t: Throwable) {
        null
    }
}
