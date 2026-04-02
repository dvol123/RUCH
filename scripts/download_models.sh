#!/bin/bash

# =============================================================================
# RUCH - Скрипт скачивания AI моделей и нативных библиотек
# =============================================================================
#
# Использование:
#   chmod +x scripts/download_models.sh
#   ./scripts/download_models.sh
#
# Требования:
#   - curl или wget
#   - tar (для распаковки)
#
# Размер скачивания: ~800 MB
# =============================================================================

set -e

# Цвета для вывода
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Определяем корневую папку проекта
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets/models"
JNI_DIR="$PROJECT_ROOT/app/src/main/jniLibs"

echo -e "${BLUE}=============================================${NC}"
echo -e "${BLUE}RUCH - Скачивание моделей и библиотек${NC}"
echo -e "${BLUE}=============================================${NC}"
echo ""

# Создаем структуру папок
echo -e "${YELLOW}Создание структуры папок...${NC}"
mkdir -p "$ASSETS_DIR"/{whisper,nllb,tts/ru,tts/zh}
mkdir -p "$JNI_DIR"/{arm64-v8a,armeabi-v7a,x86_64}
echo -e "${GREEN}✓ Папки созданы${NC}"
echo ""

# Функция скачивания
download_file() {
    local url="$1"
    local output="$2"
    local description="$3"

    echo -e "${YELLOW}Скачивание: $description${NC}"
    echo -e "  URL: $url"

    if command -v curl &> /dev/null; then
        curl -L -o "$output" "$url" --progress-bar
    elif command -v wget &> /dev/null; then
        wget -O "$output" "$url"
    else
        echo -e "${RED}Ошибка: требуется curl или wget${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ $description скачан${NC}"
    echo ""
}

# Функция скачивания и распаковки
download_and_extract() {
    local url="$1"
    local output_dir="$2"
    local description="$3"
    local tmp_file="/tmp/$(basename "$url")"

    echo -e "${YELLOW}Скачивание: $description${NC}"

    if command -v curl &> /dev/null; then
        curl -L -o "$tmp_file" "$url" --progress-bar
    elif command -v wget &> /dev/null; then
        wget -O "$tmp_file" "$url"
    else
        echo -e "${RED}Ошибка: требуется curl или wget${NC}"
        exit 1
    fi

    echo -e "${YELLOW}Распаковка...${NC}"
    tar -xf "$tmp_file" -C /tmp/

    echo -e "${GREEN}✓ $description готов${NC}"
    echo ""

    echo "$tmp_file"
}

# =============================================================================
# 1. Whisper GGML модель (для whisper.cpp)
# =============================================================================
echo -e "${BLUE}[1/5] Whisper модель (GGML)${NC}"
WHISPER_GGML="$ASSETS_DIR/ggml-small.bin"

if [ ! -f "$WHISPER_GGML" ]; then
    download_file \
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin" \
        "$WHISPER_GGML" \
        "Whisper Small GGML (466 MB)"
else
    echo -e "${GREEN}✓ Whisper GGML уже существует${NC}"
fi

# =============================================================================
# 2. Whisper ONNX модель (для sherpa-onnx)
# =============================================================================
echo -e "${BLUE}[2/5] Whisper ONNX модель${NC}"
WHISPER_ONNX_DIR="$ASSETS_DIR/whisper"

if [ ! -f "$WHISPER_ONNX_DIR/tiny-encoder.int8.onnx" ]; then
    TMP_FILE=$(download_and_extract \
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2" \
        "/tmp" \
        "Whisper Tiny ONNX (100 MB)")

    cp /tmp/sherpa-onnx-whisper-tiny/tiny-encoder.int8.onnx "$WHISPER_ONNX_DIR/"
    cp /tmp/sherpa-onnx-whisper-tiny/tiny-decoder.int8.onnx "$WHISPER_ONNX_DIR/"
    cp /tmp/sherpa-onnx-whisper-tiny/tiny-tokens.txt "$WHISPER_ONNX_DIR/"

    rm -rf /tmp/sherpa-onnx-whisper-tiny
else
    echo -e "${GREEN}✓ Whisper ONNX уже существует${NC}"
fi

# =============================================================================
# 3. TTS модели
# =============================================================================
echo -e "${BLUE}[3/5] TTS модели${NC}"

# Русский TTS
TTS_RU_DIR="$ASSETS_DIR/tts/ru"
if [ ! -f "$TTS_RU_DIR/ru_RU-irina-medium.onnx" ]; then
    TMP_FILE=$(download_and_extract \
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium.tar.bz2" \
        "/tmp" \
        "Русский TTS (61 MB)")

    cp -r /tmp/vits-piper-ru_RU-irina-medium/* "$TTS_RU_DIR/"
    rm -rf /tmp/vits-piper-ru_RU-irina-medium
else
    echo -e "${GREEN}✓ Русский TTS уже существует${NC}"
fi

# Китайский TTS
TTS_ZH_DIR="$ASSETS_DIR/tts/zh"
if [ ! -f "$TTS_ZH_DIR/zh_CN-huayan-medium.onnx" ]; then
    TMP_FILE=$(download_and_extract \
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-huayan-medium.tar.bz2" \
        "/tmp" \
        "Китайский TTS (61 MB)")

    cp -r /tmp/vits-piper-zh_CN-huayan-medium/* "$TTS_ZH_DIR/"
    rm -rf /tmp/vits-piper-zh_CN-huayan-medium
else
    echo -e "${GREEN}✓ Китайский TTS уже существует${NC}"
fi

# =============================================================================
# 4. Нативные библиотеки (.so файлы)
# =============================================================================
echo -e "${BLUE}[4/5] Нативные библиотеки${NC}"

if [ ! -f "$JNI_DIR/arm64-v8a/libsherpa-onnx-jni.so" ]; then
    TMP_FILE=$(download_and_extract \
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.10.40/sherpa-onnx-v1.10.40-android.tar.bz2" \
        "/tmp" \
        "Sherpa-ONNX Android библиотеки (33 MB)")

    cp -r /tmp/jniLibs/arm64-v8a/*.so "$JNI_DIR/arm64-v8a/"
    cp -r /tmp/jniLibs/armeabi-v7a/*.so "$JNI_DIR/armeabi-v7a/"
    cp -r /tmp/jniLibs/x86_64/*.so "$JNI_DIR/x86_64/"

    rm -rf /tmp/jniLibs
else
    echo -e "${GREEN}✓ Нативные библиотеки уже существуют${NC}"
fi

# =============================================================================
# 5. Проверка
# =============================================================================
echo -e "${BLUE}[5/5] Проверка установки${NC}"
echo ""

ERRORS=0

# Проверяем модели
check_file() {
    if [ -f "$1" ]; then
        SIZE=$(du -h "$1" | cut -f1)
        echo -e "  ${GREEN}✓${NC} $(basename "$1") ($SIZE)"
    else
        echo -e "  ${RED}✗${NC} $1 - не найден"
        ERRORS=$((ERRORS + 1))
    fi
}

echo "Модели:"
check_file "$ASSETS_DIR/ggml-small.bin"
check_file "$ASSETS_DIR/whisper/tiny-encoder.int8.onnx"
check_file "$ASSETS_DIR/whisper/tiny-decoder.int8.onnx"
check_file "$ASSETS_DIR/tts/ru/ru_RU-irina-medium.onnx"
check_file "$ASSETS_DIR/tts/zh/zh_CN-huayan-medium.onnx"
echo ""

echo "Нативные библиотеки (arm64-v8a):"
check_file "$JNI_DIR/arm64-v8a/libonnxruntime.so"
check_file "$JNI_DIR/arm64-v8a/libsherpa-onnx-jni.so"
echo ""

# =============================================================================
# Итого
# =============================================================================
echo -e "${BLUE}=============================================${NC}"
if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}✓ Все модели и библиотеки успешно скачаны!${NC}"
    echo ""
    echo "Размер:"
    du -sh "$ASSETS_DIR"
    du -sh "$JNI_DIR"
    echo ""
    echo -e "${YELLOW}Теперь можно открыть проект в Android Studio${NC}"
else
    echo -e "${RED}✗ Обнаружено ошибок: $ERRORS${NC}"
    echo "Пожалуйста, проверьте подключение к интернету и запустите скрипт снова"
fi
echo -e "${BLUE}=============================================${NC}"
