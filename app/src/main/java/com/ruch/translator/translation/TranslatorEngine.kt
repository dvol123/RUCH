package com.ruch.translator.translation

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.ruch.translator.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Translation Engine using NLLB-200 model via ONNX Runtime
 * Implements offline translation between Russian and Chinese
 */
class TranslatorEngine(private val context: Context) {
    
    companion object {
        // Language codes for NLLB model
        private const val RUSSIAN_CODE = "rus_Cyrl"
        private const val CHINESE_CODE = "zho_Hans"
        
        // Special tokens
        private const val PAD_TOKEN = 0
        private const val EOS_TOKEN = 2
        private const val MAX_SOURCE_LENGTH = 128
        private const val MAX_TARGET_LENGTH = 128
    }
    
    private var ortEnvironment: OrtEnvironment? = null
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var tokenizer: SentencePieceTokenizer? = null
    private var isInitialized = false
    
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelDir = File(context.filesDir, "models/nllb")
                if (!modelDir.exists()) {
                    return@withContext false
                }
                
                ortEnvironment = OrtEnvironment.getEnvironment()
                
                // Load encoder
                val encoderPath = File(modelDir, "encoder.onnx").absolutePath
                encoderSession = ortEnvironment?.createSession(encoderPath)
                
                // Load decoder
                val decoderPath = File(modelDir, "decoder.onnx").absolutePath
                decoderSession = ortEnvironment?.createSession(decoderPath)
                
                // Load tokenizer
                val tokenizerPath = File(modelDir, "tokenizer.model").absolutePath
                tokenizer = SentencePieceTokenizer(tokenizerPath)
                
                isInitialized = encoderSession != null && decoderSession != null && tokenizer != null
                isInitialized
            } catch (e: Exception) {
                false
            }
        }
    }
    
    suspend fun translate(text: String, sourceLang: Language, targetLang: Language): String {
        return withContext(Dispatchers.IO) {
            if (!isInitialized || text.isEmpty()) {
                return@withContext text
            }
            
            try {
                // Tokenize input
                val sourceCode = if (sourceLang == Language.RUSSIAN) RUSSIAN_CODE else CHINESE_CODE
                val targetCode = if (targetLang == Language.RUSSIAN) RUSSIAN_CODE else CHINESE_CODE
                
                val inputTokens = tokenizer?.encode(text, sourceCode) ?: intArrayOf()
                if (inputTokens.isEmpty()) {
                    return@withContext ""
                }
                
                // Pad/truncate to max length
                val paddedInput = padOrTruncate(inputTokens, MAX_SOURCE_LENGTH)
                
                // Encode
                val encodedOutput = encode(paddedInput)
                
                // Decode with target language
                val translated = decode(encodedOutput, targetCode)
                
                // Detokenize
                tokenizer?.decode(translated) ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }
    
    private fun encode(inputTokens: IntArray): Array<FloatArray> {
        val env = ortEnvironment ?: return arrayOf()
        val session = encoderSession ?: return arrayOf()
        
        val inputTensor = OnnxTensor.createTensor(env, longArrayOf(inputTokens.size.toLong()))
        val inputIds = OnnxTensor.createTensor(env, longArrayOf(*inputTokens.map { it.toLong() }.toLongArray()))
        
        val inputs = mapOf(
            "input_ids" to inputIds,
            "attention_mask" to inputTensor
        )
        
        val output = session.run(inputs)
        val encoderOutput = output[0].value as Array<FloatArray>
        output.close()
        
        return encoderOutput
    }
    
    private fun decode(encoderOutput: Array<FloatArray>, targetLangCode: String): IntArray {
        val env = ortEnvironment ?: return intArrayOf()
        val session = decoderSession ?: return intArrayOf()
        
        val result = mutableListOf<Int>()
        var decoderInput = intArrayOf(getLangTokenId(targetLangCode))
        
        for (i in 0 until MAX_TARGET_LENGTH) {
            val decoderInputTensor = OnnxTensor.createTensor(
                env,
                longArrayOf(*decoderInput.map { it.toLong() }.toLongArray())
            )
            
            val encoderOutputTensor = OnnxTensor.createTensor(env, encoderOutput)
            
            val inputs = mapOf(
                "decoder_input_ids" to decoderInputTensor,
                "encoder_hidden_states" to encoderOutputTensor
            )
            
            val output = session.run(inputs)
            val logits = output[0].value as Array<Array<Float>>
            output.close()
            
            // Get next token (greedy decoding)
            val nextToken = logits[logits.size - 1].indices.maxByOrNull { 
                logits[logits.size - 1][it] 
            } ?: EOS_TOKEN
            
            if (nextToken == EOS_TOKEN) break
            
            result.add(nextToken)
            decoderInput = decoderInput + nextToken
        }
        
        return result.toIntArray()
    }
    
    private fun padOrTruncate(tokens: IntArray, maxLength: Int): IntArray {
        return if (tokens.size >= maxLength) {
            tokens.copyOf(maxLength)
        } else {
            tokens + IntArray(maxLength - tokens.size)
        }
    }
    
    private fun getLangTokenId(langCode: String): Int {
        // Language token IDs for NLLB
        return when (langCode) {
            RUSSIAN_CODE -> 25678
            CHINESE_CODE -> 25626
            else -> 0
        }
    }
    
    fun release() {
        try {
            encoderSession?.close()
            decoderSession?.close()
            tokenizer?.close()
            isInitialized = false
        } catch (e: Exception) {
            // Ignore
        }
    }
}
