package com.example.beatflow.domain.playback

import com.example.beatflow.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-level contract for music playback operations.
 * Completely independent of Android framework and ExoPlayer classes.
 */
interface MusicPlayer {
    val currentSong: StateFlow<Song?>
    val isPlaying: StateFlow<Boolean>
    val isBuffering: StateFlow<Boolean>
    val playbackError: StateFlow<String?>

    fun playSong(song: Song)
    fun pause()
    fun resume()
    fun togglePlayPause(song: Song? = null)
    fun stop()
    fun release()
}
