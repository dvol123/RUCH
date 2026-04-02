package com.ruch.translator.viewmodel

import android.app.Application
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
import com.ruch.translator.stt.SherpaWhisperRecognizer
import com.ruch.translator.translation.OfflineTranslator
import com.ruch.translator.tts.SherpaTTSEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val preferencesManager = PreferencesManager(application)

    // Нативные движки (используют .so библиотеки)
    private val whisperRecognizer = SherpaWhisperRecognizer(application)
    private val translator = OfflineTranslator(application)
    private val ttsEngine = SherpaTTSEngine(application)

    // Аудио компоненты
    private val audioRecorder = AudioRecorder(application)
    private val audioPlayer = AudioPlayer()

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
        checkModels()
    }

    private fun checkModels() {
        viewModelScope.launch {
            val ready = preferencesManager.areModelsDownloaded()
            _modelsReady.value = ready

            if (ready) {
                initializeEngines()
            } else {
                _isInitializing.value = false
            }
        }
    }

    /**
     * Инициализация всех движков
     */
    private suspend fun initializeEngines() {
        _isInitializing.value = true

        try {
            // Инициализируем STT (Whisper)
            val sttReady = whisperRecognizer.initialize()
            Log.i(TAG, "Whisper STT initialized: $sttReady")

            // Инициализируем переводчик
            val translatorReady = translator.initialize()
            Log.i(TAG, "Translator initialized: $translatorReady")

            // Инициализируем TTS
            val ttsReady = ttsEngine.initialize()
            Log.i(TAG, "TTS initialized: $ttsReady")

            _isInitializing.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Engine initialization error: ${e.message}", e)
            _isInitializing.value = false
            _errorMessage.value = "Ошибка инициализации: ${e.message}"
        }
    }

    fun onModelsDownloaded() {
        _modelsReady.value = true
        viewModelScope.launch {
            preferencesManager.setModelsDownloaded(true)
            initializeEngines()
        }
    }

    // Speech Recognition
    fun startRecording(language: Language) {
        if (_processingState.value != ProcessingState.IDLE) return
        if (!audioRecorder.hasRecordPermission()) {
            _errorMessage.value = getApplication<Application>().getString(R.string.permission_microphone_required)
            return
        }

        currentJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.RECORDING
                if (language == Language.RUSSIAN) {
                    _isRecordingRussian.value = true
                } else {
                    _isRecordingChinese.value = true
                }

                // Записываем аудио
                val audioData = audioRecorder.recordAudio(maxDurationSeconds = 30)

                _isRecordingRussian.value = false
                _isRecordingChinese.value = false

                if (audioData != null && audioData.isNotEmpty()) {
                    _processingState.value = ProcessingState.TRANSCRIBING

                    // Распознаем через Whisper
                    val text = whisperRecognizer.recognize(audioData, language)

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
        if (text.isEmpty()) return

        currentJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.SPEAKING
                if (language == Language.RUSSIAN) {
                    _isSpeakingRussian.value = true
                } else {
                    _isSpeakingChinese.value = true
                }

                // Синтезируем речь через Sherpa TTS
                val audioData = ttsEngine.synthesizeToArray(text, language)

                if (audioData != null && audioData.isNotEmpty()) {
                    // Воспроизводим
                    audioPlayer.play(audioData, ttsEngine.getSampleRate())
                }

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
        audioPlayer.stop()
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
        whisperRecognizer.release()
        ttsEngine.release()
        translator.release()
        audioRecorder.release()
        audioPlayer.release()
    }
}
