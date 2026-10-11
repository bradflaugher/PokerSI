# PokerSI's bridge to llama.cpp. The submodule is the andriydruk/llama.cpp-android
# fork that LM Playground uses: upstream llama.cpp plus "fd:N" paths in ggml_fopen
# and llama-mmap, so a GGUF picked through Android's Storage Access Framework
# (which has no filesystem path) loads straight from its file descriptor.
cmake_minimum_required(VERSION 3.31)

project("pokersi")

add_subdirectory(llama.cpp)

add_library(${CMAKE_PROJECT_NAME} SHARED pokersi-jni.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama-common
        llama
        android
        log)
