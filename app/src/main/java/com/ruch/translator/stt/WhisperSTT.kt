package com.ruch.translator.stt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.ruch.translator.audio.MelSpectrogram
import com.ruch.translator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Speech-to-Text с прямым ONNX Runtime
 * 
 * Модели загружаются из:
 * 1. Сначала проверяем filesDir (уже скопированные)
 * 2. Если нет - ищем в /sdcard/Download/ruch_models/whisper/
 * 3. Копируем в filesDir
 */
class WhisperSTT(private val context: Context) {

    companion object {
        private const val TAG = "WhisperSTT"
        
        // Имена файлов моделей (реальные с HuggingFace)
        private const val ENCODER_FILE = "encoder_model_int8.onnx"
        private const val DECODER_FILE = "decoder_model_int8.onnx"
        private const val TOKENIZER_FILE = "tokenizer.json"
        
        // Параметры Whisper small
        private const val SAMPLE_RATE = 16000
        private const val N_MELS = 80
        private const val N_CTX = 1500
        private const val D_MODEL = 768  // Whisper small hidden size
        private const val MAX_TOKENS = 448
        private const val VOCAB_SIZE = 51865
        
        // Специальные токены Whisper
        private const val TOKEN_EOT = 50257       // <|endoftext|>
        private const val TOKEN_START = 50258     // <|startoftranscript|>
        private const val TOKEN_TRANSCRIBE = 50359 // <|transcribe|>
        private const val TOKEN_NO_TIMESTAMPS = 50363 // <|notimestamps|>
        
        // Языковые токены
        private val LANG_TOKENS = mapOf(
            Language.RUSSIAN to 50263L,  // <|ru|>
            Language.CHINESE to 50260L   // <|zh|>
        )
    }

    private var ortEnv: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    
    private var isInitialized = false
    private var tokenizerData: JSONObject? = null
    private var vocab: Map<String, Int> = emptyMap()
    private var idToToken: Map<Int, String> = emptyMap()
    
    // Mel Spectrogram calculator
    private var melSpectrogram: MelSpectrogram? = null

    /**
     * Инициализация STT с URI папки (SAF)
     */
    suspend fun initializeWithUri(folderUri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "=== Initializing Whisper STT with URI: $folderUri ===")
            
            // Копируем модели из SAF URI в filesDir
            val modelsDir = copyModelsFromSafUri(folderUri)
            if (modelsDir == null) {
                Log.e(TAG, "Failed to copy models from URI")
                return@withContext false
            }
            
            // Проверяем файлы
            val encoderFile = File(modelsDir, ENCODER_FILE)
            val decoderFile = File(modelsDir, DECODER_FILE)
            val tokenizerFile = File(modelsDir, TOKENIZER_FILE)
            
            if (!encoderFile.exists()) {
                Log.e(TAG, "Encoder not found: ${encoderFile.absolutePath}")
                return@withContext false
            }
            if (!decoderFile.exists()) {
                Log.e(TAG, "Decoder not found: ${decoderFile.absolutePath}")
                return@withContext false
            }
            if (!tokenizerFile.exists()) {
                Log.e(TAG, "Tokenizer not found: ${tokenizerFile.absolutePath}")
                return@withContext false
            }
            
            Log.i(TAG, "Models found:")
            Log.i(TAG, "  Encoder: ${encoderFile.length() / 1024 / 1024} MB")
            Log.i(TAG, "  Decoder: ${decoderFile.length() / 1024 / 1024} MB")
            Log.i(TAG, "  Tokenizer: ${tokenizerFile.length() / 1024} KB")
            
            // Загружаем токенизатор
            loadTokenizer(tokenizerFile)
            Log.i(TAG, "Tokenizer loaded, vocab size: ${vocab.size}")
            
            // Инициализируем Mel Spectrogram
            melSpectrogram = MelSpectrogram(
                sampleRate = SAMPLE_RATE,
                nMels = N_MELS,
                nFft = 400,
                hopLength = 160
            )
            Log.i(TAG, "Mel Spectrogram initialized")
            
            // Создаём ONNX Environment
            ortEnv = OrtEnvironment.getEnvironment()
            
            // Загружаем сессии
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
            }
            
            Log.i(TAG, "Loading encoder session...")
            encoderSession = ortEnv?.createSession(encoderFile.absolutePath, options)
            
            Log.i(TAG, "Loading decoder session...")
            decoderSession = ortEnv?.createSession(decoderFile.absolutePath, options)
            
            if (encoderSession == null || decoderSession == null) {
                Log.e(TAG, "Failed to create ONNX sessions")
                return@withContext false
            }
            
            logModelInfo()
            
            isInitialized = true
            Log.i(TAG, "=== Whisper STT initialized successfully ===")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            false
        }
    }

    /**
     * Копировать модели из SAF URI в filesDir
     */
    private fun copyModelsFromSafUri(folderUri: Uri): File? {
        val filesDir = File(context.filesDir, "whisper")
        if (!filesDir.exists()) filesDir.mkdirs()
        
        try {
            val folder = DocumentFile.fromTreeUri(context, folderUri)
            if (folder == null || !folder.exists()) {
                Log.e(TAG, "Folder not found: $folderUri")
                return null
            }
            
            // Ищем подпапку whisper или используем саму папку
            var whisperFolder = folder.findFile("whisper")
            if (whisperFolder == null || !whisperFolder.isDirectory) {
                whisperFolder = folder
            }
            
            // Копируем файлы
            val files = listOf(ENCODER_FILE, DECODER_FILE, TOKENIZER_FILE)
            var allCopied = true
            
            for (fileName in files) {
                val source = whisperFolder.findFile(fileName)
                if (source == null || !source.exists()) {
                    Log.e(TAG, "Source file not found: $fileName")
                    allCopied = false
                    continue
                }
                
                val destFile = File(filesDir, fileName)
                
                // Копируем через ContentResolver
                try {
                    context.contentResolver.openInputStream(source.uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.i(TAG, "Copied: $fileName (${destFile.length() / 1024 / 1024} MB)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy $fileName", e)
                    allCopied = false
                }
            }
            
            return if (allCopied) filesDir else null
        } catch (e: Exception) {
            Log.e(TAG, "Error copying from SAF URI", e)
            return null
        }
    }

    /**
     * Инициализация STT (старый метод для обратной совместимости)
     * Загружает модели из filesDir или копирует из Download
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "=== Initializing Whisper STT ===")
            
            // Шаг 1: Получаем директорию с моделями
            val modelsDir = getOrCopyModels()
            if (modelsDir == null) {
                Log.e(TAG, "Models not found! Place models in /sdcard/Download/ruch_models/whisper/")
                return@withContext false
            }
            
            // Шаг 2: Проверяем файлы
            val encoderFile = File(modelsDir, ENCODER_FILE)
            val decoderFile = File(modelsDir, DECODER_FILE)
            val tokenizerFile = File(modelsDir, TOKENIZER_FILE)
            
            if (!encoderFile.exists()) {
                Log.e(TAG, "Encoder not found: ${encoderFile.absolutePath}")
                return@withContext false
            }
            if (!decoderFile.exists()) {
                Log.e(TAG, "Decoder not found: ${decoderFile.absolutePath}")
                return@withContext false
            }
            if (!tokenizerFile.exists()) {
                Log.e(TAG, "Tokenizer not found: ${tokenizerFile.absolutePath}")
                return@withContext false
            }
            
            Log.i(TAG, "Models found:")
            Log.i(TAG, "  Encoder: ${encoderFile.length() / 1024 / 1024} MB")
            Log.i(TAG, "  Decoder: ${decoderFile.length() / 1024 / 1024} MB")
            Log.i(TAG, "  Tokenizer: ${tokenizerFile.length() / 1024} KB")
            
            // Шаг 3: Загружаем токенизатор
            loadTokenizer(tokenizerFile)
            Log.i(TAG, "Tokenizer loaded, vocab size: ${vocab.size}")
            
            // Шаг 3.5: Инициализируем Mel Spectrogram
            melSpectrogram = MelSpectrogram(
                sampleRate = SAMPLE_RATE,
                nMels = N_MELS,
                nFft = 400,
                hopLength = 160
            )
            Log.i(TAG, "Mel Spectrogram initialized")
            
            // Шаг 4: Создаём ONNX Environment
            ortEnv = OrtEnvironment.getEnvironment()
            
            // Шаг 5: Загружаем сессии
            val options = OrtSession.SessionOptions().apply {
                // Оптимизации для мобильных
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
            }
            
            Log.i(TAG, "Loading encoder session...")
            encoderSession = ortEnv?.createSession(encoderFile.absolutePath, options)
            
            Log.i(TAG, "Loading decoder session...")
            decoderSession = ortEnv?.createSession(decoderFile.absolutePath, options)
            
            if (encoderSession == null || decoderSession == null) {
                Log.e(TAG, "Failed to create ONNX sessions")
                return@withContext false
            }
            
            // Логируем входы/выходы моделей
            logModelInfo()
            
            isInitialized = true
            Log.i(TAG, "=== Whisper STT initialized successfully ===")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialization failed", e)
            false
        }
    }

    /**
     * Получить модели: из filesDir или скопировать из Download
     */
    private fun getOrCopyModels(): File? {
        val filesDir = File(context.filesDir, "whisper")
        
        // Проверяем, есть ли уже модели в filesDir
        val encoderInFiles = File(filesDir, ENCODER_FILE)
        if (encoderInFiles.exists() && encoderInFiles.length() > 10_000_000) {
            Log.i(TAG, "Models already in filesDir: ${filesDir.absolutePath}")
            return filesDir
        }
        
        // Ищем в /sdcard/Download/ruch_models/whisper/
        val downloadDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "ruch_models/whisper"
        )
        
        Log.i(TAG, "Looking for models in: ${downloadDir.absolutePath}")
        
        if (!downloadDir.exists()) {
            Log.e(TAG, "Download directory not found: ${downloadDir.absolutePath}")
            Log.e(TAG, "Please create folder and place models:")
            Log.e(TAG, "  /sdcard/Download/ruch_models/whisper/encoder_model_int8.onnx")
            Log.e(TAG, "  /sdcard/Download/ruch_models/whisper/decoder_model_int8.onnx")
            Log.e(TAG, "  /sdcard/Download/ruch_models/whisper/tokenizer.json")
            return null
        }
        
        // Копируем файлы
        if (!filesDir.exists()) filesDir.mkdirs()
        
        val files = listOf(ENCODER_FILE, DECODER_FILE, TOKENIZER_FILE)
        var allCopied = true
        
        for (fileName in files) {
            val source = File(downloadDir, fileName)
            val dest = File(filesDir, fileName)
            
            if (!source.exists()) {
                Log.e(TAG, "Source file not found: ${source.absolutePath}")
                allCopied = false
                continue
            }
            
            if (!dest.exists() || dest.length() != source.length()) {
                Log.i(TAG, "Copying $fileName...")
                try {
                    source.copyTo(dest, overwrite = true)
                    Log.i(TAG, "Copied: $fileName (${dest.length() / 1024 / 1024} MB)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to copy $fileName", e)
                    allCopied = false
                }
            }
        }
        
        return if (allCopied) filesDir else null
    }

    /**
     * Загрузить токенизатор из tokenizer.json
     */
    private fun loadTokenizer(file: File) {
        try {
            val json = file.readText()
            tokenizerData = JSONObject(json)
            
            // Извлекаем vocab из model.vocab
            val modelObj = tokenizerData?.getJSONObject("model")
            val vocabObj = modelObj?.getJSONObject("vocab")
            
            if (vocabObj != null) {
                vocab = mutableMapOf()
                idToToken = mutableMapOf()
                
                val keys = vocabObj.keys()
                while (keys.hasNext()) {
                    val token = keys.next()
                    val id = vocabObj.getInt(token)
                    (vocab as MutableMap)[token] = id
                    (idToToken as MutableMap)[id] = token
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tokenizer", e)
        }
    }

    /**
     * Логировать информацию о моделях
     */
    private fun logModelInfo() {
        encoderSession?.let { session ->
            Log.i(TAG, "Encoder inputs: ${session.inputNames}")
            Log.i(TAG, "Encoder outputs: ${session.outputNames}")
        }
        decoderSession?.let { session ->
            Log.i(TAG, "Decoder inputs: ${session.inputNames}")
            Log.i(TAG, "Decoder outputs: ${session.outputNames}")
        }
    }

    /**
     * Транскрибировать аудио
     */
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
            Log.i(TAG, "=== Transcribing ${audioData.size} samples (${"%.2f".format(durationSec)}s) ===")

            // Минимум 0.5 секунд
            if (audioData.size < SAMPLE_RATE / 2) {
                Log.e(TAG, "Audio too short")
                return@withContext null
            }

            // Шаг 1: Вычислить log-mel спектрограмму
            val melSpectrogram = computeMelSpectrogram(audioData)
            Log.d(TAG, "Mel spectrogram computed: ${melSpectrogram.size}x${melSpectrogram[0].size}")

            // Шаг 2: Запустить encoder
            val encoderOutput = runEncoder(melSpectrogram)
            if (encoderOutput == null) {
                Log.e(TAG, "Encoder failed")
                return@withContext null
            }
            Log.d(TAG, "Encoder output: ${encoderOutput.size}x${encoderOutput[0].size}")

            // Шаг 3: Декодировать токены
            val tokens = decodeTokens(encoderOutput, language)
            Log.d(TAG, "Decoded ${tokens.size} tokens")

            // Шаг 4: Детокенизировать в текст
            val text = detokenize(tokens)
            Log.i(TAG, "Result: '$text'")

            text.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error", e)
            null
        }
    }

    /**
     * Вычислить log-mel спектрограмму из аудио
     */
    private fun computeMelSpectrogram(audioData: FloatArray): Array<FloatArray> {
        val mel = melSpectrogram ?: return Array(N_MELS) { FloatArray(N_CTX) }
        
        // Вычисляем mel-спектрограмму
        // Whisper ожидает фиксированную длину 3000 фреймов (30 секунд)
        val melSpec = mel.compute(audioData, N_CTX)
        
        // melSpec имеет размер [80 x N_CTX], что нам и нужно
        return melSpec
    }

    /**
     * Запустить encoder
     */
    private fun runEncoder(melSpectrogram: Array<FloatArray>): Array<FloatArray>? {
        val env = ortEnv ?: return null
        val session = encoderSession ?: return null
        
        try {
            // Создаём входной тензор [1, n_mels, n_ctx]
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
            
            // Получаем имя входа (обычно "input" или "mel")
            val inputName = session.inputNames.iterator().next()
            Log.d(TAG, "Encoder input name: $inputName")
            
            val result = session.run(mapOf(inputName to melTensor))
            
            // Получаем encoder output [1, n_ctx, d_model]
            val outputTensor = result[0].value as OnnxTensor
            val outputData = FloatArray(N_CTX * D_MODEL)
            outputTensor.floatBuffer.get(outputData)
            
            val encoderOutput = Array(N_CTX) { FloatArray(D_MODEL) }
            for (i in 0 until N_CTX) {
                for (j in 0 until D_MODEL) {
                    encoderOutput[i][j] = outputData[i * D_MODEL + j]
                }
            }
            
            melTensor.close()
            result.close()
            
            return encoderOutput
        } catch (e: Exception) {
            Log.e(TAG, "Encoder error", e)
            return null
        }
    }

    /**
     * Декодировать токены
     */
    private fun decodeTokens(encoderOutput: Array<FloatArray>, language: Language): List<Int> {
        val env = ortEnv ?: return emptyList()
        val session = decoderSession ?: return emptyList()
        
        try {
            val tokens = mutableListOf<Int>()
            
            // Начальные токены для Whisper
            tokens.add(TOKEN_START.toInt())
            LANG_TOKENS[language]?.let { tokens.add(it.toInt()) }
            tokens.add(TOKEN_TRANSCRIBE.toInt())
            tokens.add(TOKEN_NO_TIMESTAMPS.toInt())
            
            // Декодирование по токенам
            for (i in 0 until MAX_TOKENS) {
                // Создаём input_ids [1, num_tokens]
                val tokenIds = LongArray(tokens.size) { j -> tokens[j].toLong() }
                val inputIdsTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(tokenIds),
                    longArrayOf(1, tokens.size.toLong())
                )
                
                // Создаём encoder_hidden_states [1, n_ctx, d_model]
                val encoderData = FloatArray(N_CTX * D_MODEL)
                for (j in 0 until N_CTX) {
                    for (k in 0 until D_MODEL) {
                        encoderData[j * D_MODEL + k] = encoderOutput[j][k]
                    }
                }
                val encoderTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(encoderData),
                    longArrayOf(1, N_CTX.toLong(), D_MODEL.toLong())
                )
                
                // Запускаем decoder
                val inputs = mapOf(
                    "input_ids" to inputIdsTensor,
                    "encoder_hidden_states" to encoderTensor
                )
                
                val result = session.run(inputs)
                
                // Получаем logits [1, num_tokens, vocab_size]
                val logitsTensor = result[0].value as OnnxTensor
                val logits = FloatArray(tokens.size * VOCAB_SIZE)
                logitsTensor.floatBuffer.get(logits)
                
                // Берём logits для последнего токена
                val lastTokenLogits = FloatArray(VOCAB_SIZE) { j ->
                    logits[(tokens.size - 1) * VOCAB_SIZE + j]
                }
                
                // Argmax
                val nextToken = argMax(lastTokenLogits)
                
                // Проверяем на EOT
                if (nextToken == TOKEN_EOT) {
                    Log.d(TAG, "EOT at position $i")
                    inputIdsTensor.close()
                    encoderTensor.close()
                    result.close()
                    break
                }
                
                tokens.add(nextToken)
                
                inputIdsTensor.close()
                encoderTensor.close()
                result.close()
            }
            
            // Возвращаем только текстовые токены (без специальных)
            return tokens.drop(4) // Пропускаем начальные токены
        } catch (e: Exception) {
            Log.e(TAG, "Decoder error", e)
            return emptyList()
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

    /**
     * Детокенизировать токены в текст
     */
    private fun detokenize(tokens: List<Int>): String {
        val sb = StringBuilder()
        
        for (tokenId in tokens) {
            val token = idToToken[tokenId]
            if (token != null) {
                // Убираем специальный символ Whisper (Ġ = пробел)
                val decoded = token
                    .replace("Ġ", " ")
                    .replace("Ċ", "\n")
                    .replace("<|", "")
                    .replace("|>", "")
                
                // Пропускаем специальные токены
                if (!token.startsWith("<|")) {
                    sb.append(decoded)
                }
            }
        }
        
        return sb.toString().trim()
    }

    suspend fun transcribeFile(audioPath: String, language: Language): String? = withContext(Dispatchers.IO) {
        val file = File(audioPath)
        if (!file.exists()) {
            Log.e(TAG, "File not found: $audioPath")
            return@withContext null
        }
        
        // Читаем WAV файл (пропускаем 44 байта header)
        val bytes = file.readBytes()
        if (bytes.size < 44) {
            Log.e(TAG, "File too small")
            return@withContext null
        }
        
        val samples = (bytes.size - 44) / 2
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
            encoderSession?.close()
            decoderSession?.close()
            encoderSession = null
            decoderSession = null
            isInitialized = false
            Log.i(TAG, "Released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing", e)
        }
    }
}
