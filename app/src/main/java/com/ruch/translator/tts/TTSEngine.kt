package com.ruch.translator.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.ruch.translator.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Text-to-Speech Engine using Android's built-in TTS
 * Provides offline-capable speech synthesis for Russian and Chinese
 */
class TTSEngine(private val context: Context) {
    
    companion object {
        private const val SAMPLE_RATE = 22050
    }
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false
    
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.Main) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        continuation.resume(true) {}
                    } else {
                        isInitialized = false
                        continuation.resume(false) {}
                    }
                }
                
                continuation.invokeOnCancellation {
                    tts?.shutdown()
                }
            }
        }
    }
    
    suspend fun speak(text: String, language: Language) {
        withContext(Dispatchers.Main) {
            if (!isInitialized || tts == null) {
                // Try to initialize
                initialize()
            }
            
            val locale = if (language == Language.RUSSIAN) {
                Locale("ru", "RU")
            } else {
                Locale("zh", "CN")
            }
            
            val result = tts?.setLanguage(locale)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to default locale
                tts?.setLanguage(Locale.getDefault())
            }
            
            isSpeaking = true
            
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
            
            // Wait for speech to complete
            kotlinx.coroutines.delay(500)
            while (tts?.isSpeaking == true) {
                kotlinx.coroutines.delay(100)
            }
            
            isSpeaking = false
        }
    }
    
    fun stop() {
        tts?.stop()
        isSpeaking = false
    }
    
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
