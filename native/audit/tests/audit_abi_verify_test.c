/* ABI: resurs_audit_verify_chain - argument errors, an intact chain, an
 * inflated entry length, and verifying after shutdown. */
#include "resurs_audit.h"
#include "abi_fixture.h"
#include "test_util.h"

static const char *const KEY_PATH = "audit_abi_verify_key.pem";
static const char *const PUB_PATH = "audit_abi_verify_pub.der";

/* Shared signed chain, built once in main(). Tests that alter it work on a
 * private copy. */
static unsigned char g_pub[RESURS_AUDIT_PUBKEY_LEN];
static struct abi_chain g_chain;

/* No init required for verify_chain. */
static void test_verify_invalid_args(void)
{
    const struct abi_chain *c = &g_chain;
    int first_invalid = -2;

    check(resurs_audit_verify_chain(NULL, c->signatures, c->signature_lens, c->entries_buf,
                                    c->entry_lens, ABI_CHAIN_LEN, c->entries_len,
                                    sizeof c->signatures, g_pub,
                                    &first_invalid) == RESURS_AUDIT_ERR_INVALID_ARG,
          "verify_chain(NULL hashes) -> INVALID_ARG");
    check(resurs_audit_verify_chain(c->hashes, c->signatures, c->signature_lens, c->entries_buf,
                                    c->entry_lens, 0, c->entries_len,
                                    sizeof c->signatures, g_pub,
                                    &first_invalid) == RESURS_AUDIT_ERR_INVALID_ARG,
          "verify_chain(entry_count == 0) -> INVALID_ARG");
}

static void test_verify_intact(void)
{
    int first_invalid;
    check(abi_verify(&g_chain, g_pub, &first_invalid) == RESURS_AUDIT_OK,
          "verify_chain(intact) -> OK");
    check(first_invalid == -1, "verify_chain(intact) -> first_invalid_index == -1");
}

/* entry_lens[0] claims more than entries_len actually has. */
static void test_verify_inflated_entry_len(void)
{
    struct abi_chain c = g_chain;
    c.entry_lens[0] = c.entries_len + 4096; /* much more than entries_buf */

    int first_invalid;
    check(abi_verify(&c, g_pub, &first_invalid) == RESURS_AUDIT_ERR_INVALID_ARG,
          "verify_chain: inflated entry_lens[0] past entries_len -> INVALID_ARG, not a crash");
}

/* Called after resurs_audit_shutdown(). verify_chain never touches
 * AuditKeyManager - it only needs the public_key argument. */
static void test_verify_after_shutdown(void)
{
    int first_invalid;
    check(abi_verify(&g_chain, g_pub, &first_invalid) == RESURS_AUDIT_OK,
          "verify_chain after shutdown -> OK");
    check(first_invalid == -1, "verify_chain after shutdown still verifies the intact chain");
}

int main(void)
{
    if (abi_setup(KEY_PATH, PUB_PATH, g_pub) != 0)
    {
        return 1;
    }
    if (abi_build_chain(&g_chain) != RESURS_AUDIT_OK)
    {
        abi_teardown(KEY_PATH, PUB_PATH);
        return 1;
    }

    test_verify_invalid_args();
    test_verify_intact();
    test_verify_inflated_entry_len();

    resurs_audit_shutdown();
    test_verify_after_shutdown();

    abi_teardown(KEY_PATH, PUB_PATH);
    return test_summary();
}
