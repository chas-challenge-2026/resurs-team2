/* ABI: resurs_encrypt_pii_raw - the cases a NUL-terminated string can't
 * express (embedded NUL, file-sized data), its buffer contract and bounds. */
#include "resurs_crypto.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <stdlib.h>
#include <string.h>

static const char *const KEY_PATH = "crypto_abi_raw.key";

static unsigned char g_nonce[RESURS_NONCE_LEN];

/* 5 bytes with an embedded NUL in the middle. */
static const unsigned char RAW[] = {'a', 'b', '\0', 'c', 'd'};

static void test_raw_embedded_nul(void)
{
    const size_t raw_len = sizeof RAW;
    const size_t raw_ct_expected = RESURS_KEY_VERSION_LEN + raw_len + RESURS_TAG_LEN;

    unsigned char raw_ct[64];
    size_t raw_ct_len = sizeof raw_ct;
    check(resurs_encrypt_pii_raw(RAW, raw_len, g_nonce, RESURS_NONCE_LEN, raw_ct, &raw_ct_len) == RESURS_OK,
          "encrypt_raw: OK for data with an embedded NUL");
    check(raw_ct_len == raw_ct_expected,
          "encrypt_raw: ciphertext length == version + data_len + tag");

    char raw_out[64];
    size_t raw_out_len = sizeof raw_out;
    check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, raw_ct, raw_ct_len, raw_out, &raw_out_len) == RESURS_OK,
          "decrypt: OK for _raw ciphertext (same format as resurs_encrypt_pii)");
    check(raw_out_len == raw_len && memcmp(raw_out, RAW, raw_len) == 0,
          "decrypt: full 5 bytes round-trip through _raw, embedded NUL intact");

    /* Contrast: the string-based wrapper truncates at the first NUL. */
    unsigned char trunc_ct[64];
    size_t trunc_ct_len = sizeof trunc_ct;
    check(resurs_encrypt_pii((const char *)RAW, g_nonce, RESURS_NONCE_LEN, trunc_ct, &trunc_ct_len) == RESURS_OK,
          "encrypt (string wrapper): OK, but only sees up to the NUL");
    char trunc_out[64];
    size_t trunc_out_len = sizeof trunc_out;
    check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, trunc_ct, trunc_ct_len, trunc_out, &trunc_out_len) == RESURS_OK,
          "decrypt: OK for the truncated ciphertext");
    check(trunc_out_len == 2 && memcmp(trunc_out, "ab", 2) == 0,
          "decrypt: string wrapper round-trips only \"ab\" (2 bytes) -- this is exactly why _raw exists");
}

/* Output buffer capacity / nonce: same cases as encrypt, via _raw. */
static void test_raw_buffer(void)
{
    const size_t raw_len = sizeof RAW;
    const size_t raw_ct_expected = RESURS_KEY_VERSION_LEN + raw_len + RESURS_TAG_LEN;

    unsigned char small[4];
    size_t n = sizeof small; /* smaller than raw_ct_expected */
    check(resurs_encrypt_pii_raw(RAW, raw_len, g_nonce, RESURS_NONCE_LEN, small, &n) == RESURS_ERR_BUFFER_SMALL,
          "encrypt_raw: small buffer -> BUFFER_SMALL");
    check(n == raw_ct_expected, "encrypt_raw: BUFFER_SMALL reports the required size");

    size_t q = 0;
    check(resurs_encrypt_pii_raw(RAW, raw_len, g_nonce, RESURS_NONCE_LEN, NULL, &q) == RESURS_ERR_BUFFER_SMALL,
          "encrypt_raw: size query (NULL, 0) -> BUFFER_SMALL");
    check(q == raw_ct_expected, "encrypt_raw: size query reports the required size");

    check(resurs_encrypt_pii_raw(RAW, raw_len, g_nonce, RESURS_NONCE_LEN - 1, small, &n) == RESURS_ERR_INVALID_ARG,
          "encrypt_raw: wrong nonce_len -> INVALID_ARG");
}

/* _raw allows well past RESURS_MAX_PLAINTEXT_LEN -- that bound only applies
 * to the string wrapper. Prove it with data one byte over the text limit,
 * round-tripped through decrypt too. */
static void test_raw_past_text_bound(void)
{
    size_t past_text_bound = RESURS_MAX_PLAINTEXT_LEN + 1;
    unsigned char *big_data = malloc(past_text_bound);
    check(big_data != NULL, "encrypt_raw: allocated data past the text bound");
    if (big_data)
    {
        memset(big_data, 'f', past_text_bound); /* 'f' for "file" */
        size_t big_ct_expected = RESURS_KEY_VERSION_LEN + past_text_bound + RESURS_TAG_LEN;
        unsigned char *big_ct = malloc(big_ct_expected);
        size_t big_ct_len = big_ct_expected;
        check(big_ct != NULL, "encrypt_raw: allocated ciphertext buffer");
        if (big_ct)
        {
            check(resurs_encrypt_pii_raw(big_data, past_text_bound, g_nonce, RESURS_NONCE_LEN,
                                         big_ct, &big_ct_len) == RESURS_OK,
                  "encrypt_raw: OK for data past RESURS_MAX_PLAINTEXT_LEN (file-sized data)");

            char *big_out = malloc(past_text_bound);
            size_t big_out_len = past_text_bound;
            check(big_out != NULL, "encrypt_raw: allocated plaintext-out buffer");
            if (big_out)
            {
                check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, big_ct, big_ct_len,
                                         big_out, &big_out_len) == RESURS_OK,
                      "decrypt: OK for a _raw ciphertext past RESURS_MAX_PLAINTEXT_LEN");
                check(big_out_len == past_text_bound &&
                          memcmp(big_out, big_data, past_text_bound) == 0,
                      "decrypt: file-sized data round-trips through _raw intact");
                free(big_out);
            }
            free(big_ct);
        }
        free(big_data);
    }

    /* And resurs_encrypt_pii (string wrapper) still rejects the same
     * length -- its own RESURS_MAX_PLAINTEXT_LEN check stays tight. */
    char *big_string = malloc(past_text_bound + 1);
    check(big_string != NULL, "encrypt: allocated oversized string");
    if (big_string)
    {
        memset(big_string, 'a', past_text_bound);
        big_string[past_text_bound] = '\0';
        size_t sn = 0;
        check(resurs_encrypt_pii(big_string, g_nonce, RESURS_NONCE_LEN, NULL, &sn) == RESURS_ERR_INVALID_ARG,
              "encrypt (string wrapper): still rejects the same length -> INVALID_ARG");
        free(big_string);
    }
}

static void test_raw_over_max(void)
{
    size_t raw_over_max = RESURS_MAX_RAW_LEN + 1;
    unsigned char *raw_huge = malloc(raw_over_max);
    check(raw_huge != NULL, "encrypt_raw: allocated data over RESURS_MAX_RAW_LEN");
    if (raw_huge)
    {
        memset(raw_huge, 'a', raw_over_max);
        size_t big_n = 0;
        check(resurs_encrypt_pii_raw(raw_huge, raw_over_max, g_nonce, RESURS_NONCE_LEN, NULL, &big_n) == RESURS_ERR_INVALID_ARG,
              "encrypt_raw: data over RESURS_MAX_RAW_LEN -> INVALID_ARG");
        free(raw_huge);
    }
}

int main(void)
{
    if (abi_setup(KEY_PATH) != 0)
    {
        return 1;
    }
    fill_test_nonce(g_nonce);

    test_raw_embedded_nul();
    test_raw_buffer();
    test_raw_past_text_bound();
    test_raw_over_max();

    abi_teardown(KEY_PATH);
    return test_summary();
}
