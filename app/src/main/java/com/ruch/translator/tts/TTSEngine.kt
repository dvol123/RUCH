package com.ruch.translator.tts

import android.content.Context
import com.ruch.translator.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Text-to-Speech Engine using SherpaTTS
 * Implements offline speech synthesis for Russian and Chinese
 */
class TTSEngine(private val context: Context) {
    
    companion object {
        init {
            System.loadLibrary("sherpa-jni")
        }
        
        private const val SAMPLE_RATE = 22050
    }
    
    private var isInitialized = false
    private var russianTts: Long = 0
    private var chineseTts: Long = 0
    
    // Native methods
    private external fun initTTS(modelPath: String, tokensPath: String, dataType: String): Long
    private external fun generateSpeech(ttsHandle: Long, text: String): FloatArray?
    private external fun releaseTTS(ttsHandle: Long)
    
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelsDir = File(context.filesDir, "models/tts")
                
                // Initialize Russian TTS
                val russianModelDir = File(modelsDir, "russian")
                if (russianModelDir.exists()) {
                    russianTts = initTTS(
                        File(russianModelDir, "model.onnx").absolutePath,
                        File(russianModelDir, "tokens.txt").absolutePath,
                        "int8"
                    )
                }
                
                // Initialize Chinese TTS
                val chineseModelDir = File(modelsDir, "chinese")
                if (chineseModelDir.exists()) {
                    chineseTts = initTTS(
                        File(chineseModelDir, "model.onnx").absolutePath,
                        File(chineseModelDir, "tokens.txt").absolutePath,
                        "int8"
                    )
                }
                
                isInitialized = russianTts != 0L || chineseTts != 0L
                isInitialized
            } catch (e: Exception) {
                false
            }
        }
    }
    
    suspend fun speak(text: String, language: Language) {
        withContext(Dispatchers.IO) {
            try {
                val ttsHandle = if (language == Language.RUSSIAN) russianTts else chineseTts
                
                if (ttsHandle == 0L) {
                    // Fallback to Android TTS
                    speakWithAndroidTTS(text, language)
                    return@withContext
                }
                
                val audioData = generateSpeech(ttsHandle, text)
                
                if (audioData != null && audioData.isNotEmpty()) {
                    playAudio(audioData)
                }
            } catch (e: Exception) {
                // Fallback to Android TTS on error
                speakWithAndroidTTS(text, language)
            }
        }
    }
    
    private fun playAudio(audioData: FloatArray) {
        // Convert float samples to 16-bit PCM
        val pcmData = ShortArray(audioData.size)
        for (i in audioData.indices) {
            val sample = (audioData[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcmData[i] = sample.toShort()
        }
        
        // Play using AudioTrack
        val audioTrack = android.media.AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                android.media.AudioFormat.Builder()
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcmData.size * 2)
            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
            .build()
        
        audioTrack.write(pcmData, 0, pcmData.size)
        audioTrack.play()
        
        // Wait for playback to complete
        Thread.sleep((pcmData.size / SAMPLE_RATE.toFloat() * 1000).toLong())
        
        audioTrack.release()
    }
    
    private fun speakWithAndroidTTS(text: String, language: Language) {
        // Fallback to Android's built-in TTS
        val tts = android.speech.tts.TextToSpeech(context, null)
        
        val locale = if (language == Language.RUSSIAN) {
            java.util.Locale("ru", "RU")
        } else {
            java.util.Locale("zh", "CN")
        }
        
        tts.language = locale
        tts.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
        
        // Wait for speech to complete
        while (tts.isSpeaking) {
            Thread.sleep(100)
        }
        
        tts.shutdown()
    }
    
    fun stop() {
        // Stop any ongoing speech
    }
    
    fun release() {
        try {
            if (russianTts != 0L) {
                releaseTTS(russianTts)
                russianTts = 0L
            }
            if (chineseTts != 0L) {
                releaseTTS(chineseTts)
                chineseTts = 0L
            }
            isInitialized = false
        } catch (e: Exception) {
            // Ignore
        }
    }
}
