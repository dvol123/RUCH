# RUCH - Офлайн голосовой переводчик

![Android](https://img.shields.io/badge/Platform-Android%2014%2B-green)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue)
![License](https://img.shields.io/badge/License-MIT-orange)

**RUCH** — мобильное приложение для офлайн голосового перевода между русским и китайским языками.

## Возможности

- 🎤 **Голосовой ввод** — распознавание речи через Whisper (Sherpa-ONNX)
- 🔄 **Двусторонний перевод** — автоматический перевод в обоих направлениях
- 🔊 **Озвучивание** — синтез речи через VITS TTS
- 📱 **Офлайн-работа** — все функции работают без интернета
- 🌙 **Темы оформления** — светлая, тёмная и системная темы

## Технологии

| Компонент | Технология | Размер модели |
|-----------|------------|---------------|
| Распознавание речи (STT) | Whisper Tiny (ONNX int8) | ~100 MB |
| Альтернатива STT | Whisper Small (GGML) | ~466 MB |
| Машинный перевод | Словарь + NLLB-200 | ~300 MB |
| Синтез речи (TTS) | VITS Piper (RU + ZH) | ~122 MB |
| Инференс | ONNX Runtime + Sherpa-ONNX | - |

## Требования

- Android 8.0 (API 26) или выше
- Минимум 4 ГБ оперативной памяти
- ~800 MB свободного места для моделей

## Установка

### 1. Клонирование репозитория

```bash
git clone https://github.com/dvol123/RUCH.git
cd RUCH
```

### 2. Скачивание AI моделей и нативных библиотек

Модели и .so файлы не включены в репозиторий из-за большого размера. Скачайте их автоматически:

```bash
chmod +x scripts/download_models.sh
./scripts/download_models.sh
```

**Или вручную:**

<details>
<summary>📥 Инструкция по ручному скачиванию</summary>

#### Whisper модель (GGML)
```bash
mkdir -p app/src/main/assets/models
curl -L -o app/src/main/assets/models/ggml-small.bin \
  "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
```

#### Whisper ONNX (для Sherpa-ONNX)
```bash
curl -L -o whisper.tar.bz2 \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"
tar -xf whisper.tar.bz2
mkdir -p app/src/main/assets/models/whisper
cp sherpa-onnx-whisper-tiny/tiny-*.onnx app/src/main/assets/models/whisper/
cp sherpa-onnx-whisper-tiny/tiny-tokens.txt app/src/main/assets/models/whisper/
```

#### TTS Русский (Irina)
```bash
curl -L -o tts-ru.tar.bz2 \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-irina-medium.tar.bz2"
tar -xf tts-ru.tar.bz2
mkdir -p app/src/main/assets/models/tts/ru
cp -r vits-piper-ru_RU-irina-medium/* app/src/main/assets/models/tts/ru/
```

#### TTS Китайский (Huayan)
```bash
curl -L -o tts-zh.tar.bz2 \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-zh_CN-huayan-medium.tar.bz2"
tar -xf tts-zh.tar.bz2
mkdir -p app/src/main/assets/models/tts/zh
cp -r vits-piper-zh_CN-huayan-medium/* app/src/main/assets/models/tts/zh/
```

#### Нативные библиотеки (.so)
```bash
curl -L -o sherpa-android.tar.bz2 \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.10.40/sherpa-onnx-v1.10.40-android.tar.bz2"
tar -xf sherpa-android.tar.bz2
mkdir -p app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}
cp jniLibs/arm64-v8a/*.so app/src/main/jniLibs/arm64-v8a/
cp jniLibs/armeabi-v7a/*.so app/src/main/jniLibs/armeabi-v7a/
cp jniLibs/x86_64/*.so app/src/main/jniLibs/x86_64/
```

</details>

### 3. Открытие в Android Studio

1. Откройте Android Studio
2. Выберите "Open an Existing Project"
3. Укажите папку RUCH
4. Дождитесь синхронизации Gradle
5. Запустите на устройстве или эмуляторе

## Структура проекта

```
app/src/main/
├── java/com/ruch/translator/
│   ├── ui/                    # UI компоненты (MainActivity)
│   ├── viewmodel/             # ViewModel (MainViewModel)
│   ├── stt/                   # Speech-to-Text (Whisper)
│   │   └── SherpaWhisperRecognizer.kt
│   ├── translation/           # Перевод
│   │   ├── OfflineTranslator.kt
│   │   └── SentencePieceTokenizer.kt
│   ├── tts/                   # Text-to-Speech (VITS)
│   │   └── SherpaTTSEngine.kt
│   ├── audio/                 # Аудио компоненты
│   │   ├── AudioRecorder.kt
│   │   └── AudioPlayer.kt
│   └── data/                  # Данные и настройки
│       ├── Models.kt
│       ├── PreferencesManager.kt
│       └── ModelDownloadService.kt
├── assets/models/             # AI модели (скачиваются отдельно)
│   ├── ggml-small.bin         # Whisper GGML
│   ├── whisper/               # Whisper ONNX
│   └── tts/ru/, tts/zh/       # TTS модели
├── jniLibs/                   # Нативные библиотеки
│   ├── arm64-v8a/             # 64-битные устройства
│   ├── armeabi-v7a/           # 32-битные устройства
│   └── x86_64/                # Эмуляторы
└── res/                       # Ресурсы UI
```

## Нативные библиотеки

Проект использует следующие .so библиотеки:

| Библиотека | Назначение | Размер |
|------------|------------|--------|
| libonnxruntime.so | ONNX Runtime для инференса | ~16 MB |
| libsherpa-onnx-jni.so | JNI мост для Java/Kotlin | ~4 MB |
| libsherpa-onnx-c-api.so | C API Sherpa-ONNX | ~4 MB |
| libsherpa-onnx-cxx-api.so | C++ API Sherpa-ONNX | ~44 KB |

## Использование

1. Запустите приложение
2. При первом запуске разрешите доступ к микрофону
3. Нажмите на кнопку микрофона 🎤 для голосового ввода
4. Или используйте кнопку ✏️ для текстового ввода
5. Перевод появится автоматически в соседнем поле
6. Нажмите 🔊 для озвучивания

## Архитектура нативного кода

```
┌─────────────────┐
│   Kotlin Code   │
│  (ViewModel)    │
└────────┬────────┘
         │ JNI
┌────────▼────────┐
│ libsherpa-onnx  │
│   -jni.so       │
└────────┬────────┘
         │
┌────────▼────────┐
│ libonnxruntime  │
│     .so         │
└────────┬────────┘
         │
┌────────▼────────┐
│  AI Models      │
│  (.onnx, .bin)  │
└─────────────────┘
```

## Сборка APK

```bash
# Debug версия
./gradlew assembleDebug

# Release версия (требует подписи)
./gradlew assembleRelease
```

APK будет в: `app/build/outputs/apk/`

## Известные ограничения

1. **Размер APK** - с моделями ~800 MB, рекомендуется скачивание при первом запуске
2. **Словарный перевод** - полноценный NLLB-200 добавит ~300 MB
3. **libc++_shared.so** - требуется для работы C++ библиотек (включена в NDK)

## Лицензии компонентов

- [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) — Apache 2.0
- [ONNX Runtime](https://github.com/microsoft/onnxruntime) — MIT License
- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp) — MIT License
- [Piper TTS](https://github.com/rhasspy/piper) — MIT License

## Автор

Разработано по техническому заданию для проекта RUCH.

## Лицензия

MIT License
