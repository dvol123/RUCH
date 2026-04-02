package com.ruch.translator.stt

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
 * Speech-to-Text using Sherpa-ONNX with Whisper models
 * 
 * Supports offline recognition for Russian and Chinese languages.
 * Uses Whisper small model optimized with int8 quantization.
 */
class WhisperSTT(private val context: Context) {

    companion object {
        private const val TAG = "WhisperSTT"
        
        // Model files names in assets
        private const val WHISPER_MODEL_DIR = "models/whisper"
        private const val ENCODER_FILE = "encoder-epoch-99-int8.onnx"
        private const val DECODER_FILE = "decoder-epoch-99-int8.onnx"
        private const val TOKENS_FILE = "tokens.txt"
    }

    private var recognizer: OfflineRecognizer? = null
    private var isInitialized = false

    /**
     * Initialize Whisper model for offline speech recognition
     * Copies models from assets to internal storage and loads them
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "Initializing Whisper STT...")

            // Copy models from assets to internal storage
            val modelsDir = copyModelsFromAssets()
            
            // Check if models exist
            val encoderFile = File(modelsDir, ENCODER_FILE)
            val decoderFile = File(modelsDir, DECODER_FILE)
            val tokensFile = File(modelsDir, TOKENS_FILE)
            
            if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
                Log.w(TAG, "Whisper models not found, using fallback mode")
                isInitialized = false
                return@withContext false
            }

            // Create recognizer using AssetManager
            recognizer = createRecognizer(context.assets, modelsDir.absolutePath)
            
            isInitialized = recognizer != null
            Log.i(TAG, "Whisper STT initialized: $isInitialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper STT: ${e.message}", e)
            isInitialized = false
            false
        }
    }

    /**
     * Create Whisper recognizer using sherpa-onnx API
     */
    private fun createRecognizer(assetManager: AssetManager, modelDir: String): OfflineRecognizer? {
        return try {
            // Create config using the sherpa-onnx API
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = 16000,
                    featureDim = 80
                ),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = "$modelDir/$ENCODER_FILE",
                        decoder = "$modelDir/$DECODER_FILE",
                        tokens = "$modelDir/$TOKENS_FILE",
                        language = "auto",
                        task = "transcribe",
                        tailPaddings = -1
                    ),
                    numThreads = 4,
                    debug = false,
                    provider = "cpu"
                ),
                decodingMethod = "greedy_search",
                maxActivePaths = 4
            )
            
            // Create recognizer with AssetManager and config
            OfflineRecognizer(assetManager, config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create recognizer: ${e.message}", e)
            null
        }
    }

    /**
     * Copy models from assets to internal storage
     */
    private fun copyModelsFromAssets(): File {
        val modelsDir = File(context.filesDir, WHISPER_MODEL_DIR)
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val modelFiles = listOf(ENCODER_FILE, DECODER_FILE, TOKENS_FILE)
        
        for (fileName in modelFiles) {
            val destFile = File(modelsDir, fileName)
            if (!destFile.exists()) {
                try {
                    context.assets.open("$WHISPER_MODEL_DIR/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied model file: $fileName")
                } catch (e: Exception) {
                    Log.w(TAG, "Model file not found in assets: $fileName")
                }
            }
        }

        return modelsDir
    }

    /**
     * Transcribe audio data to text
     * @param audioData Float array of audio samples (-1.0 to 1.0), 16kHz mono
     * @param language Source language (Russian or Chinese)
     * @return Transcribed text or null if failed
     */
    suspend fun transcribe(audioData: FloatArray, language: Language): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) {
            Log.e(TAG, "Whisper STT not initialized")
            return@withContext null
        }

        try {
            // Create audio stream from float array
            val stream = recognizer!!.createStream()
            
            // Set language hint
            val langCode = when (language) {
                Language.RUSSIAN -> "ru"
                Language.CHINESE -> "zh"
            }
            
            // Accept waveform data (16kHz, mono)
            stream.acceptWaveform(audioData, 16000)
            
            // Decode
            recognizer!!.decode(stream)
            
            // Get result
            val result = recognizer!!.getResult(stream)
            val text = result.text.trim()
            
            stream.release()
            
            Log.d(TAG, "Transcribed ($langCode): $text")
            text.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error: ${e.message}", e)
            null
        }
    }

    /**
     * Transcribe from audio file
     */
    suspend fun transcribeFile(audioPath: String, language: Language): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) {
            return@withContext null
        }

        try {
            val stream = recognizer!!.createStream()
            
            // Load audio file (WAV format, 16kHz)
            val wave = WaveReader.readWave(audioPath)
            if (wave != null) {
                stream.acceptWaveform(wave.samples, wave.sampleRate)
                recognizer!!.decode(stream)
                val result = recognizer!!.getResult(stream)
                stream.release()
                result.text.trim().ifEmpty { null }
            } else {
                stream.release()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "File transcription error: ${e.message}", e)
            null
        }
    }

    /**
     * Check if models are ready
     */
    fun isReady(): Boolean = isInitialized && recognizer != null

    /**
     * Release resources
     */
    fun release() {
        try {
            recognizer?.release()
            recognizer = null
            isInitialized = false
            Log.i(TAG, "Whisper STT released")
        } catch (e: Exception) {
            Log.e(TAG, "Release error: ${e.message}")
        }
    }
}
