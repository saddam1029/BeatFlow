package com.example.beatflow.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beatflow.data.di.NetworkModule
import com.example.beatflow.domain.model.DeleteDeviceSongResult
import com.example.beatflow.domain.model.HomeTab
import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.playback.MusicPlayer
import com.example.beatflow.domain.usecase.DeleteDeviceSongUseCase
import com.example.beatflow.domain.usecase.DeleteOnlineSongUseCase
import com.example.beatflow.domain.usecase.GetDeviceSongsUseCase
import com.example.beatflow.domain.usecase.GetOnlineSongsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getOnlineSongsUseCase: GetOnlineSongsUseCase = NetworkModule.provideGetOnlineSongsUseCase(),
    private val getDeviceSongsUseCase: GetDeviceSongsUseCase = NetworkModule.provideGetDeviceSongsUseCase(),
    private val deleteOnlineSongUseCase: DeleteOnlineSongUseCase = NetworkModule.provideDeleteOnlineSongUseCase(),
    private val deleteDeviceSongUseCase: DeleteDeviceSongUseCase = NetworkModule.provideDeleteDeviceSongUseCase(),
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
    val playbackProgress: StateFlow<Int> = musicPlayer.playbackProgress
    val playbackError: StateFlow<String?> = musicPlayer.playbackError

    // Independent caches for Online and Device songs to ensure lists never mix
    private var allOnlineSongs: List<Song> = emptyList()
    private var allDeviceSongs: List<Song> = emptyList()

    private var onlineUiState: HomeUiState = HomeUiState.Loading
    private var deviceUiState: HomeUiState = HomeUiState.Empty(
        title = "No songs on this device",
        message = "Music saved on your device\nwill appear here."
    )

    var hasDeviceSongsLoaded: Boolean = false
        private set

    // Guard against duplicate deletion requests while in progress
    var isDeleteInProgress: Boolean = false
        private set

    private var currentSearchQuery: String = ""

    init {
        loadOnlineSongs()
    }

    fun selectTab(tab: HomeTab) {
        if (_selectedTab.value == tab) return
        _selectedTab.value = tab

        when (tab) {
            HomeTab.ONLINE -> {
                _uiState.value = onlineUiState
                if (allOnlineSongs.isEmpty()) {
                    loadOnlineSongs()
                } else if (currentSearchQuery.isNotEmpty()) {
                    filterSongs(currentSearchQuery)
                }
            }
            HomeTab.DEVICE -> {
                _uiState.value = deviceUiState
                if (allDeviceSongs.isNotEmpty() && currentSearchQuery.isNotEmpty()) {
                    filterSongs(currentSearchQuery)
                }
            }
        }
    }

    fun loadOnlineSongs(term: String = "pop") {
        viewModelScope.launch {
            val loading = HomeUiState.Loading
            onlineUiState = loading
            if (_selectedTab.value == HomeTab.ONLINE) {
                _uiState.value = loading
            }

            getOnlineSongsUseCase(term = term)
                .onSuccess { songs ->
                    allOnlineSongs = songs
                    val newState = if (songs.isEmpty()) {
                        HomeUiState.Empty(
                            title = "No songs found",
                            message = "No songs found for \"$term\"."
                        )
                    } else if (currentSearchQuery.isNotEmpty() && _selectedTab.value == HomeTab.ONLINE) {
                        filterList(songs, currentSearchQuery)
                    } else {
                        HomeUiState.Success(songs)
                    }
                    onlineUiState = newState
                    if (_selectedTab.value == HomeTab.ONLINE) {
                        _uiState.value = newState
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

                    val newState = HomeUiState.Error(
                        title = title,
                        message = message
                    )
                    onlineUiState = newState
                    if (_selectedTab.value == HomeTab.ONLINE) {
                        _uiState.value = newState
                    }
                }
        }
    }

    fun loadDeviceSongs() {
        viewModelScope.launch {
            val loading = HomeUiState.Loading
            deviceUiState = loading
            if (_selectedTab.value == HomeTab.DEVICE) {
                _uiState.value = loading
            }

            getDeviceSongsUseCase()
                .onSuccess { songs ->
                    allDeviceSongs = songs
                    hasDeviceSongsLoaded = true
                    val newState = if (songs.isEmpty()) {
                        HomeUiState.Empty(
                            title = "No songs on this device",
                            message = "Music saved on your device\nwill appear here."
                        )
                    } else if (currentSearchQuery.isNotEmpty() && _selectedTab.value == HomeTab.DEVICE) {
                        filterList(songs, currentSearchQuery)
                    } else {
                        HomeUiState.Success(songs)
                    }
                    deviceUiState = newState
                    if (_selectedTab.value == HomeTab.DEVICE) {
                        _uiState.value = newState
                    }
                }
                .onFailure { error ->
                    val isSecurity = error is SecurityException
                    val newState = if (isSecurity) {
                        HomeUiState.PermissionRequired(
                            title = "Permission Required",
                            message = "Audio access permission is needed to display and play songs saved on your device."
                        )
                    } else {
                        HomeUiState.Error(
                            title = "Unable to Load Songs",
                            message = error.localizedMessage ?: "Failed to read device audio files."
                        )
                    }
                    deviceUiState = newState
                    if (_selectedTab.value == HomeTab.DEVICE) {
                        _uiState.value = newState
                    }
                }
        }
    }

    fun onPermissionDenied(isPermanentlyDenied: Boolean) {
        val message = if (isPermanentlyDenied) {
            "Audio access permission was denied. Please allow audio access in App Settings to play your local music."
        } else {
            "Audio access permission is needed to display and play songs saved on your device."
        }
        val newState = HomeUiState.PermissionRequired(
            title = "Permission Required",
            message = message,
            isPermanentlyDenied = isPermanentlyDenied
        )
        deviceUiState = newState
        if (_selectedTab.value == HomeTab.DEVICE) {
            _uiState.value = newState
        }
    }

    /**
     * Deletes an online cached song from Room database.
     * Safely halts playback if the deleted song is currently active.
     */
    fun deleteOnlineSong(song: Song, onComplete: (Result<Unit>) -> Unit) {
        if (isDeleteInProgress) return
        isDeleteInProgress = true

        viewModelScope.launch {
            try {
                // If the deleted song is currently playing, safely stop playback
                if (currentPlayingSong.value?.trackId == song.trackId) {
                    musicPlayer.stop()
                }

                deleteOnlineSongUseCase(song.trackId)
                    .onSuccess {
                        allOnlineSongs = allOnlineSongs.filter { it.trackId != song.trackId }
                        val newState = if (allOnlineSongs.isEmpty()) {
                            HomeUiState.Empty(
                                title = "No songs found",
                                message = "No songs available in online cache."
                            )
                        } else if (currentSearchQuery.isNotEmpty() && _selectedTab.value == HomeTab.ONLINE) {
                            filterList(allOnlineSongs, currentSearchQuery)
                        } else {
                            HomeUiState.Success(allOnlineSongs)
                        }
                        onlineUiState = newState
                        if (_selectedTab.value == HomeTab.ONLINE) {
                            _uiState.value = newState
                        }
                        onComplete(Result.success(Unit))
                    }
                    .onFailure { error ->
                        onComplete(Result.failure(error))
                    }
            } finally {
                isDeleteInProgress = false
            }
        }
    }

    /**
     * Initiates deletion of a local device song via MediaStore.
     * If user confirmation is required (Android 10+ scoped storage), invokes [onRequiresConfirmation].
     */
    fun deleteDeviceSong(
        song: Song,
        onRequiresConfirmation: (Any) -> Unit,
        onComplete: (Result<Unit>) -> Unit
    ) {
        if (isDeleteInProgress) return
        isDeleteInProgress = true

        viewModelScope.launch {
            try {
                val previewUrl = song.previewUrl ?: run {
                    isDeleteInProgress = false
                    onComplete(Result.failure(IllegalArgumentException("Song content URI is missing")))
                    return@launch
                }

                when (val result = deleteDeviceSongUseCase(previewUrl)) {
                    is DeleteDeviceSongResult.Success -> {
                        isDeleteInProgress = false
                        onDeviceSongDeleteConfirmed(song)
                        onComplete(Result.success(Unit))
                    }
                    is DeleteDeviceSongResult.RequiresConfirmation -> {
                        // Keep isDeleteInProgress until user confirms or denies
                        onRequiresConfirmation(result.intentSender)
                    }
                    is DeleteDeviceSongResult.Failure -> {
                        isDeleteInProgress = false
                        onComplete(Result.failure(Exception(result.message)))
                    }
                }
            } catch (e: Exception) {
                isDeleteInProgress = false
                onComplete(Result.failure(e))
            }
        }
    }

    /**
     * Invoked when deletion of a local audio file is confirmed by the system.
     */
    fun onDeviceSongDeleteConfirmed(song: Song) {
        isDeleteInProgress = false
        // Safely halt playback if deleted song is currently playing
        if (currentPlayingSong.value?.trackId == song.trackId) {
            musicPlayer.stop()
        }

        allDeviceSongs = allDeviceSongs.filter { it.trackId != song.trackId }
        val newState = if (allDeviceSongs.isEmpty()) {
            HomeUiState.Empty(
                title = "No songs on this device",
                message = "Music saved on your device\nwill appear here."
            )
        } else if (currentSearchQuery.isNotEmpty() && _selectedTab.value == HomeTab.DEVICE) {
            filterList(allDeviceSongs, currentSearchQuery)
        } else {
            HomeUiState.Success(allDeviceSongs)
        }
        deviceUiState = newState
        if (_selectedTab.value == HomeTab.DEVICE) {
            _uiState.value = newState
        }
    }

    fun onDeviceSongDeleteCancelled() {
        isDeleteInProgress = false
    }

    fun filterSongs(query: String) {
        currentSearchQuery = query.trim()
        val sourceList = when (_selectedTab.value) {
            HomeTab.ONLINE -> allOnlineSongs
            HomeTab.DEVICE -> allDeviceSongs
        }

        if (sourceList.isEmpty()) return

        val newState = filterList(sourceList, currentSearchQuery)
        if (_selectedTab.value == HomeTab.ONLINE) {
            onlineUiState = newState
        } else {
            deviceUiState = newState
        }
        _uiState.value = newState
    }

    private fun filterList(source: List<Song>, query: String): HomeUiState {
        if (query.isEmpty()) return HomeUiState.Success(source)

        val filtered = source.filter { song ->
            song.trackName.contains(query, ignoreCase = true) ||
                    song.artistName.contains(query, ignoreCase = true)
        }

        return if (filtered.isEmpty()) {
            HomeUiState.Empty(
                title = "No songs found",
                message = "No songs match \"$query\"."
            )
        } else {
            HomeUiState.Success(filtered)
        }
    }

    fun clearSearch() {
        currentSearchQuery = ""
        val sourceList = when (_selectedTab.value) {
            HomeTab.ONLINE -> allOnlineSongs
            HomeTab.DEVICE -> allDeviceSongs
        }
        if (sourceList.isNotEmpty()) {
            val newState = HomeUiState.Success(sourceList)
            if (_selectedTab.value == HomeTab.ONLINE) {
                onlineUiState = newState
            } else {
                deviceUiState = newState
            }
            _uiState.value = newState
        }
    }

    fun playSong(song: Song) {
        musicPlayer.togglePlayPause(song)
    }

    fun togglePlayPause() {
        musicPlayer.togglePlayPause()
    }
}
