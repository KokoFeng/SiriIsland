package com.feng.siriisland

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.*
import android.view.inputmethod.InputMethodManager
import org.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class IslandService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var island: IslandView
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val history = JSONArray()
    private val ui = Handler(Looper.getMainLooper())
    private val busyCams = HashSet<String>()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyPrefs(true) }
    private val density get() = resources.displayMetrics.density

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("island", "Dynamic Island", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "island").setContentTitle("Dynamic Island aktif")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)

        prefs = getSharedPreferences("siri", MODE_PRIVATE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        island = IslandView(this)
        val L = WindowManager.LayoutParams
        lp = WindowManager.LayoutParams(-2, -2, L.TYPE_APPLICATION_OVERLAY,
            L.FLAG_NOT_FOCUSABLE or L.FLAG_LAYOUT_NO_LIMITS or L.FLAG_NOT_TOUCH_MODAL or
                L.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.softInputMode = L.SOFT_INPUT_ADJUST_NOTHING
        applyPrefs(false)
        island.onDouble = { startSiri() }
        island.onTriple = { openSearch() }
        island.onDismiss = { dismiss() }
        island.onSubmit = { ask(it) }
        wm.addView(island, lp)
        prefs.registerOnSharedPreferenceChangeListener(listener)

        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault() }

        getSystemService(AudioManager::class.java).registerAudioRecordingCallback(
            object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(c: MutableList<AudioRecordingConfiguration>) {
                    island.micOn = c.isNotEmpty()
                }
            }, ui)
        getSystemService(CameraManager::class.java).registerAvailabilityCallback(
            object : CameraManager.AvailabilityCallback() {
                override fun onCameraUnavailable(id: String) { busyCams.add(id); island.camOn = true }
                override fun onCameraAvailable(id: String) { busyCams.remove(id); island.camOn = busyCams.isNotEmpty() }
            }, ui)
    }

    private fun applyPrefs(animate: Boolean) {
        island.dotSize = prefs.getInt("dotSize", 6)
        island.dotX = prefs.getInt("dotX", 30)
        island.dotY = prefs.getInt("dotY", 0)
        island.idleW = prefs.getInt("idleW", 120)
        lp.y = (prefs.getInt("top", 8) * density).toInt()
        island.refresh(animate)
        if (island.isAttachedToWindow) wm.updateViewLayout(island, lp)
    }

    private fun focusable(on: Boolean) {
        val f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        lp.flags = if (on) lp.flags and f.inv() else lp.flags or f
        wm.updateViewLayout(island, lp)
    }

    private fun openSearch() {
        stopAll()
        focusable(true)
        island.setMode(Mode.SEARCH)
        island.postDelayed({ island.focusInput() }, 250)
    }

    private fun startSiri() {
        stopAll()
        focusable(false)
        island.setMode(Mode.SIRI)
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            island.setText("Izinkan mikrofon di aplikasi Siri Island dulu."); return
        }
        island.setListening(true)
        island.setText("Mendengarkan…")
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        sr = rec
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(b: Bundle) {
                b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let { island.setText(it) }
            }
            override fun onResults(b: Bundle) {
                val t = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                island.setListening(false)
                if (t.isNullOrBlank()) island.setText("Saya tidak mendengar apa pun.") else ask(t)
            }
            override fun onError(e: Int) { island.setListening(false); island.setText("Coba lagi (kode $e)") }
            override fun onRmsChanged(r: Float) { island.level = r }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        rec.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()))
    }

    private fun ask(q: String) {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(island.windowToken, 0)
        focusable(false)
        island.setMode(Mode.SIRI)
        island.setListening(false)
        val key = prefs.getString("key", "") ?: ""
        if (key.isEmpty()) { island.setText("Masukkan API key di aplikasi Siri Island dulu."); return }
        island.setText(q)
        island.setThinking(true)
        history.put(JSONObject().put("role", "user").put("content", q))
        val body = JSONObject().put("model", "claude-sonnet-5-5").put("max_tokens", 500)
            .put("system", "Kamu Siri, asisten suara di iPhone. Jawab singkat, hangat, tanpa markdown, " +
                "dalam bahasa yang dipakai pengguna.")
            .put("messages", history).toString()
        Thread {
            val ans = try { post(key, body) } catch (e: Exception) { "Maaf, ada masalah: ${e.message}" }
            ui.post {
                island.setThinking(false)
                island.setText(ans)
                history.put(JSONObject().put("role", "assistant").put("content", ans))
                tts?.speak(ans.replace(Regex("[*#_`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "siri")
            }
        }.start()
    }

    private fun post(key: String, body: String): String {
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-api-key", key)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        if (code !in 200..299) return "Error $code. Cek API key kamu."
        val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("content")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("type") == "text" }.joinToString("") { it.getString("text") }
    }

    private fun stopAll() {
        sr?.destroy(); sr = null
        tts?.stop()
        island.setListening(false); island.setThinking(false)
    }

    private fun dismiss() {
        stopAll()
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(island.windowToken, 0)
        focusable(false)
        island.setMode(Mode.IDLE)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        stopAll(); tts?.shutdown()
        if (island.isAttachedToWindow) wm.removeView(island)
        super.onDestroy()
    }
}
