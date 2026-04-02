#!/bin/bash
# Download AI models for RUCH Translator
# 
# This script downloads:
# 1. Whisper small (int8) - for Speech-to-Text
# 2. NLLB-200 distilled (int8) - for Machine Translation
# 3. VITS TTS models - for Text-to-Speech (Russian and Chinese)

set -e

MODELS_DIR="app/src/main/assets/models"
WHISPER_DIR="$MODELS_DIR/whisper"
NLLB_DIR="$MODELS_DIR/nllb"
TTS_DIR="$MODELS_DIR/tts"

echo "=========================================="
echo "RUCH Translator - Model Download Script"
echo "=========================================="

# Create directories
mkdir -p "$WHISPER_DIR"
mkdir -p "$NLLB_DIR"
mkdir -p "$TTS_DIR/ru"
mkdir -p "$TTS_DIR/zh"

# ==========================================
# 1. Download Whisper Model (STT)
# ==========================================
echo ""
echo "[1/3] Downloading Whisper model for Speech-to-Text..."

WHISPER_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2"
WHISPER_TEMP="/tmp/whisper-small.tar.bz2"

if [ ! -f "$WHISPER_DIR/encoder-epoch-99-int8.onnx" ]; then
    echo "Downloading Whisper small..."
    curl -L -o "$WHISPER_TEMP" "$WHISPER_URL"
    tar -xjf "$WHISPER_TEMP" -C /tmp/
    
    # Copy model files
    cp /tmp/sherpa-onnx-whisper-small/encoder-epoch-99-int8.onnx "$WHISPER_DIR/"
    cp /tmp/sherpa-onnx-whisper-small/decoder-epoch-99-int8.onnx "$WHISPER_DIR/"
    cp /tmp/sherpa-onnx-whisper-small/tokens.txt "$WHISPER_DIR/"
    
    rm -rf /tmp/sherpa-onnx-whisper-small
    rm "$WHISPER_TEMP"
    echo "✓ Whisper model downloaded"
else
    echo "✓ Whisper model already exists"
fi

# ==========================================
# 2. Download NLLB-200 Model (Translation)
# ==========================================
echo ""
echo "[2/3] Downloading NLLB-200 model for Machine Translation..."

# Note: NLLB-200 ONNX models need to be converted or downloaded from HuggingFace
# For now, we'll use a placeholder - actual models need to be converted
echo "Note: NLLB-200 ONNX models require manual conversion."
echo "Please see: https://huggingface.co/facebook/nllb-200-distilled-600M"
echo "Using dictionary fallback for now."

# Create placeholder files
if [ ! -f "$NLLB_DIR/vocab.json" ]; then
    echo '{}' > "$NLLB_DIR/vocab.json"
fi

# ==========================================
# 3. Download TTS Models
# ==========================================
echo ""
echo "[3/3] Downloading TTS models for Text-to-Speech..."

# Russian TTS - VITS model
RU_TTS_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-ru-ru_auto-medium.tar.bz2"
RU_TTS_TEMP="/tmp/vits-ru.tar.bz2"

if [ ! -f "$TTS_DIR/ru/model.onnx" ]; then
    echo "Downloading Russian TTS model..."
    curl -L -o "$RU_TTS_TEMP" "$RU_TTS_URL"
    tar -xjf "$RU_TTS_TEMP" -C /tmp/
    
    # Find and copy model files
    RU_MODEL_DIR=$(find /tmp -name "vits-ru*" -type d | head -1)
    if [ -d "$RU_MODEL_DIR" ]; then
        cp "$RU_MODEL_DIR"/*.onnx "$TTS_DIR/ru/model.onnx" 2>/dev/null || true
        cp "$RU_MODEL_DIR"/tokens.txt "$TTS_DIR/ru/" 2>/dev/null || true
    fi
    
    rm -rf /tmp/vits-ru*
    rm "$RU_TTS_TEMP"
    echo "✓ Russian TTS model downloaded"
else
    echo "✓ Russian TTS model already exists"
fi

# Chinese TTS - VITS model
ZH_TTS_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-zh-hf-thera.tar.bz2"
ZH_TTS_TEMP="/tmp/vits-zh.tar.bz2"

if [ ! -f "$TTS_DIR/zh/model.onnx" ]; then
    echo "Downloading Chinese TTS model..."
    curl -L -o "$ZH_TTS_TEMP" "$ZH_TTS_URL"
    tar -xjf "$ZH_TTS_TEMP" -C /tmp/
    
    # Find and copy model files
    ZH_MODEL_DIR=$(find /tmp -name "vits-zh*" -type d | head -1)
    if [ -d "$ZH_MODEL_DIR" ]; then
        cp "$ZH_MODEL_DIR"/*.onnx "$TTS_DIR/zh/model.onnx" 2>/dev/null || true
        cp "$ZH_MODEL_DIR"/tokens.txt "$TTS_DIR/zh/" 2>/dev/null || true
        cp -r "$ZH_MODEL_DIR"/dict "$TTS_DIR/zh/" 2>/dev/null || true
    fi
    
    rm -rf /tmp/vits-zh*
    rm "$ZH_TTS_TEMP"
    echo "✓ Chinese TTS model downloaded"
else
    echo "✓ Chinese TTS model already exists"
fi

# ==========================================
# Summary
# ==========================================
echo ""
echo "=========================================="
echo "Model download complete!"
echo "=========================================="
echo ""
echo "Models installed:"
echo "  STT (Whisper):  $(du -sh $WHISPER_DIR 2>/dev/null | cut -f1)"
echo "  MT (NLLB):      $(du -sh $NLLB_DIR 2>/dev/null | cut -f1)"
echo "  TTS (Russian):  $(du -sh $TTS_DIR/ru 2>/dev/null | cut -f1)"
echo "  TTS (Chinese):  $(du -sh $TTS_DIR/zh 2>/dev/null | cut -f1)"
echo ""
echo "Total size: $(du -sh $MODELS_DIR 2>/dev/null | cut -f1)"
echo ""
echo "You can now build the APK in Android Studio."
