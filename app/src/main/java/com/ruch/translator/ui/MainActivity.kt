package com.ruch.translator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ruch.translator.Language
import com.ruch.translator.ProcessingState
import com.ruch.translator.R
import com.ruch.translator.databinding.ActivityMainBinding
import com.ruch.translator.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    
    private var isRecording = false
    private var currentRecordingLanguage: Language? = null
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            showPermissionDeniedDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupToolbar()
        setupButtons()
        setupTextWatchers()
        observeViewModel()
        
        // Check if models are ready
        checkModelsStatus()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            showMainMenu()
        }
    }
    
    private fun showMainMenu() {
        val items = arrayOf(
            getString(R.string.menu_theme),
            "${getString(R.string.menu_streaming)} (${getString(R.string.in_development)})",
            "${getString(R.string.menu_history)} (${getString(R.string.in_development)})",
            "${getString(R.string.menu_tts_settings)} (${getString(R.string.in_development)})",
            getString(R.string.menu_about)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.app_name))
            .setItems(items) { dialog, which ->
                when (which) {
                    0 -> showThemeDialog()
                    4 -> showAboutDialog()
                }
            }
            .show()
    }
    
    private fun showThemeDialog() {
        val themes = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.menu_theme))
            .setSingleChoiceItems(themes, getApplication().getSharedPreferences("settings", MODE_PRIVATE)
                .getInt("theme_mode", 0)) { dialog, which ->
                applyTheme(which)
                dialog.dismiss()
            }
            .show()
    }
    
    private fun applyTheme(themeMode: Int) {
        getApplication().getSharedPreferences("settings", MODE_PRIVATE)
            .edit()
            .putInt("theme_mode", themeMode)
            .apply()
        
        val nightMode = when (themeMode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
    
    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage("${getString(R.string.about_description)}\n\n${getString(R.string.about_version, BuildConfig.VERSION_NAME)}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    private fun setupButtons() {
        // Russian buttons
        binding.russianMicBtn.setOnClickListener {
            handleMicClick(Language.RUSSIAN)
        }
        binding.russianKeyboardBtn.setOnClickListener {
            showKeyboard(binding.russianText)
        }
        binding.russianSpeakerBtn.setOnClickListener {
            viewModel.speakText(Language.RUSSIAN, binding.russianText.text.toString())
        }
        
        // Chinese buttons
        binding.chineseMicBtn.setOnClickListener {
            handleMicClick(Language.CHINESE)
        }
        binding.chineseKeyboardBtn.setOnClickListener {
            showKeyboard(binding.chineseText)
        }
        binding.chineseSpeakerBtn.setOnClickListener {
            viewModel.speakText(Language.CHINESE, binding.chineseText.text.toString())
        }
    }
    
    private fun handleMicClick(language: Language) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        
        if (isRecording && currentRecordingLanguage == language) {
            // Stop recording
            viewModel.stopRecording()
            isRecording = false
            currentRecordingLanguage = null
            updateRecordingUI(null)
        } else if (!isRecording) {
            // Start recording
            isRecording = true
            currentRecordingLanguage = language
            updateRecordingUI(language)
            viewModel.startRecording(language)
            
            // Auto-stop after 15 seconds
            CoroutineScope(Dispatchers.Main).launch {
                delay(15000)
                if (isRecording && currentRecordingLanguage == language) {
                    viewModel.stopRecording()
                    isRecording = false
                    currentRecordingLanguage = null
                    updateRecordingUI(null)
                }
            }
        }
    }
    
    private fun updateRecordingUI(language: Language?) {
        val recordingColor = ContextCompat.getColor(this, R.color.button_recording)
        val inactiveColor = ContextCompat.getColor(this, R.color.button_inactive)
        
        binding.russianMicBtn.backgroundTintList = 
            if (language == Language.RUSSIAN) android.content.res.ColorStateList.valueOf(recordingColor)
            else android.content.res.ColorStateList.valueOf(inactiveColor)
        
        binding.chineseMicBtn.backgroundTintList = 
            if (language == Language.CHINESE) android.content.res.ColorStateList.valueOf(recordingColor)
            else android.content.res.ColorStateList.valueOf(inactiveColor)
    }
    
    private fun showKeyboard(editText: android.widget.EditText) {
        editText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
    
    private fun setupTextWatchers() {
        var russianTextChangeJob: kotlinx.coroutines.Job? = null
        var chineseTextChangeJob: kotlinx.coroutines.Job? = null
        
        binding.russianText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideKeyboard()
                val text = binding.russianText.text.toString()
                if (text.isNotEmpty() && text != viewModel.russianText.value) {
                    viewModel.translateText(Language.RUSSIAN, text)
                }
            }
        }
        
        binding.chineseText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideKeyboard()
                val text = binding.chineseText.text.toString()
                if (text.isNotEmpty() && text != viewModel.chineseText.value) {
                    viewModel.translateText(Language.CHINESE, text)
                }
            }
        }
    }
    
    private fun observeViewModel() {
        viewModel.russianText.observe(this) { text ->
            if (binding.russianText.text.toString() != text) {
                binding.russianText.setText(text)
            }
        }
        
        viewModel.chineseText.observe(this) { text ->
            if (binding.chineseText.text.toString() != text) {
                binding.chineseText.setText(text)
            }
        }
        
        viewModel.processingState.observe(this) { state ->
            binding.progressBar.visibility = when (state) {
                ProcessingState.RECOGNIZING, ProcessingState.TRANSLATING -> View.VISIBLE
                else -> View.GONE
            }
        }
        
        viewModel.errorMessage.observe(this) { message ->
            message?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        
        viewModel.isSpeakingRussian.observe(this) { isSpeaking ->
            val color = if (isSpeaking) ContextCompat.getColor(this, R.color.button_active)
                        else ContextCompat.getColor(this, R.color.button_inactive)
            binding.russianSpeakerBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
        
        viewModel.isSpeakingChinese.observe(this) { isSpeaking ->
            val color = if (isSpeaking) ContextCompat.getColor(this, R.color.button_active)
                        else ContextCompat.getColor(this, R.color.button_inactive)
            binding.chineseSpeakerBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
    }
    
    private fun checkModelsStatus() {
        viewModel.modelsReady.observe(this) { ready ->
            if (!ready) {
                showModelDownloadDialog()
            }
        }
    }
    
    private fun showModelDownloadDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.model_download_title)
            .setMessage(R.string.model_download_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                startModelDownload()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startModelDownload() {
        Toast.makeText(this, R.string.model_downloading, Toast.LENGTH_LONG).show()
        // Model download will be implemented in ModelDownloadService
        // For now, simulate models ready
        CoroutineScope(Dispatchers.Main).launch {
            delay(1000)
            viewModel.onModelsDownloaded()
        }
    }
    
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_required)
            .setMessage("Для работы голосового ввода требуется разрешение на запись аудио.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    override fun onBackPressed() {
        if (isRecording) {
            viewModel.stopRecording()
            isRecording = false
            currentRecordingLanguage = null
            updateRecordingUI(null)
        } else {
            super.onBackPressed()
        }
    }
}
