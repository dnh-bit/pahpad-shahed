package checks
import ir.shahed.pahpad.game.*
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
fun main() {
 val mission=Mission(Levels.all.first(),SaveManager(),MissionMode.STORY,1L)
 mission.world.props.clear()
 mission.world.props.add(Prop(0f,0f,10f,20f,2f,0,0f,PropKind.BUILDING))
 mission.world.missiles.add(Missile(0f,10f,-10f,0f,0f,400f))
 val step=Mission::class.java.getDeclaredMethod("updateMissiles",Float::class.javaPrimitiveType)
 step.isAccessible=true; step.invoke(mission,.05f)
 check(mission.world.missiles.isEmpty()) {"Projectile crossed a thin building: both segment endpoints outside"}
 println("PASS real Mission projectile obstacle regression")
}
