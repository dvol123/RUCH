package com.ruch.translator.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ruch.translator.R
import com.ruch.translator.audio.AudioPlayer
import com.ruch.translator.audio.AudioRecorder
import com.ruch.translator.data.Language
import com.ruch.translator.data.PreferencesManager
import com.ruch.translator.data.ProcessingState
import com.ruch.translator.stt.AndroidSpeechRecognizer
import com.ruch.translator.translation.OfflineTranslator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val preferencesManager = PreferencesManager(application)

    // Android API based engines (no JNI required)
    private val speechRecognizer = AndroidSpeechRecognizer(application)
    private val translator = OfflineTranslator(application)

    // Audio components
    private val audioRecorder = AudioRecorder(application)
    private val audioPlayer = AudioPlayer()

    // Android TTS
    private var tts: TextToSpeech? = null
    private var ttsReady = false

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

    private val _isInitializing = MutableLiveData<Boolean>()
    val isInitializing: LiveData<Boolean> = _isInitializing

    private var currentJob: Job? = null

    init {
        _russianText.value = ""
        _chineseText.value = ""
        _processingState.value = ProcessingState.IDLE
        _isInitializing.value = true
        initializeEngines()
    }

    private fun initializeEngines() {
        viewModelScope.launch {
            try {
                // Initialize STT
                val sttReady = speechRecognizer.initialize()
                Log.i(TAG, "STT initialized: $sttReady")

                // Initialize Translator
                val translatorReady = translator.initialize()
                Log.i(TAG, "Translator initialized: $translatorReady")

                // Initialize TTS
                initTTS()

                _modelsReady.value = true
                _isInitializing.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Engine initialization error: ${e.message}", e)
                _isInitializing.value = false
                _errorMessage.value = "Initialization error: ${e.message}"
            }
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(getApplication()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                Log.i(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed")
            }
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

                // Use Android SpeechRecognizer
                val text = speechRecognizer.listenOnce(language)

                _isRecordingRussian.value = false
                _isRecordingChinese.value = false

                if (!text.isNullOrEmpty()) {
                    _processingState.value = ProcessingState.TRANSLATING

                    if (language == Language.RUSSIAN) {
                        _russianText.value = text
                        val translated = translator.translate(text, Language.RUSSIAN, Language.CHINESE)
                        _chineseText.value = translated
                    } else {
                        _chineseText.value = text
                        val translated = translator.translate(text, Language.CHINESE, Language.RUSSIAN)
                        _russianText.value = translated
                    }
                }

                _processingState.value = ProcessingState.IDLE
            } catch (e: Exception) {
                Log.e(TAG, "Recording error: ${e.message}", e)
                _errorMessage.value = getApplication<Application>().getString(R.string.error_recognition)
                _processingState.value = ProcessingState.IDLE
                _isRecordingRussian.value = false
                _isRecordingChinese.value = false
            }
        }
    }

    fun stopRecording() {
        audioRecorder.stopRecording()
    }

    // Text Translation
    fun translateText(sourceLanguage: Language, text: String) {
        if (text.isEmpty()) return

        currentJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.TRANSLATING

                val targetLanguage = if (sourceLanguage == Language.RUSSIAN) Language.CHINESE else Language.RUSSIAN
                val translated = translator.translate(text, sourceLanguage, targetLanguage)

                if (sourceLanguage == Language.RUSSIAN) {
                    _chineseText.value = translated
                } else {
                    _russianText.value = translated
                }

                _processingState.value = ProcessingState.IDLE
            } catch (e: Exception) {
                Log.e(TAG, "Translation error: ${e.message}", e)
                _errorMessage.value = getApplication<Application>().getString(R.string.error_translation)
                _processingState.value = ProcessingState.IDLE
            }
        }
    }

    // Text-to-Speech
    fun speakText(language: Language, text: String) {
        if (text.isEmpty() || !ttsReady) {
            if (!ttsReady) {
                _errorMessage.value = "TTS not ready"
            }
            return
        }

        currentJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.SPEAKING
                if (language == Language.RUSSIAN) {
                    _isSpeakingRussian.value = true
                } else {
                    _isSpeakingChinese.value = true
                }

                val locale = when (language) {
                    Language.RUSSIAN -> java.util.Locale("ru", "RU")
                    Language.CHINESE -> java.util.Locale("zh", "CN")
                }

                tts?.language = locale

                // Use speak() for direct playback
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")

                // Wait a bit for speech to complete
                Thread.sleep(text.length * 80L + 500)

                _isSpeakingRussian.value = false
                _isSpeakingChinese.value = false
                _processingState.value = ProcessingState.IDLE
            } catch (e: Exception) {
                Log.e(TAG, "TTS error: ${e.message}", e)
                _errorMessage.value = getApplication<Application>().getString(R.string.error_tts)
                _processingState.value = ProcessingState.IDLE
                _isSpeakingRussian.value = false
                _isSpeakingChinese.value = false
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
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
        tts?.shutdown()
        translator.release()
        audioRecorder.release()
        audioPlayer.release()
    }
}
