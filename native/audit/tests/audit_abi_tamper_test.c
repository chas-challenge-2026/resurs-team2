/* ABI: resurs_audit_verify_chain detects a tampered entry, a tampered
 * signature and swapped hashes. */
#include "resurs_audit.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <string.h>

static const char *const KEY_PATH = "audit_abi_tamper_key.pem";
static const char *const PUB_PATH = "audit_abi_tamper_pub.der";

/* Shared signed chain, built once in main(). Each test alters a private copy. */
static unsigned char g_pub[RESURS_AUDIT_PUBKEY_LEN];
static struct abi_chain g_chain;

/* Flip a byte inside entries[1]'s content. */
static void test_tampered_entry(void)
{
    struct abi_chain c = g_chain;
    c.entries_buf[c.entry_offset[1]] ^= 0x01;

    int first_invalid;
    check(abi_verify(&c, g_pub, &first_invalid) == RESURS_AUDIT_OK,
          "verify_chain(tampered entry) -> OK");
    check(first_invalid == 1, "tampered entries[1] -> first_invalid_index == 1");
}

/* Flip a byte inside signatures[2]. */
static void test_tampered_signature(void)
{
    struct abi_chain c = g_chain;
    c.signatures[2 * RESURS_AUDIT_SIG_LEN] ^= 0x01;

    int first_invalid;
    check(abi_verify(&c, g_pub, &first_invalid) == RESURS_AUDIT_OK,
          "verify_chain(tampered signature) -> OK");
    check(first_invalid == 2, "tampered signatures[2] -> first_invalid_index == 2");
}

/* Swap hashes[0] and hashes[1]. */
static void test_swapped_hashes(void)
{
    struct abi_chain c = g_chain;
    memcpy(c.hashes, g_chain.hashes + RESURS_AUDIT_HASH_LEN, RESURS_AUDIT_HASH_LEN);
    memcpy(c.hashes + RESURS_AUDIT_HASH_LEN, g_chain.hashes, RESURS_AUDIT_HASH_LEN);

    int first_invalid;
    check(abi_verify(&c, g_pub, &first_invalid) == RESURS_AUDIT_OK,
          "verify_chain(swapped hashes) -> OK");
    check(first_invalid != -1, "swapped hashes[0]/hashes[1] is detected");
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

    test_tampered_entry();
    test_tampered_signature();
    test_swapped_hashes();

    abi_teardown(KEY_PATH, PUB_PATH);
    return test_summary();
}
