cmake_minimum_required(VERSION 3.22.1)
project(droidclaw_llm)

# ── llama.cpp integration ────────────────────────────────────────
# Build the full llama.cpp inference engine from the submodule.
# Primary artifact: llama-server executable (subprocess approach).
# Secondary artifact: libllama.so (JNI future-proofing).

# Android cross-compilation settings (passed from Gradle NDK)
set(CMAKE_SYSTEM_NAME Android)

# Disable features not needed / unsupported on Android
set(GGML_OPENMP   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   ON  CACHE BOOL "" FORCE)
# Static linking: llama-server must be fully self-contained (no external .so deps).
# The JNI stub below is explicitly SHARED so this doesn't affect it.
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# Vulkan is toggled from Gradle cmake arguments (-DGGML_VULKAN=ON/OFF)
# No need to set it here — it's passed through from externalNativeBuild.

# Add the full llama.cpp source tree
add_subdirectory(llama.cpp)


# ── Backward-compat JNI stub ────────────────────────────────────
# Kept for any legacy code that loads droidclaw_llm via System.loadLibrary.
add_library(droidclaw_llm SHARED llama_inference_stub.cpp)
find_library(log-lib log)
target_link_libraries(droidclaw_llm ${log-lib})
