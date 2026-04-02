package com.ruch.translator.tts

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.ruch.translator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Text-to-Speech using Sherpa-ONNX with VITS models
 * 
 * Supports offline synthesis for Russian and Chinese languages.
 * Uses pre-trained VITS models for natural sounding speech.
 */
class SherpaTTSEngine(private val context: Context) {

    companion object {
        private const val TAG = "SherpaTTS"
        
        // Model directories in assets
        private const val RUSSIAN_TTS_DIR = "models/tts/ru"
        private const val CHINESE_TTS_DIR = "models/tts/zh"
        
        // Model file names
        private const val MODEL_ONNX = "model.onnx"
        private const val TOKENS_TXT = "tokens.txt"
        private const val DICT_DIR = "dict"
    }

    private var russianTts: OfflineTts? = null
    private var chineseTts: OfflineTts? = null
    private var isInitialized = false

    /**
     * Initialize TTS engines for both languages
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "Initializing Sherpa TTS...")

            // Initialize Russian TTS
            val ruSuccess = initRussianTTS()
            Log.i(TAG, "Russian TTS initialized: $ruSuccess")

            // Initialize Chinese TTS
            val zhSuccess = initChineseTTS()
            Log.i(TAG, "Chinese TTS initialized: $zhSuccess")

            isInitialized = ruSuccess || zhSuccess
            Log.i(TAG, "Sherpa TTS initialization complete: $isInitialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TTS: ${e.message}", e)
            false
        }
    }

    /**
     * Initialize Russian TTS model
     */
    private fun initRussianTTS(): Boolean {
        return try {
            val modelDir = copyTTSModels(RUSSIAN_TTS_DIR, "ru")
            val modelFile = File(modelDir, MODEL_ONNX)
            val tokensFile = File(modelDir, TOKENS_TXT)
            
            if (!modelFile.exists() || !tokensFile.exists()) {
                Log.w(TAG, "Russian TTS model not found")
                return false
            }

            russianTts = createTts(context.assets, modelDir.absolutePath)
            russianTts != null
        } catch (e: Exception) {
            Log.e(TAG, "Russian TTS init error: ${e.message}", e)
            false
        }
    }

    /**
     * Initialize Chinese TTS model
     */
    private fun initChineseTTS(): Boolean {
        return try {
            val modelDir = copyTTSModels(CHINESE_TTS_DIR, "zh")
            val modelFile = File(modelDir, MODEL_ONNX)
            val tokensFile = File(modelDir, TOKENS_TXT)
            
            if (!modelFile.exists() || !tokensFile.exists()) {
                Log.w(TAG, "Chinese TTS model not found")
                return false
            }

            chineseTts = createTts(context.assets, modelDir.absolutePath)
            chineseTts != null
        } catch (e: Exception) {
            Log.e(TAG, "Chinese TTS init error: ${e.message}", e)
            false
        }
    }

    /**
     * Create TTS using sherpa-onnx API
     */
    private fun createTts(assetManager: AssetManager, modelDir: String): OfflineTts? {
        return try {
            val modelPath = "$modelDir/$MODEL_ONNX"
            val tokensPath = "$modelDir/$TOKENS_TXT"
            
            // Create VITS model config
            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelPath,
                tokens = tokensPath,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )
            
            // Create model config
            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
            
            // Create TTS config
            val config = OfflineTtsConfig(
                modelConfig = modelConfig,
                maxNumSenetences = 1  // Note: intentional typo in sherpa-onnx API
            )
            
            // Create TTS with AssetManager
            OfflineTts(assetManager, config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TTS: ${e.message}", e)
            null
        }
    }

    /**
     * Copy TTS models from assets
     */
    private fun copyTTSModels(assetDir: String, langCode: String): File {
        val destDir = File(context.filesDir, "models/tts/$langCode")
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        // Copy all .onnx files
        try {
            val assetFiles = context.assets.list(assetDir) ?: emptyArray()
            for (fileName in assetFiles) {
                if (fileName.endsWith(".onnx")) {
                    val destFile = File(destDir, MODEL_ONNX)
                    if (!destFile.exists()) {
                        context.assets.open("$assetDir/$fileName").use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Copied ONNX model: $fileName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error copying ONNX model: ${e.message}")
        }

        // Copy tokens.txt
        val tokensFile = File(destDir, TOKENS_TXT)
        if (!tokensFile.exists()) {
            try {
                context.assets.open("$assetDir/$TOKENS_TXT").use { input ->
                    FileOutputStream(tokensFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied tokens.txt")
            } catch (e: Exception) {
                Log.w(TAG, "Error copying tokens.txt: ${e.message}")
            }
        }

        // Copy dict directory if exists (for Chinese)
        try {
            val dictDir = File(destDir, DICT_DIR)
            if (!dictDir.exists()) {
                dictDir.mkdirs()
            }
            val dictFiles = context.assets.list("$assetDir/$DICT_DIR") ?: emptyArray()
            for (dictFile in dictFiles) {
                val destDictFile = File(dictDir, dictFile)
                if (!destDictFile.exists()) {
                    context.assets.open("$assetDir/$DICT_DIR/$dictFile").use { input ->
                        FileOutputStream(destDictFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Dict directory might not exist
        }

        return destDir
    }

    /**
     * Synthesize speech from text
     * @param text Text to synthesize
     * @param language Target language
     * @param speed Speech speed (1.0 = normal)
     * @return Audio samples as ShortArray (22050Hz mono) or null if failed
     */
    suspend fun synthesize(
        text: String, 
        language: Language, 
        speed: Float = 1.0f
    ): ShortArray? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null

        val tts = when (language) {
            Language.RUSSIAN -> russianTts
            Language.CHINESE -> chineseTts
        }

        if (tts == null) {
            Log.e(TAG, "TTS not initialized for ${language.getDisplayName()}")
            return@withContext null
        }

        try {
            Log.d(TAG, "Synthesizing: $text (${language.getDisplayName()})")
            
            // Generate audio
            val audio = tts.generate(text, speed)
            
            if (audio != null) {
                val samples = audio.samples
                if (samples != null && samples.isNotEmpty()) {
                    Log.d(TAG, "Generated ${samples.size} samples at ${audio.sampleRate}Hz")
                    samples
                } else {
                    Log.e(TAG, "TTS generation returned empty result")
                    null
                }
            } else {
                Log.e(TAG, "TTS generation returned null")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS synthesis error: ${e.message}", e)
            null
        }
    }

    /**
     * Check if TTS is ready for a specific language
     */
    fun isReady(language: Language): Boolean {
        return when (language) {
            Language.RUSSIAN -> russianTts != null
            Language.CHINESE -> chineseTts != null
        }
    }

    /**
     * Check if any TTS is ready
     */
    fun isReady(): Boolean = russianTts != null || chineseTts != null

    /**
     * Release resources
     */
    fun release() {
        try {
            russianTts?.release()
            russianTts = null
            
            chineseTts?.release()
            chineseTts = null
            
            isInitialized = false
            Log.i(TAG, "Sherpa TTS released")
        } catch (e: Exception) {
            Log.e(TAG, "Release error: ${e.message}")
        }
    }
}
