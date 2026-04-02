package com.ruch.translator.stt

import android.content.Context
import android.util.Log
import com.ruch.translator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Speech-to-Text распознаватель на базе Sherpa-ONNX Whisper
 * Использует нативные библиотеки через JNI
 */
class SherpaWhisperRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "SherpaWhisperRecognizer"

        // Загружаем нативные библиотеки
        init {
            try {
                System.loadLibrary("onnxruntime")
                System.loadLibrary("sherpa-onnx-c-api")
                System.loadLibrary("sherpa-onnx-jni")
                Log.i(TAG, "Native libraries loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native libraries: ${e.message}")
            }
        }

        // Пути к моделям в assets
        private const val WHISPER_ENCODER = "models/whisper/tiny-encoder.int8.onnx"
        private const val WHISPER_DECODER = "models/whisper/tiny-decoder.int8.onnx"
        private const val WHISPER_TOKENS = "models/whisper/tiny-tokens.txt"
    }

    private var isInitialized = false
    private var recognizerPtr: Long = 0

    /**
     * Инициализация распознавателя
     * Копирует модели из assets во внутреннее хранилище
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            // Копируем модели во внутреннее хранилище
            val modelsDir = File(context.filesDir, "models/whisper")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }

            copyAssetIfNeeded(WHISPER_ENCODER, modelsDir)
            copyAssetIfNeeded(WHISPER_DECODER, modelsDir)
            copyAssetIfNeeded(WHISPER_TOKENS, modelsDir)

            // Инициализируем нативный распознаватель
            val encoderPath = File(modelsDir, "tiny-encoder.int8.onnx").absolutePath
            val decoderPath = File(modelsDir, "tiny-decoder.int8.onnx").absolutePath
            val tokensPath = File(modelsDir, "tiny-tokens.txt").absolutePath

            recognizerPtr = nativeInitRecognizer(encoderPath, decoderPath, tokensPath)

            if (recognizerPtr != 0L) {
                isInitialized = true
                Log.i(TAG, "Whisper recognizer initialized successfully")
                true
            } else {
                Log.e(TAG, "Failed to initialize native recognizer")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Initialization error: ${e.message}", e)
            false
        }
    }

    /**
     * Распознавание речи из аудиоданных
     * @param audioData PCM аудиоданные (16-bit, 16000 Hz, mono)
     * @param language Язык распознавания
     * @return Распознанный текст или null при ошибке
     */
    suspend fun recognize(audioData: FloatArray, language: Language): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizerPtr == 0L) {
            Log.e(TAG, "Recognizer not initialized")
            return@withContext null
        }

        try {
            val langCode = when (language) {
                Language.RUSSIAN -> "ru"
                Language.CHINESE -> "zh"
            }

            nativeRecognize(recognizerPtr, audioData, langCode)
        } catch (e: Exception) {
            Log.e(TAG, "Recognition error: ${e.message}", e)
            null
        }
    }

    /**
     * Освобождение ресурсов
     */
    fun release() {
        if (recognizerPtr != 0L) {
            nativeReleaseRecognizer(recognizerPtr)
            recognizerPtr = 0
            isInitialized = false
        }
    }

    /**
     * Копирование файла из assets, если он не существует
     */
    private fun copyAssetIfNeeded(assetPath: String, destDir: File) {
        val fileName = assetPath.substringAfterLast("/")
        val destFile = File(destDir, fileName)

        if (!destFile.exists()) {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied $fileName to ${destFile.absolutePath}")
        }
    }

    // Native методы JNI
    private external fun nativeInitRecognizer(
        encoderPath: String,
        decoderPath: String,
        tokensPath: String
    ): Long

    private external fun nativeRecognize(
        recognizerPtr: Long,
        audioData: FloatArray,
        language: String
    ): String?

    private external fun nativeReleaseRecognizer(recognizerPtr: Long)
}
