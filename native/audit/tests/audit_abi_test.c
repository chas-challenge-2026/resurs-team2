#include "resurs_audit.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int g_failures = 0;

static void check(int ok, const char *name)
{
    printf("%s %s\n", ok ? "[PASS]" : "[FAIL]", name);
    if (!ok)
        g_failures++;
}

/* Reads the last n bytes of a file. Used to strip the fixed-size Ed25519
 * SubjectPublicKeyInfo DER header (12 bytes) and keep only the raw 32-byte
 * public key that openssl pkey -pubout -outform DER produces. */
static int read_last_n_bytes(const char *path, unsigned char *out, size_t n)
{
    FILE *f = fopen(path, "rb");
    if (!f)
    {
        return -1;
    }
    int rc = fseek(f, -(long)n, SEEK_END);
    if (rc != 0)
    {
        fclose(f);
        return -1;
    }
    size_t r = fread(out, 1, n, f);
    fclose(f);
    return (r == n) ? 0 : -1;
}

/* Verifies the segment of `count` records starting at global position `start`.
 * The arrays are the caller's slice of the chain, not the whole chain. */
static int verify_segment(const unsigned char *hashes, const unsigned char *signatures,
                          const size_t *signature_lens, const char *entries,
                          const size_t *entry_lens, size_t count, size_t entries_len,
                          size_t start, const unsigned char *prev_hash,
                          const unsigned char *pub, int *first_invalid)
{
    return resurs_audit_verify_chain(hashes, signatures, signature_lens, entries, entry_lens,
                                     count, entries_len, count * RESURS_AUDIT_SIG_LEN,
                                     start, prev_hash, pub, first_invalid);
}

int main(void)
{
    const char *key_path = "audit_abi_key.pem";
    const char *pub_der_path = "audit_abi_pub.der";

    /* --- init: argument / IO errors before a real key is loaded --- */
    check(resurs_audit_init(NULL) == RESURS_AUDIT_ERR_INVALID_ARG,
          "init(NULL) -> INVALID_ARG");
    check(resurs_audit_init("/nonexistent/resurs-audit.key") == RESURS_AUDIT_ERR_KEY_IO,
          "init(missing file) -> KEY_IO");

    /* Generate a real Ed25519 keypair via the openssl CLI - the same tool
     * infra/Makefile uses to provision the real signing key. */
    char cmd[512];
    snprintf(cmd, sizeof cmd, "openssl genpkey -algorithm ED25519 -out %s 2>/dev/null", key_path);
    check(system(cmd) == 0, "generated Ed25519 test key via openssl CLI");

    snprintf(cmd, sizeof cmd,
             "openssl pkey -in %s -pubout -outform DER -out %s 2>/dev/null",
             key_path, pub_der_path);
    check(system(cmd) == 0, "derived DER public key via openssl CLI");

    unsigned char pub[RESURS_AUDIT_PUBKEY_LEN];
    check(read_last_n_bytes(pub_der_path, pub, RESURS_AUDIT_PUBKEY_LEN) == 0,
          "extracted raw 32-byte public key from DER");

    check(resurs_audit_init(key_path) == RESURS_AUDIT_OK, "init(valid PEM) -> OK");

    /* --- chain three entries --- */
    const char *entries_arr[3] = {
        "{\"seq\":0,\"event\":\"login\"}",
        "{\"seq\":1,\"event\":\"withdraw\",\"amount\":100}",
        "{\"seq\":2,\"event\":\"logout\"}",
    };
    size_t entry_lens[3];
    unsigned char hashes[3 * RESURS_AUDIT_HASH_LEN];
    unsigned char signatures[3 * RESURS_AUDIT_SIG_LEN];
    size_t signature_lens[3];

    for (int i = 0; i < 3; i++)
    {
        entry_lens[i] = strlen(entries_arr[i]);

        const unsigned char *prev = (i == 0) ? NULL : hashes + (i - 1) * RESURS_AUDIT_HASH_LEN;
        size_t siglen = 0;

        int rc = resurs_audit_chain_entry(prev, entries_arr[i], entry_lens[i],
                                          hashes + i * RESURS_AUDIT_HASH_LEN,
                                          signatures + i * RESURS_AUDIT_SIG_LEN,
                                          &siglen);
        check(rc == RESURS_AUDIT_OK, "chain_entry -> OK");
        check(siglen == RESURS_AUDIT_SIG_LEN, "chain_entry writes a 64-byte signature");

        signature_lens[i] = siglen;
    }

    /* Concatenate the entry JSONs back-to-back, tracking each one's offset
     * so a tamper test can flip a single byte inside a specific entry. */
    char entries_buf[256];
    size_t entry_offset[3];
    size_t off = 0;
    for (int i = 0; i < 3; i++)
    {
        entry_offset[i] = off;
        memcpy(entries_buf + off, entries_arr[i], entry_lens[i]);
        off += entry_lens[i];
    }

    /* --- verify_chain: argument errors (no init required for this call) --- */
    int first_invalid = -2;
    check(resurs_audit_verify_chain(NULL, signatures, signature_lens, entries_buf,
                                    entry_lens, 3, off, 3 * RESURS_AUDIT_SIG_LEN,
                                    0, NULL, pub, &first_invalid) == RESURS_AUDIT_ERR_INVALID_ARG,
          "verify_chain(NULL hashes) -> INVALID_ARG");
    check(resurs_audit_verify_chain(hashes, signatures, signature_lens, entries_buf,
                                    entry_lens, 0, off, 3 * RESURS_AUDIT_SIG_LEN,
                                    0, NULL, pub, &first_invalid) == RESURS_AUDIT_ERR_INVALID_ARG,
          "verify_chain(entry_count == 0) -> INVALID_ARG");

    /* --- verify_chain: intact chain --- */
    first_invalid = -2;
    int rc = resurs_audit_verify_chain(hashes, signatures, signature_lens, entries_buf,
                                       entry_lens, 3, off, 3 * RESURS_AUDIT_SIG_LEN,
                                       0, NULL, pub, &first_invalid);
    check(rc == RESURS_AUDIT_OK, "verify_chain(intact) -> OK");
    check(first_invalid == -1, "verify_chain(intact) -> first_invalid_index == -1");

    /* --- verify_chain: entry_lens[i] claims more than entries_len actually has --- */
    {
        size_t bad_entry_lens[3];
        memcpy(bad_entry_lens, entry_lens, sizeof entry_lens);
        bad_entry_lens[0] = off + 4096; /* much more than entries_buf */

        first_invalid = -2;
        int rc2 = resurs_audit_verify_chain(hashes, signatures, signature_lens, entries_buf,
                                            bad_entry_lens, 3, off, 3 * RESURS_AUDIT_SIG_LEN,
                                            0, NULL, pub, &first_invalid);
        check(rc2 == RESURS_AUDIT_ERR_INVALID_ARG,
              "verify_chain: inflated entry_lens[0] past entries_len -> INVALID_ARG, not a crash");
    }

    /* --- tamper: flip a byte inside entries[1]'s content --- */
    entries_buf[entry_offset[1]] ^= 0x01;
    first_invalid = -2;
    rc = resurs_audit_verify_chain(hashes, signatures, signature_lens, entries_buf,
                                   entry_lens, 3, off, 3 * RESURS_AUDIT_SIG_LEN,
                                   0, NULL, pub, &first_invalid);
    check(rc == RESURS_AUDIT_OK, "verify_chain(tampered entry) -> OK");
    check(first_invalid == 1, "tampered entries[1] -> first_invalid_index == 1");
    entries_buf[entry_offset[1]] ^= 0x01; /* restore */

    /* --- tamper: flip a byte inside signatures[2] --- */
    signatures[2 * RESURS_AUDIT_SIG_LEN] ^= 0x01;
    first_invalid = -2;
    rc = resurs_audit_verify_chain(hashes, signatures, signature_lens, entries_buf,
                                   entry_lens, 3, off, 3 * RESURS_AUDIT_SIG_LEN,
                                   0, NULL, pub, &first_invalid);
    check(rc == RESURS_AUDIT_OK, "verify_chain(tampered signature) -> OK");
    check(first_invalid == 2, "tampered signatures[2] -> first_invalid_index == 2");
    signatures[2 * RESURS_AUDIT_SIG_LEN] ^= 0x01; /* restore */

    /* --- tamper: swap hashes[0] and hashes[1] --- */
    unsigned char saved_hash[RESURS_AUDIT_HASH_LEN];
    memcpy(saved_hash, hashes, RESURS_AUDIT_HASH_LEN);
    memcpy(hashes, hashes + RESURS_AUDIT_HASH_LEN, RESURS_AUDIT_HASH_LEN);
    memcpy(hashes + RESURS_AUDIT_HASH_LEN, saved_hash, RESURS_AUDIT_HASH_LEN);

    first_invalid = -2;
    rc = resurs_audit_verify_chain(hashes, signatures, signature_lens, entries_buf,
                                   entry_lens, 3, off, 3 * RESURS_AUDIT_SIG_LEN,
                                   0, NULL, pub, &first_invalid);
    check(rc == RESURS_AUDIT_OK, "verify_chain(swapped hashes) -> OK");
    check(first_invalid != -1, "swapped hashes[0]/hashes[1] is detected");

    /* restore */
    memcpy(saved_hash, hashes, RESURS_AUDIT_HASH_LEN);
    memcpy(hashes, hashes + RESURS_AUDIT_HASH_LEN, RESURS_AUDIT_HASH_LEN);
    memcpy(hashes + RESURS_AUDIT_HASH_LEN, saved_hash, RESURS_AUDIT_HASH_LEN);

    /* --- chunked verify: every segment [s, s+c) of the fixture verifies intact --- */
    int seg_ok = 1;
    for (size_t s = 0; s < 3; s++)
    {
        for (size_t c = 1; s + c <= 3; c++)
        {
            size_t seg_len = (s + c < 3 ? entry_offset[s + c] : off) - entry_offset[s];
            const unsigned char *anchor = (s > 0) ? hashes + (s - 1) * RESURS_AUDIT_HASH_LEN : NULL;
            first_invalid = -2;
            rc = verify_segment(hashes + s * RESURS_AUDIT_HASH_LEN, signatures + s * RESURS_AUDIT_SIG_LEN,
                                signature_lens + s, entries_buf + entry_offset[s], entry_lens + s,
                                c, seg_len, s, anchor, pub, &first_invalid);
            if (rc != RESURS_AUDIT_OK || first_invalid != -1)
                seg_ok = 0;
        }
    }
    check(seg_ok, "chunked: every segment of the fixture verifies intact");

    /* --- chunked verify: tamper each record, every segment containing it reports
     * that record at its GLOBAL index --- */
    int tamper_ok = 1;
    for (size_t t = 0; t < 3; t++)
    {
        entries_buf[entry_offset[t]] ^= 0x01;
        for (size_t s = 0; s < 3; s++)
        {
            for (size_t c = 1; s + c <= 3; c++)
            {
                if (t < s || t >= s + c)
                    continue;
                size_t seg_len = (s + c < 3 ? entry_offset[s + c] : off) - entry_offset[s];
                const unsigned char *anchor = (s > 0) ? hashes + (s - 1) * RESURS_AUDIT_HASH_LEN : NULL;
                first_invalid = -2;
                rc = verify_segment(hashes + s * RESURS_AUDIT_HASH_LEN, signatures + s * RESURS_AUDIT_SIG_LEN,
                                    signature_lens + s, entries_buf + entry_offset[s], entry_lens + s,
                                    c, seg_len, s, anchor, pub, &first_invalid);
                if (rc != RESURS_AUDIT_OK || first_invalid != (int)t)
                    tamper_ok = 0;
            }
        }
        entries_buf[entry_offset[t]] ^= 0x01; /* restore */
    }
    check(tamper_ok, "chunked: tampered record reported at its global index in every segment containing it");

    /* --- chunked verify: wrong anchor for a segment starting at 1 --- */
    unsigned char bogus_anchor[RESURS_AUDIT_HASH_LEN];
    memset(bogus_anchor, 0xAA, sizeof bogus_anchor);
    first_invalid = -2;
    rc = verify_segment(hashes + RESURS_AUDIT_HASH_LEN, signatures + RESURS_AUDIT_SIG_LEN,
                        signature_lens + 1, entries_buf + entry_offset[1], entry_lens + 1,
                        2, off - entry_offset[1], 1, bogus_anchor, pub, &first_invalid);
    check(rc == RESURS_AUDIT_OK && first_invalid == 1,
          "chunked: wrong anchor reports the first record of the segment at its global index");

    /* --- chunked verify: start_index > 0 without prev_hash -> INVALID_ARG --- */
    check(verify_segment(hashes + RESURS_AUDIT_HASH_LEN, signatures + RESURS_AUDIT_SIG_LEN,
                         signature_lens + 1, entries_buf + entry_offset[1], entry_lens + 1,
                         2, off - entry_offset[1], 1, NULL, pub, &first_invalid) == RESURS_AUDIT_ERR_INVALID_ARG,
          "chunked: start_index > 0 with prev_hash NULL -> INVALID_ARG");

    /* --- shutdown --- */
    resurs_audit_shutdown();

    size_t siglen = RESURS_AUDIT_SIG_LEN;
    unsigned char h_out[RESURS_AUDIT_HASH_LEN];
    unsigned char s_out[RESURS_AUDIT_SIG_LEN];
    check(resurs_audit_chain_entry(NULL, entries_arr[0], entry_lens[0], h_out, s_out, &siglen) ==
              RESURS_AUDIT_ERR_NOT_INIT,
          "chain_entry after shutdown -> NOT_INIT");

    /* verify_chain never touched AuditKeyManager, so it still works after
     * shutdown - it only needs the public_key argument. */
    first_invalid = -2;
    rc = resurs_audit_verify_chain(hashes, signatures, signature_lens, entries_buf,
                                   entry_lens, 3, off, 3 * RESURS_AUDIT_SIG_LEN,
                                   0, NULL, pub, &first_invalid);
    check(rc == RESURS_AUDIT_OK, "verify_chain after shutdown -> OK");
    check(first_invalid == -1, "verify_chain after shutdown still verifies the intact chain");

    remove(key_path);
    remove(pub_der_path);

    printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}
