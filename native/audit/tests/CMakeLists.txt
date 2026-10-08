# Module sources live one level up.
set(AUDIT_SRC_DIR ${CMAKE_CURRENT_SOURCE_DIR}/..)

# --- Local sandbox test (compiles module sources directly, not via the .so,
#     because internal C++ symbols are hidden in the shared library) ---
add_executable(audit_sandbox_test
    audit_sandbox_test.cpp
    ${AUDIT_SRC_DIR}/hash_chain.cpp
    ${AUDIT_SRC_DIR}/signer.cpp
    ${AUDIT_SRC_DIR}/audit_key_manager.cpp
)

# Internal headers (hash_chain.hpp etc.) are not exported by resurs_audit.
target_include_directories(audit_sandbox_test PRIVATE ${AUDIT_SRC_DIR})

target_link_libraries(audit_sandbox_test PRIVATE OpenSSL::Crypto)

add_test(NAME audit_sandbox_test COMMAND audit_sandbox_test)

# --- Local ABI test (links against libresurs_audit.so) ---

add_executable(audit_abi_test audit_abi_test.c)
target_link_libraries(audit_abi_test PRIVATE resurs_audit)
add_test(NAME audit_abi_test COMMAND audit_abi_test)
