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

            val modelsDir = copyModelsFromAssets()
            
            val encoderFile = File(modelsDir, ENCODER_FILE)
            val decoderFile = File(modelsDir, DECODER_FILE)
            val tokensFile = File(modelsDir, TOKENS_FILE)
            
            if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
                Log.w(TAG, "Whisper models not found")
                isInitialized = false
                return@withContext false
            }

            val config = createConfig(modelsDir.absolutePath)
            recognizer = OfflineRecognizer(context.assets, config)
            
            isInitialized = recognizer != null
            Log.i(TAG, "Whisper STT initialized: $isInitialized")
            isInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper STT: ${e.message}", e)
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
                    tailPaddings = 1000
                ),
                tokens = "$modelDir/$TOKENS_FILE",
                numThreads = 4,
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
                    Log.d(TAG, "Copied: $fileName")
                } catch (e: Exception) {
                    Log.w(TAG, "File not found: $fileName")
                }
            }
        }

        return modelsDir
    }

    suspend fun transcribe(audioData: FloatArray, language: Language): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) {
            Log.e(TAG, "Not initialized")
            return@withContext null
        }

        try {
            val stream = recognizer!!.createStream()
            stream.acceptWaveform(audioData, 16000)
            recognizer!!.decode(stream)
            val result = recognizer!!.getResult(stream)
            stream.release()
            
            val text = result.text.trim()
            Log.d(TAG, "Transcribed: $text")
            text.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            null
        }
    }

    suspend fun transcribeFile(audioPath: String, language: Language): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) return@withContext null

        try {
            val stream = recognizer!!.createStream()
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
            Log.e(TAG, "Error: ${e.message}", e)
            null
        }
    }

    fun isReady(): Boolean = isInitialized && recognizer != null

    fun release() {
        recognizer?.release()
        recognizer = null
        isInitialized = false
        Log.i(TAG, "Released")
    }
}
