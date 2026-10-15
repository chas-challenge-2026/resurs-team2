/* Shared setup for the audit ABI tests (C only).
 *
 * Each test executable passes its own key/pubkey paths: ctest may run the
 * executables in parallel in the same working directory, so shared files
 * would race on create/remove.
 *
 * Setup failures go to stderr as FATAL, not through check(): they are not
 * test results, and keeping them out of the [PASS]/[FAIL] stream keeps the
 * combined check list identical across executables.
 */
#ifndef RESURS_AUDIT_ABI_FIXTURE_H
#define RESURS_AUDIT_ABI_FIXTURE_H

#include "resurs_audit.h"

#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Reads the last n bytes of a file. Used to strip the fixed-size Ed25519
 * SubjectPublicKeyInfo DER header (12 bytes) and keep only the raw 32-byte
 * public key that openssl pkey -pubout -outform DER produces. */
static inline int read_last_n_bytes(const char *path, unsigned char *out, size_t n)
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

/* Generates an Ed25519 key at key_path via the openssl CLI - the same tool
 * infra/Makefile uses to provision the real signing key - and extracts its raw
 * public key into pub (through a DER file at pub_path). Does not call init.
 * On failure prints "FATAL: ..." to stderr, removes both files, returns non-zero. */
static inline int abi_make_key(const char *key_path, const char *pub_path,
                               unsigned char pub[RESURS_AUDIT_PUBKEY_LEN])
{
    char cmd[512];
    snprintf(cmd, sizeof cmd, "openssl genpkey -algorithm ED25519 -out %s 2>/dev/null", key_path);
    if (system(cmd) != 0)
    {
        fprintf(stderr, "FATAL: openssl genpkey failed for %s\n", key_path);
        remove(key_path);
        return 1;
    }

    snprintf(cmd, sizeof cmd,
             "openssl pkey -in %s -pubout -outform DER -out %s 2>/dev/null",
             key_path, pub_path);
    if (system(cmd) != 0)
    {
        fprintf(stderr, "FATAL: openssl pkey -pubout failed for %s\n", key_path);
        remove(key_path);
        remove(pub_path);
        return 1;
    }

    if (read_last_n_bytes(pub_path, pub, RESURS_AUDIT_PUBKEY_LEN) != 0)
    {
        fprintf(stderr, "FATAL: cannot read raw public key from %s\n", pub_path);
        remove(key_path);
        remove(pub_path);
        return 1;
    }
    return 0;
}

/* abi_make_key + resurs_audit_init(key_path). On failure prints "FATAL: ..."
 * to stderr, removes both files, returns non-zero. */
static inline int abi_setup(const char *key_path, const char *pub_path,
                            unsigned char pub[RESURS_AUDIT_PUBKEY_LEN])
{
    if (abi_make_key(key_path, pub_path, pub) != 0)
    {
        return 1;
    }

    int rc = resurs_audit_init(key_path);
    if (rc != RESURS_AUDIT_OK)
    {
        fprintf(stderr, "FATAL: resurs_audit_init(%s) failed with code %d\n", key_path, rc);
        remove(key_path);
        remove(pub_path);
        return 1;
    }
    return 0;
}

/* resurs_audit_shutdown() + remove both files. */
static inline void abi_teardown(const char *key_path, const char *pub_path)
{
    resurs_audit_shutdown();
    remove(key_path);
    remove(pub_path);
}

/* The sample chain used across the ABI tests. */
#define ABI_CHAIN_LEN 3

/* Entry JSON i of the sample chain. A function, not a header-level array, so
 * executables that don't use it get no -Wunused warning. */
static inline const char *abi_entry(int i)
{
    static const char *const entries[ABI_CHAIN_LEN] = {
        "{\"seq\":0,\"event\":\"login\"}",
        "{\"seq\":1,\"event\":\"withdraw\",\"amount\":100}",
        "{\"seq\":2,\"event\":\"logout\"}",
    };
    return entries[i];
}

/* A signed chain in the layout resurs_audit_verify_chain expects. Plain data,
 * so a test can copy the struct and alter its private copy. */
struct abi_chain
{
    size_t entry_lens[ABI_CHAIN_LEN];
    unsigned char hashes[ABI_CHAIN_LEN * RESURS_AUDIT_HASH_LEN];
    unsigned char signatures[ABI_CHAIN_LEN * RESURS_AUDIT_SIG_LEN];
    size_t signature_lens[ABI_CHAIN_LEN];

    /* Entry JSONs back-to-back; entry_offset[i] is where entry i starts, so a
     * tamper test can flip a single byte inside a specific entry. */
    char entries_buf[256];
    size_t entry_offset[ABI_CHAIN_LEN];
    size_t entries_len;
};

/* Chains and signs the sample entries into c. Requires a successful init.
 * Used by main() to build the shared chain; not a check itself. */
static inline int abi_build_chain(struct abi_chain *c)
{
    memset(c, 0, sizeof *c);

    for (int i = 0; i < ABI_CHAIN_LEN; i++)
    {
        c->entry_lens[i] = strlen(abi_entry(i));

        const unsigned char *prev = (i == 0) ? NULL : c->hashes + (i - 1) * RESURS_AUDIT_HASH_LEN;
        int rc = resurs_audit_chain_entry(prev, abi_entry(i), c->entry_lens[i],
                                          c->hashes + i * RESURS_AUDIT_HASH_LEN,
                                          c->signatures + i * RESURS_AUDIT_SIG_LEN,
                                          &c->signature_lens[i]);
        if (rc != RESURS_AUDIT_OK)
        {
            fprintf(stderr, "FATAL: chain_entry(%d) failed with code %d\n", i, rc);
            return rc;
        }

        c->entry_offset[i] = c->entries_len;
        memcpy(c->entries_buf + c->entries_len, abi_entry(i), c->entry_lens[i]);
        c->entries_len += c->entry_lens[i];
    }
    return RESURS_AUDIT_OK;
}

/* resurs_audit_verify_chain over the whole chain c. Sets *first_invalid to -2
 * first, so a value left over from an earlier call cannot pass a check. */
static inline int abi_verify(const struct abi_chain *c,
                             const unsigned char pub[RESURS_AUDIT_PUBKEY_LEN],
                             int *first_invalid)
{
    *first_invalid = -2;
    return resurs_audit_verify_chain(c->hashes, c->signatures, c->signature_lens,
                                     c->entries_buf, c->entry_lens, ABI_CHAIN_LEN,
                                     c->entries_len, sizeof c->signatures,
                                     0, NULL, pub, first_invalid);
}

#endif /* RESURS_AUDIT_ABI_FIXTURE_H */
