package com.ruch.translator.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.ruch.translator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Text-to-Speech using Sherpa-ONNX with VITS models
 */
class SherpaTTSEngine(private val context: Context) {

    companion object {
        private const val TAG = "SherpaTTS"
        
        private const val RUSSIAN_TTS_DIR = "models/tts/ru"
        private const val CHINESE_TTS_DIR = "models/tts/zh"
        
        private const val TOKENS_TXT = "tokens.txt"
    }

    private var russianTts: OfflineTts? = null
    private var chineseTts: OfflineTts? = null
    private var isInitialized = false

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "Initializing Sherpa TTS...")

            val ruSuccess = initTTS(RUSSIAN_TTS_DIR, "ru")
            Log.i(TAG, "Russian TTS: $ruSuccess")

            val zhSuccess = initTTS(CHINESE_TTS_DIR, "zh")
            Log.i(TAG, "Chinese TTS: $zhSuccess")

            isInitialized = ruSuccess || zhSuccess
            Log.i(TAG, "TTS ready: $isInitialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Init error: ${e.message}", e)
            false
        }
    }

    private fun initTTS(assetDir: String, langCode: String): Boolean {
        return try {
            Log.d(TAG, "Initializing TTS for $langCode from $assetDir")
            
            // Check if assets exist
            val assetFiles = context.assets.list(assetDir) ?: emptyArray()
            Log.d(TAG, "Assets in $assetDir: ${assetFiles.toList()}")
            
            if (assetFiles.isEmpty()) {
                Log.e(TAG, "No assets found in $assetDir")
                return false
            }
            
            val modelDir = copyTTSModels(assetDir, langCode)
            
            // Find .onnx file
            val onnxFiles = modelDir.listFiles()?.filter { it.extension == "onnx" }
            Log.d(TAG, "ONNX files in ${modelDir.absolutePath}: ${onnxFiles?.map { it.name }}")
            
            if (onnxFiles.isNullOrEmpty()) {
                Log.e(TAG, "No ONNX model for $langCode in ${modelDir.absolutePath}")
                return false
            }
            
            val modelFile = onnxFiles.first()
            val tokensFile = File(modelDir, TOKENS_TXT)
            
            Log.d(TAG, "Model: ${modelFile.absolutePath}, exists=${modelFile.exists()}")
            Log.d(TAG, "Tokens: ${tokensFile.absolutePath}, exists=${tokensFile.exists()}")
            
            if (!tokensFile.exists()) {
                Log.e(TAG, "No tokens.txt for $langCode")
                return false
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = modelFile.absolutePath,
                        tokens = tokensFile.absolutePath,
                        noiseScale = 0.667f,
                        noiseScaleW = 0.8f,
                        lengthScale = 1.0f
                    ),
                    numThreads = 2,
                    debug = true,
                    provider = "cpu"
                ),
                maxNumSentences = 1
            )

            Log.d(TAG, "Creating OfflineTts for $langCode...")
            val tts = OfflineTts(context.assets, config)
            
            if (langCode == "ru") {
                russianTts = tts
            } else {
                chineseTts = tts
            }
            Log.i(TAG, "TTS $langCode initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "TTS init error ($langCode): ${e.message}", e)
            false
        }
    }

    private fun copyTTSModels(assetDir: String, langCode: String): File {
        val destDir = File(context.filesDir, "models/tts/$langCode")
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        try {
            // Copy all files from asset directory
            val assetFiles = context.assets.list(assetDir) ?: emptyArray()
            for (fileName in assetFiles) {
                val destFile = File(destDir, fileName)
                if (!destFile.exists()) {
                    context.assets.open("$assetDir/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied: $fileName")
                }
            }
            
            // Copy dict directory if exists
            val dictDir = File(destDir, "dict")
            if (!dictDir.exists()) {
                dictDir.mkdirs()
            }
            val dictFiles = context.assets.list("$assetDir/dict") ?: emptyArray()
            for (dictFile in dictFiles) {
                val destDictFile = File(dictDir, dictFile)
                if (!destDictFile.exists()) {
                    context.assets.open("$assetDir/dict/$dictFile").use { input ->
                        FileOutputStream(destDictFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Copy error: ${e.message}")
        }

        return destDir
    }

    /**
     * Synthesize speech - returns FloatArray normalized to [-1, 1] at sample rate from model
     */
    suspend fun synthesize(
        text: String, 
        language: Language, 
        speed: Float = 1.0f
    ): FloatArray? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null

        val tts = when (language) {
            Language.RUSSIAN -> russianTts
            Language.CHINESE -> chineseTts
        }

        if (tts == null) {
            Log.e(TAG, "TTS not ready for ${language.getDisplayName()}")
            return@withContext null
        }

        try {
            Log.d(TAG, "Synthesizing: $text")
            
            val audio = tts.generate(text, sid = 0, speed = speed)
            
            if (audio != null && audio.samples != null && audio.samples.isNotEmpty()) {
                Log.d(TAG, "Generated ${audio.samples.size} samples at ${audio.sampleRate}Hz")
                audio.samples
            } else {
                Log.e(TAG, "Empty result")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis error: ${e.message}", e)
            null
        }
    }

    /**
     * Get sample rate for language
     */
    fun getSampleRate(language: Language): Int {
        val tts = when (language) {
            Language.RUSSIAN -> russianTts
            Language.CHINESE -> chineseTts
        }
        return tts?.sampleRate() ?: 22050
    }

    fun isReady(language: Language): Boolean {
        return when (language) {
            Language.RUSSIAN -> russianTts != null
            Language.CHINESE -> chineseTts != null
        }
    }

    fun isReady(): Boolean = russianTts != null || chineseTts != null

    fun release() {
        russianTts?.release()
        russianTts = null
        
        chineseTts?.release()
        chineseTts = null
        
        isInitialized = false
        Log.i(TAG, "Released")
    }
}
