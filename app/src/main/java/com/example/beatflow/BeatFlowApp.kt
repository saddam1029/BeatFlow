package com.example.beatflow

import android.app.Application
import com.example.beatflow.data.di.NetworkModule
import com.google.android.gms.ads.MobileAds

class BeatFlowApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
        MobileAds.initialize(this) {}
    }
}
