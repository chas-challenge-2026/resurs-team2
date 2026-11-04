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

# --- Narrated demo (not a test - run manually to see the flow) ---

add_executable(audit_demo audit_demo.c)
target_link_libraries(audit_demo PRIVATE resurs_audit)

add_subdirectory(tests)
