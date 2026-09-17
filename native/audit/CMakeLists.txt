# The audit-signing shared library - loaded from Java via JNA (dlopen).
add_library(resurs_audit SHARED
    resurs_audit.cpp
    hash_chain.cpp
    signer.cpp
    audit_key_manager.cpp
)

target_include_directories(resurs_audit
    PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}
)

# OpenSSL is an implementation detail - PRIVATE, does not leak into the public header.
target_link_libraries(resurs_audit
    PRIVATE OpenSSL::Crypto
)

target_compile_options(resurs_audit
    PRIVATE -Wall -Wextra -Wpedantic
)

set_target_properties(resurs_audit PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
)

add_executable(audit_sandbox_test
    audit_sandbox_test.cpp
    hash_chain.cpp
    signer.cpp
    audit_key_manager.cpp
)
target_link_libraries(audit_sandbox_test PRIVATE OpenSSL::Crypto)
add_test(NAME audit_sandbox_test COMMAND audit_sandbox_test)

# --- Local ABI test (links against libresurs_audit.so) ---

add_executable(audit_abi_test audit_abi_test.c)

target_link_libraries(audit_abi_test PRIVATE resurs_audit)
add_test(NAME audit_abi_test COMMAND audit_abi_test)

