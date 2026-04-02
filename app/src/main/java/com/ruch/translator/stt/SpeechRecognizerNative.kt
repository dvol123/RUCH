package com.ruch.translator.stt

import android.content.Context
import android.util.Log
import com.ruch.translator.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Native Speech Recognizer using Whisper.cpp via JNI
 * 
 * Для работы необходимо:
 * 1. Добавить libwhisper-jni.so в jniLibs/<архитектура>/
 * 2. Скачать модель whisper-small.bin (~244MB)
 * 
 * Сборка native библиотеки:
 * 1. Клонировать https://github.com/ggerganov/whisper.cpp
 * 2. Собрать для Android:
 *    mkdir build-android && cd build-android
 *    cmake .. -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
 *             -DANDROID_ABI=arm64-v8a \
 *             -DANDROID_PLATFORM=android-26
 *    make
 * 3. Скопировать libwhisper.so в app/src/main/jniLibs/arm64-v8a/
 */
class SpeechRecognizerNative(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechRecognizerNative"
        
        // Загружаем native библиотеку
        init {
            try {
                System.loadLibrary("whisper-jni")
                Log.i(TAG, "whisper-jni library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load whisper-jni library: ${e.message}")
            }
        }
    }
    
    // Native контекст (указатель на C++ структуру)
    private var nativeContext: Long = 0
    
    // Native методы
    private external fun initWhisper(modelPath: String): Boolean
    private external fun transcribe(audioData: FloatArray, language: String): String
    private external fun releaseWhisper()
    external fun getSupportedLanguages(): Array<String>
    
    private var isInitialized = false
    
    /**
     * Инициализация Whisper модели
     * 
     * @param modelPath Путь к файлу модели (ggml-small.bin и т.д.)
     * @return true если инициализация успешна
     */
    suspend fun initialize(modelPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(modelPath)
                if (!file.exists()) {
                    Log.e(TAG, "Model file not found: $modelPath")
                    return@withContext false
                }
                
                isInitialized = initWhisper(modelPath)
                Log.i(TAG, "Whisper initialized: $isInitialized")
                isInitialized
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Whisper: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Распознавание речи из аудиоданных
     * 
     * @param audioData Массив float значений аудио (16kHz, mono)
     * @param language Код языка ("ru", "zh", "en" и т.д.)
     * @return Распознанный текст
     */
    suspend fun transcribeAudio(audioData: FloatArray, language: Language): String {
        return withContext(Dispatchers.IO) {
            if (!isInitialized) {
                Log.e(TAG, "Whisper not initialized")
                return@withContext ""
            }
            
            try {
                val langCode = if (language == Language.RUSSIAN) "ru" else "zh"
                val result = transcribe(audioData, langCode)
                Log.i(TAG, "Transcription result: $result")
                result
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed: ${e.message}")
                ""
            }
        }
    }
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        if (isInitialized) {
            try {
                releaseWhisper()
                isInitialized = false
                Log.i(TAG, "Whisper resources released")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release Whisper: ${e.message}")
            }
        }
    }
    
    /**
     * Копирование модели из assets в файловую систему
     */
    fun copyModelFromAssets(assetName: String, destPath: String): Boolean {
        return try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(destPath).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Model copied to: $destPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model: ${e.message}")
            false
        }
    }
}
