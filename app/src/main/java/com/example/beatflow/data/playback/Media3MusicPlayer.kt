package com.example.beatflow.data.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.playback.MusicPlayer
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Media3 implementation of MusicPlayer that controls playback by binding to
 * PlaybackService through a MediaController.
 */
class Media3MusicPlayer(
    private val context: Context
) : MusicPlayer {

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    override val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // Cache to look up Song when MediaController reports active MediaItem
    private val songCache = mutableMapOf<Long, Song>()

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    val controller = controllerFuture?.get() ?: return@addListener
                    mediaController = controller
                    setupPlayerListener(controller)
                    syncPlayerState(controller)
                } catch (e: Exception) {
                    _playbackError.value = "Failed to connect to playback service: ${e.localizedMessage}"
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _isPlaying.value = false
                _isBuffering.value = false
                _playbackError.value = "Playback error: ${error.localizedMessage ?: "Unable to stream audio preview"}"
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId?.toLongOrNull()
                if (trackId != null && songCache.containsKey(trackId)) {
                    _currentSong.value = songCache[trackId]
                }
            }
        })
    }

    private fun syncPlayerState(player: Player) {
        _isPlaying.value = player.isPlaying
        _isBuffering.value = (player.playbackState == Player.STATE_BUFFERING)
        val currentTrackId = player.currentMediaItem?.mediaId?.toLongOrNull()
        if (currentTrackId != null && songCache.containsKey(currentTrackId)) {
            _currentSong.value = songCache[currentTrackId]
        }
    }

    override fun playSong(song: Song) {
        _playbackError.value = null

        val previewUrl = song.previewUrl?.trim()
        if (previewUrl.isNullOrEmpty() || (!previewUrl.startsWith("http://", ignoreCase = true) && !previewUrl.startsWith("https://", ignoreCase = true))) {
            _playbackError.value = "Audio preview is not available for \"${song.trackName}\"."
            return
        }

        songCache[song.trackId] = song
        _currentSong.value = song

        val controller = mediaController
        if (controller != null) {
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.trackName)
                .setArtist(song.artistName)
                .setArtworkUri(song.artworkUrl?.let { Uri.parse(it) })
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(song.trackId.toString())
                .setUri(previewUrl)
                .setMediaMetadata(mediaMetadata)
                .build()

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        } else {
            // Re-attempt controller connection if null
            initializeController()
        }
    }

    override fun pause() {
        mediaController?.pause()
    }

    override fun resume() {
        mediaController?.play()
    }

    override fun togglePlayPause(song: Song?) {
        if (song != null && song.trackId != _currentSong.value?.trackId) {
            playSong(song)
        } else {
            if (_isPlaying.value) {
                pause()
            } else {
                if (_currentSong.value != null) {
                    resume()
                } else if (song != null) {
                    playSong(song)
                }
            }
        }
    }

    override fun stop() {
        mediaController?.stop()
        _currentSong.value = null
        _isPlaying.value = false
    }

    override fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
