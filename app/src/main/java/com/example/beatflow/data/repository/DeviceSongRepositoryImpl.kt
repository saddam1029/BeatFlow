package com.example.beatflow.data.repository

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.beatflow.domain.model.DeleteDeviceSongResult
import com.example.beatflow.domain.model.Song
import com.example.beatflow.domain.repository.DeviceSongRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.VOLUME_NAME
                )
            } else {
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM_ID
                )
            }

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
                val volumeNameColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME)
                } else {
                    -1
                }

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

                    // Construct volume-specific or external canonical content URI for the local audio track
                    val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && volumeNameColumn != -1) {
                        val volumeName = cursor.getString(volumeNameColumn)
                        if (!volumeName.isNullOrBlank()) {
                            ContentUris.withAppendedId(MediaStore.Audio.Media.getContentUri(volumeName), id)
                        } else {
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        }
                    } else {
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    }

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
                            durationMs = duration,
                            isLocal = true
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

    private fun deletePhysicalFileIfPossible(uri: Uri): Boolean {
        var filePath: String? = null
        try {
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (dataColumn != -1) {
                        filePath = cursor.getString(dataColumn)
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore query failure
        }

        val deletedRows = try {
            context.contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
            0
        }

        val path = filePath
        if (!path.isNullOrBlank()) {
            try {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
                // Ignore file delete failure
            }
        }

        val fileGone = path.isNullOrBlank() || !File(path).exists()
        return deletedRows > 0 || fileGone
    }

    override suspend fun deleteDeviceSong(contentUri: String): DeleteDeviceSongResult = withContext(ioDispatcher) {
        try {
            val uri = Uri.parse(contentUri)
            Log.d("BeatFlowDelete", "deleteDeviceSong called. SDK=${Build.VERSION.SDK_INT}, URI=$uri")
            // On Android 11+ (including Android 14, API 30+), MediaStore.createDeleteRequest is the official scoped storage API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                Log.d("BeatFlowDelete", "Created createDeleteRequest PendingIntent for API >= 30")
                DeleteDeviceSongResult.RequiresConfirmation(pendingIntent.intentSender)
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                // On Android 10 (API 29), attempt deletion; if unauthorized, throws RecoverableSecurityException (SecurityException)
                try {
                    val deletedRows = context.contentResolver.delete(uri, null, null)
                    Log.d("BeatFlowDelete", "API 29 delete rows: $deletedRows")
                    if (deletedRows > 0) {
                        DeleteDeviceSongResult.Success
                    } else {
                        DeleteDeviceSongResult.Failure("File not found or could not be deleted")
                    }
                } catch (e: SecurityException) {
                    android.util.Log.w("BeatFlowDelete", "API 29 SecurityException: ${e.javaClass.simpleName}: ${e.message}")
                    if (e is RecoverableSecurityException) {
                        DeleteDeviceSongResult.RequiresConfirmation(e.userAction.actionIntent.intentSender)
                    } else {
                        DeleteDeviceSongResult.Failure(e.localizedMessage ?: "Permission denied to delete file")
                    }
                }
            } else {
                // Android 9 and older (API <= 28): standard ContentResolver deletion with WRITE_EXTERNAL_STORAGE
                try {
                    val success = deletePhysicalFileIfPossible(uri)
                    Log.d("BeatFlowDelete", "API <= 28 delete success: $success")
                    if (success) {
                        DeleteDeviceSongResult.Success
                    } else {
                        DeleteDeviceSongResult.Failure("File not found or could not be deleted")
                    }
                } catch (e: SecurityException) {
                    Log.e("BeatFlowDelete", "API <= 28 SecurityException: ${e.message}", e)
                    DeleteDeviceSongResult.Failure("Write storage permission is required to delete files on this device.")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BeatFlowDelete", "deleteDeviceSong exception: ${e.javaClass.simpleName}: ${e.message}", e)
            DeleteDeviceSongResult.Failure(e.localizedMessage ?: "Failed to delete audio file")
        }
    }

    override suspend fun deleteDeviceSongConfirmed(contentUri: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val uri = Uri.parse(contentUri)
            android.util.Log.d("BeatFlowDelete", "confirmDelete for SDK=${Build.VERSION.SDK_INT}, uri=$uri")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Result.success(Unit)
            } else {
                val deletedRows = context.contentResolver.delete(uri, null, null)
                Log.d("BeatFlowDelete", "confirmDelete API 29 deleteRows=$deletedRows for URI=$uri")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("BeatFlowDelete", "Exception in confirmDelete: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(e)
        }
    }
}
