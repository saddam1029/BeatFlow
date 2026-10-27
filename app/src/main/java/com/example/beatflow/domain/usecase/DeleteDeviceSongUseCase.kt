package com.example.beatflow.domain.usecase

import com.example.beatflow.domain.model.DeleteDeviceSongResult
import com.example.beatflow.domain.repository.DeviceSongRepository

/**
 * UseCase for deleting a local device audio file from storage via MediaStore.
 */
class DeleteDeviceSongUseCase(
    private val repository: DeviceSongRepository
) {
    suspend operator fun invoke(contentUri: String): DeleteDeviceSongResult {
        return repository.deleteDeviceSong(contentUri)
    }
}
