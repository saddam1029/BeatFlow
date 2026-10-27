package com.example.beatflow.data.repository

import com.example.beatflow.data.model.HomeTab
import com.example.beatflow.data.model.Song
import com.example.beatflow.data.model.SongSource

/**
 * Temporary in-memory repository providing preview sample data for the Home Screen.
 * Clearly separated from future real data integration (MediaStore, Room, Retrofit, ExoPlayer).
 */
class SampleSongRepository {

    // Temporary sample list for Online Songs to preview the UI
    private val sampleOnlineSongs = listOf(
        Song(
            id = 1L,
            title = "Midnight Groove",
            artist = "Electric Pulse",
            duration = "3:45",
            source = SongSource.ONLINE
        ),
        Song(
            id = 2L,
            title = "Neon Horizon",
            artist = "Synthwave Echoes",
            duration = "4:12",
            source = SongSource.ONLINE
        ),
        Song(
            id = 3L,
            title = "Deep Resonance",
            artist = "Bass Frequency",
            duration = "3:18",
            source = SongSource.ONLINE
        ),
        Song(
            id = 4L,
            title = "Cyber Rhythm",
            artist = "Digital Drift",
            duration = "4:05",
            source = SongSource.ONLINE
        ),
        Song(
            id = 5L,
            title = "Cosmic Flow",
            artist = "Solar Flare",
            duration = "3:52",
            source = SongSource.ONLINE
        )
    )

    // Device Songs initially empty to showcase the clean empty state design
    private val sampleDeviceSongs = emptyList<Song>()

    fun getSongsForTab(tab: HomeTab): List<Song> {
        return when (tab) {
            HomeTab.ONLINE -> sampleOnlineSongs
            HomeTab.DEVICE -> sampleDeviceSongs
        }
    }
}
