/* Shared setup for the crypto ABI tests (C only).
 *
 * Each test executable passes its own key_path: ctest may run the executables
 * in parallel in the same working directory, so a shared key file would race
 * on create/remove.
 *
 * Setup failures go to stderr as FATAL, not through check(): they are not
 * test results, and keeping them out of the [PASS]/[FAIL] stream keeps the
 * combined check list identical across executables.
 */
#ifndef RESURS_ABI_FIXTURE_H
#define RESURS_ABI_FIXTURE_H

#include "resurs_crypto.h"

#include <stddef.h>
#include <stdio.h>

/* Writes n bytes to path. Returns 0 on success, -1 on failure. */
static inline int write_file(const char *path, const unsigned char *data, size_t n)
{
    FILE *f = fopen(path, "wb");
    if (!f)
    {
        return -1;
    }
    size_t w = fwrite(data, 1, n, f);
    if (fclose(f) != 0)
    {
        return -1;
    }
    return (w == n) ? 0 : -1;
}

/* Key file: 64 raw bytes = 32 AES + 32 HMAC lookup. Deterministic for the test. */
#define ABI_KEY_FILE_LEN 64

static inline void fill_test_key(unsigned char key[ABI_KEY_FILE_LEN])
{
    for (int i = 0; i < ABI_KEY_FILE_LEN; i++)
    {
        key[i] = (unsigned char)(i + 1);
    }
}

/* Fixed test nonce 1..12. Test-only: never reuse a nonce for real data. */
static inline void fill_test_nonce(unsigned char nonce[RESURS_NONCE_LEN])
{
    for (int i = 0; i < RESURS_NONCE_LEN; i++)
    {
        nonce[i] = (unsigned char)(i + 1);
    }
}

/* Sample PII value used across the ABI tests. */
#define ABI_SAMPLE_PLAIN "556000-1234"

/* Encrypts ABI_SAMPLE_PLAIN into ct (*ct_len: capacity in, written size out).
 * Used by main() to build the shared valid ciphertext; not a check itself. */
static inline int abi_encrypt_sample(const unsigned char nonce[RESURS_NONCE_LEN],
                                     unsigned char *ct, size_t *ct_len)
{
    int rc = resurs_encrypt_pii(ABI_SAMPLE_PLAIN, nonce, RESURS_NONCE_LEN, ct, ct_len);
    if (rc != RESURS_OK)
    {
        fprintf(stderr, "FATAL: encrypting the sample value failed with code %d\n", rc);
    }
    return rc;
}

/* Writes the deterministic 64-byte key (bytes 1..64) to key_path and calls
 * resurs_crypto_init. On failure prints "FATAL: ..." to stderr and returns
 * non-zero. */
static inline int abi_setup(const char *key_path)
{
    unsigned char key64[ABI_KEY_FILE_LEN];
    fill_test_key(key64);

    if (write_file(key_path, key64, sizeof key64) != 0)
    {
        fprintf(stderr, "FATAL: cannot write key file %s\n", key_path);
        return 1;
    }

    int rc = resurs_crypto_init(key_path);
    if (rc != RESURS_OK)
    {
        fprintf(stderr, "FATAL: resurs_crypto_init(%s) failed with code %d\n", key_path, rc);
        remove(key_path);
        return 1;
    }
    return 0;
}

/* resurs_crypto_shutdown() + remove(key_path). */
static inline void abi_teardown(const char *key_path)
{
    resurs_crypto_shutdown();
    remove(key_path);
}

#endif /* RESURS_ABI_FIXTURE_H */
