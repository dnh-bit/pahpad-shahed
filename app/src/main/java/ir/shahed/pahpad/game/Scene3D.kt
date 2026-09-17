package ir.shahed.pahpad.game

import android.graphics.*
import ir.shahed.pahpad.core.Theme
import kotlin.math.*

/** Coordinates: X right, Y up, Z forward. Roll is applied by GameScreen. */
class Camera {
    var x=0f; var y=0f; var z=0f
    var yaw=0f; var pitch=0f; var roll=0f; var fov=74f
    var width=0f; private set
    var height=0f; private set
    var screenCx=0f; private set
    var screenCy=0f; private set
    var focal=0f; private set
    val near=0.6f
    private var sy=0f; private var cy=1f; private var sp=0f; private var cp=1f
    private val tmp=FloatArray(3)
    fun viewport(w:Float,h:Float) {
        width=w; height=h; screenCx=w/2f; screenCy=h/2f
        focal=h/2f/tan(Math.toRadians((fov/2f).toDouble())).toFloat()
    }
    fun refresh() { sy=sin(yaw); cy=cos(yaw); sp=sin(pitch); cp=cos(pitch) }
    fun toCamera(px:Float,py:Float,pz:Float,out:FloatArray) {
        val dx=px-x; val dy=py-y; val dz=pz-z
        val forward=dx*sy+dz*cy
        out[0]=dx*cy-dz*sy; out[1]=dy*cp-forward*sp; out[2]=dy*sp+forward*cp
    }
    fun projectCamera(p:FloatArray,out:FloatArray):Boolean {
        if(p[2]<=near) return false
        out[0]=screenCx+focal*p[0]/p[2]; out[1]=screenCy-focal*p[1]/p[2]; out[2]=p[2]
        return true
    }
    fun project(px:Float,py:Float,pz:Float,out:FloatArray):Boolean {
        toCamera(px,py,pz,tmp); return projectCamera(tmp,out)
    }
    fun horizonY():Float=screenCy+focal*tan(pitch)
    fun scaleAt(depth:Float,size:Float):Float=if(depth<=near) 0f else focal*size/depth
    /** Inverse ground-plane ray, used for world-locked texture projection. */
    fun groundAt(sx:Float,screenY:Float,out:FloatArray):Boolean {
        val rx=(sx-screenCx)/focal; val ry=(screenCy-screenY)/focal
        val up=ry*cp+sp; val forward=cp-ry*sp
        if(up>=-0.00001f || y<=0f) return false
        val t=-y/up
        out[0]=x+t*(rx*cy+forward*sy); out[1]=0f; out[2]=z+t*(-rx*sy+forward*cy)
        return out[0].isFinite() && out[2].isFinite()
    }
}

class Face {
    var count=0; val xs=FloatArray(8); val ys=FloatArray(8)
    var color=0; var depth=0f; var outlineColor=0; var hasOutline=false
    var bitmap:Bitmap?=null; var transparent=false; var fog=0f; var opacity=1f
    val uv=FloatArray(8); val dst=FloatArray(8)
    val textureMatrix=FloatArray(9)
    var radius=0f // Positive for a camera-facing particle circle.
}

/** Pooled painter renderer with near-plane clipping and depth-sorted image planes.
 * This is a lightweight Canvas renderer, not a z-buffered PBR engine.
 */
class Scene3D {
    val cam=Camera()
    // One world-space sun for procedural solids, fuselage and ground shadows.
    val sunX=-.44f;val sunY=.36f;val sunZ=.82f
    fun light(nx:Float,ny:Float,nz:Float):Float =
        .66f+.46f*max(0f,nx*sunX+ny*sunY+nz*sunZ)
    private val depthOrder=Comparator<Face>{a,b->b.depth.compareTo(a.depth)}
    var fogColor=Theme.DESERT.fog; var fogStart=260f; var fogEnd=1500f
    var wallTexture:Bitmap?=null; var roofTexture:Bitmap?=null
    private val pool=ArrayList<Face>(1800); private var used=0
    private val order=ArrayList<Face>(1800)
    private val path=Path(); private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE }
    private val input=Array(8){FloatArray(3)}; private val clipped=Array(8){FloatArray(3)}
    private val a=FloatArray(3); private val b=FloatArray(3)
    private val matrix=Matrix()
    private val shaders=HashMap<Bitmap,BitmapShader>()
    private val corners=Array(4){FloatArray(3)}
    fun begin(){ used=0; order.clear(); cam.refresh() }
    fun clear(){order.clear();pool.clear();shaders.clear();used=0;wallTexture=null;roofTexture=null}
    private fun next():Face {
        if(used==pool.size)pool.add(Face())
        return pool[used++].also { it.bitmap=null; it.transparent=false; it.hasOutline=false; it.opacity=1f; it.radius=0f }
    }
    private fun fogFactor(depth:Float)=((depth-fogStart)/(fogEnd-fogStart)).coerceIn(0f,0.96f)
    private fun submit(n:Int,color:Int,outline:Int,texture:Bitmap?,transparent:Boolean,opacity:Float=1f) {
        val near=cam.near+0.002f
        var count=0
        for(i in 0 until n) {
            val p=input[(i+n-1)%n]; val q=input[i]
            val pin=p[2]>=near; val qin=q[2]>=near
            if(pin!=qin) {
                val t=(near-p[2])/(q[2]-p[2]); val o=clipped[count++]
                for(k in 0..2)o[k]=p[k]+(q[k]-p[k])*t
                o[2]=near
            }
            if(qin){ val o=clipped[count++]; for(k in 0..2)o[k]=q[k] }
        }
        if(count<3)return
        var depth=0f
        for(i in 0 until count)depth+=clipped[i][2]
        depth/=count.toFloat()
        if(depth>fogEnd*1.4f)return
        val f=next(); f.count=count; f.depth=depth; f.fog=fogFactor(depth)
        f.opacity=opacity.coerceIn(0f,1f)
        f.color=Theme.withAlpha(Theme.mix(color,fogColor,f.fog),Color.alpha(color)/255f); f.outlineColor=outline; f.hasOutline=outline!=0
        var minX=Float.POSITIVE_INFINITY;var maxX=Float.NEGATIVE_INFINITY
        var minY=Float.POSITIVE_INFINITY;var maxY=Float.NEGATIVE_INFINITY
        for(i in 0 until count) {
            val p=clipped[i];f.xs[i]=cam.screenCx+cam.focal*p[0]/p[2];f.ys[i]=cam.screenCy-cam.focal*p[1]/p[2]
            minX=min(minX,f.xs[i]);maxX=max(maxX,f.xs[i]);minY=min(minY,f.ys[i]);maxY=max(maxY,f.ys[i])
        }
        // Expanded bounds accommodate the outer Canvas roll transform.
        if(maxX < -cam.width || minX > cam.width*2 || maxY < -cam.height || minY > cam.height*2){used--;return}
        if(texture!=null && n==4 && textureMapping(texture,f.textureMatrix)) {
            f.bitmap=texture; f.transparent=transparent
        } else if(transparent) {
            // A clipped sprite must never become an opaque rectangular fallback.
            used--;return
        }
        order.add(f)
    }
    /** Map bitmap pixels to homogeneous screen coordinates BEFORE clipping.
     * Solve on the quad's dominant camera-space plane, then compose projection.
     * Never divide original vertices by z: they may lie on/behind the eye plane.
     * The clipped path supplies coverage only; it must not redefine the UV corners.
     * Like setPolyToPoly, this assumes a planar, convex, non-degenerate quad.
     */
    private fun textureMapping(bitmap:Bitmap,out:FloatArray):Boolean {
        val p=input[0];val q=input[1];val r=input[2];val s=input[3]
        val ax=(q[0]-p[0]).toDouble();val ay=(q[1]-p[1]).toDouble();val az=(q[2]-p[2]).toDouble()
        val bx=(s[0]-p[0]).toDouble();val by=(s[1]-p[1]).toDouble();val bz=(s[2]-p[2]).toDouble()
        val nx=abs(ay*bz-az*by);val ny=abs(az*bx-ax*bz);val nz=abs(ax*by-ay*bx)
        val i:Int;val j:Int
        if(nx>=ny && nx>=nz){i=1;j=2}else if(ny>=nz){i=0;j=2}else{i=0;j=1}
        val d1=(q[i]-r[i]).toDouble();val d2=(s[i]-r[i]).toDouble()
        val e1=(q[j]-r[j]).toDouble();val e2=(s[j]-r[j]).toDouble()
        val d3=p[i].toDouble()-q[i]+r[i]-s[i];val e3=p[j].toDouble()-q[j]+r[j]-s[j]
        val det=d1*e2-d2*e1
        if(!det.isFinite() || abs(det)<=1e-12*max(1.0,abs(d1*e2)+abs(d2*e1)))return false
        val g=(d3*e2-d2*e3)/det;val h=(d1*e3-d3*e1)/det
        // A convex quad has positive homogeneous corner weights.
        if(1.0+g<=0.0 || 1.0+h<=0.0 || 1.0+g+h<=0.0)return false
        for(k in 0..2) {
            val a=((1.0+g)*q[k]-p[k])/bitmap.width
            val b=((1.0+h)*s[k]-p[k])/bitmap.height
            val c=p[k].toDouble()
            if(k==2){out[6]=a.toFloat();out[7]=b.toFloat();out[8]=c.toFloat()}
            else {
                val sign=if(k==0)cam.focal else -cam.focal
                val center=if(k==0)cam.screenCx else cam.screenCy
                val za=((1.0+g)*q[2]-p[2])/bitmap.width
                val zb=((1.0+h)*s[2]-p[2])/bitmap.height
                out[k*3]=(sign*a+center*za).toFloat()
                out[k*3+1]=(sign*b+center*zb).toFloat()
                out[k*3+2]=(sign*c+center*p[2]).toFloat()
            }
        }
        return out.all { it.isFinite() }
    }
    fun quad(x0:Float,y0:Float,z0:Float,x1:Float,y1:Float,z1:Float,x2:Float,y2:Float,z2:Float,x3:Float,y3:Float,z3:Float,color:Int,outline:Int=0,texture:Bitmap?=null,transparent:Boolean=false) {
        cam.toCamera(x0,y0,z0,input[0]);cam.toCamera(x1,y1,z1,input[1]);cam.toCamera(x2,y2,z2,input[2]);cam.toCamera(x3,y3,z3,input[3])
        submit(4,color,outline,texture,transparent)
    }
    fun triangle(x0:Float,y0:Float,z0:Float,x1:Float,y1:Float,z1:Float,x2:Float,y2:Float,z2:Float,color:Int) {
        cam.toCamera(x0,y0,z0,input[0]);cam.toCamera(x1,y1,z1,input[1]);cam.toCamera(x2,y2,z2,input[2]);submit(3,color,0,null,false)
    }
    fun imagePlane(points:Array<FloatArray>,bitmap:Bitmap,opacity:Float=1f) {
        for(i in 0..3)cam.toCamera(points[i][0],points[i][1],points[i][2],input[i])
        submit(4,0,0,bitmap,true,opacity)
    }
    fun billboard(x:Float,baseY:Float,z:Float,width:Float,height:Float,bitmap:Bitmap) {
        val dx=cos(cam.yaw)*width/2f;val dz=-sin(cam.yaw)*width/2f
        quad(x-dx,baseY+height,z-dz,x+dx,baseY+height,z+dz,x+dx,baseY,z+dz,x-dx,baseY,z-dz,0,texture=bitmap,transparent=true)
    }
    fun box(cx:Float,baseY:Float,cz:Float,sx:Float,sy:Float,sz:Float,color:Int,rotY:Float=0f,topColor:Int=Theme.shade(color,1.22f),sideColor:Int=Theme.shade(color,0.78f)) {
        val s=sin(rotY);val c=cos(rotY)
        for(i in 0..3) {
            val ox=if(i==0||i==3)-sx/2f else sx/2f;val oz=if(i<2)-sz/2f else sz/2f
            corners[i][0]=cx+ox*c-oz*s;corners[i][2]=cz+ox*s+oz*c
        }
        val top=baseY+sy
        for(i in 0..3) {
            val j=(i+1)%4;val a=corners[i];val b=corners[j]
            val nx=b[2]-a[2];val nz=a[0]-b[0]
            val inv=1f/sqrt(nx*nx+nz*nz).coerceAtLeast(.001f)
            quad(a[0],top,a[2],b[0],top,b[2],b[0],baseY,b[2],a[0],baseY,a[2],Theme.shade(sideColor,light(nx*inv,0f,nz*inv)/.78f),texture=wallTexture)
        }
        quad(corners[0][0],top,corners[0][2],corners[1][0],top,corners[1][2],corners[2][0],top,corners[2][2],corners[3][0],top,corners[3][2],Theme.shade(topColor,light(0f,1f,0f)/1.22f),texture=roofTexture)
    }
    fun line(x0:Float,y0:Float,z0:Float,x1:Float,y1:Float,z1:Float,canvas:Canvas,color:Int,width:Float) {
        cam.toCamera(x0,y0,z0,a);cam.toCamera(x1,y1,z1,b)
        if(a[2]<=cam.near && b[2]<=cam.near)return
        if(a[2]<=cam.near||b[2]<=cam.near) {
            val p=if(a[2]<=cam.near)a else b;val q=if(p===a)b else a;val t=(cam.near-p[2])/(q[2]-p[2])
            for(k in 0..2)p[k]+=(q[k]-p[k])*t
        }
        stroke.color=Theme.withAlpha(Theme.mix(color,fogColor,fogFactor((a[2]+b[2])/2)),Color.alpha(color)/255f);stroke.strokeWidth=width
        canvas.drawLine(cam.screenCx+cam.focal*a[0]/a[2],cam.screenCy-cam.focal*a[1]/a[2],cam.screenCx+cam.focal*b[0]/b[2],cam.screenCy-cam.focal*b[1]/b[2],stroke)
    }
    /** Queue an already projected camera-facing particle. Shared painter order fixes
     * fully separated occluders, not intersecting/slanted surfaces: one depth per
     * primitive cannot replace a per-pixel z-buffer. Alpha blends back to front.
     */
    fun particle(sx:Float,sy:Float,depth:Float,radius:Float,color:Int) {
        if(!sx.isFinite() || !sy.isFinite() || !depth.isFinite() || !radius.isFinite() ||
            depth<=cam.near || radius<=0f || Color.alpha(color)==0)return
        val f=next();f.count=0;f.xs[0]=sx;f.ys[0]=sy;f.depth=depth;f.radius=radius;f.color=color
        order.add(f)
    }
    fun flush(canvas:Canvas) {
        order.sortWith(depthOrder)
        for(f in order) {
            if(f.radius>0f) {
                paint.shader=null;paint.color=f.color
                canvas.drawCircle(f.xs[0],f.ys[0],f.radius,paint)
                continue
            }
            path.rewind();path.moveTo(f.xs[0],f.ys[0]);for(i in 1 until f.count)path.lineTo(f.xs[i],f.ys[i]);path.close()
            paint.shader=null;paint.alpha=255;paint.color=f.color
            if(!f.transparent)canvas.drawPath(path,paint)
            val bitmap=f.bitmap
            if(bitmap!=null) {
                matrix.setValues(f.textureMatrix)
                val shader=shaders.getOrPut(bitmap){BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)}
                shader.setLocalMatrix(matrix);paint.shader=shader
                paint.alpha=if(f.transparent)(255*f.opacity*(1f-f.fog*.75f)).toInt() else (208*(1f-f.fog)).toInt()
                canvas.drawPath(path,paint);paint.shader=null;paint.alpha=255
            }
            if(f.hasOutline){stroke.color=f.outlineColor;stroke.strokeWidth=1f;canvas.drawPath(path,stroke)}
        }
        order.clear();used=0
    }
}
