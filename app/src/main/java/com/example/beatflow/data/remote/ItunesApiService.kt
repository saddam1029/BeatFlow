package com.example.beatflow.data.remote

import com.example.beatflow.data.remote.dto.ItunesResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API interface for iTunes Search API.
 */
interface ItunesApiService {

    @GET("search")
    suspend fun searchSongs(
        @Query("term") term: String = "pop",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 1000
    ): Response<ItunesResponseDto>
}
