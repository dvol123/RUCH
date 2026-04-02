# Инструкция по Native-сборке для RUCH

## Обзор

Native-сборка позволяет компилировать C/C++ код в машинные инструкции для прямого выполнения процессором, что даёт значительное ускорение для задач распознавания речи и перевода.

## Быстрый старт (предсобранные библиотеки)

### 1. Скачать готовые .so файлы

**Whisper.cpp:**
```
https://github.com/ggerganov/whisper.cpp/releases
```
Скачать `libwhisper.so` для arm64-v8a

**Sherpa-ONNX (TTS):**
```
https://github.com/k2-fsa/sherpa-onnx/releases
```
Скачать .so файлы для Android

### 2. Скопировать в проект

```
app/src/main/jniLibs/
├── arm64-v8a/
│   ├── libwhisper.so
│   ├── libsherpa-jni.so
│   └── libc++_shared.so
└── armeabi-v7a/
    └── ...
```

### 3. Загрузить в коде

```kotlin
companion object {
    init {
        System.loadLibrary("c++_shared")  // Сначала STL
        System.loadLibrary("whisper")
        System.loadLibrary("sherpa-jni")
    }
}
```

---

## Полная сборка из исходников

### Требования

1. **Android Studio** с установленными компонентами:
   - NDK (Side by side) — версия 25.x или выше
   - CMake — версия 3.22.1
   - LLDB (опционально, для отладки)

2. **Установка через SDK Manager:**
   ```
   Android Studio → Settings → Appearance & Behavior → 
   System Settings → Android SDK → SDK Tools
   ```

### Сборка Whisper.cpp

#### Вариант 1: Автоматический скрипт

```bash
cd RUCH/scripts
chmod +x build_whisper_android.sh

# Указать путь к NDK
./build_whisper_android.sh ~/Android/Sdk/ndk/25.2.9519653

# Скопировать результат
cp -r jniLibs/* ../app/src/main/jniLibs/
```

#### Вариант 2: Ручная сборка

```bash
# 1. Клонировать репозиторий
git clone https://github.com/ggerganov/whisper.cpp
cd whisper.cpp

# 2. Создать директорию сборки
mkdir build-android && cd build-android

# 3. Настроить CMake (заменить путь к NDK)
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=$HOME/Android/Sdk/ndk/25.2.9519653/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_shared \
    -DCMAKE_BUILD_TYPE=Release

# 4. Компилировать
make -j$(nproc)

# 5. Скопировать libwhisper.so в проект
cp libwhisper.so /path/to/RUCH/app/src/main/jniLibs/arm64-v8a/
```

### Сборка Sherpa-ONNX (TTS)

```bash
# 1. Клонировать
git clone https://github.com/k2-fsa/sherpa-onnx
cd sherpa-onnx

# 2. Следовать инструкциям в 
# https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/index.html

# 3. Или скачать готовые библиотеки с Releases
```

---

## Интеграция с проектом

### Включить native-сборку в build.gradle.kts

Добавить в секцию `android`:

```kotlin
defaultConfig {
    ndk {
        abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
    externalNativeBuild {
        cmake {
            cppFlags += "-std=c++17"
            arguments += listOf(
                "-DANDROID_STL=c++_shared",
                "-DANDROID_ARM_MODE=arm"
            )
        }
    }
}

externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
```

### JNI интерфейс

**Kotlin:**
```kotlin
class SpeechRecognizerNative {
    companion object {
        init {
            System.loadLibrary("whisper-jni")
        }
    }
    
    private external fun initWhisper(modelPath: String): Boolean
    private external fun transcribe(audio: FloatArray, lang: String): String
}
```

**C++ (whisper-jni.cpp):**
```cpp
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ruch_translator_stt_SpeechRecognizerNative_initWhisper(
    JNIEnv* env, jobject thiz, jstring modelPath
) {
    // Инициализация whisper.cpp
    return true;
}
```

---

## Производительность

| Метод | Время распознавания 10 сек аудио |
|-------|----------------------------------|
| Kotlin (эмуляция) | ~20-30 сек |
| Native (whisper.cpp) | ~3-5 сек |

**Ускорение: 4-5x**

---

## Модели

### Whisper (распознавание речи)

| Модель | Размер | Качество |
|--------|--------|----------|
| tiny | 75 MB | Базовое |
| base | 142 MB | Хорошее |
| small | 466 MB | Отличное |
| medium | 1.5 GB | Очень хорошее |

Рекомендуется: **small** для мобильных устройств

Скачать: https://huggingface.co/ggerganov/whisper.cpp

### TTS (синтез речи)

Русский: vits-piper-ru_RU
Китайский: vits-piper-zh_CN

Скачать: https://github.com/k2-fsa/sherpa-onnx/releases

---

## Отладка

### Проверка загрузки библиотек

```bash
adb logcat | grep -E "(whisper|sherpa|JNI)"
```

### Проверка архитектуры устройства

```bash
adb shell getprop ro.product.cpu.abi
```

### Распространённые ошибки

1. **UnsatisfiedLinkError** — библиотека не найдена
   - Проверить наличие .so в jniLibs/<arch>/
   - Проверить соответствие архитектуры

2. **dlopen failed** — не найдена зависимость
   - Загрузить libc++_shared.so первой

3. **Native method not found** — сигнатура не совпадает
   - Проверить соответствие имён методов JNI
