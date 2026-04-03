package com.ruch.translator.stt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.ruch.translator.data.Language
import com.ruch.translator.nn.TensorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Speech-to-Text с прямым ONNX Runtime (как RTranslator)
 * Использует 5 файлов моделей Whisper
 */
class WhisperSTT(private val context: Context) {

    companion object {
        private const val TAG = "WhisperSTT"
        
        private const val WHISPER_MODEL_DIR = "models/whisper"
        
        // 5 файлов моделей Whisper
        private const val INITIALIZER_FILE = "Whisper_initializer.onnx"
        private const val ENCODER_FILE = "Whisper_encoder.onnx"
        private const val DECODER_FILE = "Whisper_decoder.onnx"
        private const val CACHE_INITIALIZER_FILE = "Whisper_cache_initializer.onnx"
        private const val DETOKENIZER_FILE = "Whisper_detokenizer.onnx"
        
        // Параметры Whisper
        private const val SAMPLE_RATE = 16000
        private const val N_MELS = 80
        private const val N_CTX = 1500
        private const val MAX_TOKENS = 448
    }

    private var ortEnv: OrtEnvironment? = null
    private var initializerSession: OrtSession? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var cacheInitSession: OrtSession? = null
    private var detokenizerSession: OrtSession? = null
    
    private var isInitialized = false
    private var cachedEncoderOutput: Array<FloatArray>? = null
    
    // Языковые токены для Whisper
    private val languageTokens = mapOf(
        Language.RUSSIAN to 50258L,  // <|ru|>
        Language.CHINESE to 50260L   // <|zh|>
    )

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "Initializing Whisper STT with ONNX Runtime...")

            // Проверяем модели в assets
            val assetFiles = context.assets.list(WHISPER_MODEL_DIR) ?: emptyArray()
            Log.d(TAG, "Assets in $WHISPER_MODEL_DIR: ${assetFiles.toList()}")
            
            if (assetFiles.isEmpty()) {
                Log.e(TAG, "No Whisper models found in assets!")
                return@withContext false
            }

            // Копируем модели из assets
            val modelsDir = copyModelsFromAssets()

            // Создаём ONNX Environment
            ortEnv = OrtEnvironment.getEnvironment()

            // Загружаем все 5 сессий
            Log.i(TAG, "Loading ONNX sessions...")
            
            initializerSession = loadSession(modelsDir, INITIALIZER_FILE)
            encoderSession = loadSession(modelsDir, ENCODER_FILE)
            decoderSession = loadSession(modelsDir, DECODER_FILE)
            cacheInitSession = loadSession(modelsDir, CACHE_INITIALIZER_FILE)
            detokenizerSession = loadSession(modelsDir, DETOKENIZER_FILE)

            if (initializerSession == null || encoderSession == null || 
                decoderSession == null || cacheInitSession == null || detokenizerSession == null) {
                Log.e(TAG, "Failed to load one or more ONNX sessions")
                return@withContext false
            }

            isInitialized = true
            Log.i(TAG, "Whisper STT initialized successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Whisper STT", e)
            false
        }
    }

    private fun loadSession(modelsDir: File, fileName: String): OrtSession? {
        val file = File(modelsDir, fileName)
        if (!file.exists()) {
            Log.e(TAG, "Model file not found: $fileName")
            return null
        }
        Log.i(TAG, "Loading $fileName (${file.length() / 1024 / 1024} MB)")
        return ortEnv?.createSession(file.absolutePath, OrtSession.SessionOptions())
    }

    private fun copyModelsFromAssets(): File {
        val modelsDir = File(context.filesDir, WHISPER_MODEL_DIR)
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val files = listOf(
            INITIALIZER_FILE, ENCODER_FILE, DECODER_FILE,
            CACHE_INITIALIZER_FILE, DETOKENIZER_FILE
        )

        files.forEach { fileName ->
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
                    Log.e(TAG, "Failed to copy $fileName: ${e.message}")
                }
            }
        }

        return modelsDir
    }

    suspend fun transcribe(audioData: FloatArray, language: Language): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.e(TAG, "Not initialized")
            return@withContext null
        }

        if (audioData.isEmpty()) {
            Log.e(TAG, "Empty audio data")
            return@withContext null
        }

        try {
            val durationSec = audioData.size / SAMPLE_RATE.toFloat()
            Log.i(TAG, "=== Transcribing ${audioData.size} samples ($durationSec s) ===")

            // Минимум 0.5 секунд
            if (audioData.size < SAMPLE_RATE / 2) {
                Log.e(TAG, "Audio too short: ${audioData.size} samples")
                return@withContext null
            }

            // Step 1: Log Mel Spectrogram (упрощённый - вызываем initializer)
            val melSpectrogram = computeMelSpectrogram(audioData)
            Log.d(TAG, "Mel spectrogram shape: ${melSpectrogram.size} x ${melSpectrogram[0].size}")

            // Step 2: Encoder
            val encoderOutput = runEncoder(melSpectrogram)
            if (encoderOutput == null) {
                Log.e(TAG, "Encoder failed")
                return@withContext null
            }
            Log.d(TAG, "Encoder output shape: ${encoderOutput.size} x ${encoderOutput[0].size}")

            // Step 3: Initialize cache
            val initialCache = initializeCache(encoderOutput)
            Log.d(TAG, "Cache initialized")

            // Step 4: Decoder loop
            val tokens = decodeTokens(encoderOutput, initialCache, language)
            Log.d(TAG, "Decoded tokens: ${tokens.size}")

            // Step 5: Detokenize
            val text = detokenize(tokens)
            Log.i(TAG, "Transcribed: '$text'")

            text.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error", e)
            null
        }
    }

    private fun computeMelSpectrogram(audioData: FloatArray): Array<FloatArray> {
        // Упрощённая реализация - в реальности нужен полноценный mel spectrogram
        // Для initializer нужен входной тензор аудио
        
        val env = ortEnv ?: return Array(N_MELS) { FloatArray(N_CTX) }
        val session = initializerSession ?: return Array(N_MELS) { FloatArray(N_CTX) }
        
        try {
            // Паддинг до 30 секунд (480000 сэмплов)
            val paddedAudio = FloatArray(480000)
            val copyLen = minOf(audioData.size, 480000)
            System.arraycopy(audioData, 0, paddedAudio, 0, copyLen)
            
            // Создаём входной тензор [1, 480000]
            val audioTensor = OnnxTensor.createTensor(
                env, 
                FloatBuffer.wrap(paddedAudio),
                longArrayOf(1, 480000L)
            )
            
            val inputName = session.inputNames.iterator().next()
            val result = session.run(mapOf(inputName to audioTensor))
            
            // Получаем mel spectrogram [1, 80, 3000] -> [80, 1500]
            val outputTensor = result[0].value as OnnxTensor
            val melData = TensorUtils.getFloatArray(outputTensor)
            
            // Reshape к [N_MELS, N_CTX]
            val mel = Array(N_MELS) { FloatArray(N_CTX) }
            for (i in 0 until N_MELS) {
                for (j in 0 until N_CTX) {
                    mel[i][j] = melData[i * N_CTX + j]
                }
            }
            
            audioTensor.close()
            result.close()
            
            return mel
        } catch (e: Exception) {
            Log.e(TAG, "Mel spectrogram error", e)
            return Array(N_MELS) { FloatArray(N_CTX) }
        }
    }

    private fun runEncoder(melSpectrogram: Array<FloatArray>): Array<FloatArray>? {
        val env = ortEnv ?: return null
        val session = encoderSession ?: return null
        
        try {
            // Создаём входной тензор [1, 80, 1500]
            val melData = FloatArray(N_MELS * N_CTX)
            for (i in 0 until N_MELS) {
                for (j in 0 until N_CTX) {
                    melData[i * N_CTX + j] = melSpectrogram[i][j]
                }
            }
            
            val melTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(melData),
                longArrayOf(1, N_MELS.toLong(), N_CTX.toLong())
            )
            
            val inputName = session.inputNames.iterator().next()
            val result = session.run(mapOf(inputName to melTensor))
            
            // Получаем encoder output [1, 1500, 384] для tiny, [1, 1500, 768] для small
            val outputTensor = result[0].value as OnnxTensor
            val outputShape = outputTensor.info.shape
            val hiddenSize = outputShape[2].toInt()
            
            val encoderData = TensorUtils.getFloatArray(outputTensor)
            
            // Reshape к [1500, hiddenSize]
            val encoderOutput = Array(N_CTX) { FloatArray(hiddenSize) }
            for (i in 0 until N_CTX) {
                for (j in 0 until hiddenSize) {
                    encoderOutput[i][j] = encoderData[i * hiddenSize + j]
                }
            }
            
            cachedEncoderOutput = encoderOutput
            
            melTensor.close()
            result.close()
            
            return encoderOutput
        } catch (e: Exception) {
            Log.e(TAG, "Encoder error", e)
            return null
        }
    }

    private fun initializeCache(encoderOutput: Array<FloatArray>): Array<FloatArray> {
        val env = ortEnv ?: return emptyArray()
        val session = cacheInitSession ?: return emptyArray()
        
        try {
            // Создаём входной тензор из encoder output
            val hiddenSize = encoderOutput[0].size
            val encoderData = FloatArray(N_CTX * hiddenSize)
            for (i in 0 until N_CTX) {
                for (j in 0 until hiddenSize) {
                    encoderData[i * hiddenSize + j] = encoderOutput[i][j]
                }
            }
            
            val encoderTensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(encoderData),
                longArrayOf(1, N_CTX.toLong(), hiddenSize.toLong())
            )
            
            val inputName = session.inputNames.iterator().next()
            val result = session.run(mapOf(inputName to encoderTensor))
            
            // Получаем cache
            val outputTensor = result[0].value as OnnxTensor
            val cacheData = TensorUtils.getFloatArray(outputTensor)
            
            // Reshape cache
            val cache = Array(1) { FloatArray(cacheData.size) { cacheData[it] } }
            
            encoderTensor.close()
            result.close()
            
            return cache
        } catch (e: Exception) {
            Log.e(TAG, "Cache init error", e)
            return emptyArray()
        }
    }

    private fun decodeTokens(
        encoderOutput: Array<FloatArray>,
        initialCache: Array<FloatArray>,
        language: Language
    ): LongArray {
        val env = ortEnv ?: return longArrayOf()
        val session = decoderSession ?: return longArrayOf()
        
        try {
            val hiddenSize = encoderOutput[0].size
            val tokens = mutableListOf<Long>()
            
            // Начальный токен
            var currentToken = 50258L // <|lang|> - будет заменён на язык
            
            // Языковой токен
            val langToken = languageTokens[language] ?: 50258L
            
            // Токены для декодирования
            val sosToken = 50257L  // <|startoftranscript|>
            val transcribeToken = 50359L  // <|transcribe|>
            val eotToken = 50257L  // <|endoftext|> <|endoftext|>
            
            tokens.add(sosToken)
            tokens.add(langToken)
            tokens.add(transcribeToken)
            
            var cache = initialCache
            
            // Декодирование по токенам
            for (i in 0 until MAX_TOKENS) {
                // Подготавливаем вход для decoder
                val tokenData = LongArray(1) { currentToken }
                val tokenTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(tokenData),
                    longArrayOf(1, 1)
                )
                
                // Encoder output
                val encoderData = FloatArray(N_CTX * hiddenSize)
                for (j in 0 until N_CTX) {
                    for (k in 0 until hiddenSize) {
                        encoderData[j * hiddenSize + k] = encoderOutput[j][k]
                    }
                }
                val encoderTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(encoderData),
                    longArrayOf(1, N_CTX.toLong(), hiddenSize.toLong())
                )
                
                // Cache
                // ...
                
                val inputs = mapOf(
                    "input_ids" to tokenTensor,
                    "encoder_hidden_states" to encoderTensor
                )
                
                val result = session.run(inputs)
                
                // Получаем logits и следующий токен
                val logitsTensor = result[0].value as OnnxTensor
                val logits = TensorUtils.getFloatArray(logitsTensor)
                
                // Argmax для следующего токена
                val vocabSize = logits.size / tokens.size
                val lastLogits = FloatArray(vocabSize) { logits[(tokens.size - 1) * vocabSize + it] }
                val nextToken = argMax(lastLogits)
                
                if (nextToken == eotToken.toInt()) {
                    Log.d(TAG, "End of transcription at token $i")
                    break
                }
                
                tokens.add(nextToken.toLong())
                currentToken = nextToken.toLong()
                
                tokenTensor.close()
                encoderTensor.close()
                result.close()
            }
            
            return tokens.toLongArray()
        } catch (e: Exception) {
            Log.e(TAG, "Decoder error", e)
            return longArrayOf()
        }
    }

    private fun argMax(array: FloatArray): Int {
        var maxIdx = 0
        var maxVal = array[0]
        for (i in 1 until array.size) {
            if (array[i] > maxVal) {
                maxVal = array[i]
                maxIdx = i
            }
        }
        return maxIdx
    }

    private fun detokenize(tokens: LongArray): String {
        val env = ortEnv ?: return ""
        val session = detokenizerSession ?: return ""
        
        try {
            // Создаём входной тензор токенов
            val tokenTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tokens),
                longArrayOf(1, tokens.size.toLong())
            )
            
            val inputName = session.inputNames.iterator().next()
            val result = session.run(mapOf(inputName to tokenTensor))
            
            // Получаем строку
            val output = result[0].value
            val text = output?.toString() ?: ""
            
            // Очищаем специальные токены
            val cleanText = text
                .replace("<|startoftranscript|>", "")
                .replace("<|transcribe|>", "")
                .replace("<|notimestamps|>", "")
                .replace(Regex("<\\|[a-z]+\\|>"), "")
                .trim()
            
            tokenTensor.close()
            result.close()
            
            return cleanText
        } catch (e: Exception) {
            Log.e(TAG, "Detokenizer error", e)
            return ""
        }
    }

    suspend fun transcribeFile(audioPath: String, language: Language): String? = withContext(Dispatchers.IO) {
        // Читаем WAV файл
        val file = File(audioPath)
        if (!file.exists()) {
            Log.e(TAG, "Audio file not found: $audioPath")
            return@withContext null
        }
        
        // Простой WAV reader
        val bytes = file.readBytes()
        // Пропускаем WAV header (44 байта) и конвертируем в float
        val samples = (bytes.size - 44) / 2  // 16-bit samples
        val audioData = FloatArray(samples)
        for (i in 0 until samples) {
            val low = bytes[44 + i * 2].toInt() and 0xFF
            val high = bytes[44 + i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            audioData[i] = sample / 32768f
        }
        
        transcribe(audioData, language)
    }

    fun isReady(): Boolean = isInitialized

    fun release() {
        try {
            initializerSession?.close()
            encoderSession?.close()
            decoderSession?.close()
            cacheInitSession?.close()
            detokenizerSession?.close()
            ortEnv = null
            
            initializerSession = null
            encoderSession = null
            decoderSession = null
            cacheInitSession = null
            detokenizerSession = null
            
            isInitialized = false
            Log.i(TAG, "Released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        }
    }
}
