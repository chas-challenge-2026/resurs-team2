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

#include <errno.h>
#include <fcntl.h>
#include <stddef.h>
#include <stdio.h>
#include <sys/stat.h>
#include <unistd.h>

/* Writes n bytes to path with mode 0600 (key files that group/others can write
 * are rejected, so the result must not depend on the caller's umask).
 * open() with 0600 means a new file never has wider rights, not even briefly;
 * fchmod() also fixes the mode of a file that already existed (O_TRUNC keeps it).
 * Returns 0 on success, -1 on failure. */
static inline int write_file(const char *path, const unsigned char *data, size_t n)
{
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd < 0)
    {
        return -1;
    }
    int ok = fchmod(fd, 0600) == 0;
    size_t done = 0;
    while (ok && done < n)
    {
        ssize_t w = write(fd, data + done, n - done);
        if (w < 0 && errno == EINTR)
        {
            continue;
        }
        ok = w > 0;
        if (ok)
        {
            done += (size_t)w;
        }
    }
    if (close(fd) != 0)
    {
        ok = 0;
    }
    return ok ? 0 : -1;
}

/* Key ring file (see resurs_crypto.h): 7-byte header + 33 bytes per version. */
#define ABI_RING_HEADER_LEN 7
#define ABI_RING_ENTRY_LEN (1 + RESURS_KEY_LEN)
#define ABI_RING_LEN(count) (ABI_RING_HEADER_LEN + (count) * ABI_RING_ENTRY_LEN)
#define ABI_RING_MAX_VERSIONS 8 /* enough for the tests, keeps buffers on the stack */

/* Deterministic AES test key for a version; a different seed gives different bytes. */
static inline void fill_test_ring_key(unsigned char key[RESURS_KEY_LEN],
                                      unsigned char version, unsigned char seed)
{
    for (int i = 0; i < RESURS_KEY_LEN; i++)
    {
        key[i] = (unsigned char)(version * 31 + seed * 7 + i);
    }
}

/* Builds a key ring image holding `versions` (keys from fill_test_ring_key)
 * into buf, which must hold ABI_RING_LEN(count) bytes; count must not exceed
 * ABI_RING_MAX_VERSIONS. Does not validate: tests may build rings the module
 * must reject. Returns the image length. */
static inline size_t build_test_ring(unsigned char *buf, unsigned char active,
                                     const unsigned char *versions, size_t count,
                                     unsigned char seed)
{
    buf[0] = 'R';
    buf[1] = 'S';
    buf[2] = 'K';
    buf[3] = 'R';
    buf[4] = 1; /* format */
    buf[5] = active;
    buf[6] = (unsigned char)count;
    for (size_t i = 0; i < count; i++)
    {
        unsigned char *entry = buf + ABI_RING_HEADER_LEN + i * ABI_RING_ENTRY_LEN;
        entry[0] = versions[i];
        fill_test_ring_key(entry + 1, versions[i], seed);
    }
    return ABI_RING_LEN(count);
}

/* build_test_ring + write to path. Returns 0 on success, -1 on failure. */
static inline int write_test_ring(const char *path, unsigned char active,
                                  const unsigned char *versions, size_t count,
                                  unsigned char seed)
{
    unsigned char buf[ABI_RING_LEN(ABI_RING_MAX_VERSIONS)];
    if (count > ABI_RING_MAX_VERSIONS)
    {
        return -1;
    }
    return write_file(path, buf, build_test_ring(buf, active, versions, count, seed));
}

/* Deterministic 32-byte HMAC lookup key: 0x80, 0x81, ... */
static inline void fill_test_lookup_key(unsigned char key[RESURS_KEY_LEN])
{
    for (int i = 0; i < RESURS_KEY_LEN; i++)
    {
        key[i] = (unsigned char)(0x80 + i);
    }
}

/* The lookup key file that goes with a key ring path: "<ring_path>.lookup". */
static inline void abi_lookup_path(const char *ring_path, char *out, size_t out_len)
{
    snprintf(out, out_len, "%s.lookup", ring_path);
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

/* Writes a key ring with version 1 only (active 1) to key_path, the
 * deterministic lookup key to "<key_path>.lookup", and calls resurs_crypto_init.
 * On failure prints "FATAL: ..." to stderr and returns non-zero. */
static inline int abi_setup(const char *key_path)
{
    char lookup_path[256];
    abi_lookup_path(key_path, lookup_path, sizeof lookup_path);

    const unsigned char v1[] = {1};
    unsigned char lookup[RESURS_KEY_LEN];
    fill_test_lookup_key(lookup);

    if (write_test_ring(key_path, 1, v1, 1, 0) != 0 ||
        write_file(lookup_path, lookup, sizeof lookup) != 0)
    {
        fprintf(stderr, "FATAL: cannot write key files %s / %s\n", key_path, lookup_path);
        return 1;
    }

    int rc = resurs_crypto_init(key_path, lookup_path);
    if (rc != RESURS_OK)
    {
        fprintf(stderr, "FATAL: resurs_crypto_init(%s) failed with code %d\n", key_path, rc);
        remove(key_path);
        remove(lookup_path);
        return 1;
    }
    return 0;
}

/* resurs_crypto_shutdown() + remove both key files. */
static inline void abi_teardown(const char *key_path)
{
    char lookup_path[256];
    abi_lookup_path(key_path, lookup_path, sizeof lookup_path);

    resurs_crypto_shutdown();
    remove(key_path);
    remove(lookup_path);
}

#endif /* RESURS_ABI_FIXTURE_H */
