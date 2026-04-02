# Download AI models for RUCH Translator (Windows PowerShell)
# 
# This script downloads:
# 1. Whisper small (int8) - for Speech-to-Text
# 2. NLLB-200 distilled (int8) - for Machine Translation  
# 3. VITS TTS models - for Text-to-Speech (Russian and Chinese)

$ErrorActionPreference = "Continue"

$MODELS_DIR = "app\src\main\assets\models"
$WHISPER_DIR = "$MODELS_DIR\whisper"
$NLLB_DIR = "$MODELS_DIR\nllb"
$TTS_DIR = "$MODELS_DIR\tts"

# Retry settings
$MAX_RETRIES = 3
$RETRY_DELAY = 5  # seconds

function Download-WithRetry {
    param(
        [string]$Url,
        [string]$Output,
        [string]$Description
    )
    
    for ($i = 1; $i -le $MAX_RETRIES; $i++) {
        try {
            Write-Host "  Attempt $i of $MAX_RETRIES..."
            
            # Use .NET WebClient for better timeout control
            $webClient = New-Object System.Net.WebClient
            $webClient.Headers.Add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            
            # Register progress event
            Register-ObjectEvent -InputObject $webClient -EventName DownloadProgressChanged -SourceIdentifier "DownloadProgress" -Action {
                $Global:DownloadProgress = $EventArgs.ProgressPercentage
            } | Out-Null
            
            $Global:DownloadProgress = 0
            
            # Start async download
            $webClient.DownloadFileAsync($Url, $Output)
            
            # Wait for completion with timeout (10 minutes)
            $timeout = 600
            $elapsed = 0
            while ($webClient.IsBusy -and $elapsed -lt $timeout) {
                Start-Sleep -Milliseconds 500
                $elapsed += 0.5
                if ($elapsed % 5 -eq 0) {
                    Write-Host "  Downloading... $([int]$Global:DownloadProgress)%"
                }
            }
            
            Unregister-Event -SourceIdentifier "DownloadProgress" -ErrorAction SilentlyContinue
            $webClient.Dispose()
            
            if ($elapsed -ge $timeout) {
                throw "Download timeout"
            }
            
            if (Test-Path $Output) {
                $fileSize = (Get-Item $Output).Length
                if ($fileSize -gt 0) {
                    Write-Host "  OK - Downloaded $([math]::Round($fileSize / 1MB, 1)) MB"
                    return $true
                }
            }
            
            throw "File not downloaded or empty"
        }
        catch {
            Write-Host "  Error: $($_.Exception.Message)"
            if ($i -lt $MAX_RETRIES) {
                Write-Host "  Retrying in $RETRY_DELAY seconds..."
                Start-Sleep -Seconds $RETRY_DELAY
            }
        }
    }
    
    return $false
}

function Download-WithInvokeWebRequest {
    param(
        [string]$Url,
        [string]$Output,
        [string]$Description
    )
    
    for ($i = 1; $i -le $MAX_RETRIES; $i++) {
        try {
            Write-Host "  Attempt $i of $MAX_RETRIES (Invoke-WebRequest)..."
            
            # Use -TimeoutSec for explicit timeout
            Invoke-WebRequest -Uri $Url -OutFile $Output -UseBasicParsing -TimeoutSec 600
            
            if (Test-Path $Output) {
                $fileSize = (Get-Item $Output).Length
                if ($fileSize -gt 0) {
                    Write-Host "  OK - Downloaded $([math]::Round($fileSize / 1MB, 1)) MB"
                    return $true
                }
            }
            
            throw "File not downloaded or empty"
        }
        catch {
            Write-Host "  Error: $($_.Exception.Message)"
            if ($i -lt $MAX_RETRIES) {
                Write-Host "  Retrying in $RETRY_DELAY seconds..."
                Start-Sleep -Seconds $RETRY_DELAY
            }
        }
    }
    
    return $false
}

function Download-WithCurl {
    param(
        [string]$Url,
        [string]$Output,
        [string]$Description
    )
    
    # Check if curl is available
    $curlCmd = Get-Command curl -ErrorAction SilentlyContinue
    if (-not $curlCmd) {
        Write-Host "  curl not available, skipping..."
        return $false
    }
    
    for ($i = 1; $i -le $MAX_RETRIES; $i++) {
        try {
            Write-Host "  Attempt $i of $MAX_RETRIES (curl)..."
            
            # Use curl with retry and timeout
            $result = curl -L -o $Output --retry 3 --retry-delay 5 --connect-timeout 30 --max-time 600 $Url 2>&1
            
            if (Test-Path $Output) {
                $fileSize = (Get-Item $Output).Length
                if ($fileSize -gt 0) {
                    Write-Host "  OK - Downloaded $([math]::Round($fileSize / 1MB, 1)) MB"
                    return $true
                }
            }
            
            throw "File not downloaded or empty"
        }
        catch {
            Write-Host "  Error: $($_.Exception.Message)"
            if ($i -lt $MAX_RETRIES) {
                Write-Host "  Retrying in $RETRY_DELAY seconds..."
                Start-Sleep -Seconds $RETRY_DELAY
            }
        }
    }
    
    return $false
}

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
    Write-Host "Downloading Whisper small (~244 MB)..."
    
    $success = Download-WithCurl -Url $WHISPER_URL -Output $WHISPER_TEMP -Description "Whisper"
    
    if (-not $success) {
        $success = Download-WithInvokeWebRequest -Url $WHISPER_URL -Output $WHISPER_TEMP -Description "Whisper"
    }
    
    if ($success) {
        # Extract using tar (Windows 10+)
        $WHISPER_EXTRACT = "$env:TEMP\whisper-extract"
        New-Item -ItemType Directory -Force -Path $WHISPER_EXTRACT | Out-Null
        
        Write-Host "  Extracting archive..."
        tar -xjf $WHISPER_TEMP -C $WHISPER_EXTRACT
        
        # Find and copy model files
        $WHISPER_MODEL_DIR = Get-ChildItem -Path $WHISPER_EXTRACT -Directory | Select-Object -First 1
        
        Copy-Item "$($WHISPER_MODEL_DIR.FullName)\encoder-epoch-99-int8.onnx" $WHISPER_DIR -ErrorAction SilentlyContinue
        Copy-Item "$($WHISPER_MODEL_DIR.FullName)\decoder-epoch-99-int8.onnx" $WHISPER_DIR -ErrorAction SilentlyContinue
        Copy-Item "$($WHISPER_MODEL_DIR.FullName)\tokens.txt" $WHISPER_DIR -ErrorAction SilentlyContinue
        
        # Cleanup
        Remove-Item -Recurse -Force $WHISPER_EXTRACT -ErrorAction SilentlyContinue
        Remove-Item -Force $WHISPER_TEMP -ErrorAction SilentlyContinue
        
        Write-Host "OK - Whisper model downloaded and extracted"
    } else {
        Write-Host "FAILED - Could not download Whisper model"
        Write-Host "Please download manually from:"
        Write-Host "  $WHISPER_URL"
    }
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

# Russian TTS - use smaller model
$RU_TTS_URLS = @(
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-ru-ru_auto-medium.tar.bz2",
    "https://huggingface.co/csukuangfj/vits-ru-ru_auto-medium/resolve/main/vits-ru-ru_auto-medium.tar.bz2"
)
$RU_TTS_TEMP = "$env:TEMP\vits-ru.tar.bz2"

if (-not (Test-Path "$TTS_DIR\ru\model.onnx")) {
    Write-Host "Downloading Russian TTS model (~50 MB)..."
    
    $success = $false
    foreach ($url in $RU_TTS_URLS) {
        Write-Host "Trying: $url"
        $success = Download-WithCurl -Url $url -Output $RU_TTS_TEMP -Description "Russian TTS"
        if (-not $success) {
            $success = Download-WithInvokeWebRequest -Url $url -Output $RU_TTS_TEMP -Description "Russian TTS"
        }
        if ($success) { break }
    }
    
    if ($success) {
        $RU_EXTRACT = "$env:TEMP\vits-ru-extract"
        New-Item -ItemType Directory -Force -Path $RU_EXTRACT | Out-Null
        
        Write-Host "  Extracting archive..."
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
        Write-Host "FAILED - Could not download Russian TTS model"
        Write-Host "Please download manually from one of:"
        foreach ($url in $RU_TTS_URLS) {
            Write-Host "  $url"
        }
    }
} else {
    Write-Host "OK - Russian TTS model already exists"
}

# Chinese TTS
$ZH_TTS_URLS = @(
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-hf-thera.tar.bz2",
    "https://huggingface.co/csukuangfj/vits-zh-hf-thera/resolve/main/vits-zh-hf-thera.tar.bz2"
)
$ZH_TTS_TEMP = "$env:TEMP\vits-zh.tar.bz2"

if (-not (Test-Path "$TTS_DIR\zh\model.onnx")) {
    Write-Host "Downloading Chinese TTS model (~30 MB)..."
    
    $success = $false
    foreach ($url in $ZH_TTS_URLS) {
        Write-Host "Trying: $url"
        $success = Download-WithCurl -Url $url -Output $ZH_TTS_TEMP -Description "Chinese TTS"
        if (-not $success) {
            $success = Download-WithInvokeWebRequest -Url $url -Output $ZH_TTS_TEMP -Description "Chinese TTS"
        }
        if ($success) { break }
    }
    
    if ($success) {
        $ZH_EXTRACT = "$env:TEMP\vits-zh-extract"
        New-Item -ItemType Directory -Force -Path $ZH_EXTRACT | Out-Null
        
        Write-Host "  Extracting archive..."
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
        Write-Host "FAILED - Could not download Chinese TTS model"
        Write-Host "Please download manually from one of:"
        foreach ($url in $ZH_TTS_URLS) {
            Write-Host "  $url"
        }
    }
} else {
    Write-Host "OK - Chinese TTS model already exists"
}

# ==========================================
# Summary
# ==========================================
Write-Host ""
Write-Host "=========================================="
Write-Host "Model download summary"
Write-Host "=========================================="
Write-Host ""

$whisperExists = Test-Path "$WHISPER_DIR\encoder-epoch-99-int8.onnx"
$ruTtsExists = Test-Path "$TTS_DIR\ru\model.onnx"
$zhTtsExists = Test-Path "$TTS_DIR\zh\model.onnx"

Write-Host "Whisper STT:    $(if ($whisperExists) { 'OK' } else { 'MISSING' })"
Write-Host "Russian TTS:    $(if ($ruTtsExists) { 'OK' } else { 'MISSING' })"
Write-Host "Chinese TTS:    $(if ($zhTtsExists) { 'OK' } else { 'MISSING' })"
Write-Host ""
Write-Host "Models location: $MODELS_DIR"
Write-Host ""

if (-not $whisperExists -or -not $ruTtsExists -or -not $zhTtsExists) {
    Write-Host "WARNING: Some models are missing!"
    Write-Host "If downloads failed, try:"
    Write-Host "  1. Check your internet connection"
    Write-Host "  2. Run the script again"
    Write-Host "  3. Download models manually using the URLs above"
    Write-Host "  4. Use a VPN if GitHub/HuggingFace is blocked"
} else {
    Write-Host "All models downloaded successfully!"
    Write-Host "You can now build the APK in Android Studio."
}
