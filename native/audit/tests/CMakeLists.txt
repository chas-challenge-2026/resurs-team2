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

# --- Local ABI tests (link against libresurs_audit.so) ---
# One executable per area, so ctest reports each separately. Each uses its own
# key files, so they are safe to run in parallel (ctest -j).

set(AUDIT_ABI_TESTS init chain verify tamper)

foreach(name IN LISTS AUDIT_ABI_TESTS)
    set(target audit_abi_${name}_test)
    add_executable(${target} ${target}.c)
    target_link_libraries(${target} PRIVATE resurs_audit)
    add_test(NAME ${target} COMMAND ${target})
endforeach()
