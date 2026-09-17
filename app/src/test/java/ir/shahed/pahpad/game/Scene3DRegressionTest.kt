package ir.shahed.pahpad.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Scene3DRegressionTest {
    private fun scene()=Scene3D().apply { cam.viewport(800f,600f);begin() }
    @Suppress("UNCHECKED_CAST")
    private fun faces(scene:Scene3D):List<Face> {
        val field=Scene3D::class.java.getDeclaredField("order").apply { isAccessible=true }
        return field.get(scene) as List<Face>
    }

    @Test fun clippedTransparentTextureRetainsPerspectiveAndOpacity() {
        val s=scene();val bitmap=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)
        for(z in listOf(-.2f,0f,.3f,.6f,.602f,.61f,1f)) {
            s.begin()
            s.imagePlane(arrayOf(floatArrayOf(-.1f,.1f,z),floatArrayOf(.1f,.1f,z+2f),floatArrayOf(.1f,-.1f,z+2f),floatArrayOf(-.1f,-.1f,z)),bitmap,.4f)
            assertEquals("visible sprite must not disappear at $z",1,faces(s).size)
            val face=faces(s).single()
            assertSame(bitmap,face.bitmap);assertTrue(face.transparent);assertEquals(.4f,face.opacity,0f)
            // The stored 3x3 maps bitmap pixels to homogeneous screen coordinates:
            // screen = (M[0..5] . p) / (M[6]*x + M[7]*y + M[8]). Android's affine
            // Matrix cannot represent this; Scene3D applies the division itself.
            val m=face.textureMatrix
            for(i in 0..1) {
                val u=if(i==0).6f else .9f;val v=if(i==0).25f else .75f
                val x=u*100f;val y=v*100f
                val w=m[6]*x+m[7]*y+m[8]
                val px=(m[0]*x+m[1]*y+m[2])/w
                val py=(m[3]*x+m[4]*y+m[5])/w
                val depth=z+2f*u
                assertEquals(s.cam.screenCx+s.cam.focal*(-.1f+.2f*u)/depth,px,.01f)
                assertEquals(s.cam.screenCy-s.cam.focal*(.1f-.2f*v)/depth,py,.01f)
            }
        }
    }

    @Test fun texturedFaceRasterizesProjectiveMeshGrid() {
        val s=scene();val bitmap=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)
        s.begin()
        s.imagePlane(arrayOf(floatArrayOf(-.1f,.1f,0f),floatArrayOf(.1f,.1f,2f),floatArrayOf(.1f,-.1f,2f),floatArrayOf(-.1f,-.1f,0f)),bitmap,.5f)
        val face=faces(s).single()
        s.flush(Canvas(Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)))
        // Grid is placed in bitmap space; corners carry the projective screen positions.
        val steps=6; val n=(steps+1)*(steps+1)
        assertEquals(n*2,face.dst.size/2*2)
        // Bitmap corner (0,0) must project exactly to the far top-left polygon vertex.
        val m=face.textureMatrix
        val w=m[8]
        assertEquals((m[2])/w,face.dst[0],.01f)
        assertEquals((m[5])/w,face.dst[1],.01f)
        // Bitmap corner (100,100) lands on the projected far bottom-right vertex.
        val w2=m[6]*100f+m[7]*100f+m[8]
        assertEquals((m[0]*100f+m[1]*100f+m[2])/w2,face.dst[(n-1)*2],.01f)
        assertEquals((m[3]*100f+m[4]*100f+m[5])/w2,face.dst[(n-1)*2+1],.01f)
        // UV grid stays the uniform bitmap lattice (0..width, 0..height).
        assertEquals(100f,face.uv[(n-1)*2],.001f)
        assertEquals(100f,face.uv[(n-1)*2+1],.001f)
        assertEquals(0f,face.uv[0],.001f)
    }

    @Test fun clippedOpaqueTextureDoesNotFallBackToFlatColor() {
        val s=scene();val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        s.quad(-.1f,.1f,0f,.1f,.1f,1f,.1f,-.1f,2f,-.1f,-.1f,1f,Color.WHITE,texture=bitmap)
        val face=faces(s).single()
        assertEquals(5,face.count);assertSame(bitmap,face.bitmap);assertFalse(face.transparent)
    }

    private class RecordingCanvas:Canvas() {
        val calls=ArrayList<String>()
        override fun drawCircle(cx:Float,cy:Float,radius:Float,paint:android.graphics.Paint) { calls.add("circle") }
        override fun drawPath(path:android.graphics.Path,paint:android.graphics.Paint) { calls.add("path") }
    }

    @Test fun particlesAndSolidsShareBackToFrontDrawOrder() {
        val s=scene();val fx=Fx();val canvas=RecordingCanvas()
        fx.tracer(0f,0f,20f);fx.tracer(0f,0f,5f)
        s.quad(-1f,1f,10f,1f,1f,10f,1f,-1f,10f,-1f,-1f,10f,Color.WHITE)
        fx.draw(canvas,s)
        assertTrue("Fx must enqueue, not immediately paint over solids",canvas.calls.isEmpty())
        assertEquals(3,faces(s).size)
        s.flush(canvas)
        assertEquals(listOf("circle","path","circle"),canvas.calls)
        assertTrue(faces(s).isEmpty())
    }

    @Test fun fpvSuppressesOnlyEngineSmokeAndBlastAlsoQueues() {
        val s=scene();val fx=Fx();val canvas=RecordingCanvas()
        fx.engineSmoke(0f,0f,20f,false);fx.smoke(0f,0f,20f)
        fx.draw(canvas,s,true)
        assertEquals(1,faces(s).size)
        s.begin();fx.clear();fx.explosion(0f,0f,20f)
        fx.draw(canvas,s)
        assertTrue(canvas.calls.isEmpty())
        assertTrue(faces(s).size>2)
        assertTrue(faces(s).all { it.radius>0f })
    }
}
