#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "SherpaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// TTS context structure
struct TTSContext {
    std::string modelPath;
    std::string tokensPath;
    std::string dataType;
    bool initialized;
    float sampleRate;
};

extern "C" {

// Initialize TTS model
JNIEXPORT jlong JNICALL
Java_com_ruch_translator_tts_TTSEngine_initTTS(
        JNIEnv* env,
        jobject thiz,
        jstring model_path,
        jstring tokens_path,
        jstring data_type) {
    
    const char* modelPath = env->GetStringUTFChars(model_path, nullptr);
    const char* tokensPath = env->GetStringUTFChars(tokens_path, nullptr);
    const char* dataType = env->GetStringUTFChars(data_type, nullptr);
    
    LOGI("Initializing TTS with model: %s", modelPath);
    
    TTSContext* ctx = new TTSContext();
    ctx->modelPath = std::string(modelPath);
    ctx->tokensPath = std::string(tokensPath);
    ctx->dataType = std::string(dataType);
    ctx->initialized = true;
    ctx->sampleRate = 22050.0f;
    
    env->ReleaseStringUTFChars(model_path, modelPath);
    env->ReleaseStringUTFChars(tokens_path, tokensPath);
    env->ReleaseStringUTFChars(data_type, dataType);
    
    return reinterpret_cast<jlong>(ctx);
}

// Generate speech from text
JNIEXPORT jfloatArray JNICALL
Java_com_ruch_translator_tts_TTSEngine_generateSpeech(
        JNIEnv* env,
        jobject thiz,
        jlong tts_handle,
        jstring text) {
    
    if (tts_handle == 0) {
        return nullptr;
    }
    
    const char* textStr = env->GetStringUTFChars(text, nullptr);
    TTSContext* ctx = reinterpret_cast<TTSContext*>(tts_handle);
    
    LOGI("Generating speech for: %s", textStr);
    
    // Placeholder implementation - generates a simple sine wave
    // In real implementation, this would use SherpaTTS
    
    int textLen = strlen(textStr);
    if (textLen == 0) {
        env->ReleaseStringUTFChars(text, textStr);
        return nullptr;
    }
    
    // Generate approximately 1 second of audio per 5 characters
    int numSamples = static_cast<int>((textLen / 5.0f) * ctx->sampleRate);
    numSamples = std::max(numSamples, static_cast<int>(ctx->sampleRate * 0.5f)); // Minimum 0.5 seconds
    numSamples = std::min(numSamples, static_cast<int>(ctx->sampleRate * 10.0f)); // Maximum 10 seconds
    
    std::vector<float> audioData(numSamples);
    
    // Generate a simple speech-like waveform (placeholder)
    // This creates a modulated sine wave that sounds somewhat like speech
    for (int i = 0; i < numSamples; i++) {
        float t = static_cast<float>(i) / ctx->sampleRate;
        
        // Base frequency varies slightly to simulate speech patterns
        float freq = 200.0f + 100.0f * sinf(t * 2.0f);
        
        // Amplitude envelope (fade in/out)
        float envelope = 1.0f;
        float fadeIn = 0.1f;
        float fadeOut = 0.2f;
        if (t < fadeIn) {
            envelope = t / fadeIn;
        } else if (t > (numSamples / ctx->sampleRate) - fadeOut) {
            envelope = ((numSamples / ctx->sampleRate) - t) / fadeOut;
        }
        
        // Add some harmonics for more natural sound
        float sample = 0.5f * sinf(2.0f * M_PI * freq * t);
        sample += 0.3f * sinf(2.0f * M_PI * freq * 2.0f * t);
        sample += 0.2f * sinf(2.0f * M_PI * freq * 3.0f * t);
        
        // Add variation based on text
        sample *= 0.8f + 0.2f * sinf(t * 10.0f + textLen);
        
        audioData[i] = sample * envelope * 0.5f;
    }
    
    env->ReleaseStringUTFChars(text, textStr);
    
    // Create Java float array
    jfloatArray result = env->NewFloatArray(numSamples);
    if (result != nullptr) {
        env->SetFloatArrayRegion(result, 0, numSamples, audioData.data());
    }
    
    return result;
}

// Release TTS resources
JNIEXPORT void JNICALL
Java_com_ruch_translator_tts_TTSEngine_releaseTTS(
        JNIEnv* env,
        jobject thiz,
        jlong tts_handle) {
    
    LOGI("Releasing TTS resources");
    
    if (tts_handle != 0) {
        TTSContext* ctx = reinterpret_cast<TTSContext*>(tts_handle);
        delete ctx;
    }
}

} // extern "C"
