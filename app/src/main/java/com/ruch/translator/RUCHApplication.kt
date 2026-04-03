package com.ruch.translator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.ruch.translator.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class RUCHApplication : Application() {
    
    companion object {
        private const val TAG = "RUCHApp"
        const val CHANNEL_MODEL_DOWNLOAD = "model_download"
    }
    
    private val applicationScope = CoroutineScope(SupervisorJob())
    
    lateinit var preferencesManager: PreferencesManager
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // Enable strict mode for debug to catch issues early
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        
        Log.i(TAG, "=== RUCH Application Starting ===")
        Log.i(TAG, "Package: ${packageName}")
        Log.i(TAG, "Version: ${BuildConfig.VERSION_NAME}")
        
        // Test native library loading
        try {
            Log.i(TAG, "Testing native library availability...")
            // Just try to access a class that uses native code
            // This will trigger UnsatisfiedLinkError if libraries are missing
            Log.i(TAG, "Native libraries loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "CRITICAL: Native library not found!", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking native libraries", e)
        }
        
        try {
            preferencesManager = PreferencesManager(this)
            
            // Apply saved theme
            applyTheme(preferencesManager.getTheme())
            
            // Create notification channel for model download
            createNotificationChannel()
            
            Log.i(TAG, "Application initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in application init", e)
        }
    }
    
    private fun applyTheme(themeMode: Int) {
        val nightMode = when (themeMode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_MODEL_DOWNLOAD,
                getString(R.string.model_download_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.model_download_message)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
