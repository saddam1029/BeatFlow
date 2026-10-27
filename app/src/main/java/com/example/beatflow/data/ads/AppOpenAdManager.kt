package com.example.beatflow.data.ads

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

object AppOpenAdManager {

    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    var isShowingAd = false
    private val handler = Handler(Looper.getMainLooper())

    fun loadAndShowAd(
        context: Context,
        onAdShowed: () -> Unit = {},
        onAdDismissedOrFailed: () -> Unit = {}
    ) {
        if (isLoadingAd || isShowingAd) {
            onAdDismissedOrFailed()
            return
        }

        if (appOpenAd != null) {
            showAd(context, onAdShowed, onAdDismissedOrFailed)
            return
        }

        isLoadingAd = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    showAd(context, onAdShowed, onAdDismissedOrFailed)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    appOpenAd = null
                    isLoadingAd = false
                    onAdDismissedOrFailed()
                }
            }
        )
    }

    fun showAdIfAvailable(activity: Activity) {
        if (isShowingAd || isLoadingAd || InterstitialAdManager.isShowingAd) {
            return
        }

        var loadingDialog: AlertDialog? = null
        var isFinished = false

        if (!activity.isFinishing && !activity.isDestroyed) {
            try {
                loadingDialog = AlertDialog.Builder(activity)
                    .setMessage("Ad is loading...")
                    .setCancelable(false)
                    .create()
                loadingDialog.show()
            } catch (e: Exception) {
                // Ignore if activity context is not valid
            }
        }

        val dismissDialog = {
            if (!isFinished) {
                isFinished = true
                try {
                    loadingDialog?.dismiss()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        // 5-second timeout
        val timeoutRunnable = Runnable {
            dismissDialog()
            if (isLoadingAd) {
                isLoadingAd = false
            }
        }
        handler.postDelayed(timeoutRunnable, 5000L)

        loadAndShowAd(
            context = activity,
            onAdShowed = {
                handler.removeCallbacks(timeoutRunnable)
                dismissDialog()
                isShowingAd = true
            },
            onAdDismissedOrFailed = {
                handler.removeCallbacks(timeoutRunnable)
                dismissDialog()
                isShowingAd = false
            }
        )
    }

    private fun showAd(
        context: Context,
        onAdShowed: () -> Unit,
        onAdDismissedOrFailed: () -> Unit
    ) {
        if (appOpenAd == null) {
            onAdDismissedOrFailed()
            return
        }

        if (context is Activity && !context.isFinishing && !context.isDestroyed) {
            isShowingAd = true
            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAd = false
                    onAdDismissedOrFailed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    appOpenAd = null
                    isShowingAd = false
                    onAdDismissedOrFailed()
                }

                override fun onAdShowedFullScreenContent() {
                    appOpenAd = null
                    onAdShowed()
                }
            }
            appOpenAd?.show(context)
        } else {
            isShowingAd = false
            onAdDismissedOrFailed()
        }
    }
}
