/* ABI: resurs_audit_init argument / IO errors and a successful load. */
#include "resurs_audit.h"
#include "abi_fixture.h"
#include "test_util.h"

static const char *const KEY_PATH = "audit_abi_init_key.pem";
static const char *const PUB_PATH = "audit_abi_init_pub.der";

/* Argument / IO errors before a real key is loaded. */
static void test_init_errors(void)
{
    check(resurs_audit_init(NULL) == RESURS_AUDIT_ERR_INVALID_ARG,
          "init(NULL) -> INVALID_ARG");
    check(resurs_audit_init("/nonexistent/resurs-audit.key") == RESURS_AUDIT_ERR_KEY_IO,
          "init(missing file) -> KEY_IO");
}

static void test_init_valid_pem(void)
{
    check(resurs_audit_init(KEY_PATH) == RESURS_AUDIT_OK, "init(valid PEM) -> OK");
}

int main(void)
{
    test_init_errors();

    unsigned char pub[RESURS_AUDIT_PUBKEY_LEN];
    if (abi_make_key(KEY_PATH, PUB_PATH, pub) != 0)
    {
        return 1;
    }

    test_init_valid_pem();

    abi_teardown(KEY_PATH, PUB_PATH);
    return test_summary();
}
