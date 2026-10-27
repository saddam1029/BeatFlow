package com.example.beatflow.domain.repository

import com.example.beatflow.domain.model.DeleteDeviceSongResult
import com.example.beatflow.domain.model.Song

/**
 * Domain repository abstraction for querying local audio stored on the device.
 * Completely decoupled from Android framework and MediaStore APIs.
 */
interface DeviceSongRepository {
    suspend fun getDeviceSongs(): Result<List<Song>>
    suspend fun deleteDeviceSong(contentUri: String): DeleteDeviceSongResult
    suspend fun deleteDeviceSongConfirmed(contentUri: String): Result<Unit>
}
