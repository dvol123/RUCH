# RUCH - Офлайн голосовой переводчик

![Android](https://img.shields.io/badge/Platform-Android%2014%2B-green)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue)
![License](https://img.shields.io/badge/License-MIT-orange)

**RUCH** — мобильное приложение для офлайн голосового перевода между русским и китайским языками.

## Возможности

- 🎤 **Голосовой ввод** — распознавание речи через Whisper (Sherpa-ONNX)
- 🔄 **Двусторонний перевод** — автоматический перевод в обоих направлениях
- 🔊 **Озвучивание** — синтез речи через VITS TTS (Sherpa-ONNX)
- 📱 **Офлайн-работа** — все функции работают без интернета
- 🌙 **Темы оформления** — светлая, тёмная и системная темы

## Технологии

| Компонент | Технология | Модель | Размер |
|-----------|------------|--------|--------|
| **STT** (распознавание речи) | Sherpa-ONNX + Whisper | whisper-small-int8 | ~128 MB |
| **MT** (перевод) | ONNX Runtime + NLLB-200 | nllb-200-distilled-600M-int8 | ~200 MB |
| **TTS** (синтез речи) | Sherpa-ONNX + VITS | ru_auto-medium + zh-hf-thera | ~60 MB |

## Требования

- Android 8.0 (API 26) или выше
- Минимум 4 ГБ оперативной памяти
- ~500 MB свободного места для моделей

## Установка

### 1. Клонирование репозитория

```bash
git clone https://github.com/dvol123/RUCH.git
cd RUCH
```

### 2. Скачивание AI моделей

**Windows PowerShell:**
```powershell
cd RUCH
.\scripts\download_ai_models.ps1
```

**Linux/macOS:**
```bash
cd RUCH
chmod +x scripts/download_ai_models.sh
./scripts/download_ai_models.sh
```

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
│   ├── ui/                    # UI компоненты
│   │   └── MainActivity.kt
│   ├── viewmodel/             # ViewModel
│   │   └── MainViewModel.kt
│   ├── stt/                   # Speech-to-Text
│   │   ├── WhisperSTT.kt      # Sherpa-ONNX Whisper
│   │   └── AndroidSpeechRecognizer.kt  # Fallback
│   ├── translation/           # Перевод
│   │   ├── NLLBTranslator.kt  # ONNX NLLB-200
│   │   ├── OfflineTranslator.kt  # Dictionary fallback
│   │   └── SentencePieceTokenizer.kt
│   ├── tts/                   # Text-to-Speech
│   │   ├── SherpaTTSEngine.kt # Sherpa-ONNX VITS
│   │   └── AndroidTTSEngine.kt  # Fallback
│   ├── audio/                 # Аудио компоненты
│   │   ├── AudioRecorder.kt
│   │   └── AudioPlayer.kt
│   └── data/                  # Данные и настройки
│       ├── Models.kt
│       ├── PreferencesManager.kt
│       └── ModelDownloadService.kt
├── assets/models/             # AI модели
│   ├── whisper/               # Whisper ONNX
│   │   ├── encoder-epoch-99-int8.onnx
│   │   ├── decoder-epoch-99-int8.onnx
│   │   └── tokens.txt
│   ├── nllb/                  # NLLB-200 ONNX
│   │   ├── encoder_int8.onnx
│   │   ├── decoder_int8.onnx
│   │   └── sentencepiece.model
│   └── tts/                   # TTS модели
│       ├── ru/                # Русский TTS
│       └── zh/                # Китайский TTS
└── res/                       # Ресурсы UI
```

## Зависимости

### AI/ML библиотеки

```kotlin
// Sherpa-ONNX - STT и TTS
implementation("com.k2fsa.sherpa:sherpa-onnx:1.10.15")

// ONNX Runtime - NLLB-200 перевод
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")
```

## Архитектура

```
┌────────────────────────────────────────────────────────┐
│                    Kotlin Layer                         │
│  ┌──────────┐  ┌─────────────┐  ┌──────────────────┐   │
│  │WhisperSTT│  │NLLBTranslator│  │SherpaTTSEngine  │   │
│  └────┬─────┘  └──────┬──────┘  └────────┬─────────┘   │
└───────┼───────────────┼──────────────────┼─────────────┘
        │               │                  │
┌───────▼───────────────▼──────────────────▼─────────────┐
│                 Native Libraries                        │
│  ┌──────────────────┐  ┌────────────────────────────┐  │
│  │ libsherpa-onnx   │  │    libonnxruntime.so       │  │
│  │ (Whisper + VITS) │  │    (NLLB-200 inference)    │  │
│  └────────┬─────────┘  └─────────────┬──────────────┘  │
└───────────┼──────────────────────────┼─────────────────┘
            │                          │
┌───────────▼──────────────────────────▼─────────────────┐
│                    AI Models                            │
│  ┌─────────┐  ┌─────────────┐  ┌──────────────────┐    │
│  │ Whisper │  │   NLLB-200  │  │    VITS TTS      │    │
│  │  ONNX   │  │    ONNX     │  │   RU + ZH ONNX   │    │
│  └─────────┘  └─────────────┘  └──────────────────┘    │
└────────────────────────────────────────────────────────┘
```

## Использование

1. Запустите приложение
2. При первом запуске разрешите доступ к микрофону
3. Нажмите на кнопку микрофона 🎤 для голосового ввода
4. Или используйте кнопку ✏️ для текстового ввода с переводом
5. Перевод появится автоматически в соседнем поле
6. Нажмите 🔊 для озвучивания

## Режимы работы

### Whisper STT (Офлайн)
- Автоматическое определение языка (русский/китайский)
- Высокое качество распознавания
- Инт8 квантизация для скорости

### NLLB-200 Перевод (Офлайн)
- Перевод между 200 языками
- Качество близкое к коммерческим системам
- Словарный fallback при отсутствии модели

### VITS TTS (Офлайн)
- Естественное звучание
- Поддержка русского и китайского
- Настраиваемая скорость речи

## Сборка APK

```bash
# Debug версия
./gradlew assembleDebug

# Release версия
./gradlew assembleRelease
```

APK будет в: `app/build/outputs/apk/`

## Известные ограничения

1. **Размер APK** - с моделями ~500 MB
2. **NLLB-200** - модель требует конвертации в ONNX (используется словарь)
3. **Память** - требуется минимум 4 GB RAM для плавной работы

## Разработка

### Добавление новых языков

1. Добавьте модель Whisper для нового языка
2. Добавьте модель VITS TTS
3. Обновите `Language.kt` enum
4. Обновите UI strings

### Конвертация NLLB-200 в ONNX

```bash
# Установите optimum
pip install optimum[onnx]

# Конвертируйте модель
optimum-cli export onnx --model facebook/nllb-200-distilled-600M \
    --task text2text-generation nllb_onnx/
```

## Лицензии компонентов

- [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) — Apache 2.0
- [ONNX Runtime](https://github.com/microsoft/onnxruntime) — MIT License
- [Whisper](https://github.com/openai/whisper) — MIT License
- [NLLB-200](https://github.com/facebookresearch/fairseq/tree/nllb) — MIT License

## Лицензия

MIT License
