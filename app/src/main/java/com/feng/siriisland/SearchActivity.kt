package com.feng.siriisland

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.*

class SearchActivity : Activity() {
    private var submitted = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(0.45f)
        val root = FrameLayout(this)
        root.setOnClickListener { finish() }
        val et = EditText(this).apply {
            hint = "Search or Ask"
            setHintTextColor(Color.argb(150, 255, 255, 255)); setTextColor(Color.WHITE)
            background = null; setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        fun submit() {
            val q = et.text.toString().trim()
            if (q.isNotEmpty()) {
                submitted = true
                IslandController.current?.askFromSearch(q)
                finish()
            }
        }
        et.setOnEditorActionListener { _, _, _ -> submit(); true }
        val go = TextView(this).apply {
            text = "↑"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = glass(22); setOnClickListener { submit() }
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = darkGlass(30)
            setPadding(dp(20), 0, dp(8), 0)
            addView(et, LinearLayout.LayoutParams(0, dp(56), 1f))
            addView(go, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        val top = Store.prefs.getInt("top", 8) + Store.prefs.getInt("idleH", 40) + 12
        root.addView(bar, FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP).apply {
            setMargins(dp(16), dp(top), dp(16), 0)
        })
        setContentView(root)
        et.requestFocus()
    }

    override fun onDestroy() {
        if (!submitted) IslandController.current?.searchClosed()
        super.onDestroy()
    }
}
