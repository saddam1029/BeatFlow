package com.example.beatflow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beatflow.data.di.NetworkModule
import com.example.beatflow.domain.model.HomeTab
import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.playback.MusicPlayer
import com.example.beatflow.domain.usecase.GetOnlineSongsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getOnlineSongsUseCase: GetOnlineSongsUseCase = NetworkModule.provideGetOnlineSongsUseCase(),
    private val musicPlayer: MusicPlayer = NetworkModule.provideMusicPlayer()
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(HomeTab.ONLINE)
    val selectedTab: StateFlow<HomeTab> = _selectedTab.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Playback state exposed directly from the music player
    val currentPlayingSong: StateFlow<Song?> = musicPlayer.currentSong
    val isPlaying: StateFlow<Boolean> = musicPlayer.isPlaying
    val isBuffering: StateFlow<Boolean> = musicPlayer.isBuffering
    val playbackError: StateFlow<String?> = musicPlayer.playbackError

    init {
        loadOnlineSongs()
    }

    fun selectTab(tab: HomeTab) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab

        when (tab) {
            HomeTab.ONLINE -> loadOnlineSongs()
            HomeTab.DEVICE -> {
                // Device songs feature is scoped for a future task
                _uiState.value = HomeUiState.Empty(
                    title = "No Songs Available",
                    message = "No songs found on your device yet."
                )
            }
        }
    }

    fun loadOnlineSongs(term: String = "pop") {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            getOnlineSongsUseCase(term = term)
                .onSuccess { songs ->
                    if (songs.isEmpty()) {
                        _uiState.value = HomeUiState.Empty(
                            title = "No Songs Available",
                            message = "No songs found for \"$term\"."
                        )
                    } else {
                        _uiState.value = HomeUiState.Success(songs)
                    }
                }
                .onFailure { error ->
                    val readableMessage = when {
                        error.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                            "No internet connection. Please check your network and try again."
                        error.message?.contains("timeout", ignoreCase = true) == true ->
                            "Connection timed out. Please try again."
                        else -> error.localizedMessage ?: "Failed to load online songs."
                    }
                    _uiState.value = HomeUiState.Error(
                        title = "Unable to Load Songs",
                        message = readableMessage
                    )
                }
        }
    }

    fun playSong(song: Song) {
        musicPlayer.togglePlayPause(song)
    }

    fun togglePlayPause() {
        musicPlayer.togglePlayPause()
    }
}
