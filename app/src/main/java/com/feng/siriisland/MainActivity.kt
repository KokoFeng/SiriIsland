package com.feng.siriisland

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val p = getSharedPreferences("siri", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48)
        }
        fun btn(t: String, a: () -> Unit) {
            root.addView(Button(this).apply { text = t; setOnClickListener { a() } })
        }
        fun seek(label: String, key: String, lo: Int, hi: Int, def: Int) {
            val tv = TextView(this); root.addView(tv)
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
            root.addView(sb)
        }
        btn("1. Izinkan tampil di atas aplikasi lain") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        btn("2. Izinkan mikrofon & notifikasi") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO,
                "android.permission.POST_NOTIFICATIONS"), 1)
        }
        val key = EditText(this).apply {
            hint = "API key Anthropic"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(p.getString("key", ""))
        }
        root.addView(key)
        btn("Simpan API key") {
            p.edit().putString("key", key.text.toString().trim()).apply()
            Toast.makeText(this, "Tersimpan", Toast.LENGTH_SHORT).show()
        }
        seek("Posisi vertikal pulau (dp)", "top", 0, 60, 8)
        seek("Lebar pulau (dp)", "idleW", 80, 200, 120)
        seek("Ukuran titik indikator (dp)", "dotSize", 3, 14, 6)
        seek("Posisi titik horizontal", "dotX", -100, 100, 30)
        seek("Posisi titik vertikal", "dotY", -20, 20, 0)
        btn("Aktifkan Dynamic Island") {
            startForegroundService(Intent(this, IslandService::class.java))
        }
        btn("Matikan") { stopService(Intent(this, IslandService::class.java)) }
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
