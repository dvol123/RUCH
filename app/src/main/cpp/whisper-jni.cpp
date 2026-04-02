/**
 * JNI обёртка для Whisper.cpp
 * 
 * Этот файл предоставляет интерфейс между Kotlin и C++ реализацией Whisper.
 * 
 * Для полноценной работы нужно:
 * 1. Скачать whisper.cpp исходники: https://github.com/ggerganov/whisper.cpp
 * 2. Скопировать whisper.cpp и ggml.c в папку whisper/
 * 3. Или использовать предсобранную библиотеку libwhisper.so
 */

#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Структура для хранения контекста Whisper
struct WhisperContext {
    void* ctx;              // Указатель на whisper_context
    std::string modelPath;  // Путь к модели
    bool initialized;       // Флаг инициализации
};

extern "C" {

// ============================================================
// Инициализация Whisper с моделью
// ============================================================
JNIEXPORT jboolean JNICALL
Java_com_ruch_translator_stt_SpeechRecognizerNative_initWhisper(
        JNIEnv* env,
        jobject thiz,
        jstring model_path) {
    
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing Whisper with model: %s", path);
    
    // Создаём контекст
    WhisperContext* ctx = new WhisperContext();
    ctx->modelPath = std::string(path);
    ctx->initialized = false;
    
    // TODO: Реальная инициализация whisper.cpp
    // ctx->ctx = whisper_init_from_file(path);
    // if (ctx->ctx != nullptr) {
    //     ctx->initialized = true;
    // }
    
    // Для демонстрации считаем инициализацию успешной
    ctx->initialized = true;
    
    // Сохраняем контекст в Java объекте
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "nativeContext", "J");
    if (field != nullptr) {
        env->SetLongField(thiz, field, reinterpret_cast<jlong>(ctx));
    }
    
    env->ReleaseStringUTFChars(model_path, path);
    
    LOGI("Whisper initialized: %s", ctx->initialized ? "success" : "failed");
    return ctx->initialized ? JNI_TRUE : JNI_FALSE;
}

// ============================================================
// Распознавание речи из аудиоданных
// ============================================================
JNIEXPORT jstring JNICALL
Java_com_ruch_translator_stt_SpeechRecognizerNative_transcribe(
        JNIEnv* env,
        jobject thiz,
        jfloatArray audio_data,
        jstring language) {
    
    // Получаем аудиоданные
    jsize length = env->GetArrayLength(audio_data);
    jfloat* data = env->GetFloatArrayElements(audio_data, nullptr);
    
    const char* lang = env->GetStringUTFChars(language, nullptr);
    
    LOGI("Transcribing %d samples in language: %s", length, lang);
    
    // Получаем контекст
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "nativeContext", "J");
    WhisperContext* ctx = nullptr;
    if (field != nullptr) {
        ctx = reinterpret_cast<WhisperContext*>(env->GetLongField(thiz, field));
    }
    
    std::string result;
    
    if (ctx != nullptr && ctx->initialized) {
        // TODO: Реальное распознавание через whisper.cpp
        // whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        // params.language = lang;
        // whisper_full(ctx->ctx, params, data, length);
        // int n_segments = whisper_full_n_segments(ctx->ctx);
        // for (int i = 0; i < n_segments; i++) {
        //     result += whisper_full_get_segment_text(ctx->ctx, i);
        // }
        
        // Демо: определяем наличие речи по уровню сигнала
        float sum = 0.0f;
        for (int i = 0; i < length; i++) {
            sum += std::abs(data[i]);
        }
        float avgLevel = sum / length;
        
        if (avgLevel > 0.01f) {
            // Возвращаем демо-текст
            if (strcmp(lang, "ru") == 0) {
                result = "Привет, как дела?";  // Демо для русского
            } else {
                result = "你好，你好吗？";       // Демо для китайского
            }
        }
    } else {
        LOGE("Whisper context not initialized");
    }
    
    env->ReleaseFloatArrayElements(audio_data, data, 0);
    env->ReleaseStringUTFChars(language, lang);
    
    return env->NewStringUTF(result.c_str());
}

// ============================================================
// Освобождение ресурсов
// ============================================================
JNIEXPORT void JNICALL
Java_com_ruch_translator_stt_SpeechRecognizerNative_releaseWhisper(
        JNIEnv* env,
        jobject thiz) {
    
    LOGI("Releasing Whisper resources");
    
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "nativeContext", "J");
    if (field != nullptr) {
        WhisperContext* ctx = reinterpret_cast<WhisperContext*>(env->GetLongField(thiz, field));
        if (ctx != nullptr) {
            // TODO: whisper_free(ctx->ctx);
            delete ctx;
            env->SetLongField(thiz, field, 0);
        }
    }
}

// ============================================================
// Получение информации о модели
// ============================================================
JNIEXPORT jobjectArray JNICALL
Java_com_ruch_translator_stt_SpeechRecognizerNative_getSupportedLanguages(
        JNIEnv* env,
        jobject thiz) {
    
    std::vector<std::string> languages = {"ru", "zh", "en", "de", "fr", "es", "ja", "ko"};
    
    jobjectArray result = env->NewObjectArray(
        languages.size(),
        env->FindClass("java/lang/String"),
        env->NewStringUTF("")
    );
    
    for (size_t i = 0; i < languages.size(); i++) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(languages[i].c_str()));
    }
    
    return result;
}

} // extern "C"
