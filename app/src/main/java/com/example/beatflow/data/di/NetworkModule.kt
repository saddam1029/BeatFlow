package com.example.beatflow.data.di

import android.content.Context
import com.example.beatflow.data.local.dao.SongDao
import com.example.beatflow.data.local.database.SongDatabase
import com.example.beatflow.data.remote.ItunesApiService
import com.example.beatflow.data.repository.SongRepositoryImpl
import com.example.beatflow.domain.repository.SongRepository
import com.example.beatflow.domain.usecase.GetOnlineSongsUseCase
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Simple, maintainable manual Dependency Injection module providing Retrofit,
 * Room Database, Repository, and UseCase instances.
 */
object NetworkModule {

    private const val BASE_URL = "https://itunes.apple.com/"
    private var applicationContext: Context? = null

    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        }
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ItunesApiService by lazy {
        retrofit.create(ItunesApiService::class.java)
    }

    val songDatabase: SongDatabase by lazy {
        val context = applicationContext
            ?: throw IllegalStateException("NetworkModule must be initialized with Context via NetworkModule.init(context)")
        SongDatabase.getInstance(context)
    }

    val songDao: SongDao by lazy {
        songDatabase.songDao()
    }

    val songRepository: SongRepository by lazy {
        SongRepositoryImpl(
            apiService = apiService,
            songDao = songDao
        )
    }

    val musicPlayer: com.example.beatflow.domain.playback.MusicPlayer by lazy {
        val context = applicationContext
            ?: throw IllegalStateException("NetworkModule must be initialized with Context via NetworkModule.init(context)")
        com.example.beatflow.data.playback.Media3MusicPlayer(context)
    }

    fun provideGetOnlineSongsUseCase(): GetOnlineSongsUseCase {
        return GetOnlineSongsUseCase(songRepository)
    }

    fun provideMusicPlayer(): com.example.beatflow.domain.playback.MusicPlayer {
        return musicPlayer
    }
}
