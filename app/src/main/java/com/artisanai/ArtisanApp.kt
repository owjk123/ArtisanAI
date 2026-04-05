package com.artisanai

import android.app.Application
import androidx.room.Room
import com.artisanai.data.local.ArtisanDatabase
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
    }

    companion object {
        private lateinit var instance: ArtisanApp

        fun getDatabase(): ArtisanDatabase = instance.database
    }
}
