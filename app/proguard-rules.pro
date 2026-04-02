# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools.

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }

# Keep model classes
-keep class com.ruch.translator.stt.** { *; }
-keep class com.ruch.translator.tts.** { *; }
-keep class com.ruch.translator.translation.** { *; }

# Keep coroutine related classes
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
