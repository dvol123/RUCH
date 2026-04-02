package com.ruch.translator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ruch.translator.R
import com.ruch.translator.data.PreferencesManager
import com.ruch.translator.stt.SpeechRecognizer
import com.ruch.translator.translation.TranslatorEngine
import com.ruch.translator.tts.TTSEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val preferencesManager = PreferencesManager(application)
    private val speechRecognizer = SpeechRecognizer(application)
    private val translatorEngine = TranslatorEngine(application)
    private val ttsEngine = TTSEngine(application)
    
    private val _russianText = MutableLiveData<String>()
    val russianText: LiveData<String> = _russianText
    
    private val _chineseText = MutableLiveData<String>()
    val chineseText: LiveData<String> = _chineseText
    
    private val _processingState = MutableLiveData<ProcessingState>()
    val processingState: LiveData<ProcessingState> = _processingState
    
    private val _isRecordingRussian = MutableLiveData(false)
    val isRecordingRussian: LiveData<Boolean> = _isRecordingRussian
    
    private val _isRecordingChinese = MutableLiveData(false)
    val isRecordingChinese: LiveData<Boolean> = _isRecordingChinese
    
    private val _isSpeakingRussian = MutableLiveData(false)
    val isSpeakingRussian: LiveData<Boolean> = _isSpeakingRussian
    
    private val _isSpeakingChinese = MutableLiveData(false)
    val isSpeakingChinese: LiveData<Boolean> = _isSpeakingChinese
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _modelsReady = MutableLiveData<Boolean>()
    val modelsReady: LiveData<Boolean> = _modelsReady
    
    private var currentJob: Job? = null
    
    init {
        _russianText.value = ""
        _chineseText.value = ""
        _processingState.value = ProcessingState.IDLE
        checkModels()
    }
    
    private fun checkModels() {
        viewModelScope.launch {
            val ready = preferencesManager.areModelsDownloaded()
            _modelsReady.value = ready
        }
    }
    
    fun onModelsDownloaded() {
        _modelsReady.value = true
        viewModelScope.launch {
            preferencesManager.setModelsDownloaded(true)
        }
    }
    
    // Speech Recognition
    fun startRecording(language: Language) {
        if (_processingState.value != ProcessingState.IDLE) return
        
        currentJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.RECORDING
                if (language == Language.RUSSIAN) {
                    _isRecordingRussian.value = true
                } else {
                    _isRecordingChinese.value = true
                }
                
                val text = speechRecognizer.recordAndRecognize(language)
                
                _isRecordingRussian.value = false
                _isRecordingChinese.value = false
                
                if (text.isNotEmpty()) {
                    _processingState.value = ProcessingState.TRANSLATING
                    
                    if (language == Language.RUSSIAN) {
                        _russianText.value = text
                        val translated = translatorEngine.translate(text, Language.RUSSIAN, Language.CHINESE)
                        _chineseText.value = translated
                    } else {
                        _chineseText.value = text
                        val translated = translatorEngine.translate(text, Language.CHINESE, Language.RUSSIAN)
                        _russianText.value = translated
                    }
                }
                
                _processingState.value = ProcessingState.IDLE
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_recognition)
                _processingState.value = ProcessingState.IDLE
                _isRecordingRussian.value = false
                _isRecordingChinese.value = false
            }
        }
    }
    
    fun stopRecording() {
        speechRecognizer.stopRecording()
    }
    
    // Text Translation
    fun translateText(sourceLanguage: Language, text: String) {
        if (text.isEmpty()) return
        
        currentJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.TRANSLATING
                
                val targetLanguage = if (sourceLanguage == Language.RUSSIAN) Language.CHINESE else Language.RUSSIAN
                val translated = translatorEngine.translate(text, sourceLanguage, targetLanguage)
                
                if (sourceLanguage == Language.RUSSIAN) {
                    _chineseText.value = translated
                } else {
                    _russianText.value = translated
                }
                
                _processingState.value = ProcessingState.IDLE
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_translation)
                _processingState.value = ProcessingState.IDLE
            }
        }
    }
    
    // Text-to-Speech
    fun speakText(language: Language, text: String) {
        if (text.isEmpty()) return
        
        currentJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.SPEAKING
                if (language == Language.RUSSIAN) {
                    _isSpeakingRussian.value = true
                } else {
                    _isSpeakingChinese.value = true
                }
                
                ttsEngine.speak(text, language)
                
                _isSpeakingRussian.value = false
                _isSpeakingChinese.value = false
                _processingState.value = ProcessingState.IDLE
            } catch (e: Exception) {
                _errorMessage.value = getApplication<Application>().getString(R.string.error_tts)
                _processingState.value = ProcessingState.IDLE
                _isSpeakingRussian.value = false
                _isSpeakingChinese.value = false
            }
        }
    }
    
    fun stopSpeaking() {
        ttsEngine.stop()
        _isSpeakingRussian.value = false
        _isSpeakingChinese.value = false
        _processingState.value = ProcessingState.IDLE
    }
    
    // Manual text input
    fun setRussianText(text: String) {
        _russianText.value = text
    }
    
    fun setChineseText(text: String) {
        _chineseText.value = text
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
    
    override fun onCleared() {
        super.onCleared()
        speechRecognizer.release()
        ttsEngine.release()
        translatorEngine.release()
    }
}
