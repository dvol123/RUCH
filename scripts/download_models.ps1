# =============================================================================
# RUCH - PowerShell скрипт скачивания AI моделей и нативных библиотек
# =============================================================================
# Запуск: .\scripts\download_models.ps1
# =============================================================================

$ErrorActionPreference = "Stop"

$PROJECT_ROOT = Split-Path -Parent $PSScriptRoot
$ASSETS_DIR = Join-Path $PROJECT_ROOT "app\src\main\assets\models"
$JNI_DIR = Join-Path $PROJECT_ROOT "app\src\main\jniLibs"

Write-Host "=============================================" -ForegroundColor Blue
Write-Host "RUCH - Скачивание моделей и библиотек" -ForegroundColor Blue
Write-Host "=============================================" -ForegroundColor Blue
Write-Host ""

# Создаем структуру папок
Write-Host "Создание структуры папок..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "$ASSETS_DIR\whisper" | Out-Null
New-Item -ItemType Directory -Force -Path "$ASSETS_DIR\nllb" | Out-Null
New-Item -ItemType Directory -Force -Path "$ASSETS_DIR\tts\ru" | Out-Null
New-Item -ItemType Directory -Force -Path "$ASSETS_DIR\tts\zh" | Out-Null
New-Item -ItemType Directory -Force -Path "$JNI_DIR\arm64-v8a" | Out-Null
New-Item -ItemType Directory -Force -Path "$JNI_DIR\armeabi-v7a" | Out-Null
New-Item -ItemType Directory -Force -Path "$JNI_DIR\x86_64" | Out-Null
Write-Host "✓ Папки созданы" -ForegroundColor Green
Write-Host ""

# Функция скачивания
function Download-File {
    param($Url, $Output, $Description)
    
    Write-Host "Скачивание: $Description" -ForegroundColor Yellow
    Write-Host "  URL: $Url"
    
    try {
        # Удаляем файл если существует с ошибкой
        if (Test-Path $Output) { Remove-Item $Output -Force }
        
        $webClient = New-Object System.Net.WebClient
        $webClient.DownloadFile($Url, $Output)
        
        Write-Host "✓ $Description скачан" -ForegroundColor Green
        Write-Host ""
        return $true
    }
    catch {
        Write-Host "✗ Ошибка скачивания: $_" -ForegroundColor Red
        return $false
    }
}

# =============================================================================
# 1. Whisper GGML
# =============================================================================
Write-Host "[1/4] Whisper модель (GGML)" -ForegroundColor Blue
$WHISPER_GGML = Join-Path $ASSETS_DIR "ggml-small.bin"

if (-not (Test-Path $WHISPER_GGML)) {
    Download-File `
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin" `
        $WHISPER_GGML `
        "Whisper Small GGML (466 MB)"
} else {
    Write-Host "✓ Whisper GGML уже существует" -ForegroundColor Green
    Write-Host ""
}

# =============================================================================
# 2. Whisper ONNX
# =============================================================================
Write-Host "[2/4] Whisper ONNX модель" -ForegroundColor Blue
$WHISPER_DIR = Join-Path $ASSETS_DIR "whisper"
$ENCODER = Join-Path $WHISPER_DIR "tiny-encoder.int8.onnx"

if (-not (Test-Path $ENCODER)) {
    $TMP_FILE = Join-Path $env:TEMP "whisper-tiny.tar.bz2"
    
    Download-File `
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2" `
        $TMP_FILE `
        "Whisper Tiny ONNX (100 MB)"
    
    Write-Host "Распаковка... (требуется 7-Zip или tar)" -ForegroundColor Yellow
    
    # Пробуем tar (Windows 10+)
    if (Get-Command tar -ErrorAction SilentlyContinue) {
        tar -xf $TMP_FILE -C $env:TEMP
        Copy-Item "$env:TEMP\sherpa-onnx-whisper-tiny\tiny-encoder.int8.onnx" $WHISPER_DIR
        Copy-Item "$env:TEMP\sherpa-onnx-whisper-tiny\tiny-decoder.int8.onnx" $WHISPER_DIR
        Copy-Item "$env:TEMP\sherpa-onnx-whisper-tiny\tiny-tokens.txt" $WHISPER_DIR
        Write-Host "✓ Whisper ONNX готов" -ForegroundColor Green
    } else {
        Write-Host "⚠ Установите 7-Zip или используйте WSL для распаковки .tar.bz2" -ForegroundColor Yellow
    }
    Write-Host ""
} else {
    Write-Host "✓ Whisper ONNX уже существует" -ForegroundColor Green
    Write-Host ""
}

# =============================================================================
# 3. TTS модели
# =============================================================================
Write-Host "[3/4] TTS модели" -ForegroundColor Blue

# Русский TTS
$TTS_RU = Join-Path $ASSETS_DIR "tts\ru\ru_RU-irina-medium.onnx"
if (-not (Test-Path $TTS_RU)) {
    $TMP_FILE = Join-Path $env:TEMP "tts-ru.tar.bz2"
    
    Download-File `
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium.tar.bz2" `
        $TMP_FILE `
        "Русский TTS (61 MB)"
    
    if (Get-Command tar -ErrorAction SilentlyContinue) {
        tar -xf $TMP_FILE -C $env:TEMP
        Copy-Item -Recurse "$env:TEMP\vits-piper-ru_RU-irina-medium\*" "$ASSETS_DIR\tts\ru\"
        Write-Host "✓ Русский TTS готов" -ForegroundColor Green
    }
    Write-Host ""
} else {
    Write-Host "✓ Русский TTS уже существует" -ForegroundColor Green
    Write-Host ""
}

# Китайский TTS
$TTS_ZH = Join-Path $ASSETS_DIR "tts\zh\zh_CN-huayan-medium.onnx"
if (-not (Test-Path $TTS_ZH)) {
    $TMP_FILE = Join-Path $env:TEMP "tts-zh.tar.bz2"
    
    Download-File `
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-huayan-medium.tar.bz2" `
        $TMP_FILE `
        "Китайский TTS (61 MB)"
    
    if (Get-Command tar -ErrorAction SilentlyContinue) {
        tar -xf $TMP_FILE -C $env:TEMP
        Copy-Item -Recurse "$env:TEMP\vits-piper-zh_CN-huayan-medium\*" "$ASSETS_DIR\tts\zh\"
        Write-Host "✓ Китайский TTS готов" -ForegroundColor Green
    }
    Write-Host ""
} else {
    Write-Host "✓ Китайский TTS уже существует" -ForegroundColor Green
    Write-Host ""
}

# =============================================================================
# 4. Нативные библиотеки
# =============================================================================
Write-Host "[4/4] Нативные библиотеки" -ForegroundColor Blue
$JNI_ARM64 = Join-Path $JNI_DIR "arm64-v8a\libsherpa-onnx-jni.so"

if (-not (Test-Path $JNI_ARM64)) {
    $TMP_FILE = Join-Path $env:TEMP "sherpa-android.tar.bz2"
    
    Download-File `
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.10.40/sherpa-onnx-v1.10.40-android.tar.bz2" `
        $TMP_FILE `
        "Sherpa-ONNX Android библиотеки (33 MB)"
    
    if (Get-Command tar -ErrorAction SilentlyContinue) {
        tar -xf $TMP_FILE -C $env:TEMP
        Copy-Item "$env:TEMP\jniLibs\arm64-v8a\*" "$JNI_DIR\arm64-v8a\"
        Copy-Item "$env:TEMP\jniLibs\armeabi-v7a\*" "$JNI_DIR\armeabi-v7a\"
        Copy-Item "$env:TEMP\jniLibs\x86_64\*" "$JNI_DIR\x86_64\"
        Write-Host "✓ Нативные библиотеки готовы" -ForegroundColor Green
    }
    Write-Host ""
} else {
    Write-Host "✓ Нативные библиотеки уже существуют" -ForegroundColor Green
    Write-Host ""
}

# =============================================================================
# Итого
# =============================================================================
Write-Host "=============================================" -ForegroundColor Blue
Write-Host "✓ Скачивание завершено!" -ForegroundColor Green
Write-Host ""

# Проверяем размеры
if (Test-Path $ASSETS_DIR) {
    $assetsSize = (Get-ChildItem -Recurse $ASSETS_DIR | Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Host ("Модели: {0:N0} MB" -f $assetsSize)
}
if (Test-Path $JNI_DIR) {
    $jniSize = (Get-ChildItem -Recurse $JNI_DIR | Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Host ("Библиотеки: {0:N0} MB" -f $jniSize)
}

Write-Host ""
Write-Host "Теперь можно открыть проект в Android Studio" -ForegroundColor Yellow
Write-Host "=============================================" -ForegroundColor Blue
