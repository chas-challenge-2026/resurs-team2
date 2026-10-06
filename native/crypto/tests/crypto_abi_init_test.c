/* ABI: resurs_crypto_init argument/key-file validation and the shutdown
 * lifecycle. Manages init itself instead of using abi_setup(). */
#include "resurs_crypto.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <stdio.h>

static const char *const KEY64_PATH = "crypto_abi_init_key64.bin";
static const char *const KEY63_PATH = "crypto_abi_init_key63.bin";

static void test_init_validation(void)
{
    unsigned char key64[ABI_KEY_FILE_LEN];
    fill_test_key(key64);

    check(write_file(KEY64_PATH, key64, 64) == 0, "wrote 64-byte key file");
    check(write_file(KEY63_PATH, key64, 63) == 0, "wrote 63-byte key file");

    check(resurs_crypto_init(NULL) == RESURS_ERR_INVALID_ARG, "init(NULL) -> INVALID_ARG");
    check(resurs_crypto_init("/nonexistent/resurs.key") == RESURS_ERR_KEY_IO, "init(missing) -> KEY_IO");
    check(resurs_crypto_init(KEY63_PATH) == RESURS_ERR_KEY_IO, "init(63 bytes) -> KEY_IO");
    check(resurs_crypto_init(KEY64_PATH) == RESURS_OK, "init(64 bytes) -> OK");
}

/* Runs after a successful init: shutdown must be idempotent and leave every
 * entry point reporting NOT_INIT. */
static void test_shutdown(void)
{
    resurs_crypto_shutdown();
    resurs_crypto_shutdown();
    check(1, "shutdown x2 no crash");

    unsigned char h[RESURS_HMAC_LEN];
    size_t n = sizeof h;
    check(resurs_encrypt_pii("x", NULL, 0, NULL, NULL) == RESURS_ERR_NOT_INIT,
          "encrypt after shutdown -> NOT_INIT");
    check(resurs_decrypt_pii(NULL, 0, NULL, 0, NULL, NULL) == RESURS_ERR_NOT_INIT,
          "decrypt after shutdown -> NOT_INIT");
    check(resurs_hmac_sha256((const unsigned char *)"x", 1, h, &n) == RESURS_ERR_NOT_INIT,
          "hmac after shutdown -> NOT_INIT");
}

int main(void)
{
    test_init_validation();
    test_shutdown();

    remove(KEY64_PATH);
    remove(KEY63_PATH);

    return test_summary();
}
