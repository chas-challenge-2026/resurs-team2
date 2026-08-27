cmake_minimum_required(VERSION 3.22)

project(resurs_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE RelWithDebInfo CACHE STRING "Build type" FORCE)
endif()

find_package(OpenSSL REQUIRED)

enable_testing()

add_subdirectory(crypto)

if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/audit/CMakeLists.txt")
    add_subdirectory(audit)
endif()
