#!/bin/bash
# Скачивание моделей Whisper для RUCH Translator
# Модели сохраняются в ruch_models/whisper/

set -e

WHISPER_DIR="ruch_models/whisper"

echo "=========================================="
echo "RUCH Translator - Скачивание моделей Whisper"
echo "=========================================="
echo ""
echo "Модели будут скачаны с HuggingFace:"
echo "  - encoder_model_int8.onnx (~92 MB)"
echo "  - decoder_model_int8.onnx (~156 MB)"
echo "  - tokenizer.json (~2.5 MB)"
echo ""
echo "Итого: ~250 MB"
echo ""

# Создаём директорию
mkdir -p "$WHISPER_DIR"

# Базовый URL
BASE_URL="https://huggingface.co/onnx-community/whisper-small/resolve/main"

# Скачиваем файлы
download_file() {
    local filename=$1
    local target="$WHISPER_DIR/$filename"
    local url="$BASE_URL/$filename"
    
    if [ -f "$target" ]; then
        local size=$(du -h "$target" | cut -f1)
        echo "✓ $filename уже существует ($size)"
    else
        echo "Скачиваю $filename..."
        curl -L --progress-bar -o "$target" "$url"
        local size=$(du -h "$target" | cut -f1)
        echo "✓ Скачано: $filename ($size)"
    fi
}

# Скачиваем encoder
download_file "onnx/encoder_model_int8.onnx"
mv "$WHISPER_DIR/onnx/encoder_model_int8.onnx" "$WHISPER_DIR/" 2>/dev/null || true

# Скачиваем decoder
download_file "onnx/decoder_model_int8.onnx"
mv "$WHISPER_DIR/onnx/decoder_model_int8.onnx" "$WHISPER_DIR/" 2>/dev/null || true

# Скачиваем tokenizer
download_file "tokenizer.json"

# Удаляем пустую директорию onnx если есть
rmdir "$WHISPER_DIR/onnx" 2>/dev/null || true

echo ""
echo "=========================================="
echo "Скачивание завершено!"
echo "=========================================="
echo ""
echo "Файлы в $WHISPER_DIR/:"
ls -lh "$WHISPER_DIR/"
echo ""
echo "Общий размер: $(du -sh "$WHISPER_DIR" | cut -f1)"
echo ""
echo "=========================================="
echo "ИНСТРУКЦИЯ:"
echo "=========================================="
echo ""
echo "1. Скопируйте папку ruch_models на телефон:"
echo "   adb push ruch_models /sdcard/Download/"
echo ""
echo "   Или вручную через USB/файловый менеджер"
echo ""
echo "2. Структура на телефоне должна быть:"
echo "   /sdcard/Download/ruch_models/whisper/"
echo "   ├── encoder_model_int8.onnx"
echo "   ├── decoder_model_int8.onnx"
echo "   └── tokenizer.json"
echo ""
echo "3. Запустите приложение RUCH"
echo "   При первом запуске модели будут скопированы"
echo "   в память приложения."
echo ""
