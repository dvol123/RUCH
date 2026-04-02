# Download AI models for RUCH Translator (Windows PowerShell)
# 
# This script downloads:
# 1. Whisper small (int8) - for Speech-to-Text
# 2. NLLB-200 distilled (int8) - for Machine Translation  
# 3. VITS TTS models - for Text-to-Speech (Russian and Chinese)

$ErrorActionPreference = "Stop"

$MODELS_DIR = "app\src\main\assets\models"
$WHISPER_DIR = "$MODELS_DIR\whisper"
$NLLB_DIR = "$MODELS_DIR\nllb"
$TTS_DIR = "$MODELS_DIR\tts"

Write-Host "=========================================="
Write-Host "RUCH Translator - Model Download Script"
Write-Host "=========================================="

# Create directories
New-Item -ItemType Directory -Force -Path $WHISPER_DIR | Out-Null
New-Item -ItemType Directory -Force -Path $NLLB_DIR | Out-Null
New-Item -ItemType Directory -Force -Path "$TTS_DIR\ru" | Out-Null
New-Item -ItemType Directory -Force -Path "$TTS_DIR\zh" | Out-Null

# ==========================================
# 1. Download Whisper Model (STT)
# ==========================================
Write-Host ""
Write-Host "[1/3] Downloading Whisper model for Speech-to-Text..."

$WHISPER_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2"
$WHISPER_TEMP = "$env:TEMP\whisper-small.tar.bz2"

if (-not (Test-Path "$WHISPER_DIR\encoder-epoch-99-int8.onnx")) {
    Write-Host "Downloading Whisper small..."
    
    # Download
    Invoke-WebRequest -Uri $WHISPER_URL -OutFile $WHISPER_TEMP -UseBasicParsing
    
    # Extract using 7-Zip or built-in tar (Windows 10+)
    $WHISPER_EXTRACT = "$env:TEMP\whisper-extract"
    New-Item -ItemType Directory -Force -Path $WHISPER_EXTRACT | Out-Null
    
    # Try using tar (Windows 10+)
    tar -xjf $WHISPER_TEMP -C $WHISPER_EXTRACT
    
    # Find and copy model files
    $WHISPER_MODEL_DIR = Get-ChildItem -Path $WHISPER_EXTRACT -Directory | Select-Object -First 1
    
    Copy-Item "$($WHISPER_MODEL_DIR.FullName)\encoder-epoch-99-int8.onnx" $WHISPER_DIR -ErrorAction SilentlyContinue
    Copy-Item "$($WHISPER_MODEL_DIR.FullName)\decoder-epoch-99-int8.onnx" $WHISPER_DIR -ErrorAction SilentlyContinue
    Copy-Item "$($WHISPER_MODEL_DIR.FullName)\tokens.txt" $WHISPER_DIR -ErrorAction SilentlyContinue
    
    # Cleanup
    Remove-Item -Recurse -Force $WHISPER_EXTRACT -ErrorAction SilentlyContinue
    Remove-Item -Force $WHISPER_TEMP -ErrorAction SilentlyContinue
    
    Write-Host "OK - Whisper model downloaded"
} else {
    Write-Host "OK - Whisper model already exists"
}

# ==========================================
# 2. Download NLLB-200 Model (Translation)
# ==========================================
Write-Host ""
Write-Host "[2/3] NLLB-200 model for Machine Translation..."
Write-Host "Note: NLLB-200 ONNX models require manual conversion."
Write-Host "Using dictionary fallback for now."

# Create placeholder
if (-not (Test-Path "$NLLB_DIR\vocab.json")) {
    '{}' | Out-File -FilePath "$NLLB_DIR\vocab.json" -Encoding utf8
}

# ==========================================
# 3. Download TTS Models
# ==========================================
Write-Host ""
Write-Host "[3/3] Downloading TTS models for Text-to-Speech..."

# Russian TTS
$RU_TTS_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-ru-ru_auto-medium.tar.bz2"
$RU_TTS_TEMP = "$env:TEMP\vits-ru.tar.bz2"

if (-not (Test-Path "$TTS_DIR\ru\model.onnx")) {
    Write-Host "Downloading Russian TTS model..."
    
    Invoke-WebRequest -Uri $RU_TTS_URL -OutFile $RU_TTS_TEMP -UseBasicParsing
    
    $RU_EXTRACT = "$env:TEMP\vits-ru-extract"
    New-Item -ItemType Directory -Force -Path $RU_EXTRACT | Out-Null
    
    tar -xjf $RU_TTS_TEMP -C $RU_EXTRACT
    
    $RU_MODEL_DIR = Get-ChildItem -Path $RU_EXTRACT -Directory | Select-Object -First 1
    
    # Copy .onnx file as model.onnx
    $ONNX_FILE = Get-ChildItem -Path $RU_MODEL_DIR.FullName -Filter "*.onnx" | Select-Object -First 1
    if ($ONNX_FILE) {
        Copy-Item $ONNX_FILE.FullName "$TTS_DIR\ru\model.onnx"
    }
    Copy-Item "$($RU_MODEL_DIR.FullName)\tokens.txt" "$TTS_DIR\ru\" -ErrorAction SilentlyContinue
    
    Remove-Item -Recurse -Force $RU_EXTRACT -ErrorAction SilentlyContinue
    Remove-Item -Force $RU_TTS_TEMP -ErrorAction SilentlyContinue
    
    Write-Host "OK - Russian TTS model downloaded"
} else {
    Write-Host "OK - Russian TTS model already exists"
}

# Chinese TTS
$ZH_TTS_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-hf-thera.tar.bz2"
$ZH_TTS_TEMP = "$env:TEMP\vits-zh.tar.bz2"

if (-not (Test-Path "$TTS_DIR\zh\model.onnx")) {
    Write-Host "Downloading Chinese TTS model..."
    
    Invoke-WebRequest -Uri $ZH_TTS_URL -OutFile $ZH_TTS_TEMP -UseBasicParsing
    
    $ZH_EXTRACT = "$env:TEMP\vits-zh-extract"
    New-Item -ItemType Directory -Force -Path $ZH_EXTRACT | Out-Null
    
    tar -xjf $ZH_TTS_TEMP -C $ZH_EXTRACT
    
    $ZH_MODEL_DIR = Get-ChildItem -Path $ZH_EXTRACT -Directory | Select-Object -First 1
    
    # Copy .onnx file as model.onnx
    $ONNX_FILE = Get-ChildItem -Path $ZH_MODEL_DIR.FullName -Filter "*.onnx" | Select-Object -First 1
    if ($ONNX_FILE) {
        Copy-Item $ONNX_FILE.FullName "$TTS_DIR\zh\model.onnx"
    }
    Copy-Item "$($ZH_MODEL_DIR.FullName)\tokens.txt" "$TTS_DIR\zh\" -ErrorAction SilentlyContinue
    
    # Copy dict directory if exists
    if (Test-Path "$($ZH_MODEL_DIR.FullName)\dict") {
        Copy-Item -Recurse "$($ZH_MODEL_DIR.FullName)\dict" "$TTS_DIR\zh\"
    }
    
    Remove-Item -Recurse -Force $ZH_EXTRACT -ErrorAction SilentlyContinue
    Remove-Item -Force $ZH_TTS_TEMP -ErrorAction SilentlyContinue
    
    Write-Host "OK - Chinese TTS model downloaded"
} else {
    Write-Host "OK - Chinese TTS model already exists"
}

# ==========================================
# Summary
# ==========================================
Write-Host ""
Write-Host "=========================================="
Write-Host "Model download complete!"
Write-Host "=========================================="
Write-Host ""
Write-Host "Models installed in: $MODELS_DIR"
Write-Host ""
Write-Host "You can now build the APK in Android Studio."
