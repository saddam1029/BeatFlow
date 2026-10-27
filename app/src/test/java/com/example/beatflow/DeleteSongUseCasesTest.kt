package com.example.beatflow

import com.example.beatflow.domain.model.DeleteDeviceSongResult
import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.repository.DeviceSongRepository
import com.example.beatflow.domain.repository.SongRepository
import com.example.beatflow.domain.usecase.DeleteDeviceSongUseCase
import com.example.beatflow.domain.usecase.DeleteOnlineSongUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteSongUseCasesTest {

    private class FakeSongRepository(
        var deleteResult: Result<Unit> = Result.success(Unit)
    ) : SongRepository {
        var lastDeletedTrackId: Long? = null
        override suspend fun getOnlineSongs(term: String, limit: Int): Result<List<Song>> = Result.success(emptyList())
        override suspend fun deleteOnlineSong(trackId: Long): Result<Unit> {
            lastDeletedTrackId = trackId
            return deleteResult
        }
    }

    private class FakeDeviceSongRepository(
        var deleteResult: DeleteDeviceSongResult = DeleteDeviceSongResult.Success,
        var confirmResult: Result<Unit> = Result.success(Unit)
    ) : DeviceSongRepository {
        var lastDeletedContentUri: String? = null
        var lastConfirmedContentUri: String? = null
        override suspend fun getDeviceSongs(): Result<List<Song>> = Result.success(emptyList())
        override suspend fun deleteDeviceSong(contentUri: String): DeleteDeviceSongResult {
            lastDeletedContentUri = contentUri
            return deleteResult
        }
        override suspend fun deleteDeviceSongConfirmed(contentUri: String): Result<Unit> {
            lastConfirmedContentUri = contentUri
            return confirmResult
        }
    }

    @Test
    fun test_deleteOnlineSongUseCase_success() = runBlocking {
        val repo = FakeSongRepository(Result.success(Unit))
        val useCase = DeleteOnlineSongUseCase(repo)

        val result = useCase(12345L)

        assertTrue(result.isSuccess)
        assertEquals(12345L, repo.lastDeletedTrackId)
    }

    @Test
    fun test_deleteOnlineSongUseCase_failure() = runBlocking {
        val exception = NoSuchElementException("Song not found")
        val repo = FakeSongRepository(Result.failure(exception))
        val useCase = DeleteOnlineSongUseCase(repo)

        val result = useCase(999L)

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun test_deleteDeviceSongUseCase_success() = runBlocking {
        val repo = FakeDeviceSongRepository(DeleteDeviceSongResult.Success)
        val useCase = DeleteDeviceSongUseCase(repo)

        val result = useCase("content://media/external/audio/media/10")

        assertEquals(DeleteDeviceSongResult.Success, result)
        assertEquals("content://media/external/audio/media/10", repo.lastDeletedContentUri)
    }

    @Test
    fun test_deleteDeviceSongUseCase_requiresConfirmation() = runBlocking {
        val fakeSender = Any()
        val repo = FakeDeviceSongRepository(DeleteDeviceSongResult.RequiresConfirmation(fakeSender))
        val useCase = DeleteDeviceSongUseCase(repo)

        val result = useCase("content://media/external/audio/media/20")

        assertTrue(result is DeleteDeviceSongResult.RequiresConfirmation)
        assertEquals(fakeSender, (result as DeleteDeviceSongResult.RequiresConfirmation).intentSender)
    }

    @Test
    fun test_deleteDeviceSongUseCase_failure() = runBlocking {
        val repo = FakeDeviceSongRepository(DeleteDeviceSongResult.Failure("Permission denied"))
        val useCase = DeleteDeviceSongUseCase(repo)

        val result = useCase("content://media/external/audio/media/30")

        assertTrue(result is DeleteDeviceSongResult.Failure)
        assertEquals("Permission denied", (result as DeleteDeviceSongResult.Failure).message)
    }

    @Test
    fun test_confirmDelete_callsRepository() = runBlocking {
        val repo = FakeDeviceSongRepository()
        val useCase = DeleteDeviceSongUseCase(repo)

        val result = useCase.confirmDelete("content://media/external/audio/media/40")

        assertTrue(result.isSuccess)
        assertEquals("content://media/external/audio/media/40", repo.lastConfirmedContentUri)
    }
}
