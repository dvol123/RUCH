#!/bin/bash
# ================================================================
# Скрипт сборки Whisper.cpp для Android
# ================================================================
#
# Требования:
# - Android NDK установлен
# - Git установлен
# - CMake установлен
#
# Использование:
# chmod +x build_whisper_android.sh
# ./build_whisper_android.sh /path/to/android-ndk
#
# ================================================================

set -e

# Проверяем аргументы
if [ -z "$1" ]; then
    echo "Использование: $0 <путь_к_android_ndk>"
    echo "Пример: $0 \$HOME/Android/Sdk/ndk/25.2.9519653"
    exit 1
fi

ANDROID_NDK="$1"
BUILD_DIR="$(pwd)/whisper-android-build"
OUTPUT_DIR="$(pwd)/jniLibs"

# Проверяем NDK
if [ ! -d "$ANDROID_NDK" ]; then
    echo "Ошибка: NDK не найден по пути $ANDROID_NDK"
    exit 1
fi

echo "=== Сборка Whisper.cpp для Android ==="
echo "NDK: $ANDROID_NDK"
echo "Build dir: $BUILD_DIR"
echo "Output dir: $OUTPUT_DIR"

# Архитектуры для сборки
ARCHS=("arm64-v8a" "armeabi-v7a")

# Клонируем whisper.cpp если нет
if [ ! -d "whisper.cpp" ]; then
    echo "=== Клонирование whisper.cpp ==="
    git clone https://github.com/ggerganov/whisper.cpp
fi

cd whisper.cpp

# Для каждой архитектуры
for ARCH in "${ARCHS[@]}"; do
    echo ""
    echo "=== Сборка для $ARCH ==="
    
    BUILD_ARCH_DIR="$BUILD_DIR/$ARCH"
    mkdir -p "$BUILD_ARCH_DIR"
    cd "$BUILD_ARCH_DIR"
    
    # Настройка CMake
    cmake ../.. \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ARCH" \
        -DANDROID_PLATFORM=android-26 \
        -DANDROID_STL=c++_shared \
        -DCMAKE_BUILD_TYPE=Release \
        -DWHISPER_SUPPORT_OPENBLAS=OFF \
        -DWHISPER_SUPPORT_CUDA=OFF
    
    # Компиляция
    make -j$(nproc)
    
    # Копируем результат
    mkdir -p "$OUTPUT_DIR/$ARCH"
    cp libwhisper.so "$OUTPUT_DIR/$ARCH/"
    
    echo "Готово: $OUTPUT_DIR/$ARCH/libwhisper.so"
    
    cd - > /dev/null
done

echo ""
echo "=== Сборка завершена ==="
echo "Библиотеки находятся в: $OUTPUT_DIR"
echo ""
echo "Скопируйте содержимое $OUTPUT_DIR в app/src/main/jniLibs/"
