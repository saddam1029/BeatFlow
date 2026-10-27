package com.example.beatflow

import com.example.beatflow.domain.model.DeleteDeviceSongResult
import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.repository.DeviceSongRepository
import com.example.beatflow.domain.usecase.GetDeviceSongsUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetDeviceSongsUseCaseTest {

    private class FakeDeviceSongRepository(
        var result: Result<List<Song>>
    ) : DeviceSongRepository {
        override suspend fun getDeviceSongs(): Result<List<Song>> = result
        override suspend fun deleteDeviceSong(contentUri: String): DeleteDeviceSongResult = DeleteDeviceSongResult.Success
    }

    @Test
    fun test_invoke_success_returnsLocalSongs() = runBlocking {
        val expectedSongs = listOf(
            Song(
                trackId = 1L,
                trackName = "Local Track 1",
                artistName = "Local Artist 1",
                artworkUrl = "content://media/external/audio/albumart/10",
                previewUrl = "content://media/external/audio/media/1",
                trackViewUrl = null,
                durationMs = 180000L
            ),
            Song(
                trackId = 2L,
                trackName = "Local Track 2",
                artistName = "Local Artist 2",
                artworkUrl = null,
                previewUrl = "content://media/external/audio/media/2",
                trackViewUrl = null,
                durationMs = 240000L
            )
        )

        val repository = FakeDeviceSongRepository(Result.success(expectedSongs))
        val useCase = GetDeviceSongsUseCase(repository)

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
        assertEquals("Local Track 1", result.getOrNull()?.get(0)?.trackName)
        assertEquals("content://media/external/audio/media/1", result.getOrNull()?.get(0)?.previewUrl)
        assertEquals(180000L, result.getOrNull()?.get(0)?.durationMs)
    }

    @Test
    fun test_invoke_failure_returnsError() = runBlocking {
        val securityException = SecurityException("Permission denied")
        val repository = FakeDeviceSongRepository(Result.failure(securityException))
        val useCase = GetDeviceSongsUseCase(repository)

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(securityException, result.exceptionOrNull())
    }
}
