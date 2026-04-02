#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Whisper context structure
struct WhisperContext {
    void* model;
    std::string modelPath;
    bool initialized;
};

extern "C" {

// Initialize Whisper model
JNIEXPORT jboolean JNICALL
Java_com_ruch_translator_stt_SpeechRecognizer_initWhisper(
        JNIEnv* env,
        jobject thiz,
        jstring model_path) {
    
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing Whisper with model: %s", path);
    
    // Store context for later use
    WhisperContext* ctx = new WhisperContext();
    ctx->modelPath = std::string(path);
    ctx->initialized = true;
    
    // Set the context as a field in the Java object
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "nativeContext", "J");
    if (field != nullptr) {
        env->SetLongField(thiz, field, reinterpret_cast<jlong>(ctx));
    }
    
    env->ReleaseStringUTFChars(model_path, path);
    return JNI_TRUE;
}

// Transcribe audio data
JNIEXPORT jstring JNICALL
Java_com_ruch_translator_stt_SpeechRecognizer_transcribe(
        JNIEnv* env,
        jobject thiz,
        jfloatArray audio_data,
        jstring language) {
    
    // Get audio data
    jsize length = env->GetArrayLength(audio_data);
    jfloat* data = env->GetFloatArrayElements(audio_data, nullptr);
    
    const char* lang = env->GetStringUTFChars(language, nullptr);
    
    LOGI("Transcribing %d samples in language: %s", length, lang);
    
    // Get context
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "nativeContext", "J");
    WhisperContext* ctx = nullptr;
    if (field != nullptr) {
        ctx = reinterpret_cast<WhisperContext*>(env->GetLongField(thiz, field));
    }
    
    // Placeholder transcription - in real implementation this would use whisper.cpp
    // For demonstration, return a sample transcription
    std::string result;
    
    // Simple voice activity detection based on audio levels
    float sum = 0.0f;
    for (int i = 0; i < length; i++) {
        sum += std::abs(data[i]);
    }
    float avgLevel = sum / length;
    
    if (avgLevel > 0.01f) {
        // Audio detected - return placeholder text
        if (strcmp(lang, "ru") == 0) {
            result = "Привет, как дела?";
        } else {
            result = "你好，你好吗？";
        }
    } else {
        result = "";
    }
    
    env->ReleaseFloatArrayElements(audio_data, data, 0);
    env->ReleaseStringUTFChars(language, lang);
    
    return env->NewStringUTF(result.c_str());
}

// Release Whisper resources
JNIEXPORT void JNICALL
Java_com_ruch_translator_stt_SpeechRecognizer_releaseWhisper(
        JNIEnv* env,
        jobject thiz) {
    
    LOGI("Releasing Whisper resources");
    
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "nativeContext", "J");
    if (field != nullptr) {
        WhisperContext* ctx = reinterpret_cast<WhisperContext*>(env->GetLongField(thiz, field));
        if (ctx != nullptr) {
            delete ctx;
            env->SetLongField(thiz, field, 0);
        }
    }
}

} // extern "C"
