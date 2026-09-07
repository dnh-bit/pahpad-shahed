package ir.shahed.pahpad.game

import android.content.Context
import android.graphics.Canvas
import ir.shahed.pahpad.core.Gfx
import ir.shahed.pahpad.core.Theme
import kotlin.math.*

/** Native Canvas visual upgrade. Existing levels, controls and saves stay compatible. */
class WorldRenderer(private val gfxContext:Context) {
    val scene=Scene3D();val fx=Fx()
    private val art=SceneryArt(gfxContext);private val drone=DroneArt(gfxContext)
    private val proj=FloatArray(3)
    private var smokeTime=-1f
    private fun material(building:Boolean=true){
        scene.wallTexture=Gfx.get(gfxContext,if(building)"material_wall" else "material_concrete")
        scene.roofTexture=Gfx.get(gfxContext,"material_roof")
    }
    private fun plain(){scene.wallTexture=null;scene.roofTexture=null}
    fun draw(c:Canvas,mission:Mission,w:Float,h:Float){
        val world=mission.world
        scene.fogStart=300f;scene.fogEnd=1650f;scene.begin()
        art.draw(c,scene,world,w,h)
        art.roads(scene,world)
        val camX=scene.cam.x;val camZ=scene.cam.z
        val maxD2=1600f*1600f
        for(p in world.props){
            val dx=p.x-camX;val dz=p.z-camZ;val d2=dx*dx+dz*dz
            if(d2>maxD2)continue
            if(d2<550f*550f)art.shadow(c,scene,p.x+p.h*.24f,p.z+p.h*.14f,p.w*.8f,p.d*.75f,.65f)
            when(p.kind){
                PropKind.TREE,PropKind.ROCK->{
                    val tree=p.kind==PropKind.TREE
                    val file=if(tree)if(p.h%2f<1f)"prop_tree1" else "prop_tree2" else if(p.w%2f<1f)"prop_rock1" else "prop_rock2"
                    val bitmap=Gfx.get(gfxContext,file)
                    if(bitmap!=null){
                        // Project using real camera depth, not the former fixed screen size.
                        val width=if(tree)max(p.w,p.h*.95f) else p.w
                        scene.billboard(p.x,0f,p.z,width,p.h,bitmap)
                    }else{plain();scene.box(p.x,0f,p.z,p.w,p.h,p.d,p.color,p.rot)}
                }
                PropKind.SHIP->ship(p.x,p.z,p.w,p.h,p.d,p.rot,false)
                PropKind.TOWER_MAST->{plain();scene.box(p.x,0f,p.z,p.w*.28f,p.h,p.d*.28f,p.color,p.rot)}
                PropKind.BUILDING->{
                    material();scene.box(p.x,0f,p.z,p.w,p.h,p.d,0xFF9E9E90.toInt(),p.rot)
                    if(d2<500f*500f){
                        material(false)
                        scene.box(p.x-p.w*.16f,p.h,p.z,p.w*.2f,2.1f,p.d*.2f,0xFF9A9E95.toInt(),p.rot)
                        scene.box(p.x+p.w*.24f,p.h,p.z+p.d*.18f,p.w*.13f,1.6f,p.d*.3f,0xFF777E77.toInt(),p.rot)
                    }
                }
            }
        }
        for(r in world.radars)zone(c,r.x,r.z,r.radius,Theme.BLUE)
        for(e in world.ewZones)zone(c,e.x,e.z,e.radius,Theme.VIOLET)
        plain()
        for(a in world.aaSites){
            if(!a.alive)continue
            val dx=a.x-camX;val dz=a.z-camZ;if(dx*dx+dz*dz>maxD2)continue
            zone(c,a.x,a.z,a.range,Theme.RED)
            scene.box(a.x,0f,a.z,9f,3.5f,12f,0xFF50584B.toInt())
            scene.box(a.x,3.5f,a.z,5.5f,3f,6f,0xFF69705B.toInt(),a.barrelYaw)
            val bx=sin(a.barrelYaw);val bz=cos(a.barrelYaw)
            scene.box(a.x+bx*4f,5.2f,a.z+bz*4f,1.2f,1.2f,8f,0xFF7E8370.toInt(),a.barrelYaw)
            if(a.flash>0f)fx.spark(a.x+bx*8f,6f,a.z+bz*8f,Theme.AMBER)
        }
        val emitSmoke=mission.elapsed-smokeTime>.075f||mission.elapsed<smokeTime
        if(emitSmoke)smokeTime=mission.elapsed
        for(t in world.targets){
            val dx=t.x-camX;val dz=t.z-camZ;if(dx*dx+dz*dz>maxD2)continue
            target(t)
            if(emitSmoke&&t.destroyed&&t.burn<6f)fx.smoke(t.x,t.h*.6f,t.z,2f)
        }
        plain()
        for(b in world.bonuses)if(!b.collected)scene.box(b.x,b.y,b.z,4.5f,4.5f,4.5f,Theme.AMBER,b.spin)
        for(m in world.missiles)scene.box(m.x,m.y,m.z,1.2f,1.2f,4f,Theme.AMBER)
        if(!mission.fpv&&mission.phase!=Mission.Phase.ENDED){
            val alt=mission.y.coerceAtLeast(0f)
            if(alt<220f)art.shadow(c,scene,mission.x+alt*.3f,mission.z+alt*.18f,7f+alt*.025f,7f+alt*.025f,(.65f-alt*.0025f).coerceAtLeast(.08f))
            drone.draw(scene,mission)
        }
        scene.flush(c)
        fx.draw(c,scene)
    }
    private fun zone(c:Canvas,x:Float,z:Float,radius:Float,color:Int){
        val segments=48;var px=x+radius;var pz=z
        for(i in 1..segments){val a=i*2f*PI.toFloat()/segments;val nx=x+cos(a)*radius;val nz=z+sin(a)*radius
            scene.line(px,.6f,pz,nx,.6f,nz,c,Theme.withAlpha(color,.36f),1.4f);px=nx;pz=nz}
    }
    private fun ship(x:Float,z:Float,w:Float,h:Float,d:Float,rot:Float,destroyed:Boolean){
        material(false)
        val hull=if(destroyed)0xFF4C4940.toInt() else 0xFF687B7E.toInt()
        scene.box(x,0f,z,w,h*.45f,d,hull,rot)
        scene.box(x,h*.45f,z,w*.6f,h*.65f,d*.28f,0xFFB8BDB3.toInt(),rot)
        plain()
        scene.box(x,h*1.1f,z,1.4f,h*.6f,1.4f,0xFF696F69.toInt(),rot)
        scene.box(x,h*1.5f,z,w*.45f,.5f,1f,0xFFB7BDB4.toInt(),rot)
        // Dark bridge glazing adds readable scale instead of a featureless cube.
        scene.box(x,h*.82f,z,w*.64f,h*.12f,d*.29f,0xFF364F59.toInt(),rot)
    }
    private fun target(t:Target){
        val base=if(t.destroyed)0xFF514B40.toInt() else 0xFFA6A28F.toInt()
        val accent=if(t.destroyed)0xFF3C3931.toInt() else 0xFF817E6D.toInt()
        material(t.shape==TargetShape.BUILDING||t.shape==TargetShape.TOWER)
        when(t.shape){
            TargetShape.BUNKER->{
                scene.box(t.x,0f,t.z,t.w,t.h,t.d,base,t.heading)
                scene.box(t.x,t.h,t.z,t.w*.6f,2.5f,t.d*.6f,accent,t.heading)
                plain();scene.box(t.x,0f,t.z-t.d*.505f,t.w*.25f,t.h*.6f,.25f,0xFF383E38.toInt())
            }
            TargetShape.BUILDING->{scene.box(t.x,0f,t.z,t.w,t.h,t.d,base,t.heading);material(false);scene.box(t.x,t.h,t.z,t.w*.45f,4f,t.d*.45f,accent,t.heading)}
            TargetShape.TOWER->{scene.box(t.x,0f,t.z,t.w,t.h*.85f,t.d,base,t.heading);scene.box(t.x,t.h*.85f,t.z,t.w*1.4f,t.h*.15f,t.d*1.4f,accent,t.heading)}
            TargetShape.SHIP->ship(t.x,t.z,t.w,t.h,t.d,t.heading,t.destroyed)
            TargetShape.VEHICLE->{plain();scene.box(t.x,0f,t.z,t.w,t.h*.6f,t.d,0xFF657052.toInt(),t.heading);scene.box(t.x,t.h*.6f,t.z,t.w*.85f,t.h*.5f,t.d*.45f,accent,t.heading)}
            TargetShape.BRIDGE->{
                scene.box(t.x,t.h*.7f,t.z,t.w,3.5f,t.d,base,t.heading)
                for(i in -2..2)scene.box(t.x+i*t.w*.2f,0f,t.z,5f,t.h*.7f,5f,Theme.shade(base,.85f),t.heading)
                plain();scene.box(t.x,t.h*.7f+3.5f,t.z,t.w,.2f,t.d*.75f,0xFF606866.toInt(),t.heading)
            }
            TargetShape.ANTENNA->{
                plain();scene.box(t.x,0f,t.z,t.w*.35f,t.h,t.d*.35f,base,t.heading)
                scene.box(t.x,t.h*.55f,t.z,t.w*1.6f,1.5f,t.d*.2f,accent,t.heading)
                scene.box(t.x,t.h*.8f,t.z,t.w*1.2f,1.5f,t.d*.2f,accent,t.heading)
                scene.box(t.x,0f,t.z,t.w*1.4f,3f,t.d*1.4f,Theme.shade(base,.8f),t.heading)
            }
            TargetShape.TRAIN->{
                for(i in -2..2){val off=i*30f;val ox=sin(t.heading)*off;val oz=cos(t.heading)*off
                    scene.box(t.x+ox,0f,t.z+oz,t.w,t.h,26f,if(i==0)accent else 0xFF72796F.toInt(),t.heading)}
            }
            TargetShape.TUNNEL->{scene.box(t.x,0f,t.z,t.w*2.2f,t.h*1.8f,t.d*2.2f,0xFF8D8469.toInt(),t.heading);plain();scene.box(t.x,0f,t.z-t.d*1.05f,t.w,t.h,2f,0xFF292F2A.toInt(),t.heading)}
        }
    }
    /** Shared unrolled coordinates: retains the original HUD contract. */
    fun project(x:Float,y:Float,z:Float):FloatArray?=if(scene.cam.project(x,y,z,proj))proj else null
}
