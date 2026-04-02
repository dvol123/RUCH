package com.ruch.translator.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    
    companion object {
        private val THEME_KEY = intPreferencesKey("theme_mode")
        private val MODELS_DOWNLOADED_KEY = booleanPreferencesKey("models_downloaded")
    }
    
    fun getTheme(): Int {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getInt(THEME_KEY.name, 0)
    }
    
    suspend fun setTheme(themeMode: Int) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putInt(THEME_KEY.name, themeMode)
            .apply()
    }
    
    fun areModelsDownloaded(): Boolean {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(MODELS_DOWNLOADED_KEY.name, false)
    }
    
    suspend fun setModelsDownloaded(downloaded: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MODELS_DOWNLOADED_KEY.name, downloaded)
            .apply()
    }
    
    fun getTtsSpeed(): Float {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getFloat("tts_speed", 1.0f)
    }
    
    suspend fun setTtsSpeed(speed: Float) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putFloat("tts_speed", speed)
            .apply()
    }
    
    val themeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: 0
    }
}
