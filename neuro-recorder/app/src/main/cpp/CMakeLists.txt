cmake_minimum_required(VERSION 3.22.1)
project(neuro_recorder_whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp)

add_library(neuro_whisper SHARED whisper_jni.cpp)
target_include_directories(neuro_whisper PRIVATE whisper.cpp/include)
target_link_libraries(neuro_whisper whisper android log)
