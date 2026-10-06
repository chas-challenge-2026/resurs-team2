# The crypto shared library - loaded from Java via JNA (dlopen).
add_library(resurs_crypto SHARED
    resurs_crypto.cpp
    aes_gcm_cipher.cpp
    hmac_sha256.cpp
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

# --- Narrated demo (not a test - run manually to see the flow) ---

add_executable(crypto_demo crypto_demo.c)
target_link_libraries(crypto_demo PRIVATE resurs_crypto)

add_subdirectory(tests)