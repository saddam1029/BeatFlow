package com.example.beatflow.domain.usecase

import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.repository.SongRepository

/**
 * UseCase for fetching online songs from the repository.
 */
class GetOnlineSongsUseCase(
    private val repository: SongRepository
) {
    suspend operator fun invoke(term: String = "pop", limit: Int = 1000): Result<List<Song>> {
        return repository.getOnlineSongs(term = term, limit = limit)
    }
}
