package ir.shahed.pahpad.game

import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.data.Levels
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk=[28])
class MissileObstacleTest {
    @Test fun projectileStopsAtBuildingEvenIfBothEndpointsOutside() {
        val mission = Mission(Levels.all.first(), SaveManager(RuntimeEnvironment.getApplication()), MissionMode.STORY, 1L)
        mission.world.props.clear()
        mission.world.props.add(Prop(0f, 0f, 10f, 20f, 2f, 0, 0f, PropKind.BUILDING))
        mission.world.missiles.add(Missile(0f, 10f, -10f, 0f, 0f, 400f))
        val update = Mission::class.java.getDeclaredMethod("updateMissiles", Float::class.javaPrimitiveType)
        update.isAccessible = true
        update.invoke(mission, .05f)
        assertTrue("Projectile crossed the building without being removed", mission.world.missiles.isEmpty())
    }
}
