package com.ruch.translator.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Запись аудио с микрофона для STT
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000 // Whisper требует 16kHz
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val MAX_DURATION_SECONDS = 30 // Максимум 30 секунд
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    sealed class RecordingState {
        object Idle : RecordingState()
        object Recording : RecordingState()
        data class Error(val message: String) : RecordingState()
    }

    /**
     * Проверка разрешений на запись аудио
     */
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Запись аудио и возврат данных
     * @param maxDurationSeconds Максимальная длительность в секундах
     * @return FloatArray аудиоданных нормализованных для Whisper (-1.0 to 1.0)
     */
    suspend fun recordAudio(maxDurationSeconds: Int = MAX_DURATION_SECONDS): FloatArray? = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            _recordingState.value = RecordingState.Error("Нет разрешения на запись аудио")
            return@withContext null
        }

        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return@withContext null
        }

        // Limit max duration to prevent OOM
        val actualMaxDuration = maxDurationSeconds.coerceAtMost(MAX_DURATION_SECONDS)
        val maxSamples = SAMPLE_RATE * actualMaxDuration
        
        Log.d(TAG, "Starting recording, max duration: ${actualMaxDuration}s, max samples: $maxSamples")

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, AUDIO_FORMAT)
            .coerceAtLeast(SAMPLE_RATE / 2) // Минимум 0.5 секунды буфера

        // Pre-allocate array for audio data
        val audioBuffer = ShortArray(maxSamples)
        var totalSamples = 0
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                AUDIO_FORMAT,
                bufferSize * 2 // Double buffer for safety
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized, state: ${audioRecord?.state}")
                _recordingState.value = RecordingState.Error("Не удалось инициализировать аудио запись")
                return@withContext null
            }

            isRecording.set(true)
            _recordingState.value = RecordingState.Recording
            Log.d(TAG, "Recording started, buffer size: $bufferSize")

            val tempBuffer = ShortArray(bufferSize / 2)
            
            audioRecord?.startRecording()
            
            var errorCount = 0
            val maxErrors = 5

            while (isRecording.get() && totalSamples < maxSamples && isActive) {
                val readResult = audioRecord?.read(tempBuffer, 0, tempBuffer.size)
                
                when {
                    readResult == null -> {
                        Log.e(TAG, "AudioRecord.read returned null")
                        errorCount++
                    }
                    readResult == AudioRecord.ERROR_INVALID_OPERATION -> {
                        Log.e(TAG, "AudioRecord ERROR_INVALID_OPERATION")
                        errorCount++
                    }
                    readResult == AudioRecord.ERROR_BAD_VALUE -> {
                        Log.e(TAG, "AudioRecord ERROR_BAD_VALUE")
                        errorCount++
                    }
                    readResult == AudioRecord.ERROR_DEAD_OBJECT -> {
                        Log.e(TAG, "AudioRecord ERROR_DEAD_OBJECT - recorder died")
                        break
                    }
                    readResult < 0 -> {
                        Log.e(TAG, "AudioRecord error: $readResult")
                        errorCount++
                    }
                    readResult > 0 -> {
                        errorCount = 0 // Reset error count on successful read
                        
                        // Copy to main buffer
                        val samplesToCopy = minOf(readResult, maxSamples - totalSamples)
                        if (samplesToCopy > 0) {
                            System.arraycopy(tempBuffer, 0, audioBuffer, totalSamples, samplesToCopy)
                            totalSamples += samplesToCopy
                            
                            // Log progress every 3 seconds
                            if (totalSamples % (SAMPLE_RATE * 3) < tempBuffer.size) {
                                Log.d(TAG, "Recorded: ${totalSamples / SAMPLE_RATE}s / ${actualMaxDuration}s")
                            }
                        }
                    }
                }
                
                // Stop if too many errors
                if (errorCount >= maxErrors) {
                    Log.e(TAG, "Too many errors, stopping recording")
                    break
                }
            }

            Log.d(TAG, "Recording loop ended, samples: $totalSamples")
            
            // Stop recording safely
            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord?.stop()
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Error stopping recorder: ${e.message}")
            }

            if (totalSamples == 0) {
                Log.w(TAG, "No audio data recorded")
                _recordingState.value = RecordingState.Idle
                return@withContext null
            }
            
            // Convert to FloatArray
            Log.d(TAG, "Converting $totalSamples samples to FloatArray")
            val floatAudio = FloatArray(totalSamples) { i ->
                audioBuffer[i].toFloat() / Short.MAX_VALUE.toFloat()
            }

            _recordingState.value = RecordingState.Idle
            Log.d(TAG, "Recording complete: ${totalSamples / SAMPLE_RATE.toFloat()}s")
            floatAudio
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError during recording", e)
            _recordingState.value = RecordingState.Error("Недостаточно памяти")
            // Return what we have
            if (totalSamples > 0) {
                Log.d(TAG, "Returning partial audio: $totalSamples samples")
                try {
                    FloatArray(totalSamples) { i ->
                        audioBuffer[i].toFloat() / Short.MAX_VALUE.toFloat()
                    }
                } catch (e2: OutOfMemoryError) {
                    Log.e(TAG, "Cannot even convert partial audio", e2)
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording error: ${e.message}", e)
            _recordingState.value = RecordingState.Error(e.message ?: "Ошибка записи")
            // Return what we have
            if (totalSamples > 0) {
                Log.d(TAG, "Returning partial audio after error: $totalSamples samples")
                try {
                    FloatArray(totalSamples) { i ->
                        audioBuffer[i].toFloat() / Short.MAX_VALUE.toFloat()
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Error converting partial audio", e2)
                    null
                }
            } else {
                null
            }
        } finally {
            isRecording.set(false)
            releaseRecorder()
        }
    }

    /**
     * Остановка записи
     */
    fun stopRecording() {
        Log.d(TAG, "stopRecording called, isRecording=${isRecording.get()}")
        isRecording.set(false)
        _recordingState.value = RecordingState.Idle
    }

    /**
     * Проверка, идет ли запись
     */
    fun isCurrentlyRecording(): Boolean = isRecording.get()

    /**
     * Освобождение ресурсов
     */
    fun release() {
        stopRecording()
        releaseRecorder()
    }

    private fun releaseRecorder() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Release error: ${e.message}")
        }
        audioRecord = null
    }
}
