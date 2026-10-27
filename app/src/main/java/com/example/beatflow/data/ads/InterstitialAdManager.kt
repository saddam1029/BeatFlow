package com.example.beatflow.data.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAdManager {

    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingAd = false
    var isShowingAd = false

    fun preloadAd(context: Context) {
        if (isLoadingAd || interstitialAd != null || isShowingAd) {
            return
        }
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            AD_UNIT_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isLoadingAd = false
                }
            }
        )
    }

    fun showAdIfReady(
        activity: Activity,
        onAdShowed: () -> Unit = {},
        onAdDismissedOrFailed: () -> Unit
    ) {
        if (isShowingAd) {
            onAdDismissedOrFailed()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            isShowingAd = true
            interstitialAd = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    isShowingAd = false
                    preloadAd(activity)
                    onAdDismissedOrFailed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    isShowingAd = false
                    preloadAd(activity)
                    onAdDismissedOrFailed()
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingAd = true
                    onAdShowed()
                }
            }
            ad.show(activity)
        } else {
            isShowingAd = false
            preloadAd(activity)
            onAdDismissedOrFailed()
        }
    }
}
