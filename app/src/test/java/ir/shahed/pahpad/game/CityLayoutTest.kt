package ir.shahed.pahpad.game
import ir.shahed.pahpad.data.Env
import ir.shahed.pahpad.data.LevelDef
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE,sdk=[28])
class CityLayoutTest {
 @Test fun allGeneratedFootprintsClearBothRoadAxesIncludingRotatedScenery() {
  for(env in listOf(Env.URBAN, Env.SPECIAL)) for(seed in 1L..20L) {
   val level=LevelDef(3,1,"test","test",1,env,1800f,newChallenge="",briefing="")
   val world=World(level,seed)
   for(p in world.props) {
    val hx=abs(cos(p.rot))*p.w/2+abs(sin(p.rot))*p.d/2
    val hz=abs(sin(p.rot))*p.w/2+abs(cos(p.rot))*p.d/2
    if(abs(p.x)-hx<=400f) {
     val row=round((p.z-220f)/95f-.5f).toInt()
     val gap=abs(p.z-(220f+(row+.5f)*95f))-hz-8f
     assertTrue("crossroad env=$env seed=$seed at ${p.x},${p.z}: gap=$gap",gap>=2.999f)
    }
    for(col in -4..4) {
     val road=col*78f+39f
     val gap=abs(p.x-road)-hx-8f
     assertTrue("longitudinal env=$env seed=$seed at ${p.x},${p.z}: gap=$gap",gap>=2.999f)
    }
   }
  }
 }
}
