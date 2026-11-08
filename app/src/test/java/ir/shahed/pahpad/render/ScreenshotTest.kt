package ir.shahed.pahpad.render

import android.graphics.Bitmap
import android.graphics.Canvas
import ir.shahed.pahpad.MainActivity
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.screens.ChapterSelectScreen
import ir.shahed.pahpad.screens.LeaderboardScreen
import ir.shahed.pahpad.screens.LevelSelectScreen
import ir.shahed.pahpad.screens.MainMenuScreen
import ir.shahed.pahpad.screens.SettingsScreen
import ir.shahed.pahpad.screens.ShopScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * هر صفحهٔ رابط کاربری را در چند اندازهٔ واقعی گوشی رندر می‌کند و PNG
 * می‌نویسد تا بتوان با چشم دید — نه با حدس.
 *
 * خروجی‌ها در `build/screens/` جمع می‌شوند و CI آن‌ها را به‌عنوان
 * artifact می‌گذارد.
 */
@RunWith(RobolectricTestRunner::class)
// حالت گرافیک بومی: Robolectric واقعاً با Skia رستر می‌کند، پس فونت فارسی
// و متن‌ها در تصویر خروجی دیده می‌شوند (حالت پیش‌فرض متن را نمی‌کشد).
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28])
class ScreenshotTest {

    private val outDir = File("build/screens").apply { mkdirs() }

    /** اندازه‌های واقعی landscape اندروید (رزولوشن فیزیکی) */
    private val sizes = listOf(
        "720x360" to (1280 to 720),    // گوشی ارزان
        "1080p" to (1920 to 1080),     // رایج
        "20x9" to (2340 to 1080),      // بلند
        "tablet" to (2560 to 1440),
    )

    private fun shoot(name: String, screen: BaseScreen, w: Int, h: Int, tag: String) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        screen.layout(0, 0, w, h)
        screen.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY),
        )
        screen.layout(0, 0, w, h)
        // یک فریم رسم
        val draw = BaseScreen::class.java.getDeclaredMethod("onDraw", Canvas::class.java)
        draw.isAccessible = true
        draw.invoke(screen, c)
        val f = File(outDir, "${name}_$tag.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("نوشتم: ${f.absolutePath}  (${w}x${h})")
    }

    @Test
    fun renderAllScreens() {
        // MainActivity در onCreate سراغ Tapsell/Play Services می‌رود که در
        // محیط تست موجود نیست؛ صفحه‌ها فقط save/audio/show می‌خواهند، پس
        // همان دو فیلد را دستی پر می‌کنیم و onCreate را اجرا نمی‌کنیم.
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val save = ir.shahed.pahpad.core.SaveManager(activity)
        fun fill(name: String) {
            val f = MainActivity::class.java.getDeclaredField(name)
            f.isAccessible = true
            when (name) {
                "save" -> f.set(activity, save)
                "audio" -> f.set(activity, ir.shahed.pahpad.core.GameAudio(activity, save))
            }
        }
        fill("save"); fill("audio")

        for ((tag, dims) in sizes) {
            val (w, h) = dims
            shoot("menu", MainMenuScreen(activity), w, h, tag)
            shoot("chapters", ChapterSelectScreen(activity), w, h, tag)
            shoot("levels_c3", LevelSelectScreen(activity, 3), w, h, tag)
            shoot("shop", ShopScreen(activity), w, h, tag)
            shoot("settings", SettingsScreen(activity), w, h, tag)
            shoot("leaderboard", LeaderboardScreen(activity), w, h, tag)
            shoot("game", ir.shahed.pahpad.screens.GameScreen(activity,
                ir.shahed.pahpad.data.Levels.all.first { it.chapter == 1 },
                ir.shahed.pahpad.game.MissionMode.STORY, 7L), w, h, tag)
        }
        println("تعداد تصاویر: ${outDir.listFiles()?.size}")
    }
}
