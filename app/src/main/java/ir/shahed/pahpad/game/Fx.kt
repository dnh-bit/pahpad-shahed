package ir.shahed.pahpad.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import ir.shahed.pahpad.core.Theme
import java.util.Random
import kotlin.math.*

class Particle {
    var x=0f; var y=0f; var z=0f
    var vx=0f; var vy=0f; var vz=0f
    var life=0f; var maxLife=1f; var size=1f; var growth=0f
    var color=0; var gravity=0f; var drag=.98f; var alive=false
    var engineSmoke=false
    var depth=0f
}

/** Fixed-size visual pools. No gameplay state or gameplay RNG is modified. */
class Fx(capacity: Int = 420) {
    private val pool=Array(capacity) { Particle() }
    private val rnd=Random()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val proj=FloatArray(3)
    private val drawOrder=ArrayList<Particle>(capacity)
    private val depthOrder=Comparator<Particle> { a,b -> b.depth.compareTo(a.depth) }
    private class Blast {
        var x=0f;var y=0f;var z=0f;var power=1f;var age=1f
    }
    private val blasts=Array(8) { Blast() }
    private var nextBlast=0
    private var nextParticle=0
    private fun spawn():Particle? {
        repeat(pool.size) {
            val p=pool[nextParticle]
            nextParticle=(nextParticle+1)%pool.size
            if(!p.alive) { p.engineSmoke=false; return p }
        }
        return null
    }
    fun clear() {
        for(p in pool)p.alive=false
        for(b in blasts)b.age=1f
        drawOrder.clear()
    }
    fun clearEngineSmoke() { for(p in pool)if(p.engineSmoke)p.alive=false }

    /** Thin, low-alpha engine vapour. Kept separate from smoke at burning targets. */
    fun engineSmoke(x:Float,y:Float,z:Float,boost:Boolean) {
        val p=spawn()?:return
        p.alive=true;p.engineSmoke=true
        p.x=x;p.y=y;p.z=z
        p.vx=(rnd.nextFloat()-.5f)*.35f;p.vy=.22f;p.vz=(rnd.nextFloat()-.5f)*.35f
        p.maxLife=.42f+rnd.nextFloat()*.20f;p.life=p.maxLife
        p.size=if(boost).55f else .42f;p.growth=.55f
        p.gravity=0f;p.drag=.95f
        p.color=if(boost)0x66B9C3CC else 0x50B9C3CC
    }
    /** Burning wreckage, not engine exhaust. */
    fun smoke(x:Float,y:Float,z:Float,strength:Float=1f) {
        val p=spawn()?:return
        p.alive=true;p.x=x;p.y=y;p.z=z
        p.vx=rnd.nextFloat()*2f-1f;p.vy=.6f+rnd.nextFloat();p.vz=rnd.nextFloat()*2f-1f
        p.maxLife=1.1f+rnd.nextFloat()*.7f;p.life=p.maxLife
        p.size=1.4f*strength;p.growth=3.2f;p.gravity=0f;p.drag=.94f
        p.color=0x996E7475.toInt()
    }
    fun explosion(x:Float,y:Float,z:Float,power:Float=1f) {
        val strength=if(power.isFinite())power.coerceIn(.25f,3f) else 1f
        val blast=blasts[nextBlast];nextBlast=(nextBlast+1)%blasts.size
        blast.x=x;blast.y=y;blast.z=z;blast.power=strength;blast.age=0f
        for(i in 0 until (46*strength).toInt().coerceIn(18,90)) {
            val p=spawn()?:break
            p.alive=true;p.x=x;p.y=y;p.z=z
            val a=rnd.nextFloat()*PI.toFloat()*2f
            val sp=(14f+rnd.nextFloat()*40f)*strength
            p.vx=cos(a)*sp;p.vz=sin(a)*sp;p.vy=rnd.nextFloat()*1.2f*sp*.8f
            p.maxLife=.7f+rnd.nextFloat()*1.5f;p.life=p.maxLife
            p.size=(2.2f+rnd.nextFloat()*4.5f)*strength;p.growth=5f;p.gravity=-14f;p.drag=.9f
            p.color=when(rnd.nextInt(4)){0->Theme.AMBER;1->Theme.ORANGE;2->0xFFFFF1C9.toInt();else->0xFF6E6A66.toInt()}
        }
        for(i in 0 until (10*strength).toInt().coerceAtMost(24)) {
            val p=spawn()?:break
            p.alive=true;p.x=x+rnd.nextFloat()*8f-4f;p.y=y+rnd.nextFloat()*6f;p.z=z+rnd.nextFloat()*8f-4f
            p.vx=rnd.nextFloat()*4f-2f;p.vy=7f+rnd.nextFloat()*9f;p.vz=rnd.nextFloat()*4f-2f
            p.maxLife=2.4f+rnd.nextFloat()*2f;p.life=p.maxLife
            p.size=5f*strength;p.growth=7f;p.gravity=0f;p.drag=.95f;p.color=0xC04A4A4A.toInt()
        }
    }
    fun debris(x:Float,y:Float,z:Float) {
        repeat(8) {
            val p=spawn()?:return
            p.alive=true;p.x=x;p.y=y;p.z=z
            p.vx=rnd.nextFloat()*30f-15f;p.vy=6f+rnd.nextFloat()*18f;p.vz=rnd.nextFloat()*30f-15f
            p.maxLife=1.4f+rnd.nextFloat();p.life=p.maxLife
            p.size=.9f;p.growth=0f;p.gravity=-22f;p.drag=.99f;p.color=0xFF8C8577.toInt()
        }
    }
    fun tracer(x:Float,y:Float,z:Float) {
        val p=spawn()?:return
        p.alive=true;p.x=x;p.y=y;p.z=z;p.vx=0f;p.vy=0f;p.vz=0f
        p.maxLife=.35f;p.life=p.maxLife;p.size=1.1f;p.growth=.4f;p.gravity=0f;p.drag=1f;p.color=Theme.AMBER
    }
    fun spark(x:Float,y:Float,z:Float,color:Int) {
        val p=spawn()?:return
        p.alive=true;p.x=x;p.y=y;p.z=z
        p.vx=rnd.nextFloat()*10f-5f;p.vy=rnd.nextFloat()*8f;p.vz=rnd.nextFloat()*10f-5f
        p.maxLife=.5f;p.life=p.maxLife;p.size=.8f;p.growth=.6f;p.gravity=-8f;p.drag=.95f;p.color=color
    }
    fun update(dt:Float) {
        for(b in blasts)if(b.age<1f)b.age+=dt
        for(p in pool) {
            if(!p.alive)continue
            p.life-=dt
            if(p.life<=0f){p.alive=false;continue}
            p.vy+=p.gravity*dt
            val drag=p.drag.toDouble().pow((dt*60f).toDouble()).toFloat()
            p.vx*=drag;p.vy*=drag;p.vz*=drag
            p.x+=p.vx*dt;p.y+=p.vy*dt;p.z+=p.vz*dt;p.size+=p.growth*dt
            if(p.y<0f){p.y=0f;p.vy=-p.vy*.3f}
        }
    }
    /** Draw before solids so the ground wave does not paint over buildings. */
    fun drawGround(c:Canvas,scene:Scene3D) {
        for(b in blasts) {
            if(b.age>=.65f)continue
            val t=b.age/.65f
            val radius=(3f+65f*(1f-(1f-t)*(1f-t)))*sqrt(b.power)
            val color=Theme.withAlpha(0xFFF5D6A0.toInt(),(1f-t)*(1f-t)*.65f)
            var px=b.x+radius;var pz=b.z
            for(i in 1..40) {
                val a=i*2f*PI.toFloat()/40
                val nx=b.x+cos(a)*radius;val nz=b.z+sin(a)*radius
                scene.line(px,.18f,pz,nx,.18f,nz,c,color,1.6f)
                px=nx;pz=nz
            }
        }
    }
    fun draw(canvas:Canvas,scene:Scene3D,fpv:Boolean=false) {
        drawOrder.clear()
        for(p in pool)if(p.alive&&!(fpv&&p.engineSmoke)) {
            if(!scene.cam.project(p.x,p.y,p.z,proj))continue
            p.depth=proj[2];drawOrder.add(p)
        }
        drawOrder.sortWith(depthOrder)
        for(p in drawOrder) {
            if(!scene.cam.project(p.x,p.y,p.z,proj))continue
            val r=scene.cam.scaleAt(proj[2],p.size).coerceAtMost(scene.cam.height*.3f)
            if(r<.4f||proj[0]+r < -scene.cam.width||proj[0]-r>scene.cam.width*2f||proj[1]+r < -scene.cam.height||proj[1]-r>scene.cam.height*2f)continue
            val t=(p.life/p.maxLife).coerceIn(0f,1f)
            val fade=if(p.engineSmoke)t*t else t*.9f
            paint.color=Theme.withAlpha(p.color,Color.alpha(p.color)/255f*fade)
            canvas.drawCircle(proj[0],proj[1],r,paint)
        }
        for(b in blasts) {
            if(b.age>=.05f||!scene.cam.project(b.x,b.y,b.z,proj))continue
            val radius=scene.cam.scaleAt(proj[2],(10f+b.age*120f)*sqrt(b.power)).coerceAtMost(scene.cam.height*.3f)
            paint.color=Theme.withAlpha(0xFFFFDCA0.toInt(),(1f-b.age/.05f)*.3f)
            canvas.drawCircle(proj[0],proj[1],radius*1.8f,paint)
            paint.color=Theme.withAlpha(0xFFFFF1D0.toInt(),(1f-b.age/.05f)*.85f)
            canvas.drawCircle(proj[0],proj[1],radius,paint)
        }
    }
}
