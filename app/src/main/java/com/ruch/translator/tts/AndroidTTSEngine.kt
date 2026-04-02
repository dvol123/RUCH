package com.ruch.translator.tts

import android.content.Context
import android.speech.tts.Locale
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ruch.translator.data.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Text-to-Speech using Android's built-in TTS engine
 * Works offline on most devices
 */
class AndroidTTSEngine(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AndroidTTS"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var initResult: Int = TextToSpeech.ERROR

    override fun onInit(status: Int) {
        initResult = status
        isInitialized = true
        
        if (status == TextToSpeech.SUCCESS) {
            Log.i(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            tts = TextToSpeech(context) { status ->
                initResult = status
                isInitialized = true
                if (status == TextToSpeech.SUCCESS) {
                    Log.i(TAG, "TTS initialized successfully")
                } else {
                    Log.e(TAG, "TTS initialization failed")
                }
                continuation.resume(status == TextToSpeech.SUCCESS)
            }
        }
    }

    fun isReady(language: Language): Boolean {
        val ttsInstance = tts ?: return false
        val locale = when (language) {
            Language.RUSSIAN -> java.util.Locale("ru", "RU")
            Language.CHINESE -> java.util.Locale("zh", "CN")
        }
        
        val result = ttsInstance.isLanguageAvailable(locale)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    suspend fun synthesizeToArray(text: String, language: Language): ShortArray? = withContext(Dispatchers.Main) {
        if (!isInitialized || tts == null) {
            Log.e(TAG, "TTS not initialized")
            return@withContext null
        }

        val ttsInstance = tts!!
        val locale = when (language) {
            Language.RUSSIAN -> java.util.Locale("ru", "RU")
            Language.CHINESE -> java.util.Locale("zh", "CN")
        }

        ttsInstance.language = locale

        // Use synthesizeToFile to get audio data
        val outputFile = java.io.File(context.cacheDir, "tts_temp_${System.currentTimeMillis()}.wav")
        
        suspendCancellableCoroutine<ShortArray?> { continuation ->
            ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                
                override fun onDone(utteranceId: String?) {
                    try {
                        if (outputFile.exists()) {
                            val bytes = outputFile.readBytes()
                            // Convert WAV bytes to ShortArray (skip 44-byte header)
                            val shorts = ShortArray((bytes.size - 44) / 2)
                            for (i in shorts.indices) {
                                shorts[i] = ((bytes[44 + i * 2 + 1].toInt() shl 8) or (bytes[44 + i * 2].toInt() and 0xFF)).toShort()
                            }
                            outputFile.delete()
                            continuation.resume(shorts)
                        } else {
                            continuation.resume(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading TTS output: ${e.message}")
                        continuation.resume(null)
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS synthesis error")
                    continuation.resume(null)
                }
            })

            val result = ttsInstance.synthesizeToFile(
                text,
                null,
                outputFile,
                "tts_utterance_${System.currentTimeMillis()}"
            )

            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "synthesizeToFile failed: $result")
                continuation.resume(null)
            }
        }
    }

    fun getSampleRate(): Int = 22050

    fun release() {
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
