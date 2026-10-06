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
        blob[0] = RESURS_KEY_VERSION_CURRENT;
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
    test_ciphertext_over_raw_max();

    abi_teardown(KEY_PATH);
    return test_summary();
}
