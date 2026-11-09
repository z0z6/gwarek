cmake_minimum_required(VERSION 3.22.1)
project(gadka_llm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# Statyczne biblioteki llama/ggml są wlinkowane do .so – muszą być PIC.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# llama.cpp przypięte do konkretnego wydania i sprawdzane sumą kontrolną.
include(FetchContent)
FetchContent_Declare(
    llama
    URL https://codeload.github.com/ggml-org/llama.cpp/tar.gz/refs/tags/b12000
    URL_HASH SHA256=cb6e28ed73c46156d8db0b17b51ba86ba5b86d9fe70063502b0c80f6e1bc33b3
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama)

add_library(gadka_llm SHARED gadka_llm.cpp)
target_link_libraries(gadka_llm PRIVATE llama)
if(ANDROID)
    target_link_libraries(gadka_llm PRIVATE android log)
endif()
