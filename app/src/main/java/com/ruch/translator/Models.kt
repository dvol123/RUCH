package com.ruch.translator

enum class Language {
    RUSSIAN,
    CHINESE
}

enum class ProcessingState {
    IDLE,
    RECORDING,
    RECOGNIZING,
    TRANSLATING,
    SPEAKING
}

data class TranslationResult(
    val sourceLanguage: Language,
    val sourceText: String,
    val targetText: String
)
