package com.feng.siriisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast

// Mode cadangan (overlay biasa). Mode utama = IslandAccessibility.
class IslandService : Service() {
    private var c: IslandController? = null

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("island", "Dynamic Island", NotificationManager.IMPORTANCE_MIN))
        val n = Notification.Builder(this, "island").setContentTitle("Dynamic Island aktif")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan tampil di atas aplikasi lain dulu", Toast.LENGTH_LONG).show()
            stopSelf(); return
        }
        c = IslandController(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY).also { it.start(false) }
    }

    override fun onDestroy() { c?.stop(); c = null; super.onDestroy() }
}
