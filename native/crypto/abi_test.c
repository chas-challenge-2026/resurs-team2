#include "resurs_crypto.h"

#include <stdio.h>
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

        unsigned char ct[64];
        size_t ct_len = 0;
        check(resurs_encrypt_pii(plain, nonce, ct, &ct_len) == RESURS_OK,
              "encrypt -> OK");
        check(ct_len == RESURS_KEY_VERSION_LEN + plain_len + RESURS_TAG_LEN,
              "ciphertext length == version + plaintext + tag");
        check(ct[0] == RESURS_KEY_VERSION_CURRENT,
              "first byte == current key version");

        char out[64];
        size_t out_len = 0;
        check(resurs_decrypt_pii(nonce, ct, ct_len, out, &out_len) == RESURS_OK,
              "decrypt -> OK");
        check(out_len == plain_len && memcmp(out, plain, plain_len) == 0,
              "decrypt round-trip matches the original");

        /* Wrong version byte: rejected before OpenSSL. */
        unsigned char saved = ct[0];
        ct[0] = 0xFF;
        check(resurs_decrypt_pii(nonce, ct, ct_len, out, &out_len) == RESURS_ERR_KEY_VERSION,
              "bad version byte -> KEY_VERSION");
        ct[0] = saved;

        /* Flipped ciphertext body: GCM tag no longer verifies. */
        ct[5] ^= 0x01;
        check(resurs_decrypt_pii(nonce, ct, ct_len, out, &out_len) == RESURS_ERR_AUTH,
              "tampered body -> AUTH");
        ct[5] ^= 0x01;

        /* Wrong nonce: GCM tag no longer verifies. */
        unsigned char wrong_nonce[RESURS_NONCE_LEN];
        memcpy(wrong_nonce, nonce, RESURS_NONCE_LEN);
        wrong_nonce[0] ^= 0x01;
        check(resurs_decrypt_pii(wrong_nonce, ct, ct_len, out, &out_len) == RESURS_ERR_AUTH,
              "wrong nonce -> AUTH");
    }

    /* --- blind-index HMAC (key is loaded) --- */
    {
        const char *org = "556000-1234";
        const size_t org_len = strlen(org);

        unsigned char h1[RESURS_HMAC_LEN];
        unsigned char h2[RESURS_HMAC_LEN];
        unsigned char h3[RESURS_HMAC_LEN];

        check(resurs_hmac_sha256((const unsigned char *)org, org_len, h1) == RESURS_OK,
              "hmac -> OK");
        check(resurs_hmac_sha256((const unsigned char *)org, org_len, h2) == RESURS_OK,
              "hmac (again) -> OK");
        check(memcmp(h1, h2, RESURS_HMAC_LEN) == 0,
              "hmac is deterministic for the same input");

        check(resurs_hmac_sha256((const unsigned char *)"556000-9999", 11, h3) == RESURS_OK,
              "hmac (other input) -> OK");
        check(memcmp(h1, h3, RESURS_HMAC_LEN) != 0,
              "hmac differs for different input");

        check(resurs_hmac_sha256(NULL, 0, h1) == RESURS_ERR_INVALID_ARG,
              "hmac(NULL data) -> INVALID_ARG");
        check(resurs_hmac_sha256((const unsigned char *)org, org_len, NULL) == RESURS_ERR_INVALID_ARG,
              "hmac(NULL out) -> INVALID_ARG");
    }

    resurs_crypto_shutdown();
    resurs_crypto_shutdown();
    check(1, "shutdown x2 no crash");

    {
        unsigned char h[RESURS_HMAC_LEN];
        check(resurs_encrypt_pii("x", NULL, NULL, NULL) == RESURS_ERR_NOT_INIT,
              "encrypt after shutdown -> NOT_INIT");
        check(resurs_decrypt_pii(NULL, NULL, 0, NULL, NULL) == RESURS_ERR_NOT_INIT,
              "decrypt after shutdown -> NOT_INIT");
        check(resurs_hmac_sha256((const unsigned char *)"x", 1, h) == RESURS_ERR_NOT_INIT,
              "hmac after shutdown -> NOT_INIT");
    }

    remove("abi_key64.bin");
    remove("abi_key63.bin");

    printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}
