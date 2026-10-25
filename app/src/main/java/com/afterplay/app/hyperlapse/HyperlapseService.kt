package com.afterplay.app.hyperlapse

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.afterplay.app.AfterPlayApp
import com.afterplay.app.MainActivity
import com.afterplay.app.R

class HyperlapseService : LifecycleService() {
    private var notificationText: String? = null
    private val controller get() = (application as AfterPlayApp).hyperlapse
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "하이퍼랩스 촬영", NotificationManager.IMPORTANCE_LOW))
        notify("하이퍼랩스 촬영 준비 중", false)
        controller.attach(this, ::stopSelf, ::notify)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }
    private fun notify(text: String, saving: Boolean) {
        if (notificationText == text) return
        notificationText = text
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_quick_settings)
            .setContentTitle("이어듣기 · 하이퍼랩스").setContentText(text).setContentIntent(open)
            .setOngoing(true).setSilent(true).build()
        val type = if (Build.VERSION.SDK_INT >= 30) {
            if (saving) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else if (Build.VERSION.SDK_INT >= 29 && saving) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, ID, notification, type)
    }
    override fun onDestroy() {
        controller.detach(this)
        super.onDestroy()
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        controller.disable()
        stopSelf()
    }
    companion object { private const val CHANNEL = "hyperlapse"; private const val ID = 502 }
}
