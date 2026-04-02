# ================================================================
# Конфигурация для Native-сборки (NDK)
# ================================================================
#
# Чтобы включить native-сборку, добавьте следующий код в 
# app/build.gradle.kts:
#
# android {
#     defaultConfig {
#         ndk {
#             abiFilters += listOf("arm64-v8a", "armeabi-v7a")
#         }
#         externalNativeBuild {
#             cmake {
#                 cppFlags += "-std=c++17"
#                 arguments += listOf(
#                     "-DANDROID_STL=c++_shared",
#                     "-DANDROID_ARM_MODE=arm"
#                 )
#             }
#         }
#     }
#     
#     externalNativeBuild {
#         cmake {
#             path = file("src/main/cpp/CMakeLists.txt")
#             version = "3.22.1"
#         }
#     }
#     
#     buildFeatures {
#         viewBinding = true
#         buildConfig = true
#     }
# }
#
# ================================================================
# Требования:
# ================================================================
#
# 1. Android NDK (установить через SDK Manager):
#    - Открыть Android Studio → SDK Manager → SDK Tools
#    - Установить: NDK (Side by side), CMake, LLDB
#
# 2. Версии:
#    - NDK: 25.x или выше
#    - CMake: 3.22.1
#    - minSdk: 26 (для C++17)
#
# ================================================================
# Альтернатива: использовать предсобранные библиотеки
# ================================================================
#
# Вместо компиляции можно скачать готовые .so файлы:
#
# 1. Whisper.cpp:
#    https://github.com/ggerganov/whisper.cpp/releases
#    Скачать libwhisper.so для arm64-v8a
#
# 2. Sherpa-ONNX:
#    https://github.com/k2-fsa/sherpa-onnx/releases
#    Скачать .so файлы для Android
#
# 3. Положить в:
#    app/src/main/jniLibs/arm64-v8a/libwhisper.so
#    app/src/main/jniLibs/arm64-v8a/libsherpa.so
#
# 4. В Kotlin загрузить:
#    System.loadLibrary("whisper")
#    System.loadLibrary("sherpa")
#
# ================================================================

# Пример скрипта для сборки Whisper.cpp для Android:

#!/bin/bash
# build_whisper_android.sh

# Установите путь к NDK
export ANDROID_NDK=/path/to/android-ndk

# Клонируем whisper.cpp
git clone https://github.com/ggerganov/whisper.cpp
cd whisper.cpp

# Создаём директорию для сборки
mkdir build-android && cd build-android

# Настраиваем CMake для Android
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release \
    -DWHISPER_SUPPORT_OPENBLAS=OFF

# Компилируем
make -j$(nproc)

# Результат:
# libwhisper.so -> скопировать в app/src/main/jniLibs/arm64-v8a/
