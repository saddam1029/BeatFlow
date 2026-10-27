package com.example.beatflow.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.beatflow.data.local.dao.SongDao
import com.example.beatflow.data.local.entity.SongEntity

/**
 * Room Database for offline caching of songs.
 */
@Database(
    entities = [SongEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SongDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        const val DATABASE_NAME = "music_player_database"

        @Volatile
        private var instance: SongDatabase? = null

        fun getInstance(context: Context): SongDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SongDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
