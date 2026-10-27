package com.example.beatflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.beatflow.data.local.entity.SongEntity

/**
 * Data Access Object for local song caching and querying.
 */
@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>): List<Long>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int

    @Query("DELETE FROM songs WHERE trackId = :trackId")
    suspend fun deleteSongById(trackId: Long): Int

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs(): Int
}
