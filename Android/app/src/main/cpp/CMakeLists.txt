# CMakeLists.txt for WhisperKey native library
cmake_minimum_required(VERSION 3.18.1)

project("whisperkey")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Path to whisper.cpp (relative to this CMakeLists.txt location)
# From Android/app/src/main/cpp/ we need to go up 5 levels to repo root
set(WHISPER_LIB_DIR ${CMAKE_SOURCE_DIR}/../../../../../whisper.cpp)

# Verify the path exists
if(NOT EXISTS ${WHISPER_LIB_DIR}/src/whisper.cpp)
    message(FATAL_ERROR "whisper.cpp not found at ${WHISPER_LIB_DIR}. Make sure the submodule is initialized.")
endif()

# Find required libraries
find_library(LOG_LIB log)

# Include directories for whisper and ggml
set(WHISPER_INCLUDE_DIRS
    ${WHISPER_LIB_DIR}
    ${WHISPER_LIB_DIR}/src
    ${WHISPER_LIB_DIR}/include
    ${WHISPER_LIB_DIR}/ggml/include
    ${WHISPER_LIB_DIR}/ggml/src
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu
)

# Core ggml source files
set(GGML_CORE_SOURCES
    ${WHISPER_LIB_DIR}/ggml/src/ggml.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-alloc.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml-backend.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-backend-reg.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-backend-dl.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-opt.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-quants.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml-threading.cpp
    ${WHISPER_LIB_DIR}/ggml/src/gguf.cpp
)

# ggml-cpu source files
set(GGML_CPU_SOURCES
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/ggml-cpu.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/ggml-cpu.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/repack.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/hbm.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/quants.c
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/traits.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/amx/amx.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/amx/mmq.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/binary-ops.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/unary-ops.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/vec.cpp
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/ops.cpp
)

# Architecture-specific source files
if(${ANDROID_ABI} STREQUAL "arm64-v8a" OR ${ANDROID_ABI} STREQUAL "armeabi-v7a")
    # ARM architecture files
    list(APPEND GGML_CPU_SOURCES
        ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/arch/arm/quants.c
        ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/arch/arm/repack.cpp
    )
elseif(${ANDROID_ABI} STREQUAL "x86_64" OR ${ANDROID_ABI} STREQUAL "x86")
    # x86 architecture files
    list(APPEND GGML_CPU_SOURCES
        ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/arch/x86/quants.c
        ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu/arch/x86/repack.cpp
    )
endif()

# Combine all ggml sources
set(GGML_SOURCES ${GGML_CORE_SOURCES} ${GGML_CPU_SOURCES})

# Source files for whisper library
set(WHISPER_SOURCES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${GGML_SOURCES}
    ${CMAKE_SOURCE_DIR}/jni.c
)

# Create the whisper library
add_library(whisper SHARED ${WHISPER_SOURCES})

# Set include directories
target_include_directories(whisper PRIVATE ${WHISPER_INCLUDE_DIRS})

# Version definitions
set(GGML_VERSION "0.9.5")
set(GGML_COMMIT "unknown")
set(WHISPER_VERSION "1.7.4")

# Compile definitions
target_compile_definitions(whisper PRIVATE
    GGML_USE_CPU
    GGML_VERSION="${GGML_VERSION}"
    GGML_COMMIT="${GGML_COMMIT}"
    WHISPER_VERSION="${WHISPER_VERSION}"
)

# Compiler options
target_compile_options(whisper PRIVATE
    -fPIC
    -pthread
)

# Architecture-specific optimizations
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(whisper PRIVATE -march=armv8-a)
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    target_compile_options(whisper PRIVATE -mfpu=neon-vfpv4 -mfloat-abi=softfp)
endif()

# Optimizations for all builds (whisper.cpp is too slow without optimization)
target_compile_options(whisper PRIVATE
    -O3
    -ffunction-sections
    -fdata-sections
)

# Additional release-only optimizations
if(NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(whisper PRIVATE
        -fvisibility=hidden
        -fvisibility-inlines-hidden
    )
    target_link_options(whisper PRIVATE
        -Wl,--gc-sections
        -Wl,--exclude-libs,ALL
    )
endif()

# Link libraries
target_link_libraries(whisper
    ${LOG_LIB}
    android
    m
    dl
)
