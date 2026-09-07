package ir.shahed.pahpad.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme
import kotlin.math.*

/** Decorative mesh. Collision dimensions and flight model are unchanged. */
class DroneArt(private val context:Context) {
    private val points=Array(4){FloatArray(3)}
    private val p0=FloatArray(3);private val p1=FloatArray(3);private val p2=FloatArray(3);private val p3=FloatArray(3)
    private var sy=0f;private var cy=1f;private var sp=0f;private var cp=1f;private var sr=0f;private var cr=1f
    private var originX=0f;private var originY=0f;private var originZ=0f
    private var propAngle=0f
    private val blur by lazy {
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=RadialGradient(32f,32f,31f,intArrayOf(0x003B433F,0x103B433F,0x483B433F,0x003B433F),floatArrayOf(0f,.25f,.87f,1f),Shader.TileMode.CLAMP)
        Canvas(bitmap).drawCircle(32f,32f,31f,p)
        bitmap
    }
    fun update(dt:Float,m:Mission) {
        if(m.phase==Mission.Phase.FLYING)
            propAngle=(propAngle+dt*(43f+42f*m.throttle+(if(m.boost)20f else 0f)))%(2f*PI.toFloat())
    }
    private fun transform(x:Float,y:Float,z:Float,p:FloatArray){
        val rx=x*cr-y*sr;val ry=x*sr+y*cr
        val py=ry*cp+z*sp;val pz=z*cp-ry*sp
        p[0]=originX+rx*cy+pz*sy;p[1]=originY+py;p[2]=originZ-rx*sy+pz*cy
    }
    private fun face(scene:Scene3D,a:FloatArray,b:FloatArray,c:FloatArray,d:FloatArray,color:Int){
        val ux=b[0]-a[0];val uy=b[1]-a[1];val uz=b[2]-a[2]
        val vx=d[0]-a[0];val vy=d[1]-a[1];val vz=d[2]-a[2]
        val nx=uy*vz-uz*vy;val ny=uz*vx-ux*vz;val nz=ux*vy-uy*vx
        val inv=1f/sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.0001f)
        val shaded=Theme.shade(color,scene.light(nx*inv,ny*inv,nz*inv))
        scene.quad(a[0],a[1],a[2],b[0],b[1],b[2],c[0],c[1],c[2],d[0],d[1],d[2],shaded)
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
                face(scene,p0,p1,p2,p3,base)
                // Narrow raised panel seams, geometry rather than screen-space lines.
                if(ring==2||ring==5){
                    val seamRadius=ra+.008f
                    transform(cos(a)*seamRadius,sin(a)*seamRadius+.17f,za,p0)
                    transform(cos(b)*seamRadius,sin(b)*seamRadius+.17f,za,p1)
                    transform(cos(b)*seamRadius,sin(b)*seamRadius+.17f,za+.027f,p2)
                    transform(cos(a)*seamRadius,sin(a)*seamRadius+.17f,za+.027f,p3)
                    face(scene,p0,p1,p2,p3,Theme.shade(base,.68f))
                }
            }
        }
        if(id=="131"||id=="136"){
            for(sideIndex in 0..1){
                val side=if(sideIndex==0)-1 else 1
                val x=side*scale*.82f
                transform(x,0f,-scale*.66f,p0);transform(x,.95f,-scale*.5f,p1);transform(x,.65f,-scale*.23f,p2);transform(x,0f,-scale*.15f,p3)
                face(scene,p0,p1,p2,p3,base)
            }
            val tail=-scale*.78f
            val flying=m.phase==Mission.Phase.FLYING
            val ux=cos(propAngle)*1.05f;val uy=sin(propAngle)*1.05f
            transform(-ux,-uy+.17f,tail,p0);transform(ux,uy+.17f,tail,p1)
            transform(ux,uy+.29f,tail-.03f,p2);transform(-ux,-uy+.29f,tail-.03f,p3)
            face(scene,p0,p1,p2,p3,if(flying)0x804C514B.toInt() else 0xFF4C514B.toInt())
            if(flying){
                transform(-1.18f,1.35f,tail-.05f,points[0]);transform(1.18f,1.35f,tail-.05f,points[1])
                transform(1.18f,-1.01f,tail-.05f,points[2]);transform(-1.18f,-1.01f,tail-.05f,points[3])
                scene.imagePlane(points,blur,.60f+m.throttle*.30f)
            }
        }
    }
}
