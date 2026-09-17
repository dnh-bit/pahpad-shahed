package checks.render

import android.graphics.*
import ir.shahed.pahpad.game.*
import kotlin.math.abs

fun main() {
    var failures=0
    fun test(name:String, body:()->Unit) {
        try { body(); println("PASS $name") }
        catch(e:Throwable) { failures++; println("FAIL $name: ${e.message}") }
    }
    test("near-clipped opaque quad keeps texture and perspective UV") {
        val scene=Scene3D();scene.cam.viewport(800f,600f);scene.begin()
        val texture=Bitmap(100,100)
        // Planar parallelogram crossing both near and camera planes. x,y remain visible.
        val p=arrayOf(floatArrayOf(-.1f,.1f,-.2f),floatArrayOf(.1f,.1f,2f),floatArrayOf(.1f,-.1f,2f),floatArrayOf(-.1f,-.1f,-.2f))
        scene.quad(p[0][0],p[0][1],p[0][2],p[1][0],p[1][1],p[1][2],p[2][0],p[2][1],p[2][2],p[3][0],p[3][1],p[3][2],-1,texture=texture)
        val canvas=Canvas();scene.flush(canvas)
        val draw=canvas.draws.singleOrNull { it.matrix!=null }
        check(draw!=null) { "clipped quad lost its bitmap" }
        check(draw.points.all { it.first.isFinite() && it.second.isFinite() })
        val m=draw.matrix!!
        for(u in listOf(40f,55f,90f)) for(v in listOf(10f,50f,85f)) {
            val x=-.1f+.2f*u/100f;val y=.1f-.2f*v/100f;val z=-.2f+2.2f*u/100f
            val w=m[6]*u+m[7]*v+m[8]
            val sx=(m[0]*u+m[1]*v+m[2])/w;val sy=(m[3]*u+m[4]*v+m[5])/w
            check(abs(sx-(scene.cam.screenCx+scene.cam.focal*x/z))<.01f) { "U projection stretched at $u,$v" }
            check(abs(sy-(scene.cam.screenCy-scene.cam.focal*y/z))<.01f) { "V projection stretched at $u,$v" }
        }
    }
    test("transparent quad survives near-plane sweep without opaque fallback") {
        val scene=Scene3D();scene.cam.viewport(800f,600f)
        val bitmap=Bitmap(100,100)
        for(z in listOf(-.2f,0f,.3f,.6f,.602f,.61f,1f)) {
            scene.begin()
            scene.imagePlane(arrayOf(floatArrayOf(-.1f,.1f,z),floatArrayOf(.1f,.1f,z+2f),floatArrayOf(.1f,-.1f,z+2f),floatArrayOf(-.1f,-.1f,z)),bitmap,.4f)
            val c=Canvas();scene.flush(c)
            check(c.draws.size==1 && c.draws[0].matrix!=null) { "sprite missing or opaque fallback at z=$z" }
            check(c.draws[0].alpha==102) { "sprite opacity lost" }
            check(c.draws[0].matrix!!.all { it.isFinite() })
        }
    }
    test("one-corner clipping keeps a five-vertex textured polygon") {
        val scene=Scene3D();scene.cam.viewport(800f,600f);scene.begin()
        scene.imagePlane(arrayOf(floatArrayOf(-.1f,.1f,0f),floatArrayOf(.1f,.1f,1f),floatArrayOf(.1f,-.1f,2f),floatArrayOf(-.1f,-.1f,1f)),Bitmap(64,64))
        val c=Canvas();scene.flush(c)
        check(c.draws.size==1 && c.draws[0].points.size==5 && c.draws[0].matrix!=null) { "clipping must not remap first four of five vertices" }
    }
    test("fully hidden and degenerate transparent quads are culled") {
        val scene=Scene3D();scene.cam.viewport(800f,600f);scene.begin()
        val bitmap=Bitmap(100,100)
        scene.imagePlane(arrayOf(floatArrayOf(-.1f,.1f,-2f),floatArrayOf(.1f,.1f,0f),floatArrayOf(.1f,-.1f,0f),floatArrayOf(-.1f,-.1f,-2f)),bitmap)
        scene.imagePlane(Array(4){floatArrayOf(0f,0f,2f)},bitmap)
        val c=Canvas();scene.flush(c);check(c.draws.isEmpty())
    }
    test("unclipped trapezoid matches four-corner homography at interior UVs") {
        val scene=Scene3D();scene.cam.viewport(800f,600f);scene.begin()
        // Non-parallelogram in plane z=2+x, requiring nonzero g/h.
        val p=arrayOf(floatArrayOf(-.5f,.5f,1.5f),floatArrayOf(.5f,.5f,2.5f),floatArrayOf(.25f,-.5f,2.25f),floatArrayOf(-.25f,-.5f,1.75f))
        scene.imagePlane(p,Bitmap(100,100));val c=Canvas();scene.flush(c)
        val actual=c.draws.single().matrix!!
        val dst=FloatArray(8);val projected=FloatArray(3)
        for(i in 0..3){scene.cam.project(p[i][0],p[i][1],p[i][2],projected);dst[i*2]=projected[0];dst[i*2+1]=projected[1]}
        val reference=Matrix();check(reference.setPolyToPoly(floatArrayOf(0f,0f,100f,0f,100f,100f,0f,100f),0,dst,0,4))
        val expected=FloatArray(9);reference.getValues(expected)
        fun project(m:FloatArray,u:Float,v:Float,k:Int)=(m[k*3]*u+m[k*3+1]*v+m[k*3+2])/(m[6]*u+m[7]*v+m[8])
        for(u in listOf(0f,15f,50f,100f))for(v in listOf(0f,35f,100f))for(k in 0..1)
            check(abs(project(actual,u,v,k)-project(expected,u,v,k))<.001f)
    }
    test("particles queue between far and near solids instead of overlaying") {
        val scene=Scene3D();scene.cam.viewport(800f,600f);scene.begin()
        val fx=Fx();fx.tracer(0f,0f,20f);fx.tracer(0f,0f,5f)
        scene.quad(-1f,1f,10f,1f,1f,10f,1f,-1f,10f,-1f,-1f,10f,-1)
        val canvas=Canvas();fx.draw(canvas,scene)
        check(canvas.draws.isEmpty()) { "Fx draws immediately outside shared queue" }
        scene.flush(canvas)
        check(canvas.draws.map { it.kind }==listOf("circle","path","circle")) { "incorrect particle/solid depth order" }
        canvas.draws.clear();scene.flush(canvas);check(canvas.draws.isEmpty())
    }
    check(failures==0) { "$failures render regression(s) failed" }
}
