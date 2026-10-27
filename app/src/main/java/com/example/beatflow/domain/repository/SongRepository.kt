package com.example.beatflow.domain.repository

import com.example.beatflow.domain.model.Song

/**
 * Domain repository abstraction for fetching songs.
 */
interface SongRepository {
    suspend fun getOnlineSongs(term: String = "pop", limit: Int = 1000): Result<List<Song>>
    suspend fun deleteOnlineSong(trackId: Long): Result<Unit>
}
