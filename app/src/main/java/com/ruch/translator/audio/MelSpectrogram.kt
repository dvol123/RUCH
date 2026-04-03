package com.ruch.translator.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Вычисление Mel Spectrogram для Whisper
 * 
 * Преобразует аудио сигнал в mel-спектрограмму, которую понимает Whisper.
 * Реализация основана на librosa/whisper preprocessing.
 * 
 * Параметры соответствуют Whisper:
 * - sampleRate: 16000 Hz
 * - nMels: 80 (количество mel-фильтров)
 * - nFft: 400 (размер FFT окна = 25мс при 16kHz)
 * - hopLength: 160 (шаг между окнами = 10мс при 16kHz)
 */
class MelSpectrogram(
    private val sampleRate: Int = 16000,
    private val nMels: Int = 80,
    private val nFft: Int = 400,
    private val hopLength: Int = 160,
    private val fMin: Float = 0f,
    private val fMax: Float = 8000f
) {
    companion object {
        private const val TAG = "MelSpectrogram"
        
        // Параметры Whisper из официальной реализации
        const val WHISPER_SAMPLE_RATE = 16000
        const val WHISPER_N_FFT = 400
        const val WHISPER_HOP_LENGTH = 160
        const val WHISPER_N_MELS = 80
        const val WHISPER_N_AUDIO_CTX = 3000  // 30 секунд аудио
    }
    
    // Hann window для сглаживания
    private val hannWindow: FloatArray = FloatArray(nFft) { i ->
        0.5f * (1f - cos(2f * PI.toFloat() * i / (nFft - 1)))
    }
    
    // Mel filter bank (создаётся один раз)
    private val melFilterBank: Array<FloatArray> = createMelFilterBank()
    
    /**
     * Вычислить mel-спектрограмму из аудио сигнала
     * 
     * @param audio Входной аудио сигнал (нормализованный [-1, 1])
     * @param nTargetFrames Желаемое количество фреймов (default: 3000 для Whisper)
     * @return Mel-спектрограмма [nMels x nFrames]
     */
    fun compute(audio: FloatArray, nTargetFrames: Int = WHISPER_N_AUDIO_CTX): Array<FloatArray> {
        // Шаг 1: Паддинг аудио до нужной длины
        val paddedAudio = padAudio(audio, nTargetFrames)
        
        // Шаг 2: Вычисление STFT (Short-Time Fourier Transform)
        val stft = computeSTFT(paddedAudio)
        
        // Шаг 3: Применение mel-фильтров
        val melSpec = applyMelFilters(stft)
        
        // Шаг 4: Логарифмическое масштабирование
        val logMelSpec = toLogScale(melSpec)
        
        return logMelSpec
    }
    
    /**
     * Паддинг аудио до фиксированной длины (30 секунд = 480000 сэмплов)
     * Whisper ожидает фиксированный размер входа
     */
    private fun padAudio(audio: FloatArray, nTargetFrames: Int): FloatArray {
        // Целевая длина аудио: nTargetFrames * hopLength + nFft - hopLength
        // Для 3000 фреймов: 3000 * 160 + 400 - 160 = 480240
        val targetLength = nTargetFrames * hopLength + nFft - hopLength
        val targetLengthCapped = min(targetLength, WHISPER_SAMPLE_RATE * 30) // максимум 30 сек
        
        val padded = FloatArray(targetLengthCapped)
        
        // Копируем аудио (с обрезкой или дополнением нулями)
        val copyLength = min(audio.size, targetLengthCapped)
        System.arraycopy(audio, 0, padded, 0, copyLength)
        
        return padded
    }
    
    /**
     * Short-Time Fourier Transform
     * Разбивает аудио на перекрывающиеся окна и вычисляет FFT для каждого
     */
    private fun computeSTFT(audio: FloatArray): Array<FloatArray> {
        // Количество фреймов
        val nFrames = max(1, (audio.size - nFft) / hopLength + 1)
        
        // Результат: [nFrames x nFft/2+1] - только положительные частоты
        val nFreqs = nFft / 2 + 1
        val stft = Array(nFrames) { FloatArray(nFreqs) }
        
        // Буфер для FFT
        val frame = FloatArray(nFft)
        
        for (frameIdx in 0 until nFrames) {
            // Извлекаем фрейм с применением Hann window
            val start = frameIdx * hopLength
            for (i in 0 until nFft) {
                val sampleIdx = start + i
                frame[i] = if (sampleIdx < audio.size) {
                    audio[sampleIdx] * hannWindow[i]
                } else {
                    0f
                }
            }
            
            // Вычисляем FFT (используем упрощённую реализацию)
            val spectrum = fftMagnitude(frame)
            
            // Копируем результат (только положительные частоты)
            for (freqIdx in 0 until nFreqs) {
                stft[frameIdx][freqIdx] = spectrum[freqIdx]
            }
        }
        
        return stft
    }
    
    /**
     * FFT с использованием алгоритма Cooley-Tukey
     * Возвращает магнитуду спектра
     */
    private fun fftMagnitude(input: FloatArray): FloatArray {
        val n = input.size
        
        // Для степеней двойки используем быстрый алгоритм
        if (isPowerOfTwo(n)) {
            return fftMagnitudeRadix2(input)
        }
        
        // Для других размеров - DFT (медленно, но работает)
        return dftMagnitude(input)
    }
    
    /**
     * Проверка, является ли число степенью двойки
     */
    private fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && (n and (n - 1)) == 0
    }
    
    /**
     * FFT для степеней двойки (алгоритм Cooley-Tukey)
     */
    private fun fftMagnitudeRadix2(input: FloatArray): FloatArray {
        val n = input.size
        
        // Массивы для real и imaginary частей
        val real = input.copyOf()
        val imag = FloatArray(n)
        
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                // Swap real
                val tempReal = real[i]
                real[i] = real[j]
                real[j] = tempReal
                // Swap imag
                val tempImag = imag[i]
                imag[i] = imag[j]
                imag[j] = tempImag
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }
        
        // Cooley-Tukey FFT
        var length = 2
        while (length <= n) {
            val halfLength = length shr 1
            val angleStep = -2f * PI.toFloat() / length
            
            for (i in 0 until n step length) {
                for (k in 0 until halfLength) {
                    val angle = angleStep * k
                    val wReal = cos(angle)
                    val wImag = kotlin.math.sin(angle)
                    
                    val idx1 = i + k
                    val idx2 = i + k + halfLength
                    
                    val tReal = wReal * real[idx2] - wImag * imag[idx2]
                    val tImag = wReal * imag[idx2] + wImag * real[idx2]
                    
                    real[idx2] = real[idx1] - tReal
                    imag[idx2] = imag[idx1] - tImag
                    real[idx1] = real[idx1] + tReal
                    imag[idx1] = imag[idx1] + tImag
                }
            }
            length = length shl 1
        }
        
        // Вычисляем магнитуду (только положительные частоты)
        val nFreqs = n / 2 + 1
        val magnitude = FloatArray(nFreqs)
        for (i in 0 until nFreqs) {
            magnitude[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }
        
        return magnitude
    }
    
    /**
     * DFT (Discrete Fourier Transform) - медленный но надёжный метод
     * Используется для размеров, не являющихся степенью двойки
     */
    private fun dftMagnitude(input: FloatArray): FloatArray {
        val n = input.size
        val nFreqs = n / 2 + 1
        val magnitude = FloatArray(nFreqs)
        
        for (k in 0 until nFreqs) {
            var sumReal = 0.0
            var sumImag = 0.0
            
            for (t in 0 until n) {
                val angle = -2.0 * PI * k * t / n
                sumReal += input[t] * cos(angle)
                sumImag += input[t] * kotlin.math.sin(angle)
            }
            
            magnitude[k] = sqrt((sumReal * sumReal + sumImag * sumImag).toFloat())
        }
        
        return magnitude
    }
    
    /**
     * Создание mel filter bank
     * Преобразует линейную шкалу частот в mel-шкалу
     */
    private fun createMelFilterBank(): Array<FloatArray> {
        val nFreqs = nFft / 2 + 1
        
        // Конвертируем частоты в mel
        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        
        // Равноотстоящие точки в mel-пространстве
        val melPoints = FloatArray(nMels + 2)
        for (i in melPoints.indices) {
            melPoints[i] = melMin + (melMax - melMin) * i / (nMels + 1)
        }
        
        // Конвертируем обратно в Hz
        val hzPoints = FloatArray(melPoints.size) { i -> melToHz(melPoints[i]) }
        
        // Конвертируем в индексы FFT бинов
        val binPoints = IntArray(hzPoints.size) { i ->
            kotlin.math.floor((nFft + 1) * hzPoints[i] / sampleRate).toInt()
        }
        
        // Создаём фильтры
        val filterBank = Array(nMels) { FloatArray(nFreqs) }
        
        for (m in 0 until nMels) {
            val left = binPoints[m]
            val center = binPoints[m + 1]
            val right = binPoints[m + 2]
            
            // Левая часть фильтра (возрастающая)
            for (k in left until center) {
                if (k >= 0 && k < nFreqs && center != left) {
                    filterBank[m][k] = (k - left).toFloat() / (center - left)
                }
            }
            
            // Правая часть фильтра (убывающая)
            for (k in center until right) {
                if (k >= 0 && k < nFreqs && right != center) {
                    filterBank[m][k] = (right - k).toFloat() / (right - center)
                }
            }
        }
        
        // Нормализация (slaney style)
        for (m in 0 until nMels) {
            var enorm = 0f
            for (k in 0 until nFreqs) {
                enorm += filterBank[m][k]
            }
            if (enorm > 0) {
                for (k in 0 until nFreqs) {
                    filterBank[m][k] *= 2.0f / enorm
                }
            }
        }
        
        return filterBank
    }
    
    /**
     * Применить mel-фильтры к STFT
     */
    private fun applyMelFilters(stft: Array<FloatArray>): Array<FloatArray> {
        val nFrames = stft.size
        val nFreqs = stft[0].size
        
        // Результат: [nMels x nFrames]
        val melSpec = Array(nMels) { FloatArray(nFrames) }
        
        for (frameIdx in 0 until nFrames) {
            for (melIdx in 0 until nMels) {
                var sum = 0f
                for (freqIdx in 0 until min(nFreqs, melFilterBank[melIdx].size)) {
                    sum += stft[frameIdx][freqIdx] * melFilterBank[melIdx][freqIdx]
                }
                melSpec[melIdx][frameIdx] = sum
            }
        }
        
        return melSpec
    }
    
    /**
     * Преобразование в логарифмическую шкалу
     * Whisper использует log-mel spectrogram
     */
    private fun toLogScale(melSpec: Array<FloatArray>): Array<FloatArray> {
        val nMels = melSpec.size
        val nFrames = melSpec[0].size
        
        val logMelSpec = Array(nMels) { FloatArray(nFrames) }
        
        // Whisper использует power spectrogram, потом log
        // Добавляем маленькое значение для избежания log(0)
        val minVal = 1e-10f
        
        for (i in 0 until nMels) {
            for (j in 0 until nFrames) {
                // Power (квадрат магнитуды)
                val power = melSpec[i][j] * melSpec[i][j]
                // Log10 и масштабирование как в Whisper
                logMelSpec[i][j] = max(minVal, power).let { 
                    log10(it) * 10f  // dB scale
                }
            }
        }
        
        return logMelSpec
    }
    
    /**
     * Конвертация Hz в Mel
     * Формула: mel = 2595 * log10(1 + hz / 700)
     */
    private fun hzToMel(hz: Float): Float {
        return 2595f * log10(1f + hz / 700f)
    }
    
    /**
     * Конвертация Mel в Hz
     * Формула: hz = 700 * (10^(mel/2595) - 1)
     */
    private fun melToHz(mel: Float): Float {
        return 700f * (10f.pow(mel / 2595f) - 1f)
    }
}
