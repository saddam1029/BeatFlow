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

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val splashDelayMillis = 2000L
    private val handler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable {
        if (!isFinishing && !isDestroyed) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Schedule transition to MainActivity after 2 seconds
        handler.postDelayed(navigateRunnable, splashDelayMillis)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Prevent memory leaks and callbacks if the Activity is destroyed early
        handler.removeCallbacks(navigateRunnable)
    }
}
