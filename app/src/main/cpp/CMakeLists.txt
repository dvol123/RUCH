cmake_minimum_required(VERSION 3.22.1)
project("ruch_translator")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add compile options
add_compile_options(-Wall -Wextra -O3)

# Whisper JNI library
add_library(whisper-jni SHARED
    whisper-jni.cpp
)

# Sherpa JNI library
add_library(sherpa-jni SHARED
    sherpa-jni.cpp
)

# Link libraries
target_link_libraries(whisper-jni
    android
    log
)

target_link_libraries(sherpa-jni
    android
    log
    m
)
