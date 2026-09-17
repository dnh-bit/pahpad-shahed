package ir.shahed.pahpad.core

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE, sdk=[28])
class FrameClockTest {
    class Probe(game: MainActivity): BaseScreen(game) {
        var elapsed = 0f
        var biggestStep = 0f
        override fun update(dt: Float) { elapsed += dt; biggestStep = maxOf(biggestStep, dt) }
        override fun render(c: Canvas) {}
        fun sample() { onDraw(Canvas(Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888))) }
    }
    @Test fun slowFramePreservesElapsedTimeUsingSmallSteps() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val p = Probe(activity)
        p.layout(0,0,8,8)
        p.startLoop()
        val last = BaseScreen::class.java.getDeclaredField("lastFrame").apply { isAccessible=true }
        // Drive two controlled frames: first primes lastFrame, second is a 100ms frame.
        last.setLong(p, System.nanoTime()-16_000_000L)
        p.sample()
        last.setLong(p, System.nanoTime()-100_000_000L)
        p.sample()
        // CI/JIT can stretch the wall clock between the two calls; the invariant is that
        // whatever elapsed time was measured is preserved (>= the 100ms lower bound) and
        // the physics never takes a step above 50ms.
        assertTrue("100ms of play must not be discarded at 10fps (elapsed=${p.elapsed})", p.elapsed>=.1f)
        assertTrue("physics step must stay bounded (step=${p.biggestStep})", p.biggestStep<=.0501f)
    }
}
