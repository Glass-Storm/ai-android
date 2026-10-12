cmake_minimum_required(VERSION 3.22.1)
project(recombine LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Pinned llama.cpp source tree. Production vendors this as a git submodule
# (or shallow clone) at the same relative path:
#   pin: tag v0.6.0  commit d81235049384534c167caea52b85a694f6103d14
set(RECOMBINE_LLAMA_CPP_DIR "${CMAKE_CURRENT_LIST_DIR}/third_party/llama.cpp"
    CACHE PATH "llama.cpp source tree")

# ---------------------------------------------------------------------------
# llama.cpp build options (reused Wave-0 S3 recipe)
# ---------------------------------------------------------------------------
set(BUILD_SHARED_LIBS    ON  CACHE BOOL "" FORCE)  # emit libllama.so / libggml*.so
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)  # no libcurl / no model download
set(GGML_OPENMP          ON  CACHE BOOL "" FORCE)
if(ANDROID AND ANDROID_ABI STREQUAL "x86_64")
    set(GGML_OPENMP      OFF CACHE BOOL "" FORCE)  # x86_64 is test-only; keep it simple
endif()

add_subdirectory(${RECOMBINE_LLAMA_CPP_DIR} ${CMAKE_BINARY_DIR}/llama.cpp-build)

# ---------------------------------------------------------------------------
# JNI binding: hk.edu.hkmu.recombine.NativeLlama
# ---------------------------------------------------------------------------
add_library(recombine SHARED llama_jni.cpp)

target_include_directories(recombine PRIVATE
    ${RECOMBINE_LLAMA_CPP_DIR}/include
    ${RECOMBINE_LLAMA_CPP_DIR}/ggml/include)

target_link_libraries(recombine PRIVATE llama)

if(ANDROID)
    target_link_libraries(recombine PRIVATE android log)
endif()
