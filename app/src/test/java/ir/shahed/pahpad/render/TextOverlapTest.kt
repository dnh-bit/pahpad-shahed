package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.core.Ui
import ir.shahed.pahpad.screens.ChapterSelectScreen
import ir.shahed.pahpad.screens.LeaderboardScreen
import ir.shahed.pahpad.screens.LevelSelectScreen
import ir.shahed.pahpad.screens.MainMenuScreen
import ir.shahed.pahpad.screens.SettingsScreen
import ir.shahed.pahpad.screens.ShopScreen
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * تشخیص خودکار همپوشانی متن.
 *
 * هر متن رسم‌شده مستطیل خودش را ثبت می‌کند؛ این تست همهٔ جفت‌ها را مقایسه
 * می‌کند و هر همپوشانی معناداری را با متن دقیق گزارش می‌دهد.
 *
 * آستانه: همپوشانی کمتر از ۱dp در هر بُعد نادیده گرفته می‌شود، چون متن‌های
 * فارسی با اعراب و کشیدگی طبیعتاً چند درصد بیرون می‌زنند.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class TextOverlapTest {

    private val sizes = listOf(
        1280 to 720,
        1920 to 1080,
        2340 to 1080,
    )

    private fun renderAndCollect(activity: MainActivity, screen: BaseScreen, w: Int, h: Int): List<Ui.TextBox> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        screen.layout(0, 0, w, h)
        screen.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
        )
        screen.layout(0, 0, w, h)

        screen.ui.textBoxes.clear()
        screen.ui.recordTextBoxes = true
        val draw = BaseScreen::class.java.getDeclaredMethod("onDraw", Canvas::class.java)
        draw.isAccessible = true
        draw.invoke(screen, c)
        val boxes = ArrayList(screen.ui.textBoxes)
        screen.ui.recordTextBoxes = false
        return boxes
    }

    private fun overlap(a: Ui.TextBox, b: Ui.TextBox, tol: Float): Float {
        val ox = minOf(a.r, b.r) - maxOf(a.l, b.l)
        val oy = minOf(a.b, b.b) - maxOf(a.t, b.t)
        if (ox <= tol || oy <= tol) return 0f
        return ox * oy
    }

    @Test
    fun noTextOverlapsOnAnyScreen() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val save = SaveManager(activity)
        MainActivity::class.java.getDeclaredField("save").apply { isAccessible = true }.set(activity, save)
        MainActivity::class.java.getDeclaredField("audio").apply {
            isAccessible = true
        }.set(activity, ir.shahed.pahpad.core.GameAudio(activity, save))

        val problems = StringBuilder()

        for ((w, h) in sizes) {
            val screens = mapOf(
                "menu" to MainMenuScreen(activity),
                "chapters" to ChapterSelectScreen(activity),
                "levels" to LevelSelectScreen(activity, 3),
                "shop" to ShopScreen(activity),
                "settings" to SettingsScreen(activity),
                "leaderboard" to LeaderboardScreen(activity),
            )
            for ((name, screen) in screens) {
                val boxes = renderAndCollect(activity, screen, w, h)
                val density = screen.resources.displayMetrics.density
                val tol = density * 1.5f
                for (i in boxes.indices) {
                    for (j in i + 1 until boxes.size) {
                        val area = overlap(boxes[i], boxes[j], tol)
                        if (area > 0f) {
                            problems.appendLine(
                                "${name}@${w}x$h: «${boxes[i].text}» با «${boxes[j].text}» " +
                                    "به اندازهٔ ${area.toInt()}px² روی هم"
                            )
                        }
                    }
                }
            }
        }

        if (problems.isNotEmpty()) {
            println("=== همپوشانی متن‌ها ===")
            print(problems)
        }
        assertTrue("همپوشانی متن پیدا شد:\n$problems", problems.isEmpty())
    }
}
