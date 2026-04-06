package com.artisanai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
import com.artisanai.data.local.ArtisanDatabase
import com.artisanai.service.GenerationForegroundService
import com.artisanai.util.ApiKeyManager

class ArtisanApp : Application() {

    lateinit var database: ArtisanDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = Room.databaseBuilder(
            applicationContext,
            ArtisanDatabase::class.java,
            ArtisanDatabase.DATABASE_NAME
        ).build()
        ApiKeyManager.init(applicationContext)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(GenerationForegroundService.CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                GenerationForegroundService.CHANNEL_ID,
                "图片生成进度",
                NotificationManager.IMPORTANCE_LOW  // 低优先级：静默，不发声
            ).apply {
                description = "显示图片生成进度，切换到后台时保持连接"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private lateinit var instance: ArtisanApp

        fun getDatabase(): ArtisanDatabase = instance.database
    }
}

