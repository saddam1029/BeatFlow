package com.example.beatflow.data.playback

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Media3 implementation of MusicPlayer that controls playback by binding to
 * PlaybackService through a MediaController.
 */
class Media3MusicPlayer(
    private val context: Context
) : MusicPlayer {

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressUpdateJob: Job? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    override val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0)
    override val playbackProgress: StateFlow<Int> = _playbackProgress.asStateFlow()

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
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_ENDED) {
                    _isPlaying.value = false
                    _playbackProgress.value = 100
                    stopProgressTracker()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _isPlaying.value = false
                _isBuffering.value = false
                stopProgressTracker()
                val errorMsg = error.localizedMessage?.lowercase(Locale.ROOT) ?: ""
                val isNetworkErr = errorMsg.contains("unable to resolve host") ||
                        errorMsg.contains("connect") ||
                        errorMsg.contains("timeout") ||
                        errorMsg.contains("network") ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

                _playbackError.value = if (isNetworkErr) {
                    "No internet connection. Music can't play."
                } else {
                    "Playback error: ${error.localizedMessage ?: "Unable to stream audio preview"}"
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val trackId = mediaItem?.mediaId?.toLongOrNull()
                if (trackId != null && songCache.containsKey(trackId)) {
                    _currentSong.value = songCache[trackId]
                    _playbackProgress.value = 0
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
        if (player.isPlaying) {
            startProgressTracker()
        }
    }

    private fun startProgressTracker() {
        progressUpdateJob?.cancel()
        progressUpdateJob = playerScope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val duration = if (controller.duration > 0) controller.duration else (_currentSong.value?.durationMs ?: 0L)
                    val position = controller.currentPosition
                    if (duration > 0) {
                        val progressPercent = ((position.toDouble() / duration.toDouble()) * 100).toInt()
                        _playbackProgress.value = progressPercent.coerceIn(0, 100)
                    } else {
                        _playbackProgress.value = 0
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracker() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    override fun playSong(song: Song, queue: List<Song>) {
        _playbackError.value = null
        _playbackProgress.value = 0

        val previewUrl = song.previewUrl?.trim()
        val isValidUri = !previewUrl.isNullOrEmpty() && (
            previewUrl.startsWith("http://", ignoreCase = true) ||
            previewUrl.startsWith("https://", ignoreCase = true) ||
            previewUrl.startsWith("content://", ignoreCase = true) ||
            previewUrl.startsWith("file://", ignoreCase = true)
        )
        if (!isValidUri) {
            _playbackError.value = "Audio is not available for \"${song.trackName}\"."
            return
        }

        val isOnlineUrl = previewUrl.startsWith("http://", ignoreCase = true) || previewUrl.startsWith("https://", ignoreCase = true)
        if (isOnlineUrl && !isNetworkAvailable()) {
            _playbackError.value = "No internet connection. Music can't play."
            return
        }

        songCache.clear()
        val mediaItems = mutableListOf<MediaItem>()
        var startIndex = 0

        val effectiveQueue = if (queue.isNotEmpty()) queue else listOf(song)
        for ((index, s) in effectiveQueue.withIndex()) {
            songCache[s.trackId] = s
            if (s.trackId == song.trackId) {
                startIndex = index
            }
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(s.trackName)
                .setArtist(s.artistName)
                .setArtworkUri(s.artworkUrl?.let { Uri.parse(it) })
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(s.trackId.toString())
                .setUri(s.previewUrl?.trim() ?: "")
                .setMediaMetadata(mediaMetadata)
                .build()
            mediaItems.add(mediaItem)
        }

        _currentSong.value = song

        val controller = mediaController
        if (controller != null) {
            controller.setMediaItems(mediaItems, startIndex, 0L)
            controller.repeatMode = Player.REPEAT_MODE_OFF
            controller.prepare()
            controller.play()
        } else {
            // Re-attempt controller connection if null
            initializeController()
        }
    }

    override fun pause() {
        mediaController?.pause()
        stopProgressTracker()
    }

    override fun resume() {
        mediaController?.play()
        startProgressTracker()
    }

    override fun togglePlayPause(song: Song?, queue: List<Song>) {
        if (song != null && song.trackId != _currentSong.value?.trackId) {
            playSong(song, queue)
        } else {
            if (_isPlaying.value) {
                pause()
            } else {
                if (_currentSong.value != null) {
                    resume()
                } else if (song != null) {
                    playSong(song, queue)
                }
            }
        }
    }

    override fun stop() {
        stopProgressTracker()
        mediaController?.stop()
        _currentSong.value = null
        _isPlaying.value = false
        _playbackProgress.value = 0
    }

    override fun release() {
        stopProgressTracker()
        playerScope.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
