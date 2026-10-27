package com.example.beatflow

import android.app.Application
import com.example.beatflow.data.di.NetworkModule

class BeatFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
    }
}
