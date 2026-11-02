package com.feng.siriisland

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.MotionEvent
import android.view.View.MeasureSpec.*
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*

enum class Mode { IDLE, SIRI, SEARCH }

class IslandView(c: Context) : FrameLayout(c) {
    var onDouble: () -> Unit = {}
    var onTriple: () -> Unit = {}
    var onDismiss: () -> Unit = {}
    var onSubmit: (String) -> Unit = {}
    var micOn = false
        set(v) { field = v; invalidate() }
    var camOn = false
        set(v) { field = v; invalidate() }
    var level = 0f
    var dotSize = 6; var dotX = 30; var dotY = 0; var idleW = 120
    var mode = Mode.IDLE; private set

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private var curW = 0f; private var curH = 0f
    private var tgtW = 0f; private var tgtH = 0f
    private var listening = false; private var thinking = false
    private var spin = 0f
    private var anim: ValueAnimator? = null
    private var spinAnim: ValueAnimator? = null
    private var taps = 0
    private val hd = Handler(Looper.getMainLooper())
    private val resolve = Runnable {
        val n = taps; taps = 0
        if (n == 2) onDouble() else if (n >= 3) onTriple()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rainbow = intArrayOf(0xFFFF5E5E.toInt(), 0xFFFFB84D.toInt(), 0xFF5EFF9E.toInt(),
        0xFF4DB8FF.toInt(), 0xFFB04DFF.toInt(), 0xFFFF5E5E.toInt())

    private val text = TextView(context).apply { setTextColor(Color.WHITE); textSize = 17f }
    private val scroll = ScrollView(context).apply {
        isVerticalScrollBarEnabled = false; visibility = INVISIBLE; addView(text)
    }
    private val input = EditText(context).apply {
        hint = "Search or Ask"
        setHintTextColor(Color.argb(150, 255, 255, 255)); setTextColor(Color.WHITE)
        background = null; setSingleLine()
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        visibility = GONE
        setOnEditorActionListener { v, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) {
                val q = v.text.toString().trim()
                if (q.isNotEmpty()) { v.text = ""; onSubmit(q) }
                true
            } else false
        }
    }

    init { addView(scroll); addView(input); setWillNotDraw(false) }

    fun refresh(animate: Boolean) { if (mode == Mode.IDLE) retarget(animate); invalidate() }

    fun setMode(m: Mode) {
        mode = m
        scroll.visibility = if (m == Mode.SIRI) VISIBLE else INVISIBLE
        input.visibility = if (m == Mode.SEARCH) VISIBLE else GONE
        val v: View? = when (m) { Mode.SIRI -> scroll; Mode.SEARCH -> input; else -> null }
        v?.alpha = 0f
        v?.animate()?.alpha(1f)?.setStartDelay(150)?.setDuration(250)?.start()
        retarget(true)
    }

    fun setText(s: String) {
        text.text = s
        if (mode == Mode.SIRI) retarget(true)
    }
    fun setListening(b: Boolean) { listening = b; updateSpin() }
    fun setThinking(b: Boolean) { thinking = b; updateSpin() }
    fun focusInput() {
        input.requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(input, 0)
    }

    private fun retarget(animate: Boolean) {
        val tw: Float; val th: Float
        when (mode) {
            Mode.IDLE -> { tw = dp(idleW).toFloat(); th = dp(37).toFloat() }
            Mode.SIRI -> {
                tw = dp(340).toFloat()
                text.measure(makeMeasureSpec(dp(300), EXACTLY), makeMeasureSpec(0, UNSPECIFIED))
                th = (text.measuredHeight + dp(56)).coerceIn(dp(96), dp(300)).toFloat()
            }
            Mode.SEARCH -> { tw = dp(340).toFloat(); th = dp(60).toFloat() }
        }
        tgtW = tw; tgtH = th
        anim?.cancel()
        if (!animate) { curW = tw; curH = th; requestLayout(); return }
        val w0 = curW; val h0 = curH
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450; interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                val f = it.animatedValue as Float
                curW = w0 + (tw - w0) * f; curH = h0 + (th - h0) * f
                requestLayout(); invalidate()
            }
            start()
        }
    }

    private fun updateSpin() {
        val on = listening || thinking
        if (on && spinAnim == null) {
            spinAnim = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 2200; repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { spin = it.animatedValue as Float; invalidate() }
                start()
            }
        } else if (!on) { spinAnim?.cancel(); spinAnim = null; invalidate() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow(); anim?.cancel(); spinAnim?.cancel(); spinAnim = null
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val cw = dp(340) - dp(40)
        scroll.measure(makeMeasureSpec(cw, EXACTLY),
            makeMeasureSpec(maxOf(dp(20), tgtH.toInt() - dp(44)), EXACTLY))
        input.measure(makeMeasureSpec(cw, EXACTLY), makeMeasureSpec(dp(48), AT_MOST))
        setMeasuredDimension(curW.toInt(), curH.toInt())
    }

    override fun onLayout(ch: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val x = ((r - l) - dp(300)) / 2
        scroll.layout(x, dp(22), x + scroll.measuredWidth, dp(22) + scroll.measuredHeight)
        val iy = (dp(60) - input.measuredHeight) / 2
        input.layout(x, iy, x + input.measuredWidth, iy + input.measuredHeight)
    }

    override fun dispatchDraw(cv: Canvas) {
        val w = curW; val h = curH
        if (w <= 0f || h <= 0f) return
        val idleP = dp(idleW).toFloat()
        val e = ((w - idleP) / (dp(340) - idleP).coerceAtLeast(1f)).coerceIn(0f, 1f)
        val rect = RectF(0f, 0f, w, h)
        val rad = minOf(h / 2f, dp(34).toFloat())
        fill.shader = LinearGradient(0f, 0f, 0f, h,
            Color.argb((255 - 35 * e).toInt(), (45 * e).toInt(), (45 * e).toInt(), (50 * e).toInt()),
            Color.argb((255 - 35 * e).toInt(), 6, 6, 8), Shader.TileMode.CLAMP)
        cv.drawRoundRect(rect, rad, rad, fill)

        val active = listening || thinking
        val sw = dp(2) + maxOf(0f, level) * d * 0.25f
        if (active) {
            val sg = SweepGradient(w / 2, h / 2, rainbow, null)
            sg.setLocalMatrix(Matrix().apply { setRotate(spin, w / 2, h / 2) })
            stroke.shader = sg
            stroke.strokeWidth = sw * 3; stroke.alpha = 70
            cv.drawRoundRect(RectF(sw, sw, w - sw, h - sw), rad, rad, stroke)
            stroke.strokeWidth = sw; stroke.alpha = 255
        } else {
            stroke.shader = LinearGradient(0f, 0f, w, h, Color.argb(120, 255, 255, 255),
                Color.argb(20, 255, 255, 255), Shader.TileMode.CLAMP)
            stroke.strokeWidth = d * 1.5f
        }
        cv.drawRoundRect(RectF(sw / 2, sw / 2, w - sw / 2, h - sw / 2), rad, rad, stroke)

        val path = Path().apply { addRoundRect(rect, rad, rad, Path.Direction.CW) }
        cv.save(); cv.clipPath(path); super.dispatchDraw(cv); cv.restore()

        if ((micOn || camOn) && e < 0.6f) {
            dot.color = if (camOn) 0xFF30D158.toInt() else 0xFFFF9F0A.toInt()
            dot.alpha = (255 * (1f - e / 0.6f)).toInt()
            cv.drawCircle(w / 2 + dotX * d, h / 2 + dotY * d, dotSize * d / 2f, dot)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_OUTSIDE) {
            if (mode != Mode.IDLE) onDismiss()
            return true
        }
        if (mode == Mode.IDLE && e.action == MotionEvent.ACTION_UP) {
            taps++; hd.removeCallbacks(resolve); hd.postDelayed(resolve, 320)
        }
        return true
    }
}
