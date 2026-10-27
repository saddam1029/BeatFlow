package com.example.beatflow

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.beatflow.data.ads.AppOpenAdManager
import com.example.beatflow.data.ads.InterstitialAdManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false
    private var timeoutRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        InterstitialAdManager.preloadAd(this)
        loadAndShowAppOpenAd()
    }

    private fun loadAndShowAppOpenAd() {
        var adProcessed = false

        timeoutRunnable = Runnable {
            if (!adProcessed) {
                adProcessed = true
                navigateToHome()
            }
        }
        // Strict 6-second timeout when loading begins
        handler.postDelayed(timeoutRunnable!!, 6000L)

        AppOpenAdManager.loadAndShowAd(
            context = this,
            onAdShowed = {
                if (!adProcessed) {
                    timeoutRunnable?.let { handler.removeCallbacks(it) }
                }
            },
            onAdDismissedOrFailed = {
                if (!adProcessed) {
                    adProcessed = true
                    timeoutRunnable?.let { handler.removeCallbacks(it) }
                    navigateToHome()
                }
            }
        )
    }

    private fun navigateToHome() {
        if (!hasNavigated && !isFinishing && !isDestroyed) {
            hasNavigated = true
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
