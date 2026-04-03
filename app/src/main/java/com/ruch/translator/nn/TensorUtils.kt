package com.ruch.translator.nn

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.IntBuffer

/**
 * Утилиты для работы с ONNX тензорами
 */
object TensorUtils {

    /**
     * Создать Float тензор из массива
     */
    fun createFloatTensor(
        env: OrtEnvironment,
        data: FloatArray,
        shape: LongArray
    ): OnnxTensor {
        val buffer = FloatBuffer.wrap(data)
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    /**
     * Создать Long тензор из массива
     */
    fun createLongTensor(
        env: OrtEnvironment,
        data: LongArray,
        shape: LongArray
    ): OnnxTensor {
        val buffer = LongBuffer.wrap(data)
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    /**
     * Создать Int тензор из массива
     */
    fun createIntTensor(
        env: OrtEnvironment,
        data: IntArray,
        shape: LongArray
    ): OnnxTensor {
        val buffer = IntBuffer.wrap(data)
        return OnnxTensor.createTensor(env, buffer, shape)
    }

    /**
     * Создать 2D Float тензор
     */
    fun createFloatTensor2D(
        env: OrtEnvironment,
        data: Array<FloatArray>
    ): OnnxTensor {
        val rows = data.size
        val cols = if (rows > 0) data[0].size else 0
        val flatData = FloatArray(rows * cols)
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                flatData[i * cols + j] = data[i][j]
            }
        }
        return createFloatTensor(env, flatData, longArrayOf(rows.toLong(), cols.toLong()))
    }

    /**
     * Извлечь Float массив из тензора
     */
    fun getFloatArray(tensor: OnnxTensor): FloatArray {
        val buffer = tensor.floatBuffer
        val array = FloatArray(buffer.remaining())
        buffer.get(array)
        return array
    }

    /**
     * Извлечь Long массив из тензора
     */
    fun getLongArray(tensor: OnnxTensor): LongArray {
        val buffer = tensor.longBuffer
        val array = LongArray(buffer.remaining())
        buffer.get(array)
        return array
    }

    /**
     * Извлечь Int массив из тензора
     */
    fun getIntArray(tensor: OnnxTensor): IntArray {
        val buffer = tensor.intBuffer
        val array = IntArray(buffer.remaining())
        buffer.get(array)
        return array
    }

    /**
     * Получить argmax вдоль последней оси
     */
    fun argMax(data: FloatArray, dim: Int): IntArray {
        val result = IntArray(data.size / dim)
        for (i in result.indices) {
            var maxIdx = 0
            var maxVal = data[i * dim]
            for (j in 1 until dim) {
                val val_ = data[i * dim + j]
                if (val_ > maxVal) {
                    maxVal = val_
                    maxIdx = j
                }
            }
            result[i] = maxIdx
        }
        return result
    }

    /**
     * Применить softmax к массиву
     */
    fun softmax(data: FloatArray): FloatArray {
        val max = data.maxOrNull() ?: 0f
        val exp = FloatArray(data.size) { Math.exp((data[it] - max).toDouble()).toFloat() }
        val sum = exp.sum()
        return FloatArray(data.size) { exp[it] / sum }
    }

    /**
     * Логирование формы тензора
     */
    fun shapeToString(tensor: OnnxTensor): String {
        return tensor.info.shape.joinToString(", ", "[", "]")
    }

    /**
     * Безопасное закрытие результатов сессии
     */
    fun closeResults(results: OrtSession.Result?) {
        results?.use { /* auto-close */ }
    }
}
