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
        private const val LEXICON_TXT = "lexicon.txt"
        private const val RULE_FAR = "rule.far"
    }

    private var russianTts: OfflineTts? = null
    private var chineseTts: OfflineTts? = null
    private var isInitialized = false

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "Initializing Sherpa TTS...")

            val ruSuccess = initRussianTTS()
            Log.i(TAG, "Russian TTS: $ruSuccess")

            val zhSuccess = initChineseTTS()
            Log.i(TAG, "Chinese TTS: $zhSuccess")

            isInitialized = ruSuccess || zhSuccess
            Log.i(TAG, "TTS ready: $isInitialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Init error: ${e.message}", e)
            false
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library error: ${e.message}", e)
            false
        }
    }

    private fun initRussianTTS(): Boolean {
        return try {
            Log.d(TAG, "Initializing Russian TTS from $RUSSIAN_TTS_DIR")
            
            // Check assets
            val assetFiles = context.assets.list(RUSSIAN_TTS_DIR) ?: emptyArray()
            Log.d(TAG, "Assets in $RUSSIAN_TTS_DIR: ${assetFiles.toList()}")
            
            if (assetFiles.isEmpty()) {
                Log.e(TAG, "No assets found in $RUSSIAN_TTS_DIR")
                return false
            }
            
            // Copy models to filesDir
            val modelDir = copyTTSModels(RUSSIAN_TTS_DIR, "ru")
            
            val modelFile = File(modelDir, "model.onnx")
            val tokensFile = File(modelDir, TOKENS_TXT)
            
            Log.d(TAG, "Model: ${modelFile.absolutePath}, exists=${modelFile.exists()}, size=${modelFile.length()}")
            Log.d(TAG, "Tokens: ${tokensFile.absolutePath}, exists=${tokensFile.exists()}, size=${tokensFile.length()}")
            
            if (!modelFile.exists() || !tokensFile.exists()) {
                Log.e(TAG, "Missing Russian TTS files")
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
                    debug = false,
                    provider = "cpu"
                ),
                maxNumSentences = 1
            )

            Log.d(TAG, "Creating Russian OfflineTts...")
            // Use null for assetManager since we're using file paths, not assets
            russianTts = OfflineTts(null, config)
            Log.i(TAG, "Russian TTS initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Russian TTS init error: ${e.message}", e)
            false
        }
    }

    private fun initChineseTTS(): Boolean {
        return try {
            Log.d(TAG, "Initializing Chinese TTS from $CHINESE_TTS_DIR")
            
            // Check assets
            val assetFiles = context.assets.list(CHINESE_TTS_DIR) ?: emptyArray()
            Log.d(TAG, "Assets in $CHINESE_TTS_DIR: ${assetFiles.toList()}")
            
            if (assetFiles.isEmpty()) {
                Log.e(TAG, "No assets found in $CHINESE_TTS_DIR")
                return false
            }
            
            // Copy models to filesDir
            val modelDir = copyTTSModels(CHINESE_TTS_DIR, "zh")
            
            val modelFile = File(modelDir, "vits-aishell3.int8.onnx")
            val tokensFile = File(modelDir, TOKENS_TXT)
            val lexiconFile = File(modelDir, LEXICON_TXT)
            val ruleFarFile = File(modelDir, RULE_FAR)
            
            Log.d(TAG, "Model: ${modelFile.absolutePath}, exists=${modelFile.exists()}, size=${modelFile.length()}")
            Log.d(TAG, "Tokens: ${tokensFile.absolutePath}, exists=${tokensFile.exists()}")
            Log.d(TAG, "Lexicon: ${lexiconFile.absolutePath}, exists=${lexiconFile.exists()}")
            Log.d(TAG, "Rule.far: ${ruleFarFile.absolutePath}, exists=${ruleFarFile.exists()}")
            
            if (!modelFile.exists() || !tokensFile.exists()) {
                Log.e(TAG, "Missing Chinese TTS files")
                return false
            }

            // Chinese needs lexicon for proper pronunciation
            val vitsConfig = if (lexiconFile.exists()) {
                OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    lexicon = lexiconFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            } else {
                OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                ruleFars = if (ruleFarFile.exists()) ruleFarFile.absolutePath else "",
                maxNumSentences = 1
            )

            Log.d(TAG, "Creating Chinese OfflineTts...")
            // Use null for assetManager since we're using file paths, not assets
            chineseTts = OfflineTts(null, config)
            Log.i(TAG, "Chinese TTS initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Chinese TTS init error: ${e.message}", e)
            false
        }
    }

    private fun copyTTSModels(assetDir: String, langCode: String): File {
        val destDir = File(context.filesDir, "models/tts/$langCode")
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        try {
            val assetFiles = context.assets.list(assetDir) ?: emptyArray()
            for (fileName in assetFiles) {
                val destFile = File(destDir, fileName)
                if (!destFile.exists()) {
                    context.assets.open("$assetDir/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied: $fileName (${destFile.length()} bytes)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Copy error: ${e.message}")
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
