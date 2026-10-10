# Host (desktop) build of PokerSI's llama.cpp glue, for trying prompts and
# grammars without a phone. See README.md.
cmake_minimum_required(VERSION 3.22)
project(pokersi-llm-harness C CXX)

set(CMAKE_CXX_STANDARD 17)

set(LLAMA_DIR "${CMAKE_CURRENT_LIST_DIR}/../../app/src/main/cpp/llama.cpp" CACHE PATH "llama.cpp source tree")
set(POKERSI_CPP_DIR "${CMAKE_CURRENT_LIST_DIR}/../../app/src/main/cpp" CACHE PATH "Directory with pokersi-llm.h")

set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama.cpp EXCLUDE_FROM_ALL)

# The same inference code the app's JNI bridge uses.
add_executable(pokersi-harness harness.cpp)
target_include_directories(pokersi-harness PRIVATE ${POKERSI_CPP_DIR})
target_link_libraries(pokersi-harness PRIVATE llama-common llama)
