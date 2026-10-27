package com.example.beatflow.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.repository.DeviceSongRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of DeviceSongRepository that queries local audio
 * files stored on the device using Android's MediaStore API.
 */
class DeviceSongRepositoryImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DeviceSongRepository {

    private val albumArtUri = Uri.parse("content://media/external/audio/albumart")

    override suspend fun getDeviceSongs(): Result<List<Song>> = withContext(ioDispatcher) {
        try {
            val songs = mutableListOf<Song>()
            val contentResolver = context.contentResolver

            val collection: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
            )

            // Filter for music tracks and exclude empty/invalid durations
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

            contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)

                    val rawTitle = if (titleColumn != -1) cursor.getString(titleColumn) else null
                    val title = rawTitle?.takeIf { it.isNotBlank() } ?: "Unknown Track"

                    val rawArtist = if (artistColumn != -1) cursor.getString(artistColumn) else null
                    val artist = rawArtist?.takeIf {
                        it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true)
                    } ?: "Unknown Artist"

                    val duration = if (durationColumn != -1) cursor.getLong(durationColumn) else 0L
                    val albumId = if (albumIdColumn != -1) cursor.getLong(albumIdColumn) else -1L

                    // Construct unique content URI for the local audio track
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    // Construct album artwork URI if a valid album ID is present
                    val artworkUriString = if (albumId > 0) {
                        ContentUris.withAppendedId(albumArtUri, albumId).toString()
                    } else {
                        null
                    }

                    songs.add(
                        Song(
                            trackId = id,
                            trackName = title,
                            artistName = artist,
                            artworkUrl = artworkUriString,
                            previewUrl = contentUri.toString(),
                            trackViewUrl = null,
                            durationMs = duration
                        )
                    )
                }
            }

            Result.success(songs)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
