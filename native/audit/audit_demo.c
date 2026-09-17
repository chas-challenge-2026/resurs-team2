/* Demo: walks through the audit-signing flow end to end and narrates each
 * step to stdout. Not a test (no pass/fail assertions) - run it manually to
 * see what resurs_audit actually does:
 *
 *   ./build/audit/audit_demo
 */
#include "resurs_audit.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

static void print_hex(const unsigned char *data, size_t len)
{
    for (size_t i = 0; i < len; i++)
    {
        printf("%02x", data[i]);
    }
}

static int must(int rc, const char *what)
{
    if (rc != RESURS_AUDIT_OK)
    {
        fprintf(stderr, "FATAL: %s failed with code %d\n", what, rc);
        exit(1);
    }
    return rc;
}

int main(void)
{
    const char *key_path = "demo_audit_key.pem";
    const char *pub_der_path = "demo_audit_pub.der";

    printf("=== 1. Provisioning an Ed25519 signing key (like infra/Makefile's audit-keys) ===\n");
    char cmd[512];
    snprintf(cmd, sizeof cmd, "openssl genpkey -algorithm ED25519 -out %s 2>/dev/null", key_path);
    system(cmd);
    snprintf(cmd, sizeof cmd,
             "openssl pkey -in %s -pubout -outform DER -out %s 2>/dev/null",
             key_path, pub_der_path);
    system(cmd);

    /* Ed25519 SubjectPublicKeyInfo DER is a fixed 44 bytes: a 12-byte header
     * followed by the 32-byte raw public key. */
    unsigned char pub[RESURS_AUDIT_PUBKEY_LEN];
    FILE *f = fopen(pub_der_path, "rb");
    fseek(f, -(long)RESURS_AUDIT_PUBKEY_LEN, SEEK_END);
    fread(pub, 1, RESURS_AUDIT_PUBKEY_LEN, f);
    fclose(f);
    printf("public key: ");
    print_hex(pub, RESURS_AUDIT_PUBKEY_LEN);
    printf("\n\n");

    printf("=== 2. resurs_audit_init: loading the private key ===\n");
    must(resurs_audit_init(key_path), "resurs_audit_init");
    printf("key loaded.\n\n");

    printf("=== 3. resurs_audit_chain_entry: signing 3 audit entries, each chained to the previous ===\n");
    const char *entries[3] = {
        "{\"seq\":0,\"actor\":\"admin\",\"event\":\"case.created\"}",
        "{\"seq\":1,\"actor\":\"admin\",\"event\":\"case.status_changed\",\"to\":\"approved\"}",
        "{\"seq\":2,\"actor\":\"system\",\"event\":\"payment.disbursed\",\"amount\":4200}",
    };
    size_t entry_lens[3];
    unsigned char hashes[3 * RESURS_AUDIT_HASH_LEN];
    unsigned char signatures[3 * RESURS_AUDIT_SIG_LEN];
    size_t signature_lens[3];

    for (int i = 0; i < 3; i++)
    {
        entry_lens[i] = strlen(entries[i]);
        const unsigned char *prev = (i == 0) ? NULL : hashes + (i - 1) * RESURS_AUDIT_HASH_LEN;
        size_t siglen = 0;

        must(resurs_audit_chain_entry(prev, entries[i], entry_lens[i],
                                       hashes + i * RESURS_AUDIT_HASH_LEN,
                                       signatures + i * RESURS_AUDIT_SIG_LEN, &siglen),
             "resurs_audit_chain_entry");
        signature_lens[i] = siglen;

        printf("entry[%d]: %s\n", i, entries[i]);
        printf("  hash[%d]      = ", i);
        print_hex(hashes + i * RESURS_AUDIT_HASH_LEN, RESURS_AUDIT_HASH_LEN);
        printf("\n  signature[%d] = ", i);
        print_hex(signatures + i * RESURS_AUDIT_SIG_LEN, RESURS_AUDIT_SIG_LEN);
        printf("\n");
    }
    printf("\n");

    /* Concatenate the entries back to back, the way verify_chain expects them. */
    char entries_buf[256];
    size_t entry_offset[3];
    size_t off = 0;
    for (int i = 0; i < 3; i++)
    {
        entry_offset[i] = off;
        memcpy(entries_buf + off, entries[i], entry_lens[i]);
        off += entry_lens[i];
    }

    printf("=== 4. resurs_audit_verify_chain: verifying the intact chain ===\n");
    int first_invalid = -2;
    must(resurs_audit_verify_chain(hashes, signatures, signature_lens, entries_buf,
                                    entry_lens, 3, pub, &first_invalid),
         "resurs_audit_verify_chain");
    printf("first_invalid_index = %d  (chain is intact)\n\n", first_invalid);

    /* XOR in the PID so two runs launched within the same second (e.g. a
     * quick shell loop) still pick a different random entry/offset. */
    srand((unsigned)time(NULL) ^ (unsigned)getpid());
    int tamper_idx = rand() % 3;
    size_t tamper_pos = (size_t)(rand() % (int)entry_lens[tamper_idx]);
    size_t tamper_at = entry_offset[tamper_idx] + tamper_pos;

    printf("=== 5. Tampering with a random byte in a random entry, then re-verifying ===\n");
    printf("chosen: entries[%d], byte offset %zu\n", tamper_idx, tamper_pos);
    printf("before: %.*s\n", (int)entry_lens[tamper_idx], entries_buf + entry_offset[tamper_idx]);
    /* Bump the byte by one, wrapping within the printable ASCII range, so the
     * "after" line always stays human-readable regardless of which byte was picked. */
    entries_buf[tamper_at] = (entries_buf[tamper_at] == '~') ? '!' : (char)(entries_buf[tamper_at] + 1);
    printf("after:  %.*s\n", (int)entry_lens[tamper_idx], entries_buf + entry_offset[tamper_idx]);

    first_invalid = -2;
    must(resurs_audit_verify_chain(hashes, signatures, signature_lens, entries_buf,
                                    entry_lens, 3, pub, &first_invalid),
         "resurs_audit_verify_chain");
    printf("first_invalid_index = %d  (tampering detected: entries[%d]'s content no longer "
           "matches its stored hash)\n\n",
           first_invalid, first_invalid);

    printf("=== 6. resurs_audit_shutdown ===\n");
    resurs_audit_shutdown();
    printf("private key wiped. chain_entry now returns NOT_INIT (%d), "
           "but verify_chain still works - it only needs the public key.\n",
           resurs_audit_chain_entry(NULL, entries[0], entry_lens[0],
                                     hashes, signatures, signature_lens));

    remove(key_path);
    remove(pub_der_path);
    return 0;
}
