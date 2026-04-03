package com.ruch.translator.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferencesManager(private val context: Context) {
    
    companion object {
        private val THEME_KEY = intPreferencesKey("theme_mode")
        private val MODELS_DOWNLOADED_KEY = booleanPreferencesKey("models_downloaded")
        private val MODELS_FOLDER_URI_KEY = stringPreferencesKey("models_folder_uri")
        private val MODELS_LOADED_KEY = booleanPreferencesKey("models_loaded")
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
    
    // URI папки с моделями (выбранная пользователем)
    fun getModelsFolderUri(): String? {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(MODELS_FOLDER_URI_KEY.name, null)
    }
    
    suspend fun setModelsFolderUri(uri: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString(MODELS_FOLDER_URI_KEY.name, uri)
            .apply()
    }
    
    // Загружены ли модели в память приложения
    fun areModelsLoaded(): Boolean {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(MODELS_LOADED_KEY.name, false)
    }
    
    suspend fun setModelsLoaded(loaded: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MODELS_LOADED_KEY.name, loaded)
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
