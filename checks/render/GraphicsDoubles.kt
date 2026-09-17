@file:Suppress("UNUSED_PARAMETER")
package android.graphics

// Recording doubles only: no claim of Android/Skia pixel rasterization.
class Bitmap(val width: Int, val height: Int)
open class Shader { enum class TileMode { CLAMP } }
class BitmapShader(val bitmap: Bitmap, x: TileMode, y: TileMode): Shader() {
    var values = FloatArray(9)
    fun setLocalMatrix(matrix: Matrix) { matrix.getValues(values) }
}
class Matrix {
    private var values = floatArrayOf(1f,0f,0f,0f,1f,0f,0f,0f,1f)
    fun setValues(v: FloatArray) { values=v.copyOf() }
    fun getValues(v: FloatArray) { values.copyInto(v) }
    fun setPolyToPoly(src: FloatArray, si: Int, dst: FloatArray, di: Int, count: Int): Boolean {
        require(count == 4)
        val a=Array(8){DoubleArray(9)}
        for(i in 0..3) {
            val u=src[si+2*i].toDouble(); val v=src[si+2*i+1].toDouble()
            val x=dst[di+2*i].toDouble(); val y=dst[di+2*i+1].toDouble()
            a[2*i]=doubleArrayOf(u,v,1.0,0.0,0.0,0.0,-x*u,-x*v,x)
            a[2*i+1]=doubleArrayOf(0.0,0.0,0.0,u,v,1.0,-y*u,-y*v,y)
        }
        for(k in 0..7) {
            val pivot=(k..7).maxBy { kotlin.math.abs(a[it][k]) }
            val row=a[k]; a[k]=a[pivot]; a[pivot]=row
            if(kotlin.math.abs(a[k][k])<1e-12)return false
            val d=a[k][k]; for(j in k..8)a[k][j]/=d
            for(i in 0..7)if(i!=k) { val f=a[i][k];for(j in k..8)a[i][j]-=f*a[k][j] }
        }
        values=FloatArray(9){if(it==8)1f else a[it][8].toFloat()};return true
    }
}
class Paint(flags: Int=0) {
    companion object { const val ANTI_ALIAS_FLAG=1; const val FILTER_BITMAP_FLAG=2 }
    enum class Style { FILL, STROKE }
    var style=Style.FILL; var color=0; var alpha=255; var shader: Shader?=null; var strokeWidth=0f
}
class Path {
    val points=ArrayList<Pair<Float,Float>>()
    fun rewind(){points.clear()}
    fun moveTo(x:Float,y:Float){points.add(x to y)}
    fun lineTo(x:Float,y:Float){points.add(x to y)}
    fun close(){}
}
data class Draw(val kind:String,val color:Int,val alpha:Int,val matrix:FloatArray?,val points:List<Pair<Float,Float>>)
class Canvas {
    val draws=ArrayList<Draw>()
    fun drawPath(p:Path, paint:Paint){draws.add(Draw("path",paint.color,paint.alpha,(paint.shader as? BitmapShader)?.values?.copyOf(),p.points.toList()))}
    fun drawCircle(x:Float,y:Float,r:Float,paint:Paint){draws.add(Draw("circle",paint.color,paint.alpha,null,listOf(x to y,r to r)))}
    fun drawLine(x:Float,y:Float,x2:Float,y2:Float,paint:Paint){}
}
object Color {
    fun alpha(c:Int)=c ushr 24
    fun red(c:Int)=(c ushr 16) and 255
    fun green(c:Int)=(c ushr 8) and 255
    fun blue(c:Int)=c and 255
    fun argb(a:Int,r:Int,g:Int,b:Int)=(a shl 24) or (r shl 16) or (g shl 8) or b
}
