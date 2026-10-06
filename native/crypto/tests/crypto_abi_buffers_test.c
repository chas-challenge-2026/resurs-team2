/* ABI: output-buffer capacity contract (BUFFER_SMALL + required size, size
 * queries) for encrypt/decrypt/hmac, and exact nonce_len enforcement. */
#include "resurs_crypto.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <string.h>

static const char *const KEY_PATH = "crypto_abi_buffers.key";

/* Shared valid ciphertext of ABI_SAMPLE_PLAIN, built once in main(). */
static unsigned char g_nonce[RESURS_NONCE_LEN];
static unsigned char g_ct[64];
static size_t g_ct_len;

static void test_encrypt_buffer(void)
{
    const char *plain = ABI_SAMPLE_PLAIN;
    const size_t ct_expected = RESURS_KEY_VERSION_LEN + strlen(plain) + RESURS_TAG_LEN;

    unsigned char small[4];
    size_t n = sizeof small; /* smaller than ct_expected */
    check(resurs_encrypt_pii(plain, g_nonce, RESURS_NONCE_LEN, small, &n) == RESURS_ERR_BUFFER_SMALL,
          "encrypt: small buffer -> BUFFER_SMALL");
    check(n == ct_expected, "encrypt: BUFFER_SMALL reports the required size");

    size_t q = 0;
    check(resurs_encrypt_pii(plain, g_nonce, RESURS_NONCE_LEN, NULL, &q) == RESURS_ERR_BUFFER_SMALL,
          "encrypt: size query (NULL, 0) -> BUFFER_SMALL");
    check(q == ct_expected, "encrypt: size query reports the required size");

    size_t bad = 8; /* non-zero capacity with a NULL buffer */
    check(resurs_encrypt_pii(plain, g_nonce, RESURS_NONCE_LEN, NULL, &bad) == RESURS_ERR_INVALID_ARG,
          "encrypt: NULL buffer with non-zero capacity -> INVALID_ARG");
}

static void test_decrypt_buffer(void)
{
    const size_t pt_expected = strlen(ABI_SAMPLE_PLAIN);

    char small[2];
    size_t n = sizeof small; /* smaller than pt_expected */
    check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, g_ct, g_ct_len, small, &n) == RESURS_ERR_BUFFER_SMALL,
          "decrypt: small buffer -> BUFFER_SMALL");
    check(n == pt_expected, "decrypt: BUFFER_SMALL reports the required size");

    size_t q = 0;
    check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, g_ct, g_ct_len, NULL, &q) == RESURS_ERR_BUFFER_SMALL,
          "decrypt: size query (NULL, 0) -> BUFFER_SMALL");
    check(q == pt_expected, "decrypt: size query reports the required size");
}

static void test_hmac_buffer(void)
{
    const char *plain = ABI_SAMPLE_PLAIN;
    const size_t plain_len = strlen(plain);

    unsigned char h[RESURS_HMAC_LEN];
    unsigned char small[RESURS_HMAC_LEN - 1];
    size_t n = sizeof small;
    check(resurs_hmac_sha256((const unsigned char *)plain, plain_len, small, &n) == RESURS_ERR_BUFFER_SMALL,
          "hmac: small buffer -> BUFFER_SMALL");
    check(n == RESURS_HMAC_LEN, "hmac: BUFFER_SMALL reports the required size");

    size_t ok = sizeof h;
    check(resurs_hmac_sha256((const unsigned char *)plain, plain_len, h, &ok) == RESURS_OK,
          "hmac: exact buffer -> OK");
    check(ok == RESURS_HMAC_LEN, "hmac: OK reports the written size");
}

/* nonce_len must be declared and exact. */
static void test_nonce_length(void)
{
    unsigned char ct2[64];
    size_t n = sizeof ct2;
    check(resurs_encrypt_pii(ABI_SAMPLE_PLAIN, g_nonce, RESURS_NONCE_LEN - 1, ct2, &n) == RESURS_ERR_INVALID_ARG,
          "encrypt: short nonce_len -> INVALID_ARG");

    char out2[64];
    size_t m = sizeof out2;
    check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN + 1, g_ct, g_ct_len, out2, &m) == RESURS_ERR_INVALID_ARG,
          "decrypt: wrong nonce_len -> INVALID_ARG");
}

int main(void)
{
    if (abi_setup(KEY_PATH) != 0)
    {
        return 1;
    }
    fill_test_nonce(g_nonce);
    g_ct_len = sizeof g_ct;
    if (abi_encrypt_sample(g_nonce, g_ct, &g_ct_len) != RESURS_OK)
    {
        abi_teardown(KEY_PATH);
        return 1;
    }

    test_encrypt_buffer();
    test_decrypt_buffer();
    test_hmac_buffer();
    test_nonce_length();

    abi_teardown(KEY_PATH);
    return test_summary();
}
