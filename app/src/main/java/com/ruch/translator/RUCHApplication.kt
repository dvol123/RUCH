package com.ruch.translator

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.ruch.translator.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class RUCHApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob())
    
    lateinit var preferencesManager: PreferencesManager
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        preferencesManager = PreferencesManager(this)
        
        // Apply saved theme
        applyTheme(preferencesManager.getTheme())
        
        // Create notification channel for model download
        createNotificationChannel()
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
    
    companion object {
        const val CHANNEL_MODEL_DOWNLOAD = "model_download"
    }
}
