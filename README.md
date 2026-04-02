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
| Распознавание речи | Whisper (whisper.cpp) |
| Машинный перевод | NLLB-200 + ONNX Runtime |
| Синтез речи | SherpaTTS |
| UI | Kotlin + Material Design |

## Требования

- Android 14 (API 34) или выше
- Минимум 6 ГБ оперативной памяти
- Минимум 3 ГБ свободного места
- 64-битный процессор (ARMv8+)

## Сборка проекта

### Предварительные требования

1. Android Studio (последняя стабильная версия)
2. JDK 17 или выше
3. Android SDK 34
4. NDK 25.x

### Шаги сборки

```bash
# Клонировать репозиторий
git clone https://github.com/dvol123/RUCH.git
cd RUCH

# Открыть проект в Android Studio
# или собрать через командную строку:
./gradlew assembleDebug
```

### Скачивание моделей

При первом запуске приложение предложит скачать языковые модели (~2 ГБ):
- Whisper Small (244 МБ) — для распознавания речи
- NLLB-200 Distilled (600 МБ) — для перевода
- TTS модели (~1 ГБ) — для синтеза речи

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
│   ├── cpp/                       # Native код (JNI)
│   └── res/                       # Ресурсы
└── build.gradle.kts
```

## Использование

1. Запустите приложение
2. При первом запуске разрешите доступ к микрофону
3. Дождитесь загрузки моделей
4. Нажмите на кнопку микрофона для голосового ввода
5. Или используйте кнопку карандаша для текстового ввода
6. Перевод появится автоматически в соседнем поле
7. Нажмите динамик для озвучивания

## Лицензии компонентов

- [Whisper](https://github.com/openai/whisper) — MIT License
- [NLLB-200](https://github.com/facebookresearch/fairseq/tree/nllb) — MIT License  
- [ONNX Runtime](https://github.com/microsoft/onnxruntime) — MIT License
- [SherpaTTS](https://github.com/k2-fsa/sherpa) — Apache 2.0

## Автор

Разработано по техническому заданию для проекта RUCH.

## Лицензия

MIT License
