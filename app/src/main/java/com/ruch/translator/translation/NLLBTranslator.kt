package com.ruch.translator.translation

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import com.ruch.translator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.LongBuffer

/**
 * Machine Translation using NLLB-200 via ONNX Runtime
 * 
 * Uses Meta's NLLB-200 distilled model with int8 quantization
 * for efficient offline translation between Russian and Chinese.
 */
class NLLBTranslator(private val context: Context) {

    companion object {
        private const val TAG = "NLLBTranslator"
        
        // Model files
        private const val MODEL_DIR = "models/nllb"
        private const val ENCODER_FILE = "encoder_int8.onnx"
        private const val DECODER_FILE = "decoder_int8.onnx"
        private const val TOKENIZER_FILE = "sentencepiece.model"
        private const val VOCAB_FILE = "vocab.json"
        
        // NLLB-200 language codes
        private val LANG_CODES = mapOf(
            Language.RUSSIAN to "rus_Cyrl",
            Language.CHINESE to "zho_Hans"
        )
        
        // Special tokens
        private const val PAD_TOKEN = 0
        private const val EOS_TOKEN = 2
        private const val MAX_LENGTH = 256
    }

    private var ortEnvironment: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var tokenizer: SentencePieceTokenizer? = null
    private var isInitialized = false

    /**
     * Initialize NLLB-200 translator
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext true

        try {
            Log.i(TAG, "Initializing NLLB-200 Translator...")

            // Copy models from assets
            val modelDir = copyModelsFromAssets()
            
            val encoderFile = File(modelDir, ENCODER_FILE)
            val decoderFile = File(modelDir, DECODER_FILE)
            val tokenizerFile = File(modelDir, TOKENIZER_FILE)
            
            // Check if models exist
            if (!encoderFile.exists() || !decoderFile.exists()) {
                Log.w(TAG, "NLLB models not found, using dictionary fallback")
                isInitialized = false
                return@withContext false
            }

            // Initialize ONNX Runtime
            ortEnvironment = OrtEnvironment.getEnvironment()
            
            // Configure sessions for mobile
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setInterOpNumThreads(2)
                setMemoryPatternOptimization(true)
                setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            }
            
            // Load encoder and decoder
            encoderSession = ortEnvironment?.createSession(encoderFile.absolutePath, sessionOptions)
            decoderSession = ortEnvironment?.createSession(decoderFile.absolutePath, sessionOptions)
            
            // Initialize tokenizer
            if (tokenizerFile.exists()) {
                tokenizer = SentencePieceTokenizer(tokenizerFile.absolutePath)
            }
            
            isInitialized = true
            Log.i(TAG, "NLLB-200 Translator initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NLLB Translator: ${e.message}", e)
            isInitialized = false
            false
        }
    }

    /**
     * Copy models from assets to internal storage
     */
    private fun copyModelsFromAssets(): File {
        val modelsDir = File(context.filesDir, MODEL_DIR)
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val filesToCopy = listOf(ENCODER_FILE, DECODER_FILE, TOKENIZER_FILE, VOCAB_FILE)
        
        for (fileName in filesToCopy) {
            val destFile = File(modelsDir, fileName)
            if (!destFile.exists()) {
                try {
                    context.assets.open("$MODEL_DIR/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Copied model file: $fileName")
                } catch (e: Exception) {
                    Log.w(TAG, "Model file not found: $fileName")
                }
            }
        }

        return modelsDir
    }

    /**
     * Translate text between languages
     * @param text Source text
     * @param sourceLang Source language
     * @param targetLang Target language
     * @return Translated text or null if failed
     */
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Translator not initialized, using fallback")
            return@withContext translateWithDictionary(text, sourceLang, targetLang)
        }

        if (text.isBlank()) return@withContext ""

        try {
            Log.d(TAG, "Translating: '$text' from ${sourceLang.getDisplayName()} to ${targetLang.getDisplayName()}")
            
            // Get language codes
            val srcLangCode = LANG_CODES[sourceLang] ?: "rus_Cyrl"
            val tgtLangCode = LANG_CODES[targetLang] ?: "zho_Hans"
            
            // Tokenize input
            val srcLangId = getLangTokenId(srcLangCode)
            val tgtLangId = getLangTokenId(tgtLangCode)
            
            val inputTokens = tokenizer?.encode(text) ?: tokenizeSimple(text)
            
            // Add language token at the beginning
            val encoderInput = longArrayOf(srcLangId) + inputTokens + longArrayOf(EOS_TOKEN)
            
            // Run encoder
            val encoderOutput = runEncoder(encoderInput)
            
            // Run decoder with target language token
            val outputTokens = runDecoder(encoderOutput, tgtLangId)
            
            // Decode output tokens
            val translated = tokenizer?.decode(outputTokens) ?: decodeSimple(outputTokens)
            
            Log.d(TAG, "Translated to: '$translated'")
            translated.ifEmpty { text }
        } catch (e: Exception) {
            Log.e(TAG, "Translation error: ${e.message}", e)
            translateWithDictionary(text, sourceLang, targetLang)
        }
    }

    /**
     * Run encoder model
     */
    private fun runEncoder(inputTokens: LongArray): Array<FloatArray> {
        val session = encoderSession ?: throw IllegalStateException("Encoder not initialized")
        
        val inputIds = OnnxTensor.createTensor(
            ortEnvironment,
            LongBuffer.wrap(inputTokens),
            longArrayOf(1, inputTokens.size.toLong())
        )
        
        val attentionMask = OnnxTensor.createTensor(
            ortEnvironment,
            LongBuffer.wrap(LongArray(inputTokens.size) { 1 }),
            longArrayOf(1, inputTokens.size.toLong())
        )
        
        val inputs = mapOf(
            "input_ids" to inputIds,
            "attention_mask" to attentionMask
        )
        
        val output = session.run(inputs)
        
        // Get encoder hidden states
        val hiddenStates = output[0].value as Array<FloatArray>
        
        inputIds.close()
        attentionMask.close()
        output.close()
        
        return hiddenStates
    }

    /**
     * Run decoder model for autoregressive generation
     */
    private fun runDecoder(encoderOutput: Array<FloatArray>, tgtLangId: Long): LongArray {
        val session = decoderSession ?: throw IllegalStateException("Decoder not initialized")
        
        val outputTokens = mutableListOf<Long>(tgtLangId)
        
        // Create encoder output tensor
        val encoderHiddenStates = OnnxTensor.createTensor(ortEnvironment, encoderOutput)
        
        for (i in 0 until MAX_LENGTH) {
            // Prepare decoder input
            val decoderInputIds = outputTokens.toLongArray()
            val decoderInputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                LongBuffer.wrap(decoderInputIds),
                longArrayOf(1, decoderInputIds.size.toLong())
            )
            
            val attentionMask = OnnxTensor.createTensor(
                ortEnvironment,
                LongBuffer.wrap(LongArray(outputTokens.size) { 1 }),
                longArrayOf(1, outputTokens.size.toLong())
            )
            
            val inputs = mapOf(
                "input_ids" to decoderInputTensor,
                "encoder_hidden_states" to encoderHiddenStates,
                "attention_mask" to attentionMask
            )
            
            val output = session.run(inputs)
            
            // Get next token (greedy decoding)
            val logits = (output[0].value as Array<Array<FloatArray>>)[0]
            val nextToken = logits.last().indices.maxByOrNull { logits.last()[it] }?.toLong() ?: EOS_TOKEN
            
            decoderInputTensor.close()
            attentionMask.close()
            output.close()
            
            if (nextToken == EOS_TOKEN) break
            
            outputTokens.add(nextToken)
        }
        
        encoderHiddenStates.close()
        
        return outputTokens.toLongArray()
    }

    /**
     * Get language token ID for NLLB
     */
    private fun getLangTokenId(langCode: String): Long {
        // NLLB-200 language token IDs (simplified mapping)
        return when (langCode) {
            "rus_Cyrl" -> 25695L  // Russian
            "zho_Hans" -> 25657L  // Chinese Simplified
            else -> 0L
        }
    }

    /**
     * Simple tokenization fallback
     */
    private fun tokenizeSimple(text: String): LongArray {
        // Character-level tokenization as fallback
        return text.map { it.code.toLong() }.toLongArray()
    }

    /**
     * Simple detokenization fallback
     */
    private fun decodeSimple(tokens: LongArray): String {
        return tokens.mapNotNull { 
            if (it in 32..65535) it.toInt().toChar() else null 
        }.joinToString("")
    }

    /**
     * Dictionary-based translation fallback
     */
    private fun translateWithDictionary(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        val dictionary = when (sourceLang) {
            Language.RUSSIAN -> russianToChineseDictionary
            Language.CHINESE -> chineseToRussianDictionary
        }

        val words = text.split(Regex("\\s+"))
        val translatedWords = words.map { word ->
            val cleanWord = word.trim(' ', '.', ',', '!', '?', '，', '。', '！', '？')
            val punctuation = word.substringAfter(cleanWord)
            dictionary[cleanWord.lowercase()]?.let { it + punctuation } ?: word
        }

        return translatedWords.joinToString(" ")
    }

    // Basic dictionaries for fallback
    private val russianToChineseDictionary = mapOf(
        "привет" to "你好", "здравствуйте" to "您好", "спасибо" to "谢谢",
        "да" to "是", "нет" to "不是", "как" to "怎么样", "что" to "什么",
        "где" to "哪里", "когда" to "什么时候", "кто" to "谁", "почему" to "为什么",
        "я" to "我", "ты" to "你", "вы" to "您", "он" to "他", "она" to "她",
        "мы" to "我们", "они" to "他们", "хорошо" to "好", "плохо" to "不好",
        "большой" to "大", "маленький" to "小", "новый" to "新", "старый" to "旧",
        "друг" to "朋友", "семья" to "家庭", "дом" to "家", "работа" to "工作",
        "вода" to "水", "еда" to "食物", "чай" to "茶", "кофе" to "咖啡",
        "один" to "一", "два" to "二", "три" to "三", "четыре" to "四", "пять" to "五",
        "любить" to "爱", "знать" to "知道", "говорить" to "说", "идти" to "去"
    )

    private val chineseToRussianDictionary = mapOf(
        "你好" to "привет", "您好" to "здравствуйте", "谢谢" to "спасибо",
        "是" to "да", "不是" to "нет", "怎么样" to "как", "什么" to "что",
        "哪里" to "где", "什么时候" to "когда", "谁" to "кто", "为什么" to "почему",
        "我" to "я", "你" to "ты", "您" to "вы", "他" to "он", "她" to "она",
        "我们" to "мы", "他们" to "они", "好" to "хорошо", "不好" to "плохо",
        "大" to "большой", "小" to "маленький", "新" to "новый", "旧" to "старый",
        "朋友" to "друг", "家庭" to "семья", "家" to "дом", "工作" to "работа",
        "水" to "вода", "食物" to "еда", "茶" to "чай", "咖啡" to "кофе",
        "一" to "один", "二" to "два", "三" to "три", "四" to "четыре", "五" to "пять",
        "爱" to "любить", "知道" to "знать", "说" to "говорить", "去" to "идти"
    )

    /**
     * Check if translator is ready
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Release resources
     */
    fun release() {
        try {
            encoderSession?.close()
            encoderSession = null
            
            decoderSession?.close()
            decoderSession = null
            
            // Note: OrtEnvironment is a singleton, don't close it
            
            tokenizer = null
            isInitialized = false
            Log.i(TAG, "NLLB Translator released")
        } catch (e: Exception) {
            Log.e(TAG, "Release error: ${e.message}")
        }
    }
}
