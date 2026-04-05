package com.artisanai

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.artisanai.data.local.ArtisanDatabase

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
    }

    companion object {
        private lateinit var instance: ArtisanApp

        fun getDatabase(): ArtisanDatabase = instance.database

        // API Key 持久化存储（SharedPreferences加密存储）
        fun saveApiKey(context: Context, key: String) {
            context.getSharedPreferences("artisan_prefs", Context.MODE_PRIVATE)
                .edit().putString("api_key", key).apply()
        }

        fun getApiKey(context: Context): String {
            return context.getSharedPreferences("artisan_prefs", Context.MODE_PRIVATE)
                .getString("api_key", "") ?: ""
        }

        fun hasApiKey(context: Context): Boolean {
            return getApiKey(context).isNotBlank()
        }
    }
}
