package com.example.beatflow.domain.usecase

import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.repository.DeviceSongRepository

/**
 * UseCase for retrieving local device audio tracks from the repository.
 */
class GetDeviceSongsUseCase(
    private val repository: DeviceSongRepository
) {
    suspend operator fun invoke(): Result<List<Song>> {
        return repository.getDeviceSongs()
    }
}
