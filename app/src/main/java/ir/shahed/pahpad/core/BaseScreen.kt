package ir.shahed.pahpad.core

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import ir.shahed.pahpad.MainActivity

/**
 * پایه‌ی همه‌ی صفحه‌های بازی: حلقه‌ی به‌روزرسانی، رسم و مدیریت لمس دکمه‌ها.
 */
@SuppressLint("ViewConstructor")
abstract class BaseScreen(val game: MainActivity) : View(game) {

    val save: SaveManager get() = game.save
    val audio: GameAudio get() = game.audio
    val ui: Ui = Ui(game, game.resources.displayMetrics.density)

    protected val buttons = ArrayList<UiButton>()
    private var pressedButton: UiButton? = null

    private var lastFrame = 0L
    private var looping = false
    var time = 0f
        protected set
    var vw = 0f
        private set
    var vh = 0f
        private set
    private var laidOut = false

    fun dp(v: Float) = ui.dp(v)

    // -------------------------------------------------- چرخه‌ی عمر

    open fun onEnter() {}
    open fun onExit() {}
    open fun onScreenPause() { stopLoop() }
    open fun onScreenResume() { startLoop() }
    open fun onBack(): Boolean = false

    /** جای‌گذاری دکمه‌ها؛ با تغییر اندازه‌ی صفحه صدا زده می‌شود */
    open fun layoutUi(w: Float, h: Float) {}

    open fun update(dt: Float) {}

    abstract fun render(c: Canvas)

    // -------------------------------------------------- حلقه‌ی بازی

    fun startLoop() {
        if (looping) return
        looping = true
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    fun stopLoop() {
        looping = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        vw = w.toFloat()
        vh = h.toFloat()
        laidOut = true
        layoutUi(vw, vh)
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 1f / 60f else ((now - lastFrame) / 1_000_000_000f)
        lastFrame = now
        val clamped = dt.coerceIn(0.001f, 0.05f)
        time += clamped
        if (laidOut) {
            update(clamped)
            render(canvas)
        }
        if (looping) postInvalidateOnAnimation()
    }

    // -------------------------------------------------- لمس

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val b = findButton(event.x, event.y)
                if (b != null) {
                    b.pressed = true
                    pressedButton = b
                    return true
                }
                return onTouchDown(event.x, event.y, event.getPointerId(0))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val b = findButton(event.getX(i), event.getY(i))
                if (b != null) {
                    b.pressed = true
                    pressedButton = b
                    return true
                }
                return onTouchDown(event.getX(i), event.getY(i), event.getPointerId(i))
            }
            MotionEvent.ACTION_MOVE -> {
                pressedButton?.let { it.pressed = it.contains(event.x, event.y) }
                for (i in 0 until event.pointerCount) {
                    onTouchMove(event.getX(i), event.getY(i), event.getPointerId(i))
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = if (event.actionMasked == MotionEvent.ACTION_POINTER_UP) event.actionIndex else 0
                val b = pressedButton
                if (b != null) {
                    val hit = b.pressed && b.contains(event.getX(i), event.getY(i))
                    b.pressed = false
                    pressedButton = null
                    if (hit && event.actionMasked != MotionEvent.ACTION_CANCEL) {
                        audio.click()
                        b.onClick()
                    }
                    return true
                }
                onTouchUp(event.getX(i), event.getY(i), event.getPointerId(i))
                return true
            }
        }
        return true
    }

    private fun findButton(x: Float, y: Float): UiButton? {
        for (i in buttons.indices.reversed()) {
            val b = buttons[i]
            if (b.contains(x, y)) return b
        }
        return null
    }

    open fun onTouchDown(x: Float, y: Float, pointerId: Int): Boolean = true
    open fun onTouchMove(x: Float, y: Float, pointerId: Int) {}
    open fun onTouchUp(x: Float, y: Float, pointerId: Int) {}

    protected fun drawButtons(c: Canvas) {
        for (b in buttons) ui.button(c, b)
    }

    /** دکمه‌ی بازگشت استاندارد گوشه‌ی بالا-چپ */
    protected fun addBackButton(action: () -> Unit): UiButton {
        val b = UiButton("بازگشت", Theme.TEXT_DIM) {
            audio.back()
            action()
        }
        buttons.add(b)
        return b
    }
}
