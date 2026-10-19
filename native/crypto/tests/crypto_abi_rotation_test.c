/* ABI: hot key rotation - resurs_crypto_reload and resurs_crypto_active_version.
 * Starts from abi_setup() (ring {v1}, active 1) and rotates through several
 * key ring files. */
#include "resurs_crypto.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <stdio.h>
#include <string.h>

static const char *const KEY_PATH = "crypto_abi_rotation.keyring";
static const char *const RING_V12_PATH = "crypto_abi_rotation_v12.keyring";      /* {1,2} active 2 */
static const char *const RING_V2_PATH = "crypto_abi_rotation_v2.keyring";        /* {2}   active 2 */
static const char *const RING_V3_PATH = "crypto_abi_rotation_v3.keyring";        /* {3}   active 3 */
static const char *const RING_V1_OTHER_PATH = "crypto_abi_rotation_v1x.keyring"; /* {1,2} v1 bytes differ */
static const char *const RING_BAD_PATH = "crypto_abi_rotation_bad.keyring";      /* active not in ring */

static unsigned char g_nonce[RESURS_NONCE_LEN];

/* Encrypts the sample value; returns the version byte stamped on it, or -1. */
static int encrypt_sample(unsigned char *ct, size_t *ct_len)
{
    *ct_len = 64;
    if (abi_encrypt_sample(g_nonce, ct, ct_len) != RESURS_OK)
    {
        return -1;
    }
    return ct[0];
}

/* True if ct decrypts back to the sample value. */
static int decrypts_to_sample(const unsigned char *ct, size_t ct_len)
{
    char out[64];
    size_t out_len = sizeof out;
    return resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, ct, ct_len, out, &out_len) == RESURS_OK &&
           out_len == strlen(ABI_SAMPLE_PLAIN) && memcmp(out, ABI_SAMPLE_PLAIN, out_len) == 0;
}

static int decrypt_rc(const unsigned char *ct, size_t ct_len)
{
    char out[64];
    size_t out_len = sizeof out;
    return resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, ct, ct_len, out, &out_len);
}

static int hmac_sample(unsigned char out[RESURS_HMAC_LEN])
{
    size_t n = RESURS_HMAC_LEN;
    return resurs_hmac_sha256((const unsigned char *)ABI_SAMPLE_PLAIN, strlen(ABI_SAMPLE_PLAIN), out, &n);
}

static int write_rings(void)
{
    const unsigned char v12[] = {1, 2};
    const unsigned char v2[] = {2};
    const unsigned char v3[] = {3};
    if (write_test_ring(RING_V12_PATH, 2, v12, 2, 0) != 0 ||
        write_test_ring(RING_V2_PATH, 2, v2, 1, 0) != 0 ||
        write_test_ring(RING_V3_PATH, 3, v3, 1, 0) != 0 ||
        write_test_ring(RING_BAD_PATH, 9, v12, 2, 0) != 0)
    {
        return -1;
    }

    /* Same as {1,2}, but v1 (the first entry) gets key bytes from another seed. */
    unsigned char v1x[ABI_RING_LEN(2)];
    size_t len = build_test_ring(v1x, 2, v12, 2, 0);
    fill_test_ring_key(v1x + ABI_RING_HEADER_LEN + 1, 1, 99);
    return write_file(RING_V1_OTHER_PATH, v1x, len);
}

static void remove_rings(void)
{
    remove(RING_V12_PATH);
    remove(RING_V2_PATH);
    remove(RING_V3_PATH);
    remove(RING_V1_OTHER_PATH);
    remove(RING_BAD_PATH);
}

static void test_rotation(void)
{
    unsigned char ct1[64], ct2[64];
    size_t ct1_len, ct2_len;
    unsigned char h_before[RESURS_HMAC_LEN], h_after[RESURS_HMAC_LEN];

    check(resurs_crypto_reload(NULL) == RESURS_ERR_INVALID_ARG, "reload(NULL) -> INVALID_ARG");

    check(resurs_crypto_active_version() == 1, "start: active version 1");
    check(encrypt_sample(ct1, &ct1_len) == 1, "encrypt before rotation -> version byte 1");
    check(hmac_sample(h_before) == RESURS_OK, "hmac before rotation");

    /* Rotate: add v2 and make it active. */
    check(resurs_crypto_reload(RING_V12_PATH) == RESURS_OK, "reload {1,2} active 2 -> OK");
    check(resurs_crypto_active_version() == 2, "after rotation: active version 2");
    check(encrypt_sample(ct2, &ct2_len) == 2, "encrypt after rotation -> version byte 2");
    check(decrypts_to_sample(ct1, ct1_len), "old v1 ciphertext still decrypts");
    check(decrypts_to_sample(ct2, ct2_len), "new v2 ciphertext decrypts");
    check(hmac_sample(h_after) == RESURS_OK && memcmp(h_before, h_after, RESURS_HMAC_LEN) == 0,
          "blind index unchanged by reload (lookup key untouched)");

    /* Moving the version byte to another key must fail authentication, not
     * produce garbage plaintext. */
    unsigned char swapped[64];
    memcpy(swapped, ct1, ct1_len);
    swapped[0] = 2;
    check(decrypt_rc(swapped, ct1_len) == RESURS_ERR_AUTH, "v1 data relabelled as v2 -> AUTH");

    /* Rejected reloads: the current ring stays in use. */
    check(resurs_crypto_reload("/nonexistent/resurs.keyring") == RESURS_ERR_KEY_IO,
          "reload(missing file) -> KEY_IO");
    check(resurs_crypto_reload(RING_BAD_PATH) == RESURS_ERR_KEY_IO,
          "reload(invalid ring: active not in ring) -> KEY_IO");
    check(resurs_crypto_reload(RING_V1_OTHER_PATH) == RESURS_ERR_KEY_REJECTED,
          "reload(v1 with different key bytes) -> KEY_REJECTED");
    check(resurs_crypto_reload(RING_V3_PATH) == RESURS_ERR_KEY_REJECTED,
          "reload(ring without active v2) -> KEY_REJECTED");
    check(resurs_crypto_active_version() == 2, "after rejected reloads: active version still 2");
    check(decrypts_to_sample(ct1, ct1_len) && decrypts_to_sample(ct2, ct2_len),
          "after rejected reloads: v1 and v2 data still decrypt");

    /* After re-encryption, the inactive v1 may be dropped. */
    check(resurs_crypto_reload(RING_V2_PATH) == RESURS_OK, "reload {2} (drop inactive v1) -> OK");
    check(decrypt_rc(ct1, ct1_len) == RESURS_ERR_KEY_VERSION, "dropped v1: v1 ciphertext -> KEY_VERSION");
    check(decrypts_to_sample(ct2, ct2_len), "dropped v1: v2 ciphertext still decrypts");

    /* Size query for a dropped version: the version is checked first. */
    size_t need = 0;
    check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, ct1, ct1_len, NULL, &need) == RESURS_ERR_KEY_VERSION,
          "size query on unknown version -> KEY_VERSION (before BUFFER_SMALL)");
}

int main(void)
{
    fill_test_nonce(g_nonce);

    if (abi_setup(KEY_PATH) != 0)
    {
        return 1;
    }
    if (write_rings() != 0)
    {
        fprintf(stderr, "FATAL: cannot write test key rings\n");
        remove_rings();
        abi_teardown(KEY_PATH);
        return 1;
    }

    test_rotation();

    remove_rings();
    abi_teardown(KEY_PATH);
    return test_summary();
}
