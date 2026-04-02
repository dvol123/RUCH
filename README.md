# RUCH - Офлайн голосовой переводчик

![Android](https://img.shields.io/badge/Platform-Android%2014%2B-green)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue)
![License](https://img.shields.io/badge/License-MIT-orange)

**RUCH** — мобильное приложение для офлайн голосового перевода между русским и китайским языками.

## Возможности

- 🎤 **Голосовой ввод** — распознавание речи на русском и китайском языках
- 🔄 **Двусторонний перевод** — автоматический перевод в обоих направлениях
- 🔊 **Озвучивание** — синтез речи на обоих языках
- 📱 **Офлайн-работа** — все функции работают без интернета
- 🌙 **Темы оформления** — светлая, тёмная и системная темы

## Технологии

| Компонент | Технология |
|-----------|------------|
| Распознавание речи | Android SpeechRecognizer API |
| Машинный перевод | NLLB-200 + ONNX Runtime |
| Синтез речи | Android TextToSpeech API |
| UI | Kotlin + Material Design |

## Требования

- Android 8.0 (API 26) или выше
- Минимум 4 ГБ оперативной памяти
- Минимум 1 ГБ свободного места

## Сборка проекта

### Предварительные требования

1. Android Studio (последняя стабильная версия)
2. JDK 17 или выше
3. Android SDK 34

### Шаги сборки

```bash
# Клонировать репозиторий
git clone https://github.com/dvol123/RUCH.git
cd RUCH

# Открыть проект в Android Studio
# или собрать через командную строку:
./gradlew assembleDebug
```

## Структура проекта

```
app/
├── src/main/
│   ├── java/com/ruch/translator/
│   │   ├── ui/                    # UI компоненты
│   │   ├── viewmodel/             # ViewModel
│   │   ├── stt/                   # Speech-to-Text
│   │   ├── translation/           # Перевод
│   │   ├── tts/                   # Text-to-Speech
│   │   └── data/                  # Данные и сервисы
│   └── res/                       # Ресурсы
└── build.gradle.kts
```

## Использование

1. Запустите приложение
2. При первом запуске разрешите доступ к микрофону
3. Нажмите на кнопку микрофона для голосового ввода
4. Или используйте кнопку карандаша для текстового ввода
5. Перевод появится автоматически в соседнем поле
6. Нажмите динамик для озвучивания

## Добавление Whisper и SherpaTTS (опционально)

Для использования продвинутых офлайн-моделей:

1. Добавьте native библиотеки whisper.cpp и sherpa в `app/src/main/jniLibs/`
2. Раскомментируйте externalNativeBuild в `app/build.gradle.kts`
3. Добавьте CMakeLists.txt для сборки native кода
4. Обновите SpeechRecognizer и TTSEngine для использования native методов

## Лицензии компонентов

- [ONNX Runtime](https://github.com/microsoft/onnxruntime) — MIT License

## Автор

Разработано по техническому заданию для проекта RUCH.

## Лицензия

MIT License
