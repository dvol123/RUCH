package com.ruch.translator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Аудио плеер для воспроизведения синтезированной речи
 */
class AudioPlayer {

    companion object {
        private const val TAG = "AudioPlayer"
        private const val SAMPLE_RATE = 22050
    }

    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)

    /**
     * Воспроизведение FloatArray аудиоданных (нормализованные [-1, 1])
     * Sherpa-ONNX TTS возвращает FloatArray
     */
    fun play(audioData: FloatArray, sampleRate: Int = SAMPLE_RATE) {
        if (audioData.isEmpty()) {
            Log.w(TAG, "Empty audio data")
            return
        }

        // Конвертируем FloatArray в ShortArray
        val shortData = floatToShort(audioData)
        play(shortData, sampleRate)
    }

    /**
     * Воспроизведение ShortArray аудиоданных (PCM 16-bit)
     */
    fun play(audioData: ShortArray, sampleRate: Int = SAMPLE_RATE) {
        if (audioData.isEmpty()) {
            Log.w(TAG, "Empty audio data")
            return
        }

        stop()

        try {
            isStopped.set(false)
            isPlaying.set(true)

            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(audioData.size * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.apply {
                play()

                var offset = 0
                val chunkSize = bufferSize / 2

                while (offset < audioData.size && !isStopped.get()) {
                    val remaining = audioData.size - offset
                    val toWrite = minOf(chunkSize, remaining)
                    write(audioData, offset, toWrite)
                    offset += toWrite
                }

                while (playState == AudioTrack.PLAYSTATE_PLAYING && !isStopped.get()) {
                    Thread.sleep(50)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playback error: ${e.message}", e)
        } finally {
            isPlaying.set(false)
            releaseTrack()
        }
    }

    /**
     * Конвертация FloatArray [-1, 1] в ShortArray [Short.MIN_VALUE, Short.MAX_VALUE]
     */
    private fun floatToShort(floatData: FloatArray): ShortArray {
        val shortData = ShortArray(floatData.size)
        for (i in floatData.indices) {
            // Clip to [-1, 1]
            val clamped = floatData[i].coerceIn(-1f, 1f)
            // Convert to 16-bit signed integer
            shortData[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return shortData
    }

    /**
     * Асинхронное воспроизведение FloatArray
     */
    fun playAsync(audioData: FloatArray, sampleRate: Int = SAMPLE_RATE, onComplete: (() -> Unit)? = null) {
        Thread {
            play(audioData, sampleRate)
            onComplete?.invoke()
        }.start()
    }

    /**
     * Асинхронное воспроизведение ShortArray
     */
    fun playAsync(audioData: ShortArray, sampleRate: Int = SAMPLE_RATE, onComplete: (() -> Unit)? = null) {
        Thread {
            play(audioData, sampleRate)
            onComplete?.invoke()
        }.start()
    }

    /**
     * Остановка воспроизведения
     */
    fun stop() {
        isStopped.set(true)
        audioTrack?.apply {
            try {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stop error: ${e.message}")
            }
        }
    }

    /**
     * Проверка, воспроизводится ли аудио
     */
    fun isCurrentlyPlaying(): Boolean = isPlaying.get() && !isStopped.get()

    /**
     * Освобождение ресурсов
     */
    fun release() {
        stop()
        releaseTrack()
    }

    private fun releaseTrack() {
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Release error: ${e.message}")
        }
    }
}
