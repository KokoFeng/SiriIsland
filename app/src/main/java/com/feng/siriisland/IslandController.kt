package com.feng.siriisland

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as L
import android.view.inputmethod.InputMethodManager
import java.util.Locale

class IslandController(private val ctx: Context, private val type: Int) {
    companion object { @Volatile var current: IslandController? = null }

    private lateinit var wm: WindowManager
    private lateinit var island: IslandView
    private lateinit var lp: WindowManager.LayoutParams
    private var sr: SpeechRecognizer? = null
    private var wake: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val ui = Handler(Looper.getMainLooper())
    private val busyCams = HashSet<String>()
    private var started = false
    private var wakeOn = false
    private var busy = false
    private var conv = ""
    private var auto: Runnable? = null
    private val prefs: SharedPreferences get() = Store.prefs
    private val density: Float get() = ctx.resources.displayMetrics.density

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (started) {
            applyPrefs(true)
            if (k == "wake") { if (prefs.getBoolean("wake", true)) startWake() else stopWake() }
        }
    }
    private val audioCb = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(c: MutableList<AudioRecordingConfiguration>) {
            val own = if (wake != null) 1 else 0
            island.micOn = c.size > own
        }
    }
    private val camCb = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(id: String) { busyCams.add(id); island.camOn = true }
        override fun onCameraAvailable(id: String) { busyCams.remove(id); island.camOn = busyCams.isNotEmpty() }
    }

    private fun Bundle?.first(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""

    fun start(priority: Boolean) {
        Store.init(ctx)
        val cur = current
        if (cur != null && cur !== this) {
            if (!priority) return
            cur.stop()
        }
        current = this
        wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        island = IslandView(ctx)
        lp = WindowManager.LayoutParams(-2, -2, type,
            L.FLAG_NOT_FOCUSABLE or L.FLAG_LAYOUT_NO_LIMITS or L.FLAG_LAYOUT_IN_SCREEN or
                L.FLAG_NOT_TOUCH_MODAL or L.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        started = true
        applyPrefs(false)
        island.onDouble = { startSiri() }
        island.onTriple = { openSearch() }
        island.onDismiss = { dismiss() }
        wm.addView(island, lp)
        prefs.registerOnSharedPreferenceChangeListener(listener)

        tts = TextToSpeech(ctx) { s ->
            if (s == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onError(id: String?) { ui.post { scheduleDismiss(5000L) } }
                    override fun onDone(id: String?) { ui.post { scheduleDismiss(6000L) } }
                })
            }
        }
        ctx.getSystemService(AudioManager::class.java).registerAudioRecordingCallback(audioCb, ui)
        ctx.getSystemService(CameraManager::class.java).registerAvailabilityCallback(camCb, ui)
        startWake()
    }

    fun stop() {
        if (!started) return
        started = false; wakeOn = false
        if (current === this) current = null
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        ctx.getSystemService(AudioManager::class.java).unregisterAudioRecordingCallback(audioCb)
        ctx.getSystemService(CameraManager::class.java).unregisterAvailabilityCallback(camCb)
        stopAll()
        wake?.destroy(); wake = null
        tts?.shutdown(); tts = null
        if (island.isAttachedToWindow) wm.removeView(island)
    }

    private fun applyPrefs(animate: Boolean) {
        island.dotSize = prefs.getInt("dotSize", 6)
        island.dotX = prefs.getInt("dotX", 30)
        island.dotY = prefs.getInt("dotY", 0)
        island.idleW = prefs.getInt("idleW", 150)
        island.idleH = prefs.getInt("idleH", 40)
        lp.y = (prefs.getInt("top", 8) * density).toInt()
        island.refresh(animate)
        if (island.isAttachedToWindow) wm.updateViewLayout(island, lp)
    }

    private fun focusable(on: Boolean) {
        lp.flags = if (on) lp.flags and L.FLAG_NOT_FOCUSABLE.inv() else lp.flags or L.FLAG_NOT_FOCUSABLE
        wm.updateViewLayout(island, lp)
    }

    // ---- wake word: "Hey Siri" / "Siri" / "Hello Siri" ----
    private fun startWake() {
        if (wakeOn || !prefs.getBoolean("wake", true)) return
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        wakeOn = true
        listenWake()
    }

    private fun stopWake() { wakeOn = false; wake?.destroy(); wake = null }

    private fun again(ms: Long) { ui.postDelayed({ listenWake() }, ms) }

    private fun setBusy(b: Boolean) {
        busy = b
        if (b) { wake?.destroy(); wake = null } else again(300L)
    }

    private fun listenWake() {
        if (!wakeOn || busy || !started) return
        wake?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        wake = r
        r.setRecognitionListener(object : Rl() {
            override fun onResults(b: Bundle?) {
                val t = b.first().lowercase()
                val i = t.indexOf("siri")
                if (i < 0) { again(300L); return }
                val q = t.substring(i + 4).trim()
                if (q.length >= 3) { setBusy(true); stopAll(); conv = ""; ask(q) } else startSiri()
            }
            override fun onError(e: Int) {
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return
                again(if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500L else 400L)
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L))
    }

    // ---- Siri session ----
    private fun startSiri() {
        setBusy(true)
        stopAll()
        conv = ""
        focusable(false)
        island.setMode(Mode.SIRI)
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            island.setText("Izinkan mikrofon di aplikasi Siri dulu."); return
        }
        island.setListening(true)
        island.setText("Mendengarkan…")
        val rec = SpeechRecognizer.createSpeechRecognizer(ctx)
        sr = rec
        rec.setRecognitionListener(object : Rl() {
            override fun onPartialResults(b: Bundle?) {
                val t = b.first(); if (t.isNotEmpty()) island.setText(t)
            }
            override fun onResults(b: Bundle?) {
                val t = b.first()
                island.setListening(false)
                if (t.isBlank()) { island.setText("Saya tidak mendengar apa pun."); scheduleDismiss(3000L) } else ask(t)
            }
            override fun onError(e: Int) {
                island.setListening(false); island.setText("Coba lagi (kode $e)"); scheduleDismiss(3000L)
            }
            override fun onRmsChanged(r: Float) { island.level = r }
        })
        rec.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()))
    }

    private fun openSearch() {
        setBusy(true)
        stopAll()
        conv = ""
        ctx.startActivity(Intent(ctx, SearchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun askFromSearch(q: String) { conv = ""; ask(q) }
    fun searchClosed() { if (!island.isThinkingNow()) setBusy(false) }

    private fun ask(q: String) {
        setBusy(true)
        island.setMode(Mode.SIRI)
        island.setListening(false)
        val key = Store.apiKey
        if (key.isEmpty()) {
            island.setText("Masukkan API key di aplikasi Siri dulu."); scheduleDismiss(4000L); return
        }
        island.setText(q)
        island.setThinking(true)
        if (conv.isEmpty()) conv = Store.newConv()
        Store.add(conv, "user", q)
        val c = conv
        Thread {
            val r = runCatching { Brain.ask(key, c) }
            val a = r.getOrElse { "Maaf, ada masalah: ${it.message}" }
            val good = r.isSuccess
            ui.post {
                island.setThinking(false)
                island.setText(a)
                if (good) Store.add(c, "assistant", a)
                scheduleDismiss(15000L + a.length * 60L)
                tts?.speak(a.replace(Regex("[*#_`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "siri")
            }
        }.start()
    }

    private fun scheduleDismiss(ms: Long) {
        auto?.let { ui.removeCallbacks(it) }
        val rn = Runnable { dismiss() }
        auto = rn
        ui.postDelayed(rn, ms)
    }

    private fun stopAll() {
        sr?.destroy(); sr = null
        tts?.stop()
        island.setListening(false); island.setThinking(false)
        auto?.let { ui.removeCallbacks(it) }
    }

    private fun dismiss() {
        stopAll()
        ctx.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(island.windowToken, 0)
        focusable(false)
        island.setMode(Mode.IDLE)
        setBusy(false)
    }
}
