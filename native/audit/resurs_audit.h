#ifndef RESURS_AUDIT_H
#define RESURS_AUDIT_H

#include <stddef.h>

#if defined(_WIN32)
#define RESURS_API __declspec(dllexport)
#else
#define RESURS_API __attribute__((visibility("default")))
#endif

#define RESURS_AUDIT_HASH_LEN 32   // SHA-256
#define RESURS_AUDIT_SIG_LEN 64    // Ed25519 signature (fixed)
#define RESURS_AUDIT_PUBKEY_LEN 32 // Ed25519 public key (fixed)

enum
{
    RESURS_AUDIT_OK = 0,
    RESURS_AUDIT_ERR_NOT_INIT = -1,     // chain_entry called before successful init
    RESURS_AUDIT_ERR_INVALID_ARG = -2,  // NULL where not allowed, bad length, etc.
    RESURS_AUDIT_ERR_KEY_IO = -3,       // init: key file missing / not a valid Ed25519 PEM
    RESURS_AUDIT_ERR_BUFFER_SMALL = -4, // output buffer too small
    RESURS_AUDIT_ERR_INTERNAL = -5,     // unexpected OpenSSL error
};

#ifdef __cplusplus
extern "C"
{
#endif

    // Load the Ed25519 signing key (PEM) from key_file_path. Call once at startup.
    RESURS_API int resurs_audit_init(const char *key_file_path);

    // Hash one audit entry chained to the previous entry's hash, and sign the hash.
    // prev_hash: RESURS_AUDIT_HASH_LEN bytes, or NULL for the first entry in a chain
    //            (treated as 32 zero bytes).
    // hash_out = SHA-256(prev_hash || entry_json); written, exactly RESURS_AUDIT_HASH_LEN bytes.
    // signature_out must be at least RESURS_AUDIT_SIG_LEN bytes.
    // *signature_len is output-only: set to the number of bytes written (always 64 for Ed25519).
    RESURS_API int resurs_audit_chain_entry(const unsigned char *prev_hash,
                                            const char *entry_json, size_t entry_len,
                                            unsigned char *hash_out,
                                            unsigned char *signature_out, size_t *signature_len);

    // NOTE: extended beyond native/README.md — takes the entry JSONs too, so it can
    // recompute each hash from its actual content, not just check the signatures.
    //
    // Without the entries, tampering with an entry's content (while leaving hash+signature
    // untouched) would go undetected.
    //
    // entries / entry_lens: entry_count JSON blobs back-to-back, with their lengths.
    // hashes: entry_count * RESURS_AUDIT_HASH_LEN bytes, in order.
    // signatures / signature_lens: entry_count signatures, in the same order.
    // Recomputes hash[i] = SHA-256(hash[i-1] || entries[i]) (hash[-1] = 32 zero bytes)
    // and verifies signature[i] over hash[i] with public_key.
    // *first_invalid_index: -1 if the whole chain verifies, otherwise the index of the
    // first entry that fails either check.
    // Returns RESURS_AUDIT_OK once verification ran to completion (regardless of the
    // result found) — error codes are reserved for operational failures (bad args, etc).
    RESURS_API int resurs_audit_verify_chain(const unsigned char *hashes,
                                             const unsigned char *signatures,
                                             const size_t *signature_lens,
                                             const char *entries, const size_t *entry_lens,
                                             size_t entry_count,
                                             const unsigned char *public_key,
                                             int *first_invalid_index);

    // Frees the signing key (EVP_PKEY_free)
    // Safe to call multiple times and before init.
    RESURS_API void resurs_audit_shutdown(void);

#ifdef __cplusplus
}
#endif

#endif // RESURS_AUDIT_H
