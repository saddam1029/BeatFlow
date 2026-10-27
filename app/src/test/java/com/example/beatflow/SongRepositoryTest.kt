package com.example.beatflow

import com.example.beatflow.data.local.dao.SongDao
import com.example.beatflow.data.local.entity.SongEntity
import com.example.beatflow.data.mapper.toDomain
import com.example.beatflow.data.mapper.toEntity
import com.example.beatflow.data.remote.ItunesApiService
import com.example.beatflow.data.remote.dto.ItunesResponseDto
import com.example.beatflow.data.remote.dto.SongDto
import com.example.beatflow.data.repository.SongRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class SongRepositoryTest {

    private lateinit var fakeDao: FakeSongDao
    private lateinit var fakeApi: FakeItunesApiService
    private lateinit var repository: SongRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeSongDao()
        fakeApi = FakeItunesApiService()
        repository = SongRepositoryImpl(
            apiService = fakeApi,
            songDao = fakeDao,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun test_whenDatabaseContainsSongs_returnsCachedSongsWithoutCallingApi() = runBlocking {
        // Arrange: Pre-populate Room with 1 song
        val cachedEntity = SongEntity(
            trackId = 101L,
            trackName = "Cached Song",
            artistName = "Cached Artist",
            artworkUrl = "http://artwork.com/101.jpg",
            previewUrl = "http://preview.com/101.m4a",
            trackViewUrl = "http://view.com/101"
        )
        fakeDao.insertSongs(listOf(cachedEntity))

        // Act
        val result = repository.getOnlineSongs()

        // Assert
        assertTrue(result.isSuccess)
        val songs = result.getOrNull()
        assertEquals(1, songs?.size)
        assertEquals("Cached Song", songs?.first()?.trackName)
        assertEquals(0, fakeApi.apiCallCount) // API was never called!
    }

    @Test
    fun test_whenDatabaseIsEmpty_callsApiAndSavesSongsToRoom() = runBlocking {
        // Arrange: Room is empty, API returns 1 song
        val apiDto = SongDto(
            trackId = 202L,
            trackName = "API Song",
            artistName = "API Artist",
            artworkUrl100 = "http://artwork.com/202.jpg",
            artworkUrl60 = null,
            previewUrl = "http://preview.com/202.m4a",
            trackViewUrl = "http://view.com/202"
        )
        fakeApi.responseDto = ItunesResponseDto(resultCount = 1, results = listOf(apiDto))

        // Act
        val result = repository.getOnlineSongs()

        // Assert
        assertTrue(result.isSuccess)
        val songs = result.getOrNull()
        assertEquals(1, songs?.size)
        assertEquals("API Song", songs?.first()?.trackName)
        assertEquals(1, fakeApi.apiCallCount) // API was called
        assertEquals(1, fakeDao.getSongCount()) // Songs were saved in Room
        assertEquals("API Song", fakeDao.getAllSongs().first().trackName)
    }

    @Test
    fun test_whenDatabaseIsEmptyAndApiFails_returnsFailureAndLeavesRoomEmpty() = runBlocking {
        // Arrange: API throws network error
        fakeApi.shouldFail = true

        // Act
        val result = repository.getOnlineSongs()

        // Assert
        assertTrue(result.isFailure)
        assertEquals(0, fakeDao.getSongCount()) // Database remains empty for future retry!
        assertEquals(1, fakeApi.apiCallCount)
    }

    @Test
    fun test_mapperAccuratelyMapsBetweenDtoDomainAndEntity() {
        val dto = SongDto(
            trackId = 555L,
            trackName = "Test Track",
            artistName = "Test Artist",
            artworkUrl100 = "http://artwork.com/100.jpg",
            artworkUrl60 = "http://artwork.com/60.jpg",
            previewUrl = "http://preview.com/555.m4a",
            trackViewUrl = "http://view.com/555"
        )

        // DTO to Domain
        val domain = dto.toDomain()
        assertEquals(555L, domain?.trackId)
        assertEquals("Test Track", domain?.trackName)
        assertEquals("Test Artist", domain?.artistName)
        assertEquals("http://artwork.com/100.jpg", domain?.artworkUrl)

        // Domain to Entity
        val entity = domain!!.toEntity()
        assertEquals(555L, entity.trackId)
        assertEquals("Test Track", entity.trackName)

        // Entity back to Domain
        val domainFromEntity = entity.toDomain()
        assertEquals(domain, domainFromEntity)
    }

    @Test
    fun test_getHighResArtworkUrl() {
        // iTunes 100x100 upgraded to 500x500
        val itunes100 = "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/source/100x100bb.jpg"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/source/500x500bb.jpg",
            com.example.beatflow.data.mapper.getHighResArtworkUrl(itunes100)
        )

        // iTunes 60x60 upgraded to 500x500
        val itunes60 = "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/source/60x60bb.png"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music115/v4/source/500x500bb.png",
            com.example.beatflow.data.mapper.getHighResArtworkUrl(itunes60)
        )

        // Non-iTunes valid URL preserved
        val customUrl = "https://example.com/custom_album_art.jpg"
        assertEquals(customUrl, com.example.beatflow.data.mapper.getHighResArtworkUrl(customUrl))

        // Null and blank URLs return null
        org.junit.Assert.assertNull(com.example.beatflow.data.mapper.getHighResArtworkUrl(null))
        org.junit.Assert.assertNull(com.example.beatflow.data.mapper.getHighResArtworkUrl(""))
        org.junit.Assert.assertNull(com.example.beatflow.data.mapper.getHighResArtworkUrl("   "))

        // Malformed non-HTTP URL returns null
        org.junit.Assert.assertNull(com.example.beatflow.data.mapper.getHighResArtworkUrl("file://local/art.png"))
        org.junit.Assert.assertNull(com.example.beatflow.data.mapper.getHighResArtworkUrl("invalid_url"))
    }
}

// --- Test Fakes ---

private class FakeSongDao : SongDao {
    private val storage = mutableMapOf<Long, SongEntity>()

    override suspend fun insertSongs(songs: List<SongEntity>): List<Long> {
        songs.forEach { storage[it.trackId] = it }
        return songs.map { it.trackId }
    }

    override suspend fun getAllSongs(): List<SongEntity> = storage.values.toList()

    override suspend fun getSongCount(): Int = storage.size

    override suspend fun deleteSongById(trackId: Long): Int {
        return if (storage.remove(trackId) != null) 1 else 0
    }

    override suspend fun deleteAllSongs(): Int {
        val count = storage.size
        storage.clear()
        return count
    }
}

private class FakeItunesApiService : ItunesApiService {
    var apiCallCount = 0
    var shouldFail = false
    var responseDto = ItunesResponseDto(resultCount = 0, results = emptyList())

    override suspend fun searchSongs(
        term: String,
        entity: String,
        limit: Int
    ): Response<ItunesResponseDto> {
        apiCallCount++
        if (shouldFail) {
            throw IOException("Network error: Host unreachable")
        }
        return Response.success(responseDto)
    }
}
