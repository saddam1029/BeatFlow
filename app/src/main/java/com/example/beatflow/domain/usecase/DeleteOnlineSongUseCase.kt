package com.example.beatflow.domain.usecase

import com.example.beatflow.domain.repository.SongRepository

/**
 * UseCase for deleting an online cached song from Room by its track ID.
 */
class DeleteOnlineSongUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke(trackId: Long): Result<Unit> {
        return repository.deleteOnlineSong(trackId)
    }
}
