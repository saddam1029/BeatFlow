package com.example.beatflow

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.beatflow.data.ads.AppOpenAdManager
import com.example.beatflow.data.di.NetworkModule
import com.google.android.gms.ads.MobileAds

class BeatFlowApp : Application(), Application.ActivityLifecycleCallbacks {

    private var numStarted = 0
    private var isAppInBackground = false

    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
        MobileAds.initialize(this) {}
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (numStarted == 0) {
            if (isAppInBackground) {
                // Genuine background-to-foreground return
                if (activity !is SplashActivity) {
                    AppOpenAdManager.showAdIfAvailable(activity)
                }
            }
            isAppInBackground = false
        }
        numStarted++
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        numStarted--
        if (numStarted == 0) {
            isAppInBackground = true
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
