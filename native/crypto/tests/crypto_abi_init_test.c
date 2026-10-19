/* ABI: resurs_crypto_init argument/key-file validation and the shutdown
 * lifecycle. Manages init itself instead of using abi_setup(). */
#include "resurs_crypto.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <stdio.h>
#include <string.h>
#include <sys/stat.h> /* chmod, mkfifo */

static const char *const RING_PATH = "crypto_abi_init.keyring";
static const char *const RING_V2_PATH = "crypto_abi_init_v2.keyring";
static const char *const LEGACY_PATH = "crypto_abi_init_legacy64.key";
static const char *const LOOKUP_PATH = "crypto_abi_init.lookup";
static const char *const LOOKUP31_PATH = "crypto_abi_init_31.lookup";
static const char *const LOOKUP33_PATH = "crypto_abi_init_33.lookup";
static const char *const LOOKUP_OTHER_PATH = "crypto_abi_init_other.lookup";
static const char *const RING_WW_PATH = "crypto_abi_init_ww.keyring";
static const char *const FIFO_PATH = "crypto_abi_init.fifo";

static void write_fixtures(void)
{
    const unsigned char v1_ww[] = {1};
    unsigned char other_lookup[RESURS_KEY_LEN];
    fill_test_lookup_key(other_lookup);
    other_lookup[0] ^= 0x01;
    check(write_file(LOOKUP_OTHER_PATH, other_lookup, sizeof other_lookup) == 0, "wrote a different lookup key");
    check(write_test_ring(RING_WW_PATH, 1, v1_ww, 1, 0) == 0, "wrote key ring copy for the mode test");
    remove(FIFO_PATH);
    check(mkfifo(FIFO_PATH, 0600) == 0, "created FIFO");

    const unsigned char v1[] = {1};
    const unsigned char v2[] = {2};
    unsigned char lookup[RESURS_KEY_LEN + 1];
    fill_test_lookup_key(lookup);
    lookup[RESURS_KEY_LEN] = 0;

    /* The old single-file format: 32 AES + 32 HMAC, no header. */
    unsigned char legacy[64];
    for (int i = 0; i < 64; i++)
    {
        legacy[i] = (unsigned char)(i + 1);
    }

    check(write_test_ring(RING_PATH, 1, v1, 1, 0) == 0, "wrote key ring {v1}");
    check(write_test_ring(RING_V2_PATH, 2, v2, 1, 0) == 0, "wrote key ring {v2}");
    check(write_file(LEGACY_PATH, legacy, sizeof legacy) == 0, "wrote legacy 64-byte key file");
    check(write_file(LOOKUP_PATH, lookup, RESURS_KEY_LEN) == 0, "wrote 32-byte lookup key");
    check(write_file(LOOKUP31_PATH, lookup, RESURS_KEY_LEN - 1) == 0, "wrote 31-byte lookup key");
    check(write_file(LOOKUP33_PATH, lookup, RESURS_KEY_LEN + 1) == 0, "wrote 33-byte lookup key");
}

static void test_init_validation(void)
{
    check(resurs_crypto_active_version() == RESURS_ERR_NOT_INIT,
          "active_version before init -> NOT_INIT");

    check(resurs_crypto_init(NULL, LOOKUP_PATH) == RESURS_ERR_INVALID_ARG, "init(NULL, lookup) -> INVALID_ARG");
    check(resurs_crypto_init(RING_PATH, NULL) == RESURS_ERR_INVALID_ARG, "init(ring, NULL) -> INVALID_ARG");
    check(resurs_crypto_init("/nonexistent/resurs.keyring", LOOKUP_PATH) == RESURS_ERR_KEY_IO,
          "init(missing ring) -> KEY_IO");
    check(resurs_crypto_init(RING_PATH, "/nonexistent/resurs.lookup") == RESURS_ERR_KEY_IO,
          "init(missing lookup) -> KEY_IO");
    check(resurs_crypto_init(LEGACY_PATH, LOOKUP_PATH) == RESURS_ERR_KEY_IO,
          "init(legacy 64-byte file as ring) -> KEY_IO");
    check(resurs_crypto_init(RING_PATH, LOOKUP31_PATH) == RESURS_ERR_KEY_IO, "init(31-byte lookup) -> KEY_IO");
    check(resurs_crypto_init(RING_PATH, LOOKUP33_PATH) == RESURS_ERR_KEY_IO, "init(33-byte lookup) -> KEY_IO");
    check(resurs_crypto_active_version() == RESURS_ERR_NOT_INIT,
          "failed inits leave the module not initialised");

    check(resurs_crypto_init(RING_PATH, LOOKUP_PATH) == RESURS_OK, "init(ring, lookup) -> OK");
    check(resurs_crypto_active_version() == 1, "active_version after init == 1");
}

/* A failed init after a good one must keep the keys that were loaded. */
static void test_failed_reinit_keeps_keys(void)
{
    unsigned char nonce[RESURS_NONCE_LEN];
    fill_test_nonce(nonce);
    unsigned char ct[64];
    size_t ct_len = sizeof ct;
    check(abi_encrypt_sample(nonce, ct, &ct_len) == RESURS_OK, "encrypt under v1");

    check(resurs_crypto_init(LEGACY_PATH, LOOKUP_PATH) == RESURS_ERR_KEY_IO, "re-init with bad ring -> KEY_IO");
    check(resurs_crypto_init(RING_PATH, LOOKUP31_PATH) == RESURS_ERR_KEY_IO, "re-init with bad lookup -> KEY_IO");
    check(resurs_crypto_active_version() == 1, "failed re-init: active version unchanged");

    char out[64];
    size_t out_len = sizeof out;
    check(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, ct, ct_len, out, &out_len) == RESURS_OK &&
              out_len == strlen(ABI_SAMPLE_PLAIN) && memcmp(out, ABI_SAMPLE_PLAIN, out_len) == 0,
          "failed re-init: old ciphertext still decrypts");

    /* A second init while loaded (e.g. one per Spring context) may only repeat
     * compatible keys: it must not change the blind index or skip reload rules. */
    unsigned char h_before[RESURS_HMAC_LEN], h_after[RESURS_HMAC_LEN];
    size_t hn = sizeof h_before;
    check(resurs_hmac_sha256((const unsigned char *)"x", 1, h_before, &hn) == RESURS_OK, "hmac before re-init");

    check(resurs_crypto_init(RING_PATH, LOOKUP_PATH) == RESURS_OK, "re-init with the same keys -> OK");
    check(resurs_crypto_init(RING_PATH, LOOKUP_OTHER_PATH) == RESURS_ERR_KEY_REJECTED,
          "re-init with a different lookup key -> KEY_REJECTED");
    check(resurs_crypto_init(RING_V2_PATH, LOOKUP_PATH) == RESURS_ERR_KEY_REJECTED,
          "re-init with a ring lacking active v1 -> KEY_REJECTED");

    hn = sizeof h_after;
    check(resurs_hmac_sha256((const unsigned char *)"x", 1, h_after, &hn) == RESURS_OK &&
              memcmp(h_before, h_after, RESURS_HMAC_LEN) == 0,
          "rejected re-init: blind index unchanged");
    check(resurs_crypto_active_version() == 1, "rejected re-init: active version unchanged");
}

/* Key files must be regular and not writable by group/others. */
static void test_file_checks(void)
{
    check(resurs_crypto_init(RING_PATH, FIFO_PATH) == RESURS_ERR_KEY_IO,
          "re-init with a FIFO as lookup key -> KEY_IO (no hang)");
    check(chmod(RING_WW_PATH, 0666) == 0, "made a world-writable ring copy");
    check(resurs_crypto_init(RING_WW_PATH, LOOKUP_PATH) == RESURS_ERR_KEY_IO,
          "re-init with a world-writable ring -> KEY_IO");
    check(resurs_crypto_reload(RING_WW_PATH) == RESURS_ERR_KEY_IO,
          "reload of a world-writable ring -> KEY_IO");
    check(resurs_crypto_reload(FIFO_PATH) == RESURS_ERR_KEY_IO,
          "reload of a FIFO -> KEY_IO (no hang)");
    check(resurs_crypto_active_version() == 1, "rejected files: active version unchanged");
}

/* After shutdown, any keys may be loaded again. */
static void test_init_after_shutdown(void)
{
    check(resurs_crypto_init(RING_V2_PATH, LOOKUP_OTHER_PATH) == RESURS_OK,
          "init after shutdown with other keys -> OK");
    check(resurs_crypto_active_version() == 2, "init after shutdown: active version 2");
    resurs_crypto_shutdown();
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
    check(resurs_crypto_reload(RING_PATH) == RESURS_ERR_NOT_INIT,
          "reload after shutdown -> NOT_INIT");
    check(resurs_crypto_active_version() == RESURS_ERR_NOT_INIT,
          "active_version after shutdown -> NOT_INIT");
}

int main(void)
{
    write_fixtures();
    test_init_validation();
    test_failed_reinit_keeps_keys();
    test_file_checks();
    test_shutdown();
    test_init_after_shutdown();

    remove(RING_PATH);
    remove(RING_V2_PATH);
    remove(LEGACY_PATH);
    remove(LOOKUP_PATH);
    remove(LOOKUP31_PATH);
    remove(LOOKUP33_PATH);
    remove(LOOKUP_OTHER_PATH);
    remove(RING_WW_PATH);
    remove(FIFO_PATH);

    return test_summary();
}
