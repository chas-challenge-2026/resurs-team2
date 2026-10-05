/* ABI: blind-index HMAC - determinism, input sensitivity, argument checks. */
#include "resurs_crypto.h"
#include "abi_fixture.h"
#include "test_util.h"

#include <string.h>

static const char *const KEY_PATH = "crypto_abi_hmac.key";

static void test_hmac_blind_index(void)
{
    const char *org = ABI_SAMPLE_PLAIN;
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
}

static void test_hmac_invalid_args(void)
{
    const char *org = ABI_SAMPLE_PLAIN;
    unsigned char h[RESURS_HMAC_LEN];

    size_t n = sizeof h;
    check(resurs_hmac_sha256(NULL, 0, h, &n) == RESURS_ERR_INVALID_ARG,
          "hmac(NULL data) -> INVALID_ARG");
    check(resurs_hmac_sha256((const unsigned char *)org, strlen(org), h, NULL) == RESURS_ERR_INVALID_ARG,
          "hmac(NULL len) -> INVALID_ARG");
}

int main(void)
{
    if (abi_setup(KEY_PATH) != 0)
    {
        return 1;
    }

    test_hmac_blind_index();
    test_hmac_invalid_args();

    abi_teardown(KEY_PATH);
    return test_summary();
}
