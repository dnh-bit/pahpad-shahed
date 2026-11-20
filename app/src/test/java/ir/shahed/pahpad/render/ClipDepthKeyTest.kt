package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.game.Scene3D
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * سنجش پیوستگی «کلید عمق» وجه‌های بزرگِ مماس با دید (مثل دیواره‌ی ناو)
 * وقتی دوربین در امتدادشان می‌لغزد و صفحه‌ی نزدیک، بخشی از وجه را
 * می‌بُرد. کلید فعلی میانگین عمقِ چندضلعیِ **بریده‌شده** است؛ هر بار
 * که یک رأس از صفحه‌ی نزدیک رد شود، مجموعه‌ی رأس‌های چندضلعی عوض
 * می‌شود و میانگین می‌تواند بپرد — و پرش کلید یعنی جابه‌جایی ناگهانی
 * وجه در ترتیب رسم، یعنی همان «تغییر شکل» ناوها در فاصله‌ی نزدیک.
 *
 * این تست فقط کلیدها را چاپ می‌کند؛ تحلیل پرش بیرون انجام می‌شود.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class ClipDepthKeyTest {

    @Test
    fun hullSideKeyContinuity() {
        val scene = Scene3D()
        scene.enableDebugCapture(true)
        val bmp = Bitmap.createBitmap(800, 450, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // دیواره‌ی ناو: صفحه‌ی عمودی در x=+15، از z=-60 تا z=+90، ارتفاع ۱۴
        // عرشه: صفحه‌ی افقی در y=14 از x=-2 تا x=+15، همان بازه‌ی z
        for (step in 0..40) {
            val camZ = -20f + step * 1.5f
            val cam = scene.cam
            cam.x = 0f; cam.y = 17f; cam.z = camZ
            cam.yaw = 0f; cam.pitch = -0.16f; cam.roll = 0f
            cam.viewport(800f, 450f)
            scene.debugTag = 77
            scene.quad(
                15f, 0f, -60f, 15f, 0f, 90f, 15f, 14f, 90f, 15f, 14f, -60f,
                0xFF707880.toInt()
            )
            scene.debugTag = 78
            scene.quad(
                -2f, 14f, -60f, 15f, 14f, -60f, 15f, 14f, 90f, -2f, 14f, 90f,
                0xFF505860.toInt()
            )
            scene.debugTag = 0
            scene.flush(canvas)
            val faces = scene.debugFaces()
            val side = faces.firstOrNull { it.tag == 77 }
            val deck = faces.firstOrNull { it.tag == 78 }
            println(
                "KEY step=$step camZ=$camZ side=${side?.depth} deck=${deck?.depth} " +
                    "sideKey=${side?.let { it.depth - it.depthBias }} deckKey=${deck?.let { it.depth - it.depthBias }}"
            )
        }
        println("KEY done")
    }
}
