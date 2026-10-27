package com.example.beatflow.data.mapper

import com.example.beatflow.data.local.entity.SongEntity
import com.example.beatflow.data.remote.dto.SongDto
import com.example.beatflow.domain.model.Song

/**
 * Maps between API DTOs, Room Entities, and Domain models.
 */

/**
 * Safely resolves a high-resolution iTunes artwork URL (500x500) if the input URL
 * matches the standard iTunes sizing pattern (/100x100bb.<ext> or /60x60bb.<ext>).
 * Validates that the URL is a proper HTTP(S) URL. If non-matching, returns the original valid URL.
 * Returns null for null, blank, or invalid non-HTTP URLs.
 */
fun getHighResArtworkUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val trimmed = url.trim()
    if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
        return null
    }

    val itunesSizeRegex = Regex("""/(\d+)x(\d+)bb\.([a-zA-Z0-9]+)$""")
    val match = itunesSizeRegex.find(trimmed)
    return if (match != null) {
        val extension = match.groupValues[3]
        trimmed.substring(0, match.range.first) + "/500x500bb.$extension"
    } else {
        trimmed
    }
}

// --- DTO to Domain ---
fun SongDto.toDomain(): Song? {
    val id = trackId ?: return null
    val title = trackName?.takeIf { it.isNotBlank() } ?: return null
    val artist = artistName?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
    val rawArtwork = artworkUrl100 ?: artworkUrl60
    val artwork = getHighResArtworkUrl(rawArtwork)

    return Song(
        trackId = id,
        trackName = title,
        artistName = artist,
        artworkUrl = artwork,
        previewUrl = previewUrl,
        trackViewUrl = trackViewUrl
    )
}

@JvmName("dtoListToDomain")
fun List<SongDto>?.toDomain(): List<Song> {
    return this?.mapNotNull { it.toDomain() } ?: emptyList()
}

// --- Domain to Entity ---
fun Song.toEntity(): SongEntity {
    return SongEntity(
        trackId = trackId,
        trackName = trackName,
        artistName = artistName,
        artworkUrl = artworkUrl,
        previewUrl = previewUrl,
        trackViewUrl = trackViewUrl
    )
}

fun List<Song>.toEntities(): List<SongEntity> {
    return map { it.toEntity() }
}

// --- Entity to Domain ---
fun SongEntity.toDomain(): Song {
    return Song(
        trackId = trackId,
        trackName = trackName,
        artistName = artistName,
        artworkUrl = artworkUrl,
        previewUrl = previewUrl,
        trackViewUrl = trackViewUrl
    )
}

@JvmName("entityListToDomain")
fun List<SongEntity>.toDomain(): List<Song> {
    return map { it.toDomain() }
}
