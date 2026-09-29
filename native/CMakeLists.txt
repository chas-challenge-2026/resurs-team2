cmake_minimum_required(VERSION 3.22)

project(resurs_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE RelWithDebInfo CACHE STRING "Build type" FORCE)
endif()

find_package(OpenSSL REQUIRED)

# AddressSanitizer + UndefinedBehaviorSanitizer for every target (use `make asan`).
# -fno-sanitize-recover makes a UBSan finding abort, so the test fails instead of
# just printing a warning.
option(RESURS_SANITIZE "Build with ASAN + UBSan" OFF)
if(RESURS_SANITIZE)
    add_compile_options(-fsanitize=address,undefined -fno-sanitize-recover=all -fno-omit-frame-pointer)
    add_link_options(-fsanitize=address,undefined)
endif()

enable_testing()

add_subdirectory(crypto)

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/audit/CMakeLists.txt")
    add_subdirectory(audit)
endif()
