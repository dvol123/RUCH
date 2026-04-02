@echo off
REM Download AI models for RUCH Translator using curl
REM curl is more reliable for large downloads on Windows

setlocal enabledelayedexpansion

set MODELS_DIR=app\src\main\assets\models
set WHISPER_DIR=%MODELS_DIR%\whisper
set NLLB_DIR=%MODELS_DIR%\nllb
set TTS_DIR=%MODELS_DIR%\tts

echo ==========================================
echo RUCH Translator - Model Download (curl)
echo ==========================================
echo.

REM Create directories
if not exist "%WHISPER_DIR%" mkdir "%WHISPER_DIR%"
if not exist "%NLLB_DIR%" mkdir "%NLLB_DIR%"
if not exist "%TTS_DIR%\ru" mkdir "%TTS_DIR%\ru"
if not exist "%TTS_DIR%\zh" mkdir "%TTS_DIR%\zh"

REM Check curl
where curl >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: curl not found!
    echo Please install curl or use download_ai_models.ps1
    exit /b 1
)

REM ==========================================
REM 1. Download Whisper Model (STT)
REM ==========================================
echo [1/3] Downloading Whisper model for Speech-to-Text...

if exist "%WHISPER_DIR%\encoder-epoch-99-int8.onnx" (
    echo OK - Whisper model already exists
) else (
    echo Downloading Whisper small ~244 MB...
    
    curl -L -o "%TEMP%\whisper-small.tar.bz2" ^
        --retry 5 --retry-delay 10 ^
        --connect-timeout 60 --max-time 1800 ^
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2"
    
    if exist "%TEMP%\whisper-small.tar.bz2" (
        echo Extracting...
        mkdir "%TEMP%\whisper-extract" 2>nul
        tar -xjf "%TEMP%\whisper-small.tar.bz2" -C "%TEMP%\whisper-extract"
        
        for /d %%d in ("%TEMP%\whisper-extract\*") do (
            copy "%%d\encoder-epoch-99-int8.onnx" "%WHISPER_DIR%\" >nul 2>&1
            copy "%%d\decoder-epoch-99-int8.onnx" "%WHISPER_DIR%\" >nul 2>&1
            copy "%%d\tokens.txt" "%WHISPER_DIR%\" >nul 2>&1
        )
        
        rmdir /s /q "%TEMP%\whisper-extract" 2>nul
        del "%TEMP%\whisper-small.tar.bz2" 2>nul
        
        echo OK - Whisper model downloaded
    ) else (
        echo FAILED - Could not download Whisper model
    )
)

REM ==========================================
REM 2. NLLB-200 placeholder
REM ==========================================
echo.
echo [2/3] NLLB-200 model - using dictionary fallback

if not exist "%NLLB_DIR%\vocab.json" (
    echo {} > "%NLLB_DIR%\vocab.json"
)

REM ==========================================
REM 3. Download TTS Models
REM ==========================================
echo.
echo [3/3] Downloading TTS models for Text-to-Speech...

REM Russian TTS
if exist "%TTS_DIR%\ru\model.onnx" (
    echo OK - Russian TTS model already exists
) else (
    echo Downloading Russian TTS model ~50 MB...
    
    curl -L -o "%TEMP%\vits-ru.tar.bz2" ^
        --retry 5 --retry-delay 10 ^
        --connect-timeout 60 --max-time 1200 ^
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-ru-ru_auto-medium.tar.bz2"
    
    if exist "%TEMP%\vits-ru.tar.bz2" (
        echo Extracting...
        mkdir "%TEMP%\vits-ru-extract" 2>nul
        tar -xjf "%TEMP%\vits-ru.tar.bz2" -C "%TEMP%\vits-ru-extract"
        
        for /d %%d in ("%TEMP%\vits-ru-extract\*") do (
            for %%f in ("%%d\*.onnx") do (
                copy "%%f" "%TTS_DIR%\ru\model.onnx" >nul 2>&1
            )
            copy "%%d\tokens.txt" "%TTS_DIR%\ru\" >nul 2>&1
        )
        
        rmdir /s /q "%TEMP%\vits-ru-extract" 2>nul
        del "%TEMP%\vits-ru.tar.bz2" 2>nul
        
        echo OK - Russian TTS model downloaded
    ) else (
        echo FAILED - Could not download Russian TTS model
    )
)

REM Chinese TTS
if exist "%TTS_DIR%\zh\model.onnx" (
    echo OK - Chinese TTS model already exists
) else (
    echo Downloading Chinese TTS model ~30 MB...
    
    curl -L -o "%TEMP%\vits-zh.tar.bz2" ^
        --retry 5 --retry-delay 10 ^
        --connect-timeout 60 --max-time 1200 ^
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-hf-thera.tar.bz2"
    
    if exist "%TEMP%\vits-zh.tar.bz2" (
        echo Extracting...
        mkdir "%TEMP%\vits-zh-extract" 2>nul
        tar -xjf "%TEMP%\vits-zh.tar.bz2" -C "%TEMP%\vits-zh-extract"
        
        for /d %%d in ("%TEMP%\vits-zh-extract\*") do (
            for %%f in ("%%d\*.onnx") do (
                copy "%%f" "%TTS_DIR%\zh\model.onnx" >nul 2>&1
            )
            copy "%%d\tokens.txt" "%TTS_DIR%\zh\" >nul 2>&1
            if exist "%%d\dict" xcopy /e /i "%%d\dict" "%TTS_DIR%\zh\dict" >nul 2>&1
        )
        
        rmdir /s /q "%TEMP%\vits-zh-extract" 2>nul
        del "%TEMP%\vits-zh.tar.bz2" 2>nul
        
        echo OK - Chinese TTS model downloaded
    ) else (
        echo FAILED - Could not download Chinese TTS model
    )
)

REM ==========================================
REM Summary
REM ==========================================
echo.
echo ==========================================
echo Model download summary
echo ==========================================
echo.

if exist "%WHISPER_DIR%\encoder-epoch-99-int8.onnx" (
    echo Whisper STT:    OK
) else (
    echo Whisper STT:    MISSING
)

if exist "%TTS_DIR%\ru\model.onnx" (
    echo Russian TTS:    OK
) else (
    echo Russian TTS:    MISSING
)

if exist "%TTS_DIR%\zh\model.onnx" (
    echo Chinese TTS:    OK
) else (
    echo Chinese TTS:    MISSING
)

echo.
echo Models location: %MODELS_DIR%
echo.
echo If some models failed to download:
echo   1. Check your internet connection
echo   2. Run this script again
echo   3. Try using a VPN
echo.

pause
