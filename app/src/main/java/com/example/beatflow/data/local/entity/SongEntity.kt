package com.example.beatflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a song cached in the local database.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    val trackId: Long,
    val trackName: String,
    val artistName: String,
    val artworkUrl: String?,
    val previewUrl: String?,
    val trackViewUrl: String?
)
