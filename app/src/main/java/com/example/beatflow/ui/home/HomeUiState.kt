package com.example.beatflow.ui.home

import com.example.beatflow.domain.model.Song

/**
 * Represents the distinct UI states for the Home Screen song list.
 */
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val songs: List<Song>) : HomeUiState
    data class Empty(val title: String, val message: String) : HomeUiState
    data class Error(val title: String, val message: String) : HomeUiState
}
