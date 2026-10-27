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

    private var allOnlineSongs: List<Song> = emptyList()
    private var currentSearchQuery: String = ""

    init {
        loadOnlineSongs()
    }

    fun selectTab(tab: HomeTab) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab

        when (tab) {
            HomeTab.ONLINE -> {
                if (allOnlineSongs.isNotEmpty()) {
                    filterSongs(currentSearchQuery)
                } else {
                    loadOnlineSongs()
                }
            }
            HomeTab.DEVICE -> {
                // Device songs feature is scoped for a future task
                _uiState.value = HomeUiState.Empty(
                    title = "No songs on this device",
                    message = "Music saved on your device\nwill appear here."
                )
            }
        }
    }

    fun loadOnlineSongs(term: String = "pop") {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            getOnlineSongsUseCase(term = term)
                .onSuccess { songs ->
                    allOnlineSongs = songs
                    if (songs.isEmpty()) {
                        _uiState.value = HomeUiState.Empty(
                            title = "No songs found",
                            message = "No songs found for \"$term\"."
                        )
                    } else if (currentSearchQuery.isNotEmpty()) {
                        filterSongs(currentSearchQuery)
                    } else {
                        _uiState.value = HomeUiState.Success(songs)
                    }
                }
                .onFailure { error ->
                    val isNetworkIssue = (error.message?.contains("Unable to resolve host", ignoreCase = true) == true) ||
                            (error.message?.contains("timeout", ignoreCase = true) == true) ||
                            (error.message?.contains("connect", ignoreCase = true) == true)

                    val title = if (isNetworkIssue) "You're offline" else "Unable to Load Songs"
                    val message = if (isNetworkIssue) {
                        "Check your internet connection\nand try again."
                    } else {
                        error.localizedMessage ?: "Failed to load online songs."
                    }

                    _uiState.value = HomeUiState.Error(
                        title = title,
                        message = message
                    )
                }
        }
    }

    fun filterSongs(query: String) {
        currentSearchQuery = query.trim()
        if (_selectedTab.value != HomeTab.ONLINE) return
        if (allOnlineSongs.isEmpty()) return

        val filtered = if (currentSearchQuery.isEmpty()) {
            allOnlineSongs
        } else {
            allOnlineSongs.filter { song ->
                song.trackName.contains(currentSearchQuery, ignoreCase = true) ||
                        song.artistName.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            _uiState.value = HomeUiState.Empty(
                title = "No songs found",
                message = "No songs match \"$currentSearchQuery\"."
            )
        } else {
            _uiState.value = HomeUiState.Success(filtered)
        }
    }

    fun clearSearch() {
        currentSearchQuery = ""
        if (allOnlineSongs.isNotEmpty()) {
            _uiState.value = HomeUiState.Success(allOnlineSongs)
        }
    }

    fun playSong(song: Song) {
        musicPlayer.togglePlayPause(song)
    }

    fun togglePlayPause() {
        musicPlayer.togglePlayPause()
    }
}
