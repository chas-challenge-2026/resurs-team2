/* Demo: walks through the PII encryption flow end to end and narrates each
 * step to stdout. Not a test (no pass/fail assertions) - run it manually to
 * see what resurs_crypto actually does:
 *
 *   make demo-crypto
 */
#include "resurs_crypto.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h> /* umask */

static void print_hex(const char *label, const unsigned char *data, size_t len)
{
    printf("  %-18s (%2zu)  ", label, len);
    for (size_t i = 0; i < len; i++)
    {
        printf("%02x", data[i]);
    }
    printf("\n");
}

static int must(int rc, const char *what)
{
    if (rc != RESURS_OK)
    {
        fprintf(stderr, "FATAL: %s failed with code %d\n", what, rc);
        exit(1);
    }
    return rc;
}

/* Fresh random bytes from the OS. The Java side uses SecureRandom for nonces. */
static void random_bytes(unsigned char *buf, size_t n)
{
    FILE *f = fopen("/dev/urandom", "rb");
    if (!f || fread(buf, 1, n, f) != n)
    {
        fprintf(stderr, "FATAL: cannot read /dev/urandom\n");
        exit(1);
    }
    fclose(f);
}

static void write_or_die(const char *path, const unsigned char *data, size_t n)
{
    FILE *f = fopen(path, "wb");
    if (!f || fwrite(data, 1, n, f) != n || fclose(f) != 0)
    {
        fprintf(stderr, "FATAL: cannot write %s\n", path);
        exit(1);
    }
}

/* Two-step buffer contract, the way the Java side calls the ABI: ask for the
 * required size with (NULL, 0), allocate exactly that, then encrypt. */
static unsigned char *encrypt_alloc(const char *plain, const unsigned char *nonce, size_t *ct_len)
{
    size_t need = 0;
    int rc = resurs_encrypt_pii(plain, nonce, RESURS_NONCE_LEN, NULL, &need);
    if (rc != RESURS_ERR_BUFFER_SMALL)
    {
        must(rc, "resurs_encrypt_pii (size query)");
    }
    unsigned char *ct = malloc(need);
    if (!ct)
    {
        fprintf(stderr, "FATAL: out of memory\n");
        exit(1);
    }
    *ct_len = need;
    must(resurs_encrypt_pii(plain, nonce, RESURS_NONCE_LEN, ct, ct_len), "resurs_encrypt_pii");
    return ct;
}

int main(void)
{
    const char *ring_path = "demo_crypto.keyring";
    const char *lookup_path = "demo_crypto.lookup";

    /* Key files are created 0600, like infra/Makefile does with umask 077.
     * The module also rejects key files that group/others can write. */
    umask(077);

    printf("=== 1. Provisioning the two key files ===\n");
    /* Key ring with one version: header "RSKR", format 1, active 1, count 1,
     * then [ version 1 ][ 32 random AES key bytes ]. */
    unsigned char ring[7 + 1 + RESURS_KEY_LEN] = {'R', 'S', 'K', 'R', 1, 1, 1, 1};
    random_bytes(ring + 8, RESURS_KEY_LEN);
    unsigned char lookup[RESURS_KEY_LEN];
    random_bytes(lookup, sizeof lookup);

    write_or_die(ring_path, ring, sizeof ring);
    write_or_die(lookup_path, lookup, sizeof lookup);
    print_hex("key ring header", ring, 7);
    printf("key ring: versioned AES-256 keys; new data uses the active version, old data\n"
           "is decrypted with the version stamped on it - that is what makes rotation work.\n"
           "lookup key: 32 bytes for the blind index, a separate file that rotation never touches.\n\n");

    printf("=== 2. resurs_crypto_init: loading the keys once per process ===\n");
    must(resurs_crypto_init(ring_path, lookup_path), "resurs_crypto_init");
    printf("keys loaded, active key version = %d.\n\n", resurs_crypto_active_version());

    const char *plain = "556000-1234";
    unsigned char nonce[RESURS_NONCE_LEN];
    random_bytes(nonce, sizeof nonce);

    printf("=== 3. resurs_encrypt_pii: size query, then encrypt ===\n");
    size_t need = 0;
    int rc = resurs_encrypt_pii(plain, nonce, RESURS_NONCE_LEN, NULL, &need);
    printf("encrypt(\"%s\", out=NULL, cap=0) -> %d (BUFFER_SMALL), required size = %zu\n",
           plain, rc, need);
    size_t ct_len = 0;
    unsigned char *ct = encrypt_alloc(plain, nonce, &ct_len);
    printf("allocated %zu bytes, encrypted -> OK\n\n", ct_len);

    printf("=== 4. What the ciphertext blob contains ===\n");
    size_t body_len = ct_len - RESURS_KEY_VERSION_LEN - RESURS_TAG_LEN;
    print_hex("key version", ct, RESURS_KEY_VERSION_LEN);
    print_hex("ciphertext", ct + RESURS_KEY_VERSION_LEN, body_len);
    print_hex("GCM tag", ct + RESURS_KEY_VERSION_LEN + body_len, RESURS_TAG_LEN);
    print_hex("nonce (separate)", nonce, sizeof nonce);
    printf("the ciphertext is exactly as long as the plaintext (%zu bytes): GCM does not hide length.\n"
           "the nonce is NOT inside the blob - it must be stored next to it, or the value is lost.\n\n",
           body_len);

    printf("=== 5. resurs_decrypt_pii: round-trip ===\n");
    char out[64];
    size_t out_len = sizeof out;
    must(resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, ct, ct_len, out, &out_len), "resurs_decrypt_pii");
    printf("decrypted: \"%.*s\"\n\n", (int)out_len, out);

    printf("=== 6. Same value, fresh nonce -> a different ciphertext ===\n");
    unsigned char nonce2[RESURS_NONCE_LEN];
    random_bytes(nonce2, sizeof nonce2);
    size_t ct2_len = 0;
    unsigned char *ct2 = encrypt_alloc(plain, nonce2, &ct2_len);
    print_hex("ciphertext #1", ct, ct_len);
    print_hex("ciphertext #2", ct2, ct2_len);
    printf("equal plaintexts do not produce equal ciphertexts, so the database cannot\n"
           "search on this column. Never reuse a nonce with the same key: that breaks GCM.\n\n");
    free(ct2);

    printf("=== 7. resurs_hmac_sha256: the blind index used for lookups ===\n");
    unsigned char h1[RESURS_HMAC_LEN], h2[RESURS_HMAC_LEN], h3[RESURS_HMAC_LEN];
    size_t n1 = sizeof h1, n2 = sizeof h2, n3 = sizeof h3;
    const char *other = "556000-9999";
    must(resurs_hmac_sha256((const unsigned char *)plain, strlen(plain), h1, &n1), "resurs_hmac_sha256");
    must(resurs_hmac_sha256((const unsigned char *)plain, strlen(plain), h2, &n2), "resurs_hmac_sha256");
    must(resurs_hmac_sha256((const unsigned char *)other, strlen(other), h3, &n3), "resurs_hmac_sha256");
    print_hex("hmac(556000-1234)", h1, n1);
    print_hex("hmac(556000-1234)", h2, n2);
    print_hex("hmac(556000-9999)", h3, n3);
    printf("deterministic: the same value always gives the same index, so WHERE index = ?\n"
           "finds it - but without the lookup key nobody can compute or reverse it.\n"
           "trade-off: rows with equal values have equal indexes, so equality is visible.\n\n");

    printf("=== 8. Tampering is detected ===\n");
    unsigned char *bad = malloc(ct_len);
    if (!bad)
    {
        fprintf(stderr, "FATAL: out of memory\n");
        return 1;
    }
    memcpy(bad, ct, ct_len);
    bad[RESURS_KEY_VERSION_LEN] ^= 0x01;
    out_len = sizeof out;
    rc = resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, bad, ct_len, out, &out_len);
    printf("flipped one ciphertext bit -> %d (AUTH): the GCM tag no longer verifies.\n", rc);

    memcpy(bad, ct, ct_len);
    bad[0] = 0xFF;
    out_len = sizeof out;
    rc = resurs_decrypt_pii(nonce, RESURS_NONCE_LEN, bad, ct_len, out, &out_len);
    printf("unknown key version byte -> %d (KEY_VERSION): rejected before any decryption.\n\n", rc);
    free(bad);

    printf("=== 9. resurs_encrypt_pii_raw: data that is not a C string ===\n");
    const unsigned char raw[] = {'a', 'b', '\0', 'c', 'd'};
    unsigned char nonce3[RESURS_NONCE_LEN]; /* new value -> new nonce, always */
    random_bytes(nonce3, sizeof nonce3);
    unsigned char raw_ct[64];
    size_t raw_ct_len = sizeof raw_ct;
    must(resurs_encrypt_pii_raw(raw, sizeof raw, nonce3, RESURS_NONCE_LEN, raw_ct, &raw_ct_len),
         "resurs_encrypt_pii_raw");
    out_len = sizeof out;
    must(resurs_decrypt_pii(nonce3, RESURS_NONCE_LEN, raw_ct, raw_ct_len, out, &out_len), "resurs_decrypt_pii");
    print_hex("input", raw, sizeof raw);
    print_hex("decrypted", (const unsigned char *)out, out_len);
    printf("all %zu bytes survive, embedded NUL included; resurs_encrypt_pii would stop at\n"
           "the NUL. _raw takes an explicit length, for binary values and file contents.\n\n",
           out_len);

    printf("=== 10. resurs_crypto_shutdown ===\n");
    resurs_crypto_shutdown();
    size_t q = 0;
    printf("keys wiped from memory. encrypt now returns NOT_INIT (%d).\n",
           resurs_encrypt_pii(plain, nonce, RESURS_NONCE_LEN, NULL, &q));

    free(ct);
    remove(ring_path);
    remove(lookup_path);
    return 0;
}
