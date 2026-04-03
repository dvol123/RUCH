package com.ruch.translator.stt

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import com.ruch.translator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Speech-to-Text using Sherpa-ONNX with Whisper models
 */
class WhisperSTT(private val context: Context) {

    companion object {
        private const val TAG = "WhisperSTT"
        
        private const val WHISPER_MODEL_DIR = "models/whisper"
        private const val ENCODER_FILE = "small-encoder.int8.onnx"
        private const val DECODER_FILE = "small-decoder.int8.onnx"
        private const val TOKENS_FILE = "small-tokens.txt"
    }

    private var recognizer: OfflineRecognizer? = null
    private var isInitialized = false

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "Initializing Whisper STT...")

            // Check if models exist in assets
            val assetFiles = context.assets.list(WHISPER_MODEL_DIR) ?: emptyArray()
            Log.d(TAG, "Assets in $WHISPER_MODEL_DIR: ${assetFiles.toList()}")
            
            if (assetFiles.isEmpty()) {
                Log.e(TAG, "No Whisper models found in assets")
                return@withContext false
            }

            // Copy models from assets to filesDir (required for sherpa-onnx)
            val modelsDir = copyModelsFromAssets()
            
            val encoderFile = File(modelsDir, ENCODER_FILE)
            val decoderFile = File(modelsDir, DECODER_FILE)
            val tokensFile = File(modelsDir, TOKENS_FILE)
            
            if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
                Log.e(TAG, "Whisper models not found after copy")
                isInitialized = false
                return@withContext false
            }

            Log.i(TAG, "Encoder: ${encoderFile.absolutePath}, size=${encoderFile.length()}")
            Log.i(TAG, "Decoder: ${decoderFile.absolutePath}, size=${decoderFile.length()}")
            Log.i(TAG, "Tokens: ${tokensFile.absolutePath}, size=${tokensFile.length()}")

            // Create config with absolute paths to files on filesystem
            val config = createConfig(modelsDir.absolutePath)
            
            Log.i(TAG, "Creating OfflineRecognizer...")
            // Use null for assetManager since we're using file paths, not assets
            recognizer = OfflineRecognizer(null, config)
            
            isInitialized = recognizer != null
            Log.i(TAG, "Whisper STT initialized: $isInitialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper STT: ${e.message}", e)
            false
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library error: ${e.message}", e)
            false
        }
    }

    private fun createConfig(modelDir: String): OfflineRecognizerConfig {
        return OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "$modelDir/$ENCODER_FILE",
                    decoder = "$modelDir/$DECODER_FILE",
                    language = "auto",
                    task = "transcribe",
                    tailPaddings = 0
                ),
                tokens = "$modelDir/$TOKENS_FILE",
                numThreads = 2,  // Reduced from 4 to be safer
                debug = false,
                provider = "cpu"
            ),
            decodingMethod = "greedy_search"
        )
    }

    private fun copyModelsFromAssets(): File {
        val modelsDir = File(context.filesDir, WHISPER_MODEL_DIR)
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        listOf(ENCODER_FILE, DECODER_FILE, TOKENS_FILE).forEach { fileName ->
            val destFile = File(modelsDir, fileName)
            if (!destFile.exists()) {
                try {
                    context.assets.open("$WHISPER_MODEL_DIR/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied: $fileName (${destFile.length()} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy $fileName: ${e.message}")
                }
            } else {
                Log.d(TAG, "Already exists: $fileName")
            }
        }

        return modelsDir
    }

    suspend fun transcribe(audioData: FloatArray, language: Language): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) {
            Log.e(TAG, "Not initialized")
            return@withContext null
        }

        if (audioData.isEmpty()) {
            Log.e(TAG, "Empty audio data")
            return@withContext null
        }

        var stream: OfflineStream? = null
        try {
            val durationSec = audioData.size / 16000.0f
            Log.i(TAG, "=== Transcribing ${audioData.size} samples ($durationSec s) ===")
            
            // Whisper requires minimum 0.5 seconds of audio
            if (audioData.size < 8000) {  // 0.5 seconds at 16kHz
                Log.e(TAG, "Audio too short: ${audioData.size} samples")
                return@withContext null
            }
            
            stream = recognizer!!.createStream()
            Log.i(TAG, "Stream created")
            
            stream.acceptWaveform(audioData, 16000)
            Log.i(TAG, "Waveform accepted")
            
            recognizer!!.decode(stream)
            Log.i(TAG, "Decode complete")
            
            val result = recognizer!!.getResult(stream)
            Log.i(TAG, "Result obtained")
            
            val text = result.text.trim()
            Log.i(TAG, "Transcribed: '$text'")
            
            text.ifEmpty { null }
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError in transcribe", e)
            null
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native error in transcribe: ${e.message}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Transcribe error: ${e.message}", e)
            null
        } finally {
            try {
                stream?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing stream: ${e.message}")
            }
        }
    }

    suspend fun transcribeFile(audioPath: String, language: Language): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) return@withContext null

        var stream: OfflineStream? = null
        try {
            stream = recognizer!!.createStream()
            val wave = WaveReader.readWave(audioPath)
            if (wave != null && wave.samples.isNotEmpty()) {
                stream.acceptWaveform(wave.samples, wave.sampleRate)
                recognizer!!.decode(stream)
                val result = recognizer!!.getResult(stream)
                result.text.trim().ifEmpty { null }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            null
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native error: ${e.message}", e)
            null
        } finally {
            try {
                stream?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing stream: ${e.message}")
            }
        }
    }

    fun isReady(): Boolean = isInitialized && recognizer != null

    fun release() {
        try {
            recognizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recognizer: ${e.message}")
        }
        recognizer = null
        isInitialized = false
        Log.i(TAG, "Released")
    }
}
