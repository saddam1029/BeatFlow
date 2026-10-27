package com.example.beatflow.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object representing the root response from iTunes Search API.
 */
data class ItunesResponseDto(
    @SerializedName("resultCount")
    val resultCount: Int?,
    @SerializedName("results")
    val results: List<SongDto>?
)
