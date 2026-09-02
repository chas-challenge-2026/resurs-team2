#include "resurs_crypto.h"

#include <stdio.h>

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
    unsigned char key32[32];
    for (int i = 0; i < 32; i++)
    {
        key32[i] = (unsigned char)(i + 1);
    }

    check(write_file("abi_key32.bin", key32, 32) == 0, "wrote 32-byte key file");
    check(write_file("abi_key31.bin", key32, 31) == 0, "wrote 31-byte key file");

    check(resurs_crypto_init(NULL) == RESURS_ERR_INVALID_ARG, "init(NULL) -> INVALID_ARG");
    check(resurs_crypto_init("/nonexistent/resurs.key") == RESURS_ERR_KEY_IO, "init(missing) -> KEY_IO");
    check(resurs_crypto_init("abi_key31.bin") == RESURS_ERR_KEY_IO, "init(31 bytes) -> KEY_IO");
    check(resurs_crypto_init("abi_key32.bin") == RESURS_OK, "init(32bytes) -> OK");

    resurs_crypto_shutdown();
    resurs_crypto_shutdown();

    check(1, "shutdown x2 no crash");

    check(resurs_encrypt_pii("x", NULL, NULL, NULL) == RESURS_ERR_NOT_INIT, "encrypt after shutdown -> NOT_INIT");
    check(resurs_decrypt_pii(NULL, NULL, 0, NULL, NULL) == RESURS_ERR_NOT_INIT, "decrypt after shutdown -> NOT_INIT");

    remove("abi_key32.bin");
    remove("abi_key31.bin");

    printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}
