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
    suspend fun recordAudio(maxDurationSeconds: Int = 30): FloatArray? = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) {
            _recordingState.value = RecordingState.Error("Нет разрешения на запись аудио")
            return@withContext null
        }

        if (isRecording.get()) {
            Log.w(TAG, "Already recording")
            return@withContext null
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, AUDIO_FORMAT)
            .coerceAtLeast(SAMPLE_RATE) // Минимум 1 секунда буфера

        var audioBuffer = mutableListOf<Short>()
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _recordingState.value = RecordingState.Error("Не удалось инициализировать аудио запись")
                return@withContext null
            }

            isRecording.set(true)
            _recordingState.value = RecordingState.Recording
            Log.d(TAG, "Recording started")

            val maxSamples = SAMPLE_RATE * maxDurationSeconds
            val tempBuffer = ShortArray(bufferSize / 2)

            audioRecord?.startRecording()

            while (isRecording.get() && audioBuffer.size < maxSamples) {
                val read = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: 0
                if (read > 0) {
                    audioBuffer.addAll(tempBuffer.take(read).toList())
                }
                if (!isActive) break
            }

            Log.d(TAG, "Recording finished, samples: ${audioBuffer.size}")
            
            // Always stop recording if still recording
            try {
                audioRecord?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recorder: ${e.message}")
            }

            // Конвертируем ShortArray в FloatArray для Whisper
            if (audioBuffer.isEmpty()) {
                Log.w(TAG, "No audio data recorded")
                _recordingState.value = RecordingState.Idle
                return@withContext null
            }
            
            val floatAudio = FloatArray(audioBuffer.size) { i ->
                audioBuffer[i].toFloat() / Short.MAX_VALUE.toFloat()
            }

            _recordingState.value = RecordingState.Idle
            floatAudio
        } catch (e: Exception) {
            Log.e(TAG, "Recording error: ${e.message}", e)
            _recordingState.value = RecordingState.Error(e.message ?: "Ошибка записи")
            // Return what we have so far if there's any data
            if (audioBuffer.isNotEmpty()) {
                Log.d(TAG, "Returning partial audio: ${audioBuffer.size} samples")
                FloatArray(audioBuffer.size) { i ->
                    audioBuffer[i].toFloat() / Short.MAX_VALUE.toFloat()
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
        // Don't release here - let recordAudio finish and release in finally block
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
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Release error: ${e.message}")
        }
    }
}
