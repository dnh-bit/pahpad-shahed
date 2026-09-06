package ir.shahed.pahpad.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * بارگذار و حافظه‌ی نهان تصاویر بازی از assets/gfx.
 * همه‌ی تصاویر یک‌بار خوانده و در حافظه نگه داشته می‌شوند (حجم کل زیر ۱ مگابایت).
 */
object Gfx {

    private val cache = HashMap<String, Bitmap?>()

    fun get(context: Context, name: String): Bitmap? {
        val hit = cache[name]
        if (hit != null) return hit
        if (cache.containsKey(name)) return null // قبلاً خوانده شد و نبود
        return try {
            val bmp = context.assets.open("gfx/$name.png").use { BitmapFactory.decodeStream(it) }
            cache[name] = bmp
            bmp
        } catch (e: Exception) {
            cache[name] = null
            null
        }
    }

    /** پاک‌سازی هنگام خروج (اختیاری) */
    fun clear() {
        for (b in cache.values) b?.recycle()
        cache.clear()
    }
}
