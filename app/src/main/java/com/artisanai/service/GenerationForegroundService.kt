package com.artisanai.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.artisanai.MainActivity
import com.artisanai.R

/**
 * 保活型前台服务：在图片生成任务进行时显示持久通知，
 * 防止 Android 系统将 app 标记为后台进程并关闭 TCP socket。
 * 本服务不执行任何网络请求，业务逻辑仍在 ViewModel 的 viewModelScope 中。
 */
class GenerationForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ArtisanAI")
            .setContentText("正在生成图片，切换后台不影响进度...")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "artisan_generation_channel"
        const val NOTIFICATION_ID = 1001
    }
}
