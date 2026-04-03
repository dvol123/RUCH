# Модели Whisper для RUCH Translator

## 📥 Скачивание моделей

### Вариант 1: Скачать скриптом (Linux/Mac)

```bash
cd /path/to/RUCH
bash scripts/download_models.sh
```

### Вариант 2: Скачать вручную

Скачайте файлы с HuggingFace (ссылки проверены):

| Файл | Размер | Ссылка |
|------|--------|--------|
| encoder_model_int8.onnx | 92 MB | [Скачать](https://huggingface.co/onnx-community/whisper-small/resolve/main/onnx/encoder_model_int8.onnx) |
| decoder_model_int8.onnx | 156 MB | [Скачать](https://huggingface.co/onnx-community/whisper-small/resolve/main/onnx/decoder_model_int8.onnx) |
| tokenizer.json | 2.5 MB | [Скачать](https://huggingface.co/onnx-community/whisper-small/resolve/main/tokenizer.json) |

**Итого: ~250 MB**

---

## 📱 Установка на телефон

### Структура папок

После скачивания создайте следующую структуру на телефоне:

```
/sdcard/Download/ruch_models/
└── whisper/
    ├── encoder_model_int8.onnx
    ├── decoder_model_int8.onnx
    └── tokenizer.json
```

### Способ 1: Через ADB

```bash
# Скопировать папку на телефон
adb push ruch_models /sdcard/Download/
```

### Способ 2: Через USB

1. Подключите телефон к компьютеру
2. Откройте папку `Download` на телефоне
3. Создайте папку `ruch_models/whisper/`
4. Скопируйте 3 файла в эту папку

### Способ 3: Через файловый менеджер

1. Скачайте архив с моделями
2. Распакуйте в `/sdcard/Download/ruch_models/`

---

## ✅ Проверка

После запуска приложения:
1. Приложение проверит наличие моделей в `/sdcard/Download/ruch_models/whisper/`
2. Скопирует их в память приложения
3. Загрузит ONNX сессии

В логах должно появиться:
```
WhisperSTT: Models found:
WhisperSTT:   Encoder: 92 MB
WhisperSTT:   Decoder: 156 MB
WhisperSTT:   Tokenizer: 2500 KB
WhisperSTT: Tokenizer loaded, vocab size: 51865
WhisperSTT: === Whisper STT initialized successfully ===
```

---

## ⚠️ Возможные проблемы

### "Models not found"

- Проверьте правильность пути: `/sdcard/Download/ruch_models/whisper/`
- Проверьте имена файлов (без лишних расширений)
- На Android 13+ может потребоваться разрешение на доступ к файлам

### "Permission denied"

- Выдайте приложению разрешение на хранение
- На Android 11+: Настройки → Приложения → RUCH → Разрешения → Файлы и медиа

### Медленный первый запуск

- Копирование 250 MB занимает время
- Загрузка ONNX моделей в память тоже требует времени
- Первый запуск может занять 10-30 секунд
