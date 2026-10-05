/* ABI: encrypt/decrypt round-trip and rejection of altered input
 * (version byte, ciphertext body, nonce). */
#include "resurs_crypto.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <string.h>

static const char *const KEY_PATH = "crypto_abi_roundtrip.key";

/* Shared valid ciphertext of ABI_SAMPLE_PLAIN, built once in main().
 * Tests that alter it work on a private copy. */
static unsigned char g_nonce[RESURS_NONCE_LEN];
static unsigned char g_ct[64];
static size_t g_ct_len;

static void test_round_trip(void)
{
    const char *plain = ABI_SAMPLE_PLAIN;
    const size_t plain_len = strlen(plain);

    unsigned char ct[64];
    size_t ct_len = sizeof ct;
    check(resurs_encrypt_pii(plain, g_nonce, RESURS_NONCE_LEN, ct, &ct_len) == RESURS_OK,
          "encrypt -> OK");
    check(ct_len == RESURS_KEY_VERSION_LEN + plain_len + RESURS_TAG_LEN,
          "ciphertext length == version + plaintext + tag");
    check(ct[0] == RESURS_KEY_VERSION_CURRENT,
          "first byte == current key version");

    char out[64];
    size_t out_len = sizeof out;
    check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, ct, ct_len, out, &out_len) == RESURS_OK,
          "decrypt -> OK");
    check(out_len == plain_len && memcmp(out, plain, plain_len) == 0,
          "decrypt round-trip matches the original");
}

/* Wrong version byte: rejected before OpenSSL. */
static void test_bad_version_byte(void)
{
    unsigned char ct[64];
    memcpy(ct, g_ct, g_ct_len);
    ct[0] = 0xFF;

    char out[64];
    size_t out_len = sizeof out;
    check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, ct, g_ct_len, out, &out_len) == RESURS_ERR_KEY_VERSION,
          "bad version byte -> KEY_VERSION");
}

/* Flipped ciphertext body: GCM tag no longer verifies. */
static void test_tampered_body(void)
{
    unsigned char ct[64];
    memcpy(ct, g_ct, g_ct_len);
    ct[5] ^= 0x01;

    char out[64];
    size_t out_len = sizeof out;
    check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, ct, g_ct_len, out, &out_len) == RESURS_ERR_AUTH,
          "tampered body -> AUTH");
}

/* Wrong nonce: GCM tag no longer verifies. */
static void test_wrong_nonce(void)
{
    unsigned char wrong_nonce[RESURS_NONCE_LEN];
    memcpy(wrong_nonce, g_nonce, RESURS_NONCE_LEN);
    wrong_nonce[0] ^= 0x01;

    char out[64];
    size_t out_len = sizeof out;
    check(resurs_decrypt_pii(wrong_nonce, RESURS_NONCE_LEN, g_ct, g_ct_len, out, &out_len) == RESURS_ERR_AUTH,
          "wrong nonce -> AUTH");
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

    test_round_trip();
    test_bad_version_byte();
    test_tampered_body();
    test_wrong_nonce();

    abi_teardown(KEY_PATH);
    return test_summary();
}
