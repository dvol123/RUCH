package com.ruch.translator.stt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.ruch.translator.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Speech Recognizer using Whisper model
 * Implements offline speech recognition for Russian and Chinese
 */
class SpeechRecognizer(private val context: Context) {
    
    companion object {
        init {
            System.loadLibrary("whisper-jni")
        }
        
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_RECORDING_TIME_MS = 30000
        private const val SILENCE_THRESHOLD = 500f
        private const val SILENCE_DURATION_MS = 1500
    }
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingBuffer = mutableListOf<Short>()
    
    // Native methods
    private external fun initWhisper(modelPath: String): Boolean
    private external fun transcribe(audioData: FloatArray, language: String): String
    private external fun releaseWhisper()
    
    suspend fun initialize(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelPath = getModelPath()
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    return@withContext false
                }
                initWhisper(modelPath)
            } catch (e: Exception) {
                false
            }
        }
    }
    
    suspend fun recordAndRecognize(language: Language): String {
        return withContext(Dispatchers.IO) {
            try {
                val audioData = recordAudio()
                if (audioData.isEmpty()) {
                    return@withContext ""
                }
                
                val langCode = if (language == Language.RUSSIAN) "ru" else "zh"
                transcribe(audioData, langCode)
            } catch (e: Exception) {
                ""
            }
        }
    }
    
    private fun recordAudio(): FloatArray {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 4
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            return floatArrayOf()
        }
        
        recordingBuffer.clear()
        isRecording = true
        audioRecord?.startRecording()
        
        val buffer = ShortArray(bufferSize / 2)
        var silenceStartTime = 0L
        var hasSpeech = false
        
        val startTime = System.currentTimeMillis()
        
        while (isRecording) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) {
                for (i in 0 until read) {
                    recordingBuffer.add(buffer[i])
                }
                
                // Detect silence
                val rms = calculateRMS(buffer, read)
                if (rms < SILENCE_THRESHOLD) {
                    if (hasSpeech && silenceStartTime == 0L) {
                        silenceStartTime = System.currentTimeMillis()
                    } else if (hasSpeech && System.currentTimeMillis() - silenceStartTime > SILENCE_DURATION_MS) {
                        break
                    }
                } else {
                    hasSpeech = true
                    silenceStartTime = 0L
                }
                
                // Max recording time
                if (System.currentTimeMillis() - startTime > MAX_RECORDING_TIME_MS) {
                    break
                }
            }
        }
        
        stopRecordingInternal()
        
        // Convert to float array normalized to [-1, 1]
        return recordingBuffer.map { it.toFloat() / Short.MAX_VALUE }.toFloatArray()
    }
    
    private fun calculateRMS(buffer: ShortArray, size: Int): Float {
        var sum = 0.0
        for (i in 0 until size) {
            sum += buffer[i] * buffer[i]
        }
        return Math.sqrt(sum / size).toFloat()
    }
    
    fun stopRecording() {
        isRecording = false
    }
    
    private fun stopRecordingInternal() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            // Ignore
        }
        isRecording = false
    }
    
    private fun getModelPath(): String {
        return File(context.filesDir, "models/whisper-small.bin").absolutePath
    }
    
    fun release() {
        stopRecordingInternal()
        try {
            releaseWhisper()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
