package ir.shahed.pahpad.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/** Bounded by the bundled asset set. Decode once, retaining per-pixel alpha. */
object Gfx {
    private val cache=HashMap<String,Bitmap?>()
    fun get(context:Context,name:String):Bitmap? {
        if(cache.containsKey(name))return cache[name]
        val bitmap=try {
            val options=BitmapFactory.Options().apply { inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888 }
            context.assets.open("gfx/$name.png").use { BitmapFactory.decodeStream(it,null,options) }
        } catch(e:Exception) { Log.w("PahpadGraphics","Unable to load gfx/$name.png",e);null }
        if(bitmap==null)Log.w("PahpadGraphics","Missing or invalid image: $name")
        cache[name]=bitmap
        return bitmap
    }
    /** Drop references; don't recycle bitmaps which a shader may still reference. */
    fun clear(){cache.clear()}
}
