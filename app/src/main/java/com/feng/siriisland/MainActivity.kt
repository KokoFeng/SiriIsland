package com.feng.siriisland

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val MP = -1
    private val WC = -2
    private lateinit var root: FrameLayout
    private lateinit var main: LinearLayout
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private var conv = ""
    private var panel: View? = null
    private var keyPopup: View? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        root = FrameLayout(this).apply {
            setBackgroundColor(0xFF050507.toInt()); fitsSystemWindows = true
        }
        main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(main, FrameLayout.LayoutParams(MP, MP))
        buildMain()
        setContentView(root)
        if (Store.apiKey.isEmpty()) root.post { showKeyPopup() }
    }

    // ---------- helpers ----------
    private fun circle(t: String, a: (View) -> Unit): TextView = TextView(this).apply {
        text = t; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = glass(22)
        setOnClickListener { a(it) }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }

    private fun titleView(t: String) = TextView(this).apply {
        text = t; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    // ---------- main chat screen ----------
    private fun buildMain() {
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        bar.addView(circle("☰") { showMenu(it) })
        bar.addView(titleView("Siri"), LinearLayout.LayoutParams(0, WC, 1f))
        bar.addView(circle("✎") { newChat() })
        main.addView(bar, LinearLayout.LayoutParams(MP, WC))

        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(chat) }
        main.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))

        input = EditText(this).apply {
            hint = "Ask Siri"
            setHintTextColor(Color.argb(150, 255, 255, 255)); setTextColor(Color.WHITE)
            background = null; setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, _, _ -> sendInput(); true }
        }
        val bottom = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = glass(28)
            setPadding(dp(18), dp(4), dp(6), dp(4))
        }
        bottom.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(circle("🎙") { startVoice() })
        bottom.addView(circle("↑") { sendInput() })
        main.addView(bottom, LinearLayout.LayoutParams(MP, WC).apply {
            setMargins(dp(16), dp(4), dp(16), dp(12))
        })
    }

    private fun addMsg(role: String, t: String): TextView {
        val isUser = role == "user"
        val tv = TextView(this).apply {
            text = t; textSize = 17f; setTextColor(Color.WHITE); setLineSpacing(0f, 1.15f)
            if (isUser) {
                background = glass(20); setPadding(dp(16), dp(10), dp(16), dp(10)); maxWidth = dp(280)
            }
        }
        val lp = LinearLayout.LayoutParams(WC, WC).apply {
            topMargin = dp(10); gravity = if (isUser) Gravity.END else Gravity.START
        }
        chat.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return tv
    }

    private fun sendInput() {
        val q = input.text.toString().trim()
        if (q.isNotEmpty()) { input.setText(""); send(q) }
    }

    private fun send(q: String) {
        if (Store.apiKey.isEmpty()) { showKeyPopup(); return }
        if (conv.isEmpty()) conv = Store.newConv()
        addMsg("user", q)
        Store.add(conv, "user", q)
        val tv = addMsg("assistant", "…")
        val c = conv
        val key = Store.apiKey
        Thread {
            val r = runCatching { Brain.ask(key, c) }
            val a = r.getOrElse { "Sorry, something went wrong: ${it.message}" }
            val good = r.isSuccess
            ui.post {
                tv.text = a
                if (good) Store.add(c, "assistant", a)
                scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            }
        }.start()
    }

    private fun newChat() { conv = ""; chat.removeAllViews() }

    private fun loadConv(id: String) {
        conv = id; chat.removeAllViews()
        val msgs = Store.get(id)?.getJSONArray("msgs") ?: return
        for (i in 0 until msgs.length()) {
            val m = msgs.getJSONObject(i)
            addMsg(if (m.getString("r") == "user") "user" else "assistant", m.getString("t"))
        }
    }

    private fun startVoice() {
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()), 7)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Voice input is not available", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        if (r == 7 && c == RESULT_OK) {
            d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { send(it) }
        }
    }

    // ---------- menu (three lines) ----------
    private fun showMenu(anchor: View) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = darkGlass(22)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val pw = PopupWindow(box, dp(230), WC, true)
        pw.setBackgroundDrawable(ColorDrawable(0))
        pw.elevation = dp(12).toFloat()
        fun item(t: String, a: () -> Unit) {
            box.addView(TextView(this).apply {
                text = t; textSize = 17f; setTextColor(Color.WHITE)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                setOnClickListener { pw.dismiss(); a() }
            })
        }
        item("New chat") { newChat() }
        item("Conversations") { showConversations() }
        item("Settings") { showSettings() }
        pw.showAsDropDown(anchor, 0, dp(8))
    }

    // ---------- panels ----------
    private fun openPanel(title: String, content: View) {
        closePanel()
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF050507.toInt())
        }
        val bar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        bar.addView(circle("‹") { closePanel() })
        bar.addView(titleView(title), LinearLayout.LayoutParams(0, WC, 1f))
        bar.addView(View(this), LinearLayout.LayoutParams(dp(44), dp(44)))
        p.addView(bar, LinearLayout.LayoutParams(MP, WC))
        p.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(MP, 0, 1f))
        root.addView(p, FrameLayout.LayoutParams(MP, MP))
        panel = p
    }

    private fun closePanel() { panel?.let { root.removeView(it) }; panel = null }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (panel != null) closePanel() else super.onBackPressed() }

    private fun showConversations() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24))
        }
        val list = Store.list()
        if (list.isEmpty()) {
            box.addView(TextView(this).apply {
                text = "No conversations yet"; setTextColor(Color.LTGRAY)
                setPadding(dp(8), dp(24), dp(8), 0)
            })
        }
        val f = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        for (o in list) {
            val msgs = o.getJSONArray("msgs")
            var prev = ""
            for (i in msgs.length() - 1 downTo 0) {
                val m = msgs.getJSONObject(i)
                if (m.getString("r") != "user") { prev = m.getString("t"); break }
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = glass(22)
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            card.addView(TextView(this).apply {
                text = f.format(Date(o.getLong("time"))); textSize = 12f; setTextColor(Color.LTGRAY)
            })
            card.addView(TextView(this).apply {
                text = o.getString("title"); textSize = 17f; setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            })
            card.addView(TextView(this).apply {
                text = prev.take(110); textSize = 14f; setTextColor(Color.LTGRAY); maxLines = 3
            })
            val id = o.getString("id")
            card.setOnClickListener { loadConv(id); closePanel() }
            box.addView(card, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(10) })
        }
        openPanel("Conversations", box)
    }

    private fun showSettings() {
        val p = Store.prefs
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), dp(32))
        }
        fun label(t: String) {
            box.addView(TextView(this).apply {
                text = t; textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, dp(20), 0, dp(6))
            })
        }
        fun btn(t: String, a: () -> Unit) {
            box.addView(TextView(this).apply {
                text = t; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                background = glass(22); setPadding(dp(16), dp(14), dp(16), dp(14))
                setOnClickListener { a() }
            }, LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(8) })
        }
        fun seek(label: String, key: String, lo: Int, hi: Int, def: Int) {
            val tv = TextView(this).apply { setTextColor(Color.WHITE); setPadding(0, dp(14), 0, 0) }
            val sb = SeekBar(this)
            sb.max = hi - lo
            sb.progress = p.getInt(key, def) - lo
            tv.text = "$label: ${sb.progress + lo}"
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, v: Int, u: Boolean) {
                    tv.text = "$label: ${v + lo}"
                    p.edit().putInt(key, v + lo).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
            box.addView(tv); box.addView(sb)
        }

        label("SIRI")
        val key = EditText(this).apply {
            hint = "Anthropic API key"
            setHintTextColor(Color.argb(150, 255, 255, 255)); setTextColor(Color.WHITE)
            background = glass(16); setPadding(dp(14), dp(12), dp(14), dp(12)); setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(Store.apiKey)
        }
        box.addView(key, LinearLayout.LayoutParams(MP, WC))
        btn("Save API key") {
            Store.apiKey = key.text.toString().trim()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        box.addView(Switch(this).apply {
            text = "Voice: \"Hey Siri\", \"Siri\", \"Hello Siri\""
            setTextColor(Color.WHITE); setPadding(0, dp(16), 0, 0)
            isChecked = p.getBoolean("wake", true)
            setOnCheckedChangeListener { _, c -> p.edit().putBoolean("wake", c).apply() }
        })

        label("DYNAMIC ISLAND")
        btn("1. Allow microphone & notifications") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO,
                "android.permission.POST_NOTIFICATIONS"), 1)
        }
        btn("2. Turn on above everything (Accessibility)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        seek("Island position (dp)", "top", 0, 60, 8)
        seek("Island width (dp)", "idleW", 80, 260, 150)
        seek("Island height (dp)", "idleH", 28, 70, 40)
        seek("Indicator dot size (dp)", "dotSize", 3, 14, 6)
        seek("Indicator dot horizontal", "dotX", -100, 100, 30)
        seek("Indicator dot vertical", "dotY", -20, 20, 0)

        label("FALLBACK: OVERLAY MODE (not above Control Center)")
        btn("Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("Start overlay mode") { startForegroundService(Intent(this, IslandService::class.java)) }
        btn("Stop overlay mode") { stopService(Intent(this, IslandService::class.java)) }
        openPanel("Settings", box)
    }

    // ---------- liquid glass popup ----------
    private fun showKeyPopup() {
        if (keyPopup != null) return
        val dim = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(110, 0, 0, 0)); isClickable = true
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = darkGlass(30)
            setPadding(dp(22), dp(24), dp(22), dp(18))
        }
        card.addView(titleView("Activate Anthropic API Key").apply { textSize = 20f })
        card.addView(TextView(this).apply {
            text = "Enter your API key to turn on Siri."
            textSize = 14f; setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(16))
        })
        val key = EditText(this).apply {
            hint = "sk-ant-..."
            setHintTextColor(Color.argb(150, 255, 255, 255)); setTextColor(Color.WHITE)
            background = glass(16); setPadding(dp(14), dp(12), dp(14), dp(12)); setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        card.addView(key, LinearLayout.LayoutParams(MP, WC))
        val row = LinearLayout(this).apply { setPadding(0, dp(18), 0, 0) }

        fun close() {
            root.removeView(dim); keyPopup = null
            if (Build.VERSION.SDK_INT >= 31) main.setRenderEffect(null)
        }
        fun pill(t: String, strong: Boolean, a: () -> Unit) {
            row.addView(TextView(this).apply {
                text = t; textSize = 17f; gravity = Gravity.CENTER
                setTextColor(if (strong) Color.WHITE else Color.LTGRAY)
                typeface = if (strong) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                background = glass(22, if (strong) 110 else 40, if (strong) 60 else 15)
                setPadding(0, dp(12), 0, dp(12))
                setOnClickListener { a() }
            }, LinearLayout.LayoutParams(0, WC, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        pill("Cancel", false) { close() }
        pill("Activate", true) {
            val k = key.text.toString().trim()
            if (k.isEmpty()) { key.error = "Required" } else { Store.apiKey = k; close() }
        }
        card.addView(row, LinearLayout.LayoutParams(MP, WC))
        dim.addView(card, FrameLayout.LayoutParams(dp(310), WC, Gravity.CENTER))
        root.addView(dim, FrameLayout.LayoutParams(MP, MP))
        keyPopup = dim
        if (Build.VERSION.SDK_INT >= 31) {
            main.setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP))
        }
        card.alpha = 0f; card.scaleX = 0.85f; card.scaleY = 0.85f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(380)
            .setInterpolator(OvershootInterpolator(1.2f)).start()
    }
}
