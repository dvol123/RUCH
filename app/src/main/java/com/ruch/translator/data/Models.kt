package com.ruch.translator.data

/**
 * Поддерживаемые языки перевода
 */
enum class Language {
    RUSSIAN,
    CHINESE;

    fun getDisplayName(): String = when (this) {
        RUSSIAN -> "Русский"
        CHINESE -> "中文"
    }

    fun getCode(): String = when (this) {
        RUSSIAN -> "ru"
        CHINESE -> "zh"
    }

    companion object {
        fun fromCode(code: String): Language = when (code.lowercase()) {
            "ru", "rus", "russian" -> RUSSIAN
            "zh", "zho", "chinese", "cn" -> CHINESE
            else -> RUSSIAN
        }
    }
}

/**
 * Состояния обработки
 */
enum class ProcessingState {
    IDLE,           // Простой, готов к работе
    RECORDING,      // Запись аудио
    TRANSCRIBING,   // Распознавание речи (STT)
    TRANSLATING,    // Перевод текста
    SPEAKING        // Воспроизведение речи (TTS)
}

/**
 * Результат перевода
 */
data class TranslationResult(
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Закладка
 */
data class Bookmark(
    val id: Long = System.currentTimeMillis(),
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Тема приложения
 */
enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromOrdinal(ordinal: Int): AppTheme = values().getOrElse(ordinal) { SYSTEM }
    }
}
