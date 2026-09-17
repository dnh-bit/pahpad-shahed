package checks
import ir.shahed.pahpad.game.*
import ir.shahed.pahpad.data.*
import kotlin.math.*
fun main() {
 var checked=0
 for(env in listOf(Env.URBAN,Env.SPECIAL)) for(seed in 1L..20L) {
  val world=World(LevelDef(3,1,"test","test",1,env,1800f,newChallenge="",briefing=""),seed)
  for(p in world.props) {
   val hx=abs(cos(p.rot))*p.w/2+abs(sin(p.rot))*p.d/2
   val hz=abs(sin(p.rot))*p.w/2+abs(cos(p.rot))*p.d/2
   if(abs(p.x)-hx<=400) {
    val row=round((p.z-220f)/95f-.5f).toInt()
    check(abs(p.z-(220f+(row+.5f)*95f))-hz-8>=2.999f) {"Crossroad intersection: $env seed=$seed x=${p.x} z=${p.z}"}
   }
   for(col in -4..4) check(abs(p.x-(col*78f+39f))-hx-8>=2.999f) {"Street intersection: $env seed=$seed x=${p.x}"}
   checked++
  }
 }
 println("PASS real Kotlin footprints: $checked props across 40 worlds")
}
