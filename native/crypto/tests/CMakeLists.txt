# Module sources live one level up.
set(CRYPTO_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/..)

# --- Local sandbox test (compiles module sources directly, not via the .so,
#     because internal C++ symbols are hidden in the shared library) ---
add_executable(crypto_sandbox_test
    crypto_sandbox_test.cpp
    ${CRYPTO_SRC_DIR}/key_manager.cpp
    ${CRYPTO_SRC_DIR}/aes_gcm_cipher.cpp
    ${CRYPTO_SRC_DIR}/hmac_sha256.cpp
)

# Internal headers (key_manager.hpp etc.) are not exported by resurs_crypto.
target_include_directories(crypto_sandbox_test PRIVATE ${CRYPTO_SRC_DIR})

target_link_libraries(crypto_sandbox_test PRIVATE OpenSSL::Crypto)

# Absolute path to the committed 64-byte key file, so crypto_sandbox_test can also run
# the crypto suite against the real generated key (skipped if the file is absent).
target_compile_definitions(crypto_sandbox_test PRIVATE
    RESURS_REAL_KEY_FILE="${CMAKE_CURRENT_SOURCE_DIR}/../../../infra/secrets/resurs-crypto.key"
)

add_test(NAME crypto_sandbox_test COMMAND crypto_sandbox_test)

# --- KeyRing unit test (key ring file parsing + hot-reload rules) ---
add_executable(crypto_keyring_test
    crypto_keyring_test.cpp
    ${CRYPTO_SRC_DIR}/key_ring.cpp
    ${CRYPTO_SRC_DIR}/secret_file.cpp
)
target_include_directories(crypto_keyring_test PRIVATE ${CRYPTO_SRC_DIR})
target_link_libraries(crypto_keyring_test PRIVATE OpenSSL::Crypto)
target_compile_options(crypto_keyring_test PRIVATE -Wall -Wextra -Wpedantic)
add_test(NAME crypto_keyring_test COMMAND crypto_keyring_test)
# The FIFO check would hang on a regression; fail fast instead of ctest's 1500 s default.
set_tests_properties(crypto_keyring_test PROPERTIES TIMEOUT 30)

# --- Local ABI tests (link against libresurs_crypto.so) ---
# One executable per area, so ctest reports each separately. Each uses its own
# key file, so they are safe to run in parallel (ctest -j).

set(CRYPTO_ABI_TESTS init roundtrip buffers raw bounds hmac)

foreach(name IN LISTS CRYPTO_ABI_TESTS)
    set(target crypto_abi_${name}_test)
    add_executable(${target} ${target}.c)
    target_link_libraries(${target} PRIVATE resurs_crypto)
    add_test(NAME ${target} COMMAND ${target})
endforeach()
