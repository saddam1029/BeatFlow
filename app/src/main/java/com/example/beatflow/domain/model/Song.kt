package com.example.beatflow.domain.model

/**
 * Domain model representing a song.
 * Kept strictly independent of external libraries (Retrofit, Gson) and Android framework classes.
 */
data class Song(
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String?,
    val previewUrl: String?,
    val trackViewUrl: String?,
    val durationMs: Long = 0L
)
