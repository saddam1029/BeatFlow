package com.example.beatflow.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object representing an individual song from the iTunes Search API.
 */
data class SongDto(
    @SerializedName("trackId")
    val trackId: Long?,
    @SerializedName("trackName")
    val trackName: String?,
    @SerializedName("artistName")
    val artistName: String?,
    @SerializedName("artworkUrl100")
    val artworkUrl100: String?,
    @SerializedName("artworkUrl60")
    val artworkUrl60: String?,
    @SerializedName("previewUrl")
    val previewUrl: String?,
    @SerializedName("trackViewUrl")
    val trackViewUrl: String?
)
