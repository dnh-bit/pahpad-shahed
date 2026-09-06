package ir.shahed.pahpad

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import ir.shahed.pahpad.core.BaseScreen
import ir.shahed.pahpad.core.GameAudio
import ir.shahed.pahpad.core.SaveManager
import ir.shahed.pahpad.core.Theme
import ir.shahed.pahpad.screens.SplashScreen

/**
 * تنها اکتیویتی بازی. صفحه‌ها به صورت View روی همین اکتیویتی جابه‌جا می‌شوند.
 */
class MainActivity : Activity() {

    lateinit var save: SaveManager
    lateinit var audio: GameAudio
    private lateinit var root: FrameLayout
    private var current: BaseScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        save = SaveManager(this)
        audio = GameAudio(this, save)

        root = FrameLayout(this)
        root.setBackgroundColor(Theme.BG)
        setContentView(root)
        immersive()

        show(SplashScreen(this))
    }

    /** نمایش یک صفحه‌ی جدید و آزادسازی صفحه‌ی قبلی */
    fun show(screen: BaseScreen) {
        current?.let {
            it.stopLoop()
            it.onExit()
        }
        root.removeAllViews()
        root.addView(
            screen,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        current = screen
        screen.onEnter()
        screen.startLoop()
        immersive()
    }

    fun currentScreen(): BaseScreen? = current

    private fun immersive() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onResume() {
        super.onResume()
        current?.onScreenResume()
    }

    override fun onPause() {
        super.onPause()
        current?.onScreenPause()
        audio.stopEngine()
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val handled = current?.onBack() ?: false
        if (!handled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) finish()
            else super.onBackPressed()
        }
    }
}
