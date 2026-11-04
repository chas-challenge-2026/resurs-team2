/* ABI: resurs_audit_chain_entry - chaining the sample entries, and NOT_INIT
 * after shutdown. */
#include "resurs_audit.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <string.h>

static const char *const KEY_PATH = "audit_abi_chain_key.pem";
static const char *const PUB_PATH = "audit_abi_chain_pub.der";

static void test_chain_entries(void)
{
    unsigned char hashes[ABI_CHAIN_LEN * RESURS_AUDIT_HASH_LEN];
    unsigned char signatures[ABI_CHAIN_LEN * RESURS_AUDIT_SIG_LEN];

    for (int i = 0; i < ABI_CHAIN_LEN; i++)
    {
        const unsigned char *prev = (i == 0) ? NULL : hashes + (i - 1) * RESURS_AUDIT_HASH_LEN;
        size_t siglen = 0;

        int rc = resurs_audit_chain_entry(prev, abi_entry(i), strlen(abi_entry(i)),
                                          hashes + i * RESURS_AUDIT_HASH_LEN,
                                          signatures + i * RESURS_AUDIT_SIG_LEN,
                                          &siglen);
        check(rc == RESURS_AUDIT_OK, "chain_entry -> OK");
        check(siglen == RESURS_AUDIT_SIG_LEN, "chain_entry writes a 64-byte signature");
    }
}

/* Called after resurs_audit_shutdown(). */
static void test_chain_entry_after_shutdown(void)
{
    size_t siglen = RESURS_AUDIT_SIG_LEN;
    unsigned char h_out[RESURS_AUDIT_HASH_LEN];
    unsigned char s_out[RESURS_AUDIT_SIG_LEN];
    check(resurs_audit_chain_entry(NULL, abi_entry(0), strlen(abi_entry(0)),
                                   h_out, s_out, &siglen) == RESURS_AUDIT_ERR_NOT_INIT,
          "chain_entry after shutdown -> NOT_INIT");
}

int main(void)
{
    unsigned char pub[RESURS_AUDIT_PUBKEY_LEN];
    if (abi_setup(KEY_PATH, PUB_PATH, pub) != 0)
    {
        return 1;
    }

    test_chain_entries();

    resurs_audit_shutdown();
    test_chain_entry_after_shutdown();

    abi_teardown(KEY_PATH, PUB_PATH);
    return test_summary();
}
