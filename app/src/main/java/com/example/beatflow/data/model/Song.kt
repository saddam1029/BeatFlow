package com.example.beatflow.data.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: String = "",
    val source: SongSource = SongSource.ONLINE
)

enum class SongSource {
    ONLINE,
    DEVICE
}
