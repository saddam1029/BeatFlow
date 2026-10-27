package com.example.beatflow.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.beatflow.data.model.HomeTab
import com.example.beatflow.data.model.Song
import com.example.beatflow.data.repository.SampleSongRepository

class HomeViewModel(
    private val repository: SampleSongRepository = SampleSongRepository()
) : ViewModel() {

    private val _selectedTab = MutableLiveData(HomeTab.ONLINE)
    val selectedTab: LiveData<HomeTab> = _selectedTab

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> = _songs

    private val _currentPlayingSong = MutableLiveData<Song?>()
    val currentPlayingSong: LiveData<Song?> = _currentPlayingSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    init {
        loadSongsForTab(HomeTab.ONLINE)
    }

    fun selectTab(tab: HomeTab) {
        if (_selectedTab.value != tab) {
            _selectedTab.value = tab
            loadSongsForTab(tab)
        }
    }

    private fun loadSongsForTab(tab: HomeTab) {
        _songs.value = repository.getSongsForTab(tab)
    }

    fun playSong(song: Song) {
        if (_currentPlayingSong.value?.id == song.id) {
            _isPlaying.value = !(_isPlaying.value ?: false)
        } else {
            _currentPlayingSong.value = song
            _isPlaying.value = true
        }
    }

    fun togglePlayPause() {
        if (_currentPlayingSong.value != null) {
            _isPlaying.value = !(_isPlaying.value ?: false)
        }
    }
}
