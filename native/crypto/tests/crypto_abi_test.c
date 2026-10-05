#include "resurs_crypto.h"

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

static int write_file(const char *path, const unsigned char *data, size_t n)
{
    FILE *f = fopen(path, "wb");
    if (!f)
    {
        return -1;
    }
    size_t w = fwrite(data, 1, n, f);
    fclose(f);
    return (w == n) ? 0 : -1;
}

int main(void)
{
    /* Key file: 64 raw bytes = 32 AES + 32 HMAC lookup. Deterministic for the test. */
    unsigned char key64[64];
    for (int i = 0; i < 64; i++)
    {
        key64[i] = (unsigned char)(i + 1);
    }

    check(write_file("abi_key64.bin", key64, 64) == 0, "wrote 64-byte key file");
    check(write_file("abi_key63.bin", key64, 63) == 0, "wrote 63-byte key file");

    check(resurs_crypto_init(NULL) == RESURS_ERR_INVALID_ARG, "init(NULL) -> INVALID_ARG");
    check(resurs_crypto_init("/nonexistent/resurs.key") == RESURS_ERR_KEY_IO, "init(missing) -> KEY_IO");
    check(resurs_crypto_init("abi_key63.bin") == RESURS_ERR_KEY_IO, "init(63 bytes) -> KEY_IO");
    check(resurs_crypto_init("abi_key64.bin") == RESURS_OK, "init(64 bytes) -> OK");

    /* --- round-trip (key is loaded) --- */
    {
        /* Fixed nonce: this is a test, not production — never reuse a nonce for real data. */
        unsigned char nonce[RESURS_NONCE_LEN];
        for (int i = 0; i < RESURS_NONCE_LEN; i++)
        {
            nonce[i] = (unsigned char)(i + 1);
        }

        const char *plain = "556000-1234";
        const size_t plain_len = strlen(plain);
        const size_t ct_expected = RESURS_KEY_VERSION_LEN + plain_len + RESURS_TAG_LEN;
        const size_t pt_expected = plain_len;

        unsigned char ct[64];
        size_t ct_len = sizeof ct;
        check(resurs_encrypt_pii(plain, nonce, RESURS_NONCE_LEN, ct, &ct_len) == RESURS_OK,
              "encrypt -> OK");
        check(ct_len == ct_expected,
              "ciphertext length == version + plaintext + tag");
        check(ct[0] == RESURS_KEY_VERSION_CURRENT,
              "first byte == current key version");

        char out[64];
        size_t out_len = sizeof out;
        check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, ct, ct_len, out, &out_len) == RESURS_OK,
              "decrypt -> OK");
        check(out_len == pt_expected && memcmp(out, plain, plain_len) == 0,
              "decrypt round-trip matches the original");

        /* --- output buffer capacity: encrypt --- */
        {
            unsigned char small[4];
            size_t n = sizeof small; /* smaller than ct_expected */
            check(resurs_encrypt_pii(plain, nonce, RESURS_NONCE_LEN, small, &n) == RESURS_ERR_BUFFER_SMALL,
                  "encrypt: small buffer -> BUFFER_SMALL");
            check(n == ct_expected, "encrypt: BUFFER_SMALL reports the required size");

            size_t q = 0;
            check(resurs_encrypt_pii(plain, nonce, RESURS_NONCE_LEN, NULL, &q) == RESURS_ERR_BUFFER_SMALL,
                  "encrypt: size query (NULL, 0) -> BUFFER_SMALL");
            check(q == ct_expected, "encrypt: size query reports the required size");

            size_t bad = 8; /* non-zero capacity with a NULL buffer */
            check(resurs_encrypt_pii(plain, nonce, RESURS_NONCE_LEN, NULL, &bad) == RESURS_ERR_INVALID_ARG,
                  "encrypt: NULL buffer with non-zero capacity -> INVALID_ARG");
        }

        /* --- resurs_encrypt_pii_raw: the case a NUL-terminated string can't express --- */
        {
            const unsigned char raw[] = {'a', 'b', '\0', 'c', 'd'};
            const size_t raw_len = sizeof raw; /* 5 bytes, embedded NUL in the middle */
            const size_t raw_ct_expected = RESURS_KEY_VERSION_LEN + raw_len + RESURS_TAG_LEN;

            unsigned char raw_ct[64];
            size_t raw_ct_len = sizeof raw_ct;
            check(resurs_encrypt_pii_raw(raw, raw_len, nonce, RESURS_NONCE_LEN, raw_ct, &raw_ct_len) == RESURS_OK,
                  "encrypt_raw: OK for data with an embedded NUL");
            check(raw_ct_len == raw_ct_expected,
                  "encrypt_raw: ciphertext length == version + data_len + tag");

            char raw_out[64];
            size_t raw_out_len = sizeof raw_out;
            check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, raw_ct, raw_ct_len, raw_out, &raw_out_len) == RESURS_OK,
                  "decrypt: OK for _raw ciphertext (same format as resurs_encrypt_pii)");
            check(raw_out_len == raw_len && memcmp(raw_out, raw, raw_len) == 0,
                  "decrypt: full 5 bytes round-trip through _raw, embedded NUL intact");

            /* Contrast: the string-based wrapper truncates at the first NUL. */
            unsigned char trunc_ct[64];
            size_t trunc_ct_len = sizeof trunc_ct;
            check(resurs_encrypt_pii((const char *)raw, nonce, RESURS_NONCE_LEN, trunc_ct, &trunc_ct_len) == RESURS_OK,
                  "encrypt (string wrapper): OK, but only sees up to the NUL");
            char trunc_out[64];
            size_t trunc_out_len = sizeof trunc_out;
            check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, trunc_ct, trunc_ct_len, trunc_out, &trunc_out_len) == RESURS_OK,
                  "decrypt: OK for the truncated ciphertext");
            check(trunc_out_len == 2 && memcmp(trunc_out, "ab", 2) == 0,
                  "decrypt: string wrapper round-trips only \"ab\" (2 bytes) -- this is exactly why _raw exists");

            /* --- output buffer capacity / bounds: same cases as encrypt, via _raw --- */
            unsigned char small[4];
            size_t n = sizeof small; /* smaller than raw_ct_expected */
            check(resurs_encrypt_pii_raw(raw, raw_len, nonce, RESURS_NONCE_LEN, small, &n) == RESURS_ERR_BUFFER_SMALL,
                  "encrypt_raw: small buffer -> BUFFER_SMALL");
            check(n == raw_ct_expected, "encrypt_raw: BUFFER_SMALL reports the required size");

            size_t q = 0;
            check(resurs_encrypt_pii_raw(raw, raw_len, nonce, RESURS_NONCE_LEN, NULL, &q) == RESURS_ERR_BUFFER_SMALL,
                  "encrypt_raw: size query (NULL, 0) -> BUFFER_SMALL");
            check(q == raw_ct_expected, "encrypt_raw: size query reports the required size");

            check(resurs_encrypt_pii_raw(raw, raw_len, nonce, RESURS_NONCE_LEN - 1, small, &n) == RESURS_ERR_INVALID_ARG,
                  "encrypt_raw: wrong nonce_len -> INVALID_ARG");

            /* _raw allows well past RESURS_MAX_PLAINTEXT_LEN -- that bound only
             * applies to the string wrapper. Prove it with data one byte over the
             * old (text) limit, round-tripped through decrypt too. */
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
                        check(resurs_encrypt_pii_raw(big_data, past_text_bound, nonce, RESURS_NONCE_LEN,
                                                     big_ct, &big_ct_len) == RESURS_OK,
                              "encrypt_raw: OK for data past RESURS_MAX_PLAINTEXT_LEN (file-sized data)");

                        char *big_out = malloc(past_text_bound);
                        size_t big_out_len = past_text_bound;
                        check(big_out != NULL, "encrypt_raw: allocated plaintext-out buffer");
                        if (big_out)
                        {
                            check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, big_ct, big_ct_len,
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
                    check(resurs_encrypt_pii(big_string, nonce, RESURS_NONCE_LEN, NULL, &sn) == RESURS_ERR_INVALID_ARG,
                          "encrypt (string wrapper): still rejects the same length -> INVALID_ARG");
                    free(big_string);
                }
            }

            size_t raw_over_max = RESURS_MAX_RAW_LEN + 1;
            unsigned char *raw_huge = malloc(raw_over_max);
            check(raw_huge != NULL, "encrypt_raw: allocated data over RESURS_MAX_RAW_LEN");
            if (raw_huge)
            {
                memset(raw_huge, 'a', raw_over_max);
                size_t big_n = 0;
                check(resurs_encrypt_pii_raw(raw_huge, raw_over_max, nonce, RESURS_NONCE_LEN, NULL, &big_n) == RESURS_ERR_INVALID_ARG,
                      "encrypt_raw: data over RESURS_MAX_RAW_LEN -> INVALID_ARG");
                free(raw_huge);
            }
        }

        /* --- output buffer capacity: decrypt --- */
        {
            char small[2];
            size_t n = sizeof small; /* smaller than pt_expected */
            check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, ct, ct_len, small, &n) == RESURS_ERR_BUFFER_SMALL,
                  "decrypt: small buffer -> BUFFER_SMALL");
            check(n == pt_expected, "decrypt: BUFFER_SMALL reports the required size");

            size_t q = 0;
            check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, ct, ct_len, NULL, &q) == RESURS_ERR_BUFFER_SMALL,
                  "decrypt: size query (NULL, 0) -> BUFFER_SMALL");
            check(q == pt_expected, "decrypt: size query reports the required size");
        }

        /* --- output buffer capacity: hmac --- */
        {
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

        /* --- nonce length must be declared and exact --- */
        {
            unsigned char ct2[64];
            size_t n = sizeof ct2;
            check(resurs_encrypt_pii(plain, nonce, RESURS_NONCE_LEN - 1, ct2, &n) == RESURS_ERR_INVALID_ARG,
                  "encrypt: short nonce_len -> INVALID_ARG");
            char out2[64];
            size_t m = sizeof out2;
            check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN + 1, ct, ct_len, out2, &m) == RESURS_ERR_INVALID_ARG,
                  "decrypt: wrong nonce_len -> INVALID_ARG");
        }

        /* --- input size bounds --- */
        {
            size_t big = RESURS_MAX_PLAINTEXT_LEN + 1;
            char *huge = malloc(big + 1);
            check(huge != NULL, "allocated oversized plaintext");
            if (huge)
            {
                memset(huge, 'a', big);
                huge[big] = '\0';
                size_t n = 0;
                check(resurs_encrypt_pii(huge, nonce, RESURS_NONCE_LEN, NULL, &n) == RESURS_ERR_INVALID_ARG,
                      "encrypt: plaintext over the max -> INVALID_ARG");
                free(huge);
            }

            /* RESURS_MAX_RAW_LEN, not RESURS_MAX_PLAINTEXT_LEN: decrypt must reject
             * anything past what _raw could have produced, since it can't tell
             * which encrypt path a given ciphertext came from. */
            size_t over = RESURS_KEY_VERSION_LEN + RESURS_MAX_RAW_LEN + RESURS_TAG_LEN + 1;
            unsigned char *blob = malloc(over);
            check(blob != NULL, "allocated oversized ciphertext");
            if (blob)
            {
                memset(blob, 0, over);
                blob[0] = RESURS_KEY_VERSION_CURRENT;
                char *pt = malloc(over);
                size_t n = over;
                check(pt != NULL, "allocated plaintext buffer");
                if (pt)
                {
                    check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, blob, over, pt, &n) == RESURS_ERR_INVALID_ARG,
                          "decrypt: ciphertext over RESURS_MAX_RAW_LEN -> INVALID_ARG");
                    free(pt);
                }
                free(blob);
            }
        }

        /* Wrong version byte: rejected before OpenSSL. */
        unsigned char saved = ct[0];
        ct[0] = 0xFF;
        out_len = sizeof out;
        check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, ct, ct_len, out, &out_len) == RESURS_ERR_KEY_VERSION,
              "bad version byte -> KEY_VERSION");
        ct[0] = saved;

        /* Flipped ciphertext body: GCM tag no longer verifies. */
        ct[5] ^= 0x01;
        out_len = sizeof out;
        check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, ct, ct_len, out, &out_len) == RESURS_ERR_AUTH,
              "tampered body -> AUTH");
        ct[5] ^= 0x01;

        /* Wrong nonce: GCM tag no longer verifies. */
        unsigned char wrong_nonce[RESURS_NONCE_LEN];
        memcpy(wrong_nonce, nonce, RESURS_NONCE_LEN);
        wrong_nonce[0] ^= 0x01;
        out_len = sizeof out;
        check(resurs_decrypt_pii(wrong_nonce, RESURS_NONCE_LEN, ct, ct_len, out, &out_len) == RESURS_ERR_AUTH,
              "wrong nonce -> AUTH");
    }

    /* --- blind-index HMAC (key is loaded) --- */
    {
        const char *org = "556000-1234";
        const size_t org_len = strlen(org);

        unsigned char h1[RESURS_HMAC_LEN];
        unsigned char h2[RESURS_HMAC_LEN];
        unsigned char h3[RESURS_HMAC_LEN];
        size_t n1 = sizeof h1, n2 = sizeof h2, n3 = sizeof h3;

        check(resurs_hmac_sha256((const unsigned char *)org, org_len, h1, &n1) == RESURS_OK,
              "hmac -> OK");
        check(resurs_hmac_sha256((const unsigned char *)org, org_len, h2, &n2) == RESURS_OK,
              "hmac (again) -> OK");
        check(memcmp(h1, h2, RESURS_HMAC_LEN) == 0,
              "hmac is deterministic for the same input");

        check(resurs_hmac_sha256((const unsigned char *)"556000-9999", 11, h3, &n3) == RESURS_OK,
              "hmac (other input) -> OK");
        check(memcmp(h1, h3, RESURS_HMAC_LEN) != 0,
              "hmac differs for different input");

        size_t n = sizeof h1;
        check(resurs_hmac_sha256(NULL, 0, h1, &n) == RESURS_ERR_INVALID_ARG,
              "hmac(NULL data) -> INVALID_ARG");
        check(resurs_hmac_sha256((const unsigned char *)org, org_len, h1, NULL) == RESURS_ERR_INVALID_ARG,
              "hmac(NULL len) -> INVALID_ARG");
    }

    resurs_crypto_shutdown();
    resurs_crypto_shutdown();
    check(1, "shutdown x2 no crash");

    {
        unsigned char h[RESURS_HMAC_LEN];
        size_t n = sizeof h;
        check(resurs_encrypt_pii("x", NULL, 0, NULL, NULL) == RESURS_ERR_NOT_INIT,
              "encrypt after shutdown -> NOT_INIT");
        check(resurs_decrypt_pii(NULL, 0, NULL, 0, NULL, NULL) == RESURS_ERR_NOT_INIT,
              "decrypt after shutdown -> NOT_INIT");
        check(resurs_hmac_sha256((const unsigned char *)"x", 1, h, &n) == RESURS_ERR_NOT_INIT,
              "hmac after shutdown -> NOT_INIT");
    }

    remove("abi_key64.bin");
    remove("abi_key63.bin");

    printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}
