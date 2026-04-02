package com.ruch.translator.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.ruch.translator.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Native TTS Engine using Sherpa-ONNX via JNI
 * 
 * Для работы необходимо:
 * 1. Добавить libsherpa-jni.so в jniLibs/<архитектура>/
 * 2. Скачать модели TTS для русского и китайского
 * 
 * Модели можно скачать с:
 * https://github.com/k2-fsa/sherpa-onnx/releases
 * 
 * Сборка native библиотеки:
 * Следовать инструкциям на https://github.com/k2-fsa/sherpa-onnx
 */
class TTSEngineNative(private val context: Context) {
    
    companion object {
        private const val TAG = "TTSEngineNative"
        
        init {
            try {
                System.loadLibrary("sherpa-jni")
                Log.i(TAG, "sherpa-jni library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load sherpa-jni library: ${e.message}")
            }
        }
    }
    
    // Native методы
    private external fun initTTS(modelPath: String, tokensPath: String, dataType: String): Long
    private external fun generateSpeech(ttsHandle: Long, text: String, speed: Float): FloatArray?
    private external fun stopGeneration(ttsHandle: Long)
    private external fun releaseTTS(ttsHandle: Long)
    private external fun getSampleRate(ttsHandle: Long): Int
    private external fun getNumSpeakers(ttsHandle: Long): Int
    
    private var ttsHandle: Long = 0
    private var isInitialized = false
    private var currentSampleRate: Int = 22050
    private var audioTrack: AudioTrack? = null
    
    /**
     * Инициализация TTS модели
     */
    suspend fun initialize(language: Language): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Путь к моделям
                val modelsDir = File(context.filesDir, "models/tts/${if (language == Language.RUSSIAN) "russian" else "chinese"}")
                val modelFile = File(modelsDir, "model.onnx")
                val tokensFile = File(modelsDir, "tokens.txt")
                val dataType = "int8"  // Квантизация для оптимизации размера
                
                if (!modelFile.exists() || !tokensFile.exists()) {
                    Log.e(TAG, "Model files not found in: ${modelsDir.absolutePath}")
                    return@withContext false
                }
                
                ttsHandle = initTTS(
                    modelFile.absolutePath,
                    tokensFile.absolutePath,
                    dataType
                )
                
                if (ttsHandle != 0L) {
                    isInitialized = true
                    currentSampleRate = getSampleRate(ttsHandle)
                    Log.i(TAG, "TTS initialized, sample rate: $currentSampleRate")
                    true
                } else {
                    Log.e(TAG, "Failed to init TTS: handle is 0")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TTS: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Синтез речи
     */
    suspend fun speak(text: String, speed: Float = 1.0f): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isInitialized || ttsHandle == 0L) {
                Log.e(TAG, "TTS not initialized")
                return@withContext false
            }
            
            try {
                // Генерируем аудио
                val audioData = generateSpeech(ttsHandle, text, speed)
                
                if (audioData == null || audioData.isEmpty()) {
                    Log.e(TAG, "Generated audio is empty")
                    return@withContext false
                }
                
                // Воспроизводим
                playAudio(audioData)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Speech synthesis failed: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Воспроизведение аудио через AudioTrack
     */
    private fun playAudio(audioData: FloatArray) {
        releaseAudioTrack()
        
        // Конвертируем float в 16-bit PCM
        val pcmData = ShortArray(audioData.size)
        for (i in audioData.indices) {
            val sample = (audioData[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            pcmData[i] = sample.toShort()
        }
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(currentSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcmData.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        
        audioTrack?.write(pcmData, 0, pcmData.size)
        audioTrack?.play()
        
        Log.i(TAG, "Playing ${pcmData.size} samples at ${currentSampleRate}Hz")
        
        // Ждём завершения воспроизведения
        Thread.sleep((pcmData.size.toLong() * 1000 / currentSampleRate) + 100)
    }
    
    /**
     * Остановка воспроизведения
     */
    fun stop() {
        stopGeneration(ttsHandle)
        releaseAudioTrack()
    }
    
    private fun releaseAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        releaseAudioTrack()
        if (ttsHandle != 0L) {
            releaseTTS(ttsHandle)
            ttsHandle = 0
            isInitialized = false
            Log.i(TAG, "TTS resources released")
        }
    }
}
