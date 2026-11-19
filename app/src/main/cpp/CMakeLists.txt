cmake_minimum_required(VERSION 3.22.1)
project(digitallife_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp")

if(EXISTS "${LLAMA_DIR}/CMakeLists.txt")
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

    add_subdirectory("${LLAMA_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama_build" EXCLUDE_FROM_ALL)

    add_library(digitallife_llama SHARED llama_jni.cpp)
    target_link_libraries(digitallife_llama PRIVATE llama android log)
    target_compile_definitions(digitallife_llama PRIVATE HAS_LLAMA_CPP=1)
else()
    message(WARNING "llama.cpp directory not found at ${LLAMA_DIR}. Building stub JNI bridge.")
    add_library(digitallife_llama SHARED llama_jni.cpp)
    target_link_libraries(digitallife_llama PRIVATE android log)
    target_compile_definitions(digitallife_llama PRIVATE HAS_LLAMA_CPP=0)
endif()
