package com.ruch.translator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.ruch.translator.BuildConfig
import com.ruch.translator.data.Language
import com.ruch.translator.data.PreferencesManager
import com.ruch.translator.data.ProcessingState
import com.ruch.translator.R
import com.ruch.translator.databinding.ActivityMainBinding
import com.ruch.translator.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private val REQUIRED_MODEL_FILES = listOf(
            "encoder_model_int8.onnx",
            "decoder_model_int8.onnx",
            "tokenizer.json"
        )
    }
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var preferencesManager: PreferencesManager
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            showPermissionDeniedDialog()
        }
    }
    
    // SAF picker для выбора папки с моделями
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { handleSelectedFolder(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        
        preferencesManager = PreferencesManager(this)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupToolbar()
        setupButtons()
        setupTextWatchers()
        observeViewModel()
        
        // Проверяем, выбрана ли папка с моделями
        checkModelsFolder()
    }
    
    /**
     * Проверка папки с моделями при запуске
     */
    private fun checkModelsFolder() {
        val savedUri = preferencesManager.getModelsFolderUri()
        
        if (savedUri != null) {
            // Папка уже выбрана - проверяем валидность
            try {
                val uri = Uri.parse(savedUri)
                validateAndShowResult(uri)
            } catch (e: Exception) {
                Log.e(TAG, "Invalid saved URI", e)
                showSelectFolderDialog()
            }
        } else {
            // Папка не выбрана - показываем диалог
            showSelectFolderDialog()
        }
    }
    
    /**
     * Показать диалог выбора папки
     */
    private fun showSelectFolderDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_models_folder_title)
            .setMessage(R.string.select_models_folder_message)
            .setPositiveButton(R.string.select_folder) { _, _ ->
                folderPickerLauncher.launch(null)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Обработка выбранной папки
     */
    private fun handleSelectedFolder(uri: Uri) {
        Log.i(TAG, "Selected folder: $uri")
        validateAndShowResult(uri)
    }
    
    /**
     * Проверить папку и показать результат
     */
    private fun validateAndShowResult(folderUri: Uri) {
        val (missingFiles, foundFiles) = validateFolder(folderUri)
        
        if (missingFiles.isEmpty()) {
            // Все файлы найдены
            val message = buildString {
                append(getString(R.string.models_found_message))
                append("\n\n")
                foundFiles.forEach { append("✓ $it\n") }
            }
            
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.models_found_title)
                .setMessage(message)
                .setPositiveButton(R.string.continue_btn) { _, _ ->
                    lifecycleScope.launch {
                        preferencesManager.setModelsFolderUri(folderUri.toString())
                        // Инициализируем движки с выбранной папкой
                        viewModel.initializeWithFolder(folderUri)
                    }
                }
                .setCancelable(false)
                .show()
        } else {
            // Не все файлы найдены
            val message = buildString {
                append(getString(R.string.models_incomplete_message))
                append("\n\n")
                append(getString(R.string.missing_files))
                append(":\n")
                missingFiles.forEach { append("✗ $it\n") }
                append("\n")
                if (foundFiles.isNotEmpty()) {
                    append(getString(R.string.found_files))
                    append(":\n")
                    foundFiles.forEach { append("✓ $it\n") }
                }
            }
            
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.models_incomplete_title)
                .setMessage(message)
                .setPositiveButton(R.string.select_another) { _, _ ->
                    folderPickerLauncher.launch(null)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
    
    /**
     * Проверить папку на наличие нужных файлов
     */
    private fun validateFolder(folderUri: Uri): Pair<List<String>, List<String>> {
        val missingFiles = mutableListOf<String>()
        val foundFiles = mutableListOf<String>()
        
        try {
            val folder = DocumentFile.fromTreeUri(this, folderUri)
            if (folder == null || !folder.exists()) {
                return Pair(REQUIRED_MODEL_FILES, emptyList())
            }
            
            // Ищем подпапку whisper или проверяем саму папку
            var whisperFolder = folder.findFile("whisper")
            if (whisperFolder == null || !whisperFolder.isDirectory) {
                whisperFolder = folder
            }
            
            for (fileName in REQUIRED_MODEL_FILES) {
                val file = whisperFolder.findFile(fileName)
                if (file != null && file.exists()) {
                    foundFiles.add(fileName)
                    Log.d(TAG, "Found: $fileName (${file.length()} bytes)")
                } else {
                    missingFiles.add(fileName)
                    Log.d(TAG, "Missing: $fileName")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating folder", e)
            return Pair(REQUIRED_MODEL_FILES, emptyList())
        }
        
        return Pair(missingFiles, foundFiles)
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
        binding.russianEditBtn.setOnClickListener {
            // Translate current text in Russian field
            val text = binding.russianText.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.translateText(Language.RUSSIAN, text)
            }
        }
        binding.russianSpeakerBtn.setOnClickListener {
            viewModel.speakText(Language.RUSSIAN, binding.russianText.text.toString())
        }
        
        // Chinese buttons
        binding.chineseMicBtn.setOnClickListener {
            handleMicClick(Language.CHINESE)
        }
        binding.chineseEditBtn.setOnClickListener {
            // Translate current text in Chinese field
            val text = binding.chineseText.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.translateText(Language.CHINESE, text)
            }
        }
        binding.chineseSpeakerBtn.setOnClickListener {
            viewModel.speakText(Language.CHINESE, binding.chineseText.text.toString())
        }
    }
    
    /**
     * Show edit dialog with translate button
     */
    private fun showEditDialog(language: Language) {
        val currentText = if (language == Language.RUSSIAN) {
            binding.russianText.text.toString()
        } else {
            binding.chineseText.text.toString()
        }
        
        // Create edit text
        val editText = TextInputEditText(this).apply {
            setText(currentText)
            hint = if (language == Language.RUSSIAN) {
                getString(R.string.enter_text_russian)
            } else {
                getString(R.string.enter_text_chinese)
            }
            setSingleLine(false)
            maxLines = 8
            minLines = 3
            setPadding(32, 24, 32, 24)
            textSize = 18f
        }
        
        val container = android.widget.FrameLayout(this).apply {
            addView(editText, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            ))
            setPadding(24, 16, 24, 0)
        }
        
        val title = if (language == Language.RUSSIAN) {
            getString(R.string.russian)
        } else {
            getString(R.string.chinese)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.btn_translate) { dialog, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    // Update the text field
                    if (language == Language.RUSSIAN) {
                        binding.russianText.setText(newText)
                    } else {
                        binding.chineseText.setText(newText)
                    }
                    // Trigger translation
                    viewModel.translateText(language, newText)
                }
                dialog.dismiss()
                hideKeyboard()
            }
            .setNegativeButton(R.string.btn_cancel) { dialog, _ ->
                dialog.dismiss()
                hideKeyboard()
            }
            .setNeutralButton(android.R.string.ok) { dialog, _ ->
                // Just save without translating
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty()) {
                    if (language == Language.RUSSIAN) {
                        binding.russianText.setText(newText)
                        viewModel.setRussianText(newText)
                    } else {
                        binding.chineseText.setText(newText)
                        viewModel.setChineseText(newText)
                    }
                }
                dialog.dismiss()
                hideKeyboard()
            }
            .create()
            .apply {
                setOnShowListener {
                    editText.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                    // Select all text
                    editText.selectAll()
                }
            }
            .show()
    }
    
    private fun handleMicClick(language: Language) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        
        val currentProcessingState = viewModel.processingState.value
        
        if (currentProcessingState == ProcessingState.RECORDING) {
            // Stop recording
            viewModel.stopRecording()
        } else if (currentProcessingState == ProcessingState.IDLE) {
            // Start recording
            viewModel.startRecording(language)
        }
        // If transcribing/translating/speaking - ignore click
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
        
        // Update status text
        if (language != null) {
            binding.statusText.text = getString(R.string.recording)
            binding.statusText.visibility = View.VISIBLE
        } else {
            binding.statusText.visibility = View.GONE
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { view ->
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
    
    private fun setupTextWatchers() {
        // No longer auto-translate on focus change - use edit button instead
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
            when (state) {
                ProcessingState.RECORDING -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.statusText.text = getString(R.string.recording)
                    binding.statusText.visibility = View.VISIBLE
                    // Update recording UI based on which language is recording
                    updateRecordingUI(viewModel.recordingLanguage.value)
                }
                ProcessingState.TRANSCRIBING -> {
                    updateRecordingUI(null) // Stop recording visual
                    binding.progressBar.visibility = View.VISIBLE
                    binding.statusText.text = getString(R.string.transcribing)
                    binding.statusText.visibility = View.VISIBLE
                }
                ProcessingState.TRANSLATING -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.statusText.text = getString(R.string.translating)
                    binding.statusText.visibility = View.VISIBLE
                }
                ProcessingState.SPEAKING -> {
                    binding.statusText.text = getString(R.string.speaking)
                    binding.statusText.visibility = View.VISIBLE
                }
                ProcessingState.IDLE -> {
                    updateRecordingUI(null)
                    binding.progressBar.visibility = View.GONE
                    binding.statusText.visibility = View.GONE
                }
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
    
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_required)
            .setMessage("Для работы голосового ввода требуется разрешение на запись аудио.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
    
    /**
     * Добавить пункт меню для смены папки с моделями
     */
    private fun showChangeModelsFolder() {
        folderPickerLauncher.launch(null)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (viewModel.processingState.value == ProcessingState.RECORDING) {
            viewModel.stopRecording()
            updateRecordingUI(null)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
