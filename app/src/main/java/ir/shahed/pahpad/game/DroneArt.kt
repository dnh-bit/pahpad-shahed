package ir.shahed.pahpad.game

import android.content.Context
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme
import kotlin.math.*

/** Decorative textured wing + shaded fuselage. Dimensions are stylized game units. */
class DroneArt(private val context:Context) {
    private val points=Array(4){FloatArray(3)}
    private val p0=FloatArray(3);private val p1=FloatArray(3);private val p2=FloatArray(3);private val p3=FloatArray(3)
    private var sy=0f;private var cy=1f;private var sp=0f;private var cp=1f;private var sr=0f;private var cr=1f
    private var originX=0f;private var originY=0f;private var originZ=0f
    private fun transform(x:Float,y:Float,z:Float,p:FloatArray){
        val rx=x*cr-y*sr;val ry=x*sr+y*cr
        val py=ry*cp+z*sp;val pz=z*cp-ry*sp
        p[0]=originX+rx*cy+pz*sy;p[1]=originY+py;p[2]=originZ-rx*sy+pz*cy
    }
    private fun face(scene:Scene3D,a:FloatArray,b:FloatArray,c:FloatArray,d:FloatArray,color:Int){
        scene.quad(a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2],d[0],d[1],d[2],color)
    }
    fun draw(scene:Scene3D,m:Mission){
        sy=sin(m.yaw);cy=cos(m.yaw);sp=sin(m.pitch);cp=cos(m.pitch);sr=sin(m.roll);cr=cos(m.roll)
        originX=m.x;originY=m.y;originZ=m.z
        val id=when(m.model.id){"shahed136"->"136";"shahed238"->"238";"shahedx"->"x";else->"131"}
        val scale=if(id=="131")5.4f else 6.2f
        val bmp=Gfx.get(context,"drone_$id")
        transform(-scale,0f,scale,points[0]);transform(scale,0f,scale,points[1]);transform(scale,0f,-scale,points[2]);transform(-scale,0f,-scale,points[3])
        if(bmp!=null)scene.imagePlane(points,bmp)
        val base=if(id=="238")0xFF626764.toInt() else 0xFFCAC6B4.toInt()
        // Twelve-sided rounded fuselage, tapering to the nose and tail.
        val rings=8;val sides=12
        for(ring in 0 until rings){
            val za=-scale*.68f+ring*(scale*1.48f/rings)
            val zb=-scale*.68f+(ring+1)*(scale*1.48f/rings)
            val ra=sin((ring+.35f)/(rings+.7f)*PI.toFloat()).coerceAtLeast(.07f)*.49f
            val rb=sin((ring+1.35f)/(rings+.7f)*PI.toFloat()).coerceAtLeast(.05f)*.49f
            for(i in 0 until sides){
                val a=i*2f*PI.toFloat()/sides;val b=(i+1)*2f*PI.toFloat()/sides
                transform(cos(a)*ra,sin(a)*ra+.17f,za,p0);transform(cos(b)*ra,sin(b)*ra+.17f,za,p1)
                transform(cos(b)*rb,sin(b)*rb+.17f,zb,p2);transform(cos(a)*rb,sin(a)*rb+.17f,zb,p3)
                val light=.69f+.32f*max(0f,sin(a))+.12f*max(0f,-cos(a))
                face(scene,p0,p1,p2,p3,Theme.shade(base,light))
            }
        }
        // Raised wingtip fins give the silhouette depth in the chase camera.
        if(id=="131"||id=="136")for(side in intArrayOf(-1,1)){
            val x=side*scale*.82f
            transform(x,0f,-scale*.66f,p0);transform(x,.95f,-scale*.5f,p1);transform(x,.65f,-scale*.23f,p2);transform(x,0f,-scale*.15f,p3)
            face(scene,p0,p1,p2,p3,Theme.shade(base,if(side<0).88f else 1.07f))
        }
        // Visible animated pusher blades, driven only by existing game time.
        if(id=="131"||id=="136"){
            val angle=m.elapsed*43f
            val ux=cos(angle)*1.05f;val uy=sin(angle)*1.05f
            transform(-ux,-uy+.17f,-scale*.76f,p0);transform(ux,uy+.17f,-scale*.76f,p1)
            transform(ux,uy+.29f,-scale*.78f,p2);transform(-ux,-uy+.29f,-scale*.78f,p3)
            face(scene,p0,p1,p2,p3,0xFF4C514B.toInt())
        }
    }
}
