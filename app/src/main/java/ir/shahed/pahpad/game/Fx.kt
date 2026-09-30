package ir.shahed.pahpad.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
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
    /** Fx.SOLID, SMOKE, FIRE, VAPOR, GLOW */
    var style=0
    var alpha=1f
    var shade=0
}

/**
 * Fixed-size visual pools. No gameplay state or gameplay RNG is modified.
 *
 * 0.0.9: particles are soft sprites baked once at start-up (lumpy smoke puffs,
 * additive fire/glow), not flat discs. Fire uses screen blending so overlapping
 * flames bloom instead of turning into an opaque orange blob.
 */
class Fx(capacity: Int = 900) {
    companion object {
        const val SOLID=0; const val SMOKE=1; const val FIRE=2; const val VAPOR=3; const val GLOW=4
    }
    private val pool=Array(capacity) { Particle() }
    private val rnd=Random()
    private val proj=FloatArray(3)
    private class Blast {
        var x=0f;var y=0f;var z=0f;var power=1f;var age=1f
    }
    private val blasts=Array(8) { Blast() }
    private var nextBlast=0
    private var nextParticle=0
    private var tracerToggle=false

    // ------------------------------------------------------------ sprites
    private fun puff(rgb:Int,seed:Long,density:Float):Bitmap {
        val n=64;val bmp=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888)
        val c=Canvas(bmp);val p=Paint(Paint.ANTI_ALIAS_FLAG);val r=Random(seed)
        val base=rgb and 0xFFFFFF
        for(k in 0 until 6) {
            val cx=n/2f+(r.nextFloat()-.5f)*n*.28f;val cy=n/2f+(r.nextFloat()-.5f)*n*.28f
            val rad=n*(.20f+r.nextFloat()*.16f)
            val lift=if(cy<n/2f)1.10f else .86f // lit tops, shaded bellies
            val col=Theme.shade(0xFF000000.toInt() or base,lift) and 0xFFFFFF
            p.shader=RadialGradient(cx,cy,rad,intArrayOf((((density*150).toInt()) shl 24) or col,(((density*60).toInt()) shl 24) or col,col),
                floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP)
            c.drawCircle(cx,cy,rad,p)
        }
        p.shader=RadialGradient(n/2f,n/2f,n/2f,intArrayOf((((density*120).toInt()) shl 24) or base,(((density*40).toInt()) shl 24) or base,base),
            floatArrayOf(0f,.6f,1f),Shader.TileMode.CLAMP)
        c.drawCircle(n/2f,n/2f,n/2f,p)
        return bmp
    }
    private fun glow(core:Int,rim:Int):Bitmap {
        val n=64;val bmp=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader=RadialGradient(n/2f,n/2f,n/2f,
            intArrayOf(core,Theme.withAlpha(rim,.75f),Theme.withAlpha(rim,.22f),Theme.withAlpha(rim,0f)),
            floatArrayOf(0f,.18f,.5f,1f),Shader.TileMode.CLAMP)
        Canvas(bmp).drawCircle(n/2f,n/2f,n/2f,p)
        return bmp
    }
    private val smokeSprites=arrayOf(puff(0x2B2826,11L,1f),puff(0x55514D,12L,.95f),puff(0x8A8680,13L,.85f))
    private val vaporSprite=puff(0xD5DADF.toInt(),14L,.7f)
    private val fireSprites=arrayOf(glow(0xFFFFFBEA.toInt(),0xFFFFD27A.toInt()),glow(0xFFFFE7A8.toInt(),0xFFFF9A2E.toInt()),glow(0xFFFFB25A.toInt(),0xFFE0461A.toInt()))
    val glowRed=glow(0xFFFFE0E0.toInt(),0xFFFF2A2A.toInt())
    val glowGreen=glow(0xFFE6FFE9.toInt(),0xFF2BFF6A.toInt())
    val glowWhite=glow(0xFFFFFFFF.toInt(),0xFFBFD8FF.toInt())
    val glowAmber=fireSprites[1]

    private fun spawn():Particle? {
        repeat(pool.size) {
            val p=pool[nextParticle]
            nextParticle=(nextParticle+1)%pool.size
            if(!p.alive) { p.engineSmoke=false; p.style=SOLID; p.alpha=1f; p.shade=0; return p }
        }
        return null
    }
    fun clear() {
        for(p in pool)p.alive=false
        for(b in blasts)b.age=1f
    }
    fun clearEngineSmoke() { for(p in pool)if(p.engineSmoke)p.alive=false }

    /** Thin, low-alpha engine vapour. Kept separate from smoke at burning targets. */
    fun engineSmoke(x:Float,y:Float,z:Float,boost:Boolean) {
        val p=spawn()?:return
        p.alive=true;p.engineSmoke=true;p.style=VAPOR
        p.x=x;p.y=y;p.z=z
        p.vx=(rnd.nextFloat()-.5f)*.35f;p.vy=.22f;p.vz=(rnd.nextFloat()-.5f)*.35f
        p.maxLife=.55f+rnd.nextFloat()*.25f;p.life=p.maxLife
        p.size=if(boost).75f else .55f;p.growth=.9f
        p.gravity=0f;p.drag=.95f
        p.alpha=if(boost).42f else .30f
    }
    /** Burning wreckage, not engine exhaust. */
    fun smoke(x:Float,y:Float,z:Float,strength:Float=1f) {
        val p=spawn()?:return
        p.alive=true;p.style=SMOKE;p.shade=rnd.nextInt(2)
        p.x=x+(rnd.nextFloat()-.5f)*3f;p.y=y;p.z=z+(rnd.nextFloat()-.5f)*3f
        p.vx=rnd.nextFloat()*2f-1f;p.vy=2.2f+rnd.nextFloat()*2.4f;p.vz=rnd.nextFloat()*2f-1f
        p.maxLife=2.2f+rnd.nextFloat()*1.4f;p.life=p.maxLife
        p.size=1.8f*strength;p.growth=3.4f;p.gravity=0f;p.drag=.97f
        p.alpha=.62f
    }
    /** Licking flames on fresh wreckage. */
    fun flame(x:Float,y:Float,z:Float,strength:Float=1f) {
        val p=spawn()?:return
        p.alive=true;p.style=FIRE
        p.x=x+(rnd.nextFloat()-.5f)*4f*strength;p.y=y;p.z=z+(rnd.nextFloat()-.5f)*4f*strength
        p.vx=(rnd.nextFloat()-.5f)*1.5f;p.vy=3f+rnd.nextFloat()*3f;p.vz=(rnd.nextFloat()-.5f)*1.5f
        p.maxLife=.35f+rnd.nextFloat()*.3f;p.life=p.maxLife
        p.size=1.6f*strength;p.growth=1.2f;p.gravity=0f;p.drag=.96f;p.alpha=.8f
    }
    fun explosion(x:Float,y:Float,z:Float,power:Float=1f) {
        val strength=if(power.isFinite())power.coerceIn(.25f,3f) else 1f
        val blast=blasts[nextBlast];nextBlast=(nextBlast+1)%blasts.size
        blast.x=x;blast.y=y;blast.z=z;blast.power=strength;blast.age=0f
        // Fireball: fast, short-lived, additive.
        for(i in 0 until (26*strength).toInt().coerceIn(12,60)) {
            val p=spawn()?:break
            p.alive=true;p.style=FIRE;p.x=x;p.y=y;p.z=z
            val a=rnd.nextFloat()*PI.toFloat()*2f
            val up=rnd.nextFloat()
            val sp=(6f+rnd.nextFloat()*22f)*strength
            p.vx=cos(a)*sp*(1f-up*.5f);p.vz=sin(a)*sp*(1f-up*.5f);p.vy=up*sp*1.1f
            p.maxLife=.45f+rnd.nextFloat()*.55f;p.life=p.maxLife
            p.size=(3.2f+rnd.nextFloat()*4f)*strength;p.growth=6f*strength;p.gravity=2f;p.drag=.88f;p.alpha=.95f
        }
        // Hot sparks with gravity.
        for(i in 0 until (22*strength).toInt().coerceIn(10,48)) {
            val p=spawn()?:break
            p.alive=true;p.style=GLOW;p.x=x;p.y=y;p.z=z
            val a=rnd.nextFloat()*PI.toFloat()*2f
            val sp=(20f+rnd.nextFloat()*45f)*strength
            p.vx=cos(a)*sp;p.vz=sin(a)*sp;p.vy=(.3f+rnd.nextFloat())*sp*.7f
            p.maxLife=.6f+rnd.nextFloat()*.9f;p.life=p.maxLife
            p.size=.55f+rnd.nextFloat()*.5f;p.growth=0f;p.gravity=-26f;p.drag=.985f;p.alpha=1f
        }
        // Rolling black column.
        for(i in 0 until (14*strength).toInt().coerceIn(8,30)) {
            val p=spawn()?:break
            p.alive=true;p.style=SMOKE;p.shade=if(i%3==0)1 else 0
            p.x=x+rnd.nextFloat()*8f-4f;p.y=y+rnd.nextFloat()*5f;p.z=z+rnd.nextFloat()*8f-4f
            p.vx=rnd.nextFloat()*5f-2.5f;p.vy=6f+rnd.nextFloat()*10f;p.vz=rnd.nextFloat()*5f-2.5f
            p.maxLife=3f+rnd.nextFloat()*2.5f;p.life=p.maxLife
            p.size=(4.5f+rnd.nextFloat()*2f)*strength;p.growth=5.5f;p.gravity=0f;p.drag=.955f;p.alpha=.78f
        }
        // Dust skirt on the ground.
        for(i in 0 until (12*strength).toInt().coerceIn(6,26)) {
            val p=spawn()?:break
            p.alive=true;p.style=SMOKE;p.shade=2
            val a=rnd.nextFloat()*PI.toFloat()*2f
            val sp=(14f+rnd.nextFloat()*12f)*strength
            p.x=x;p.y=.8f;p.z=z;p.vx=cos(a)*sp;p.vz=sin(a)*sp;p.vy=1.2f
            p.maxLife=1.8f+rnd.nextFloat();p.life=p.maxLife
            p.size=3f*strength;p.growth=4f;p.gravity=0f;p.drag=.93f;p.alpha=.45f
        }
    }
    fun debris(x:Float,y:Float,z:Float) {
        repeat(10) {
            val p=spawn()?:return
            p.alive=true;p.style=SOLID;p.x=x;p.y=y;p.z=z
            p.vx=rnd.nextFloat()*30f-15f;p.vy=6f+rnd.nextFloat()*18f;p.vz=rnd.nextFloat()*30f-15f
            p.maxLife=1.4f+rnd.nextFloat();p.life=p.maxLife
            p.size=.7f+rnd.nextFloat()*.5f;p.growth=0f;p.gravity=-22f;p.drag=.99f;p.color=0xFF3C3935.toInt()
        }
    }
    /** SAM exhaust: a hot point plus (every other call) a lingering vapour trail. */
    fun tracer(x:Float,y:Float,z:Float) {
        val hot=spawn()?:return
        hot.alive=true;hot.style=FIRE;hot.x=x;hot.y=y;hot.z=z;hot.vx=0f;hot.vy=0f;hot.vz=0f
        hot.maxLife=.12f;hot.life=hot.maxLife;hot.size=1.3f;hot.growth=0f;hot.gravity=0f;hot.drag=1f;hot.alpha=.9f
        tracerToggle=!tracerToggle
        if(!tracerToggle)return
        val p=spawn()?:return
        p.alive=true;p.style=VAPOR;p.x=x;p.y=y;p.z=z
        p.vx=(rnd.nextFloat()-.5f)*.8f;p.vy=.4f;p.vz=(rnd.nextFloat()-.5f)*.8f
        p.maxLife=1.1f+rnd.nextFloat()*.4f;p.life=p.maxLife;p.size=.9f;p.growth=1.6f;p.gravity=0f;p.drag=.97f;p.alpha=.55f
    }
    fun spark(x:Float,y:Float,z:Float,color:Int) {
        val p=spawn()?:return
        p.alive=true;p.style=GLOW;p.x=x;p.y=y;p.z=z
        p.vx=rnd.nextFloat()*10f-5f;p.vy=rnd.nextFloat()*8f;p.vz=rnd.nextFloat()*10f-5f
        p.maxLife=.4f;p.life=p.maxLife;p.size=.7f;p.growth=.5f;p.gravity=-8f;p.drag=.95f;p.color=color;p.alpha=1f
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
    /** Ground shock rings; drawn with the other ground decals, before solids. */
    fun drawGround(c:Canvas,scene:Scene3D) {
        for(b in blasts) {
            if(b.age>=.8f)continue
            val t=b.age/.8f
            val radius=(3f+72f*(1f-(1f-t)*(1f-t)))*sqrt(b.power)
            val fade=(1f-t)*(1f-t)
            ring(c,scene,b.x,b.z,radius,Theme.withAlpha(0xFFFFE6B8.toInt(),fade*.7f),2.4f)
            ring(c,scene,b.x,b.z,radius*.82f,Theme.withAlpha(0xFF6B5A48.toInt(),fade*.45f),4f)
        }
    }
    private fun ring(c:Canvas,scene:Scene3D,x:Float,z:Float,radius:Float,color:Int,width:Float) {
        var px=x+radius;var pz=z
        for(i in 1..40) {
            val a=i*2f*PI.toFloat()/40
            val nx=x+cos(a)*radius;val nz=z+sin(a)*radius
            scene.line(px,.18f,pz,nx,.18f,nz,c,color,width)
            px=nx;pz=nz
        }
    }
    /** Queue a world-space point light sprite (nav lights, motor glow). */
    fun light(scene:Scene3D,x:Float,y:Float,z:Float,worldSize:Float,sprite:Bitmap,alpha:Float,minPx:Float=2.5f) {
        if(!scene.cam.project(x,y,z,proj))return
        val r=max(minPx,scene.cam.scaleAt(proj[2],worldSize)).coerceAtMost(scene.cam.height*.2f)
        scene.particle(proj[0],proj[1],proj[2]-.05f,r,Theme.withAlpha(0xFFFFFFFF.toInt(),alpha),sprite,true)
    }
    /** Enqueue effects before scene.flush; Canvas argument retained for callers. */
    @Suppress("UNUSED_PARAMETER")
    fun draw(canvas:Canvas,scene:Scene3D,fpv:Boolean=false) {
        val cam=scene.cam
        for(p in pool)if(p.alive&&!(fpv&&p.engineSmoke)) {
            if(!cam.project(p.x,p.y,p.z,proj))continue
            val sprite=p.style!=SOLID
            val r=(cam.scaleAt(proj[2],p.size)*(if(sprite)1.7f else 1f)).coerceAtMost(cam.height*.35f)
            if(r<.4f||proj[0]+r < -cam.width||proj[0]-r>cam.width*2f||proj[1]+r < -cam.height||proj[1]-r>cam.height*2f)continue
            val t=(p.life/p.maxLife).coerceIn(0f,1f)
            when(p.style) {
                SOLID -> scene.particle(proj[0],proj[1],proj[2],r,Theme.withAlpha(p.color,Color.alpha(p.color)/255f*min(1f,t*2f)))
                SMOKE -> {
                    // fade in quickly, out slowly; fog thins distant smoke
                    val a=p.alpha*min(1f,(1f-t)*6f)*t.pow(.6f)*(1f-scene.fogFactor(proj[2])*.8f)
                    scene.particle(proj[0],proj[1],proj[2],r,Theme.withAlpha(0xFFFFFFFF.toInt(),a),smokeSprites[p.shade.coerceIn(0,2)])
                }
                VAPOR -> scene.particle(proj[0],proj[1],proj[2],r,Theme.withAlpha(0xFFFFFFFF.toInt(),p.alpha*t*t),vaporSprite)
                FIRE -> {
                    val s=if(t>.66f)0 else if(t>.33f)1 else 2
                    scene.particle(proj[0],proj[1],proj[2],r,Theme.withAlpha(0xFFFFFFFF.toInt(),p.alpha*min(1f,t*1.6f)),fireSprites[s],true)
                }
                else -> scene.particle(proj[0],proj[1],proj[2],max(r,1.4f),Theme.withAlpha(0xFFFFFFFF.toInt(),t),fireSprites[0],true)
            }
        }
        for(b in blasts) {
            if(b.age>=.18f||!cam.project(b.x,b.y,b.z,proj))continue
            val k=1f-b.age/.18f
            val radius=cam.scaleAt(proj[2],(14f+b.age*160f)*sqrt(b.power)).coerceAtMost(cam.height*.45f)
            scene.particle(proj[0],proj[1],proj[2]-1f,radius*2.2f,Theme.withAlpha(0xFFFFFFFF.toInt(),k*.55f),fireSprites[1],true)
            scene.particle(proj[0],proj[1],proj[2]-1.1f,radius,Theme.withAlpha(0xFFFFFFFF.toInt(),k),fireSprites[0],true)
        }
    }
}
