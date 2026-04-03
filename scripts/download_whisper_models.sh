#!/bin/bash
# Download Whisper ONNX models for RUCH Translator
# 5 файлов ~290 MB total

set -e

WHISPER_DIR="app/src/main/assets/models/whisper"

echo "=========================================="
echo "RUCH - Whisper ONNX Models Downloader"
echo "=========================================="

# Create directory
mkdir -p "$WHISPER_DIR"

echo ""
echo "Downloading Whisper models (~290 MB total)..."
echo ""

# Базовый URL - Hugging Face модель
BASE_URL="https://huggingface.co/onnx-community/whisper-small/resolve/main"

# Файлы для скачивания
declare -A FILES=(
    ["Whisper_initializer.onnx"]="onnx/Whisper_initializer.onnx"
    ["Whisper_encoder.onnx"]="onnx/Whisper_encoder.onnx"
    ["Whisper_decoder.onnx"]="onnx/Whisper_decoder.onnx"
    ["Whisper_cache_initializer.onnx"]="onnx/Whisper_cache_initializer.onnx"
    ["Whisper_detokenizer.onnx"]="onnx/Whisper_detokenizer.onnx"
)

for file in "${!FILES[@]}"; do
    target="$WHISPER_DIR/$file"
    url="$BASE_URL/${FILES[$file]}"
    
    if [ -f "$target" ]; then
        echo "✓ $file already exists ($(du -h "$target" | cut -f1))"
    else
        echo "Downloading $file..."
        curl -L -o "$target" "$url"
        echo "✓ Downloaded $file ($(du -h "$target" | cut -f1))"
    fi
done

echo ""
echo "=========================================="
echo "Download complete!"
echo "=========================================="
echo ""
echo "Models installed in: $WHISPER_DIR"
echo ""
ls -lh "$WHISPER_DIR"
echo ""
echo "Total size: $(du -sh "$WHISPER_DIR" | cut -f1)"
