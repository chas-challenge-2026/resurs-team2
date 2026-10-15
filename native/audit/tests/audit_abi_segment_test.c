/* ABI: resurs_audit_verify_chain on a segment of a chain (start_index,
 * prev_hash) - every segment of the sample chain verifies, a tampered entry is
 * reported at its global index, a wrong anchor fails the segment's first entry,
 * and a missing anchor is rejected. */
#include "resurs_audit.h"
#include "abi_fixture.h"
#include "test_util.h"

static const char *const KEY_PATH = "audit_abi_segment_key.pem";
static const char *const PUB_PATH = "audit_abi_segment_pub.der";

/* Shared signed chain, built once in main(). Each test alters a private copy. */
static unsigned char g_pub[RESURS_AUDIT_PUBKEY_LEN];
static struct abi_chain g_chain;

/* Verifies the count entries of c that start at global position start, passing
 * only that slice. The anchor is the stored hash of entry start - 1, or NULL at
 * the start of the chain, unless anchor_override is given. */
static int verify_segment(const struct abi_chain *c, size_t start, size_t count,
                          const unsigned char *anchor_override, int *first_invalid)
{
    size_t end_offset = (start + count < ABI_CHAIN_LEN) ? c->entry_offset[start + count]
                                                        : c->entries_len;
    const unsigned char *anchor = anchor_override;
    if (anchor == NULL && start > 0)
    {
        anchor = c->hashes + (start - 1) * RESURS_AUDIT_HASH_LEN;
    }

    *first_invalid = -2;
    return resurs_audit_verify_chain(c->hashes + start * RESURS_AUDIT_HASH_LEN,
                                     c->signatures + start * RESURS_AUDIT_SIG_LEN,
                                     c->signature_lens + start,
                                     c->entries_buf + c->entry_offset[start],
                                     c->entry_lens + start, count,
                                     end_offset - c->entry_offset[start],
                                     count * RESURS_AUDIT_SIG_LEN,
                                     start, anchor, g_pub, first_invalid);
}

/* Every segment [s, s+n) of the intact chain verifies. */
static void test_every_segment_verifies(void)
{
    int ok = 1;
    for (size_t s = 0; s < ABI_CHAIN_LEN; s++)
    {
        for (size_t n = 1; s + n <= ABI_CHAIN_LEN; n++)
        {
            int first_invalid;
            if (verify_segment(&g_chain, s, n, NULL, &first_invalid) != RESURS_AUDIT_OK ||
                first_invalid != -1)
            {
                ok = 0;
            }
        }
    }
    check(ok, "segment: every segment of the intact chain verifies");
}

/* A tampered entry is reported at its global index in every segment that
 * contains it. */
static void test_tampered_entry_reported_at_global_index(void)
{
    int ok = 1;
    for (size_t t = 0; t < ABI_CHAIN_LEN; t++)
    {
        struct abi_chain c = g_chain;
        c.entries_buf[c.entry_offset[t]] ^= 0x01;

        for (size_t s = 0; s <= t; s++)
        {
            for (size_t n = t - s + 1; s + n <= ABI_CHAIN_LEN; n++)
            {
                int first_invalid;
                if (verify_segment(&c, s, n, NULL, &first_invalid) != RESURS_AUDIT_OK ||
                    first_invalid != (int)t)
                {
                    ok = 0;
                }
            }
        }
    }
    check(ok, "segment: tampered entry reported at its global index in every segment containing it");
}

/* An anchor that is not the hash of entry start - 1 fails the first entry. */
static void test_wrong_anchor(void)
{
    unsigned char bogus[RESURS_AUDIT_HASH_LEN];
    memset(bogus, 0xAA, sizeof bogus);

    int first_invalid;
    check(verify_segment(&g_chain, 1, 2, bogus, &first_invalid) == RESURS_AUDIT_OK &&
              first_invalid == 1,
          "segment: wrong anchor -> first entry of the segment at its global index");
}

/* A segment starting mid-chain without an anchor is rejected. */
static void test_missing_anchor(void)
{
    const struct abi_chain *c = &g_chain;
    int first_invalid = -2;
    check(resurs_audit_verify_chain(c->hashes + RESURS_AUDIT_HASH_LEN,
                                    c->signatures + RESURS_AUDIT_SIG_LEN,
                                    c->signature_lens + 1,
                                    c->entries_buf + c->entry_offset[1], c->entry_lens + 1,
                                    2, c->entries_len - c->entry_offset[1],
                                    2 * RESURS_AUDIT_SIG_LEN,
                                    1, NULL, g_pub, &first_invalid) == RESURS_AUDIT_ERR_INVALID_ARG,
          "segment: start_index > 0 with prev_hash NULL -> INVALID_ARG");
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

    test_every_segment_verifies();
    test_tampered_entry_reported_at_global_index();
    test_wrong_anchor();
    test_missing_anchor();

    resurs_audit_shutdown();
    abi_teardown(KEY_PATH, PUB_PATH);
    return test_summary();
}
