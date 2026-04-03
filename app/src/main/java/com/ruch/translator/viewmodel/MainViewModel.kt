package com.ruch.translator.viewmodel

import android.app.Application
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.ruch.translator.R
import com.ruch.translator.audio.AudioRecorder
import com.ruch.translator.data.Language
import com.ruch.translator.data.PreferencesManager
import com.ruch.translator.data.ProcessingState
import com.ruch.translator.stt.WhisperSTT
import com.ruch.translator.tts.SherpaTTSEngine
import com.ruch.translator.translation.NllbTranslator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Main ViewModel for RUCH Translator
 *
 * Integrates:
 * - WhisperSTT for offline speech recognition
 * - NllbTranslator for offline machine translation
 * - SherpaTTSEngine for offline text-to-speech
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val preferencesManager = PreferencesManager(application)

    // AI Engines (Offline)
    private val whisperSTT = WhisperSTT(application)
    private val translator = NllbTranslator(application)
    private val ttsEngine = SherpaTTSEngine(application)
    
    // Audio components
    private val audioRecorder = AudioRecorder(application)

    // UI State
    private val _russianText = MutableLiveData<String>()
    val russianText: LiveData<String> = _russianText

    private val _chineseText = MutableLiveData<String>()
    val chineseText: LiveData<String> = _chineseText

    private val _processingState = MutableLiveData<ProcessingState>()
    val processingState: LiveData<ProcessingState> = _processingState
    
    private val _recordingLanguage = MutableLiveData<Language?>()
    val recordingLanguage: LiveData<Language?> = _recordingLanguage

    // These are used for UI state but may not be observed in MainActivity yet
    @Suppress("unused")
    private val _isRecordingRussian = MutableLiveData(false)
    val isRecordingRussian: LiveData<Boolean> = _isRecordingRussian

    @Suppress("unused")
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

    // These are used for UI state but may not be observed in MainActivity yet
    @Suppress("unused")
    private val _isInitializing = MutableLiveData<Boolean>()
    val isInitializing: LiveData<Boolean> = _isInitializing

    @Suppress("unused")
    private val _engineStatus = MutableLiveData<String>()
    val engineStatus: LiveData<String> = _engineStatus

    private var currentJob: Job? = null
    private var audioTrack: AudioTrack? = null

    init {
        _russianText.value = ""
        _chineseText.value = ""
        _processingState.value = ProcessingState.IDLE
        _isInitializing.value = true
        _modelsReady.value = false
        initializeEngines()
    }

    /**
     * Initialize all AI engines
     */
    private fun initializeEngines() {
        viewModelScope.launch {
            try {
                _engineStatus.value = getApplication<Application>().getString(R.string.model_downloading)
                
                Log.i(TAG, "=== Starting engine initialization ===")
                
                // Initialize Whisper STT
                var sttReady = false
                try {
                    sttReady = whisperSTT.initialize()
                    Log.i(TAG, "Whisper STT initialized: $sttReady")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Native library error for STT: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "STT init error: ${e.message}")
                }

                // Initialize Translator
                var translatorReady = false
                try {
                    translatorReady = translator.initialize()
                    Log.i(TAG, "NLLB Translator initialized: $translatorReady")
                } catch (e: Exception) {
                    Log.e(TAG, "Translator init error: ${e.message}")
                }

                // Initialize TTS
                var ttsReady = false
                try {
                    ttsReady = ttsEngine.initialize()
                    Log.i(TAG, "Sherpa TTS initialized: $ttsReady")
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Native library error for TTS: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "TTS init error: ${e.message}")
                }

                val allReady = sttReady || translatorReady || ttsReady
                _modelsReady.value = allReady
                _isInitializing.value = false
                
                val status = buildString {
                    append("STT: ${if (sttReady) "✓" else "✗"}")
                    append(" | MT: ${if (translatorReady) "✓" else "✗"}")
                    append(" | TTS: ${if (ttsReady) "✓" else "✗"}")
                }
                _engineStatus.value = status
                
                Log.i(TAG, "=== All engines initialized. Ready: $allReady ===")
            } catch (e: Exception) {
                Log.e(TAG, "Engine initialization error: ${e.message}", e)
                _isInitializing.value = false
                _errorMessage.value = "Initialization error: ${e.message}"
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library error: ${e.message}", e)
                _isInitializing.value = false
                _errorMessage.value = "Native library error: ${e.message}"
            }
        }
    }

    fun onModelsDownloaded() {
        _modelsReady.value = true
        viewModelScope.launch {
            preferencesManager.setModelsDownloaded(true)
        }
    }

    /**
     * Start voice recording and recognition
     */
    fun startRecording(language: Language) {
        if (_processingState.value != ProcessingState.IDLE) return

        _recordingLanguage.value = language
        
        currentJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.RECORDING
                if (language == Language.RUSSIAN) {
                    _isRecordingRussian.value = true
                } else {
                    _isRecordingChinese.value = true
                }

                // Record audio using AudioRecorder
                val audioData = audioRecorder.recordAudio(maxDurationSeconds = 15)

                _isRecordingRussian.value = false
                _isRecordingChinese.value = false
                _recordingLanguage.value = null

                if (audioData != null && audioData.isNotEmpty()) {
                    _processingState.value = ProcessingState.TRANSCRIBING

                    // Transcribe with Whisper
                    val text = whisperSTT.transcribe(audioData, language)

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
                _recordingLanguage.value = null
            }
        }
    }

    fun stopRecording() {
        audioRecorder.stopRecording()
    }

    /**
     * Translate text from one language to another
     */
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

    /**
     * Speak text using TTS
     */
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

                // Synthesize speech
                val audioData = ttsEngine.synthesize(text, language)

                if (audioData != null && audioData.isNotEmpty()) {
                    // Play audio
                    playAudio(audioData)
                } else {
                    _errorMessage.value = getApplication<Application>().getString(R.string.error_tts)
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

    /**
     * Play audio using AudioTrack (FloatArray from Sherpa TTS)
     */
    private fun playAudio(audioData: FloatArray) {
        try {
            val sampleRate = ttsEngine.getSampleRate(Language.RUSSIAN) // Get actual sample rate
            
            // Convert FloatArray [-1, 1] to ShortArray [Short.MIN_VALUE, Short.MAX_VALUE]
            val shortData = ShortArray(audioData.size) { i ->
                val clamped = audioData[i].coerceIn(-1f, 1f)
                (clamped * Short.MAX_VALUE).toInt().toShort()
            }
            
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(shortData.size * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            audioTrack?.write(shortData, 0, shortData.size)
            
            // Wait for playback to complete
            Thread.sleep(shortData.size * 1000L / sampleRate + 100)
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback error: ${e.message}", e)
        }
    }

    @Suppress("unused")
    fun stopSpeaking() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
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
        whisperSTT.release()
        translator.release()
        ttsEngine.release()
        audioRecorder.release()
        audioTrack?.release()
    }
}
