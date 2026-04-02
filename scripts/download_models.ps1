# RUCH - Download AI Models and Native Libraries for Windows
# Run: .\scripts\download_models.ps1

$ErrorActionPreference = "Stop"

# Detect correct project root (handle both RUCH and RUCH_git structures)
$PROJECT_ROOT = $PSScriptRoot
while ($PROJECT_ROOT -and -not (Test-Path (Join-Path $PROJECT_ROOT "app\build.gradle.kts"))) {
    $PROJECT_ROOT = Split-Path -Parent $PROJECT_ROOT
}
if (-not $PROJECT_ROOT) {
    $PROJECT_ROOT = Split-Path -Parent $PSScriptRoot
}

$ASSETS_DIR = Join-Path $PROJECT_ROOT "app\src\main\assets\models"
$JNI_DIR = Join-Path $PROJECT_ROOT "app\src\main\jniLibs"

Write-Host "=============================================" -ForegroundColor Blue
Write-Host "RUCH - Downloading models and libraries" -ForegroundColor Blue
Write-Host "=============================================" -ForegroundColor Blue
Write-Host "Project root: $PROJECT_ROOT" -ForegroundColor Gray
Write-Host ""

# Create directories
Write-Host "Creating directories..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path "$ASSETS_DIR\whisper" | Out-Null
New-Item -ItemType Directory -Force -Path "$ASSETS_DIR\nllb" | Out-Null
New-Item -ItemType Directory -Force -Path "$ASSETS_DIR\tts\ru" | Out-Null
New-Item -ItemType Directory -Force -Path "$ASSETS_DIR\tts\zh" | Out-Null
New-Item -ItemType Directory -Force -Path "$JNI_DIR\arm64-v8a" | Out-Null
New-Item -ItemType Directory -Force -Path "$JNI_DIR\armeabi-v7a" | Out-Null
New-Item -ItemType Directory -Force -Path "$JNI_DIR\x86_64" | Out-Null
Write-Host "[OK] Directories created" -ForegroundColor Green
Write-Host ""

function Download-File {
    param($Url, $Output, $Description)
    
    Write-Host "Downloading: $Description" -ForegroundColor Yellow
    Write-Host "  URL: $Url"
    
    try {
        if (Test-Path $Output) { Remove-Item $Output -Force }
        $webClient = New-Object System.Net.WebClient
        $webClient.DownloadFile($Url, $Output)
        Write-Host "[OK] $Description downloaded" -ForegroundColor Green
        Write-Host ""
        return $true
    }
    catch {
        Write-Host "[ERROR] Download failed: $_" -ForegroundColor Red
        return $false
    }
}

# 1. Whisper GGML
Write-Host "[1/4] Whisper GGML model" -ForegroundColor Blue
$WHISPER_GGML = Join-Path $ASSETS_DIR "ggml-small.bin"

if (-not (Test-Path $WHISPER_GGML)) {
    Download-File "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin" $WHISPER_GGML "Whisper Small GGML - 466 MB"
} else {
    Write-Host "[OK] Whisper GGML already exists" -ForegroundColor Green
    Write-Host ""
}

# 2. Whisper ONNX
Write-Host "[2/4] Whisper ONNX model" -ForegroundColor Blue
$WHISPER_DIR = Join-Path $ASSETS_DIR "whisper"
$ENCODER = Join-Path $WHISPER_DIR "tiny-encoder.int8.onnx"

if (-not (Test-Path $ENCODER)) {
    $TMP_FILE = Join-Path $env:TEMP "whisper-tiny.tar.bz2"
    
    Download-File "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2" $TMP_FILE "Whisper Tiny ONNX - 100 MB"
    
    Write-Host "Extracting..." -ForegroundColor Yellow
    
    if (Get-Command tar -ErrorAction SilentlyContinue) {
        tar -xf $TMP_FILE -C $env:TEMP
        Copy-Item "$env:TEMP\sherpa-onnx-whisper-tiny\tiny-encoder.int8.onnx" $WHISPER_DIR
        Copy-Item "$env:TEMP\sherpa-onnx-whisper-tiny\tiny-decoder.int8.onnx" $WHISPER_DIR
        Copy-Item "$env:TEMP\sherpa-onnx-whisper-tiny\tiny-tokens.txt" $WHISPER_DIR
        Write-Host "[OK] Whisper ONNX ready" -ForegroundColor Green
    } else {
        Write-Host "[WARN] Install 7-Zip or use WSL to extract .tar.bz2" -ForegroundColor Yellow
    }
    Write-Host ""
} else {
    Write-Host "[OK] Whisper ONNX already exists" -ForegroundColor Green
    Write-Host ""
}

# 3. TTS models
Write-Host "[3/4] TTS models" -ForegroundColor Blue

# Russian TTS
$TTS_RU = Join-Path $ASSETS_DIR "tts\ru\ru_RU-irina-medium.onnx"
if (-not (Test-Path $TTS_RU)) {
    $TMP_FILE = Join-Path $env:TEMP "tts-ru.tar.bz2"
    
    Download-File "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium.tar.bz2" $TMP_FILE "Russian TTS - 61 MB"
    
    if (Get-Command tar -ErrorAction SilentlyContinue) {
        tar -xf $TMP_FILE -C $env:TEMP
        Copy-Item -Recurse "$env:TEMP\vits-piper-ru_RU-irina-medium\*" "$ASSETS_DIR\tts\ru\"
        Write-Host "[OK] Russian TTS ready" -ForegroundColor Green
    }
    Write-Host ""
} else {
    Write-Host "[OK] Russian TTS already exists" -ForegroundColor Green
    Write-Host ""
}

# Chinese TTS
$TTS_ZH = Join-Path $ASSETS_DIR "tts\zh\zh_CN-huayan-medium.onnx"
if (-not (Test-Path $TTS_ZH)) {
    $TMP_FILE = Join-Path $env:TEMP "tts-zh.tar.bz2"
    
    Download-File "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-huayan-medium.tar.bz2" $TMP_FILE "Chinese TTS - 61 MB"
    
    if (Get-Command tar -ErrorAction SilentlyContinue) {
        tar -xf $TMP_FILE -C $env:TEMP
        Copy-Item -Recurse "$env:TEMP\vits-piper-zh_CN-huayan-medium\*" "$ASSETS_DIR\tts\zh\"
        Write-Host "[OK] Chinese TTS ready" -ForegroundColor Green
    }
    Write-Host ""
} else {
    Write-Host "[OK] Chinese TTS already exists" -ForegroundColor Green
    Write-Host ""
}

# 4. Native libraries
Write-Host "[4/4] Native libraries (.so)" -ForegroundColor Blue
$JNI_ARM64 = Join-Path $JNI_DIR "arm64-v8a\libsherpa-onnx-jni.so"

if (-not (Test-Path $JNI_ARM64)) {
    $TMP_FILE = Join-Path $env:TEMP "sherpa-android.tar.bz2"
    
    Download-File "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.10.40/sherpa-onnx-v1.10.40-android.tar.bz2" $TMP_FILE "Sherpa-ONNX Android libs - 33 MB"
    
    if (Get-Command tar -ErrorAction SilentlyContinue) {
        tar -xf $TMP_FILE -C $env:TEMP
        Copy-Item "$env:TEMP\jniLibs\arm64-v8a\*" "$JNI_DIR\arm64-v8a\"
        Copy-Item "$env:TEMP\jniLibs\armeabi-v7a\*" "$JNI_DIR\armeabi-v7a\"
        Copy-Item "$env:TEMP\jniLibs\x86_64\*" "$JNI_DIR\x86_64\"
        Write-Host "[OK] Native libraries ready" -ForegroundColor Green
    }
    Write-Host ""
} else {
    Write-Host "[OK] Native libraries already exist" -ForegroundColor Green
    Write-Host ""
}

# Summary
Write-Host "=============================================" -ForegroundColor Blue
Write-Host "[DONE] Download complete!" -ForegroundColor Green
Write-Host ""

if (Test-Path $ASSETS_DIR) {
    $assetsSize = (Get-ChildItem -Recurse $ASSETS_DIR | Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Host "Models: $([math]::Round($assetsSize, 0)) MB"
}
if (Test-Path $JNI_DIR) {
    $jniSize = (Get-ChildItem -Recurse $JNI_DIR | Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Host "Libraries: $([math]::Round($jniSize, 0)) MB"
}

Write-Host ""
Write-Host "Models location: $ASSETS_DIR" -ForegroundColor Gray
Write-Host "Libraries location: $JNI_DIR" -ForegroundColor Gray
Write-Host ""
Write-Host "You can now open the project in Android Studio" -ForegroundColor Yellow
Write-Host "=============================================" -ForegroundColor Blue
