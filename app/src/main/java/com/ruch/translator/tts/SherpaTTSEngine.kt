package com.ruch.translator.tts

import android.content.Context
import android.util.Log
import com.ruch.translator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Text-to-Speech движок на базе Sherpa-ONNX VITS
 * Использует нативные библиотеки через JNI
 */
class SherpaTTSEngine(private val context: Context) {

    companion object {
        private const val TAG = "SherpaTTSEngine"
        private const val SAMPLE_RATE = 22050

        // Загружаем нативные библиотеки (должны быть загружены один раз)
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
    }

    private var russianTtsPtr: Long = 0
    private var chineseTtsPtr: Long = 0
    private var isInitialized = false

    /**
     * Инициализация TTS движка
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            // Инициализируем русский TTS
            russianTtsPtr = initTtsForLanguage(Language.RUSSIAN)
            if (russianTtsPtr == 0L) {
                Log.e(TAG, "Failed to initialize Russian TTS")
            }

            // Инициализируем китайский TTS
            chineseTtsPtr = initTtsForLanguage(Language.CHINESE)
            if (chineseTtsPtr == 0L) {
                Log.e(TAG, "Failed to initialize Chinese TTS")
            }

            isInitialized = russianTtsPtr != 0L || chineseTtsPtr != 0L
            Log.i(TAG, "TTS engine initialized: RU=${russianTtsPtr != 0L}, ZH=${chineseTtsPtr != 0L}")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Initialization error: ${e.message}", e)
            false
        }
    }

    /**
     * Инициализация TTS для конкретного языка
     */
    private fun initTtsForLanguage(language: Language): Long {
        val langDir = when (language) {
            Language.RUSSIAN -> "ru"
            Language.CHINESE -> "zh"
        }

        // Копируем модели во внутреннее хранилище
        val modelsDir = File(context.filesDir, "models/tts/$langDir")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        // Копируем файлы модели
        val assetDir = "models/tts/$langDir"
        try {
            val assetFiles = context.assets.list(assetDir) ?: return 0L

            for (fileName in assetFiles) {
                val assetPath = "$assetDir/$fileName"
                val destFile = File(modelsDir, fileName)

                if (!destFile.exists()) {
                    // Если это директория (espeak-ng-data), копируем рекурсивно
                    if (fileName == "espeak-ng-data") {
                        copyAssetDir(assetPath, destFile)
                    } else {
                        context.assets.open(assetPath).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            // Находим .onnx файл модели
            val onnxFile = modelsDir.listFiles()?.firstOrNull { it.extension == "onnx" }
                ?: return 0L

            val tokensFile = File(modelsDir, "tokens.txt")
            val dataDir = File(modelsDir, "espeak-ng-data")

            // Инициализируем нативный TTS
            return nativeInitTts(
                onnxFile.absolutePath,
                tokensFile.absolutePath,
                dataDir.absolutePath,
                1, // num_threads
                1.0f, // noise_scale
                1.0f  // length_scale
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS for $language: ${e.message}")
            return 0L
        }
    }

    /**
     * Копирование директории из assets рекурсивно
     */
    private fun copyAssetDir(assetPath: String, destDir: File) {
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        val files = context.assets.list(assetPath) ?: return
        for (fileName in files) {
            val srcPath = "$assetPath/$fileName"
            val destFile = File(destDir, fileName)

            // Проверяем, это файл или директория
            try {
                context.assets.open(srcPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // Это директория, копируем рекурсивно
                copyAssetDir(srcPath, destFile)
            }
        }
    }

    /**
     * Синтез речи
     * @param text Текст для озвучивания
     * @param language Язык
     * @param onAudioGenerated Callback для получения аудиоданных
     */
    suspend fun synthesize(
        text: String,
        language: Language,
        onAudioGenerated: (ShortArray) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val ttsPtr = when (language) {
            Language.RUSSIAN -> russianTtsPtr
            Language.CHINESE -> chineseTtsPtr
        }

        if (ttsPtr == 0L) {
            Log.e(TAG, "TTS not initialized for language: $language")
            return@withContext false
        }

        try {
            val speakerId = 0
            val speed = 1.0f

            // Генерируем аудио через нативный метод
            val audioData = nativeGenerate(ttsPtr, text, speakerId, speed)

            if (audioData != null && audioData.isNotEmpty()) {
                onAudioGenerated(audioData)
                true
            } else {
                Log.e(TAG, "Failed to generate audio for: $text")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error: ${e.message}", e)
            false
        }
    }

    /**
     * Синтез речи и возврат аудиоданных
     */
    suspend fun synthesizeToArray(text: String, language: Language): ShortArray? = withContext(Dispatchers.IO) {
        val ttsPtr = when (language) {
            Language.RUSSIAN -> russianTtsPtr
            Language.CHINESE -> chineseTtsPtr
        }

        if (ttsPtr == 0L) {
            Log.e(TAG, "TTS not initialized for language: $language")
            return@withContext null
        }

        try {
            nativeGenerate(ttsPtr, text, 0, 1.0f)
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error: ${e.message}", e)
            null
        }
    }

    /**
     * Получение частоты дискретизации
     */
    fun getSampleRate(): Int = SAMPLE_RATE

    /**
     * Проверка готовности TTS для языка
     */
    fun isReady(language: Language): Boolean {
        return when (language) {
            Language.RUSSIAN -> russianTtsPtr != 0L
            Language.CHINESE -> chineseTtsPtr != 0L
        }
    }

    /**
     * Освобождение ресурсов
     */
    fun release() {
        if (russianTtsPtr != 0L) {
            nativeReleaseTts(russianTtsPtr)
            russianTtsPtr = 0
        }
        if (chineseTtsPtr != 0L) {
            nativeReleaseTts(chineseTtsPtr)
            chineseTtsPtr = 0
        }
        isInitialized = false
    }

    // Native методы JNI
    private external fun nativeInitTts(
        modelPath: String,
        tokensPath: String,
        dataDir: String,
        numThreads: Int,
        noiseScale: Float,
        lengthScale: Float
    ): Long

    private external fun nativeGenerate(
        ttsPtr: Long,
        text: String,
        speakerId: Int,
        speed: Float
    ): ShortArray?

    private external fun nativeReleaseTts(ttsPtr: Long)
}
