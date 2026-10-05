# Module sources live one level up.
set(CRYPTO_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/..)

# --- Local sandbox test (compiles module sources directly, not via the .so,
#     because internal C++ symbols are hidden in the shared library) ---
add_executable(sandbox_test
    sandbox_test.cpp
    ${CRYPTO_SRC_DIR}/key_manager.cpp
    ${CRYPTO_SRC_DIR}/aes_gcm_cipher.cpp
    ${CRYPTO_SRC_DIR}/hmac_sha256.cpp
)

# Internal headers (key_manager.hpp etc.) are not exported by resurs_crypto.
target_include_directories(sandbox_test PRIVATE ${CRYPTO_SRC_DIR})

target_link_libraries(sandbox_test PRIVATE OpenSSL::Crypto)

# Absolute path to the committed 64-byte key file, so sandbox_test can also run
# the crypto suite against the real generated key (skipped if the file is absent).
target_compile_definitions(sandbox_test PRIVATE
    RESURS_REAL_KEY_FILE="${CMAKE_CURRENT_SOURCE_DIR}/../../../infra/secrets/resurs-crypto.key"
)

add_test(NAME sandbox_test COMMAND sandbox_test)

# --- Local ABI test (links against libresurs_crypto.so) ---

add_executable(abi_test abi_test.c)

target_link_libraries(abi_test PRIVATE resurs_crypto)
add_test(NAME abi_test COMMAND abi_test)
