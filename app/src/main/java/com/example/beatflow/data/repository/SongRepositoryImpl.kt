package com.example.beatflow.data.repository

import com.example.beatflow.data.local.dao.SongDao
import com.example.beatflow.data.mapper.toDomain
import com.example.beatflow.data.mapper.toEntities
import com.example.beatflow.data.remote.ItunesApiService
import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.repository.SongRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Concrete implementation of SongRepository that supports local Room caching.
 *
 * Caching logic:
 * 1. Checks if Room already contains songs.
 * 2. If cached songs exist, returns them immediately without making network calls.
 * 3. If Room is empty, fetches songs from ItunesApiService.
 * 4. Caches the fetched songs into Room.
 * 5. If the network call fails, returns an error Result while leaving Room empty for subsequent retries.
 */
class SongRepositoryImpl(
    private val apiService: ItunesApiService,
    private val songDao: SongDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SongRepository {

    override suspend fun getOnlineSongs(term: String, limit: Int): Result<List<Song>> = withContext(ioDispatcher) {
        try {
            // 1. Check whether songs already exist in Room
            val localCount = songDao.getSongCount()
            if (localCount > 0) {
                // 2. Return cached songs without hitting the API
                val cachedSongs = songDao.getAllSongs().toDomain()
                return@withContext Result.success(cachedSongs)
            }

            // 3. Database is empty; fetch from the remote API
            val response = apiService.searchSongs(term = term, entity = "song", limit = limit)
            if (response.isSuccessful) {
                // 4. Map API DTOs to Domain models
                val domainSongs = response.body()?.results.toDomain()

                // 5. Save the fetched songs into Room
                if (domainSongs.isNotEmpty()) {
                    songDao.insertSongs(domainSongs.toEntities())
                }

                // 6. Return the saved songs
                Result.success(domainSongs)
            } else {
                // 7. Request failed; leave Room empty for future retry
                val errorMsg = "HTTP ${response.code()}: ${response.message().ifBlank { "Unknown server error" }}"
                Result.failure(IOException(errorMsg))
            }
        } catch (e: Exception) {
            // Network/deserialization failure; leave Room empty
            Result.failure(e)
        }
    }
}
