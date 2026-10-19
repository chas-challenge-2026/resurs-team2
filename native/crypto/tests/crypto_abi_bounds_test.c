/* ABI: input size bounds for the string encrypt path and for decrypt. */
#include "resurs_crypto.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <stdlib.h>
#include <string.h>

static const char *const KEY_PATH = "crypto_abi_bounds.key";

static unsigned char g_nonce[RESURS_NONCE_LEN];

static void test_plaintext_over_max(void)
{
    size_t big = RESURS_MAX_PLAINTEXT_LEN + 1;
    char *huge = malloc(big + 1);
    check(huge != NULL, "allocated oversized plaintext");
    if (huge)
    {
        memset(huge, 'a', big);
        huge[big] = '\0';
        size_t n = 0;
        check(resurs_encrypt_pii(huge, g_nonce, RESURS_NONCE_LEN, NULL, &n) == RESURS_ERR_INVALID_ARG,
              "encrypt: plaintext over the max -> INVALID_ARG");
        free(huge);
    }
}

/* Exactly the max is still allowed (size query, so no 8 KiB output needed). */
static void test_plaintext_at_max(void)
{
    size_t max = RESURS_MAX_PLAINTEXT_LEN;
    char *at_max = malloc(max + 1);
    check(at_max != NULL, "allocated max-length plaintext");
    if (at_max)
    {
        memset(at_max, 'a', max);
        at_max[max] = '\0';
        size_t n = 0;
        check(resurs_encrypt_pii(at_max, g_nonce, RESURS_NONCE_LEN, NULL, &n) == RESURS_ERR_BUFFER_SMALL,
              "encrypt: plaintext of exactly the max -> passes the length check");
        check(n == RESURS_KEY_VERSION_LEN + max + RESURS_TAG_LEN,
              "encrypt: max-length size query reports the required size");
        free(at_max);
    }
}

/* No NUL anywhere: the length scan must stop at max + 1 bytes, i.e.
 * exactly at the end of this buffer (ASAN catches any read past it). */
static void test_plaintext_unterminated(void)
{
    size_t max = RESURS_MAX_PLAINTEXT_LEN;
    char *no_nul = malloc(max + 1);
    check(no_nul != NULL, "allocated unterminated buffer");
    if (no_nul)
    {
        memset(no_nul, 'a', max + 1);
        size_t n = 0;
        check(resurs_encrypt_pii(no_nul, g_nonce, RESURS_NONCE_LEN, NULL, &n) == RESURS_ERR_INVALID_ARG,
              "encrypt: unterminated buffer past the max -> INVALID_ARG, no over-read");
        free(no_nul);
    }
}

/* RESURS_MAX_RAW_LEN, not RESURS_MAX_PLAINTEXT_LEN: decrypt must reject
 * anything past what _raw could have produced, since it can't tell which
 * encrypt path a given ciphertext came from. */
static void test_ciphertext_over_raw_max(void)
{
    size_t over = RESURS_KEY_VERSION_LEN + RESURS_MAX_RAW_LEN + RESURS_TAG_LEN + 1;
    unsigned char *blob = malloc(over);
    check(blob != NULL, "allocated oversized ciphertext");
    if (blob)
    {
        memset(blob, 0, over);
        blob[0] = (unsigned char)resurs_crypto_active_version();
        char *pt = malloc(over);
        size_t n = over;
        check(pt != NULL, "allocated plaintext buffer");
        if (pt)
        {
            check(resurs_decrypt_pii(g_nonce, RESURS_NONCE_LEN, blob, over, pt, &n) == RESURS_ERR_INVALID_ARG,
                  "decrypt: ciphertext over RESURS_MAX_RAW_LEN -> INVALID_ARG");
            free(pt);
        }
        free(blob);
    }
}

int main(void)
{
    if (abi_setup(KEY_PATH) != 0)
    {
        return 1;
    }
    fill_test_nonce(g_nonce);

    test_plaintext_over_max();
    test_plaintext_at_max();
    test_plaintext_unterminated();
    test_ciphertext_over_raw_max();

    abi_teardown(KEY_PATH);
    return test_summary();
}
