cmake_minimum_required(VERSION 3.22.1)
project("ruch_translator")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ============================================================
# Whisper.cpp JNI Library
# ============================================================

# Добавляем whisper.cpp исходники (если есть)
set(WHISPER_SOURCES
    whisper/whisper.cpp
    whisper/ggml.c
)

# Создаём библиотеку whisper-jni
add_library(whisper-jni SHARED
    whisper-jni.cpp
)

# Если whisper.cpp исходники есть, компилируем их вместе
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/whisper/whisper.cpp")
    target_sources(whisper-jni PRIVATE ${WHISPER_SOURCES})
endif()

# ============================================================
# Sherpa-TTS JNI Library  
# ============================================================

add_library(sherpa-jni SHARED
    sherpa-jni.cpp
)

# ============================================================
# ONNX Runtime (предсобранная библиотека)
# ============================================================

# ONNX Runtime поставляется через Gradle зависимость
# onnxruntime-android уже содержит .so файлы

# ============================================================
# Общие настройки
# ============================================================

# Линковка с системными библиотеками
target_link_libraries(whisper-jni
    android
    log
    c++_shared
)

target_link_libraries(sherpa-jni
    android
    log
    c++_shared
    m  # математическая библиотека
)

# Оптимизации
target_compile_options(whisper-jni PRIVATE -O3 -Wall)
target_compile_options(sherpa-jni PRIVATE -O3 -Wall)

# ============================================================
# Если используем предсобранные библиотеки
# ============================================================

# Альтернативно, можно использовать предсобранные .so файлы:
# 1. Скачать libwhisper.so с https://github.com/ggerganov/whisper.cpp/releases
# 2. Положить в app/src/main/jniLibs/arm64-v8a/
# 3. JNI обёртка будет загружать System.loadLibrary("whisper")
