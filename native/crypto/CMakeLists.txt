# The crypto shared library - loaded from Java via JNA (dlopen).
add_library(resurs_crypto SHARED
    resurs_crypto.cpp
    aes_gcm_cipher.cpp
    key_manager.cpp
)

target_include_directories(resurs_crypto
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
)

# OpenSSL is an implementation detail - PRIVATE, does not leak into the public header.
target_link_libraries(resurs_crypto
    PRIVATE OpenSSL::Crypto
)

target_compile_options(resurs_crypto
    PRIVATE -Wall -Wextra -Wpedantic
)

set_target_properties(resurs_crypto PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

# --- Local sandbox test (compiles module sources directly, not via the .so,
#     because internal C++ symbols are hidden in the shared library) ---
add_executable(sandbox_test
    sandbox_test.cpp
    key_manager.cpp
    aes_gcm_cipher.cpp
)

target_link_libraries(sandbox_test PRIVATE OpenSSL::Crypto)
add_test(NAME sandbox_test COMMAND sandbox_test)

# --- Local ABI test (links against libresurs_crypto.so) ---

add_executable(abi_test abi_test.c)

target_link_libraries(abi_test PRIVATE resurs_crypto)
add_test(NAME abi_test COMMAND abi_test)