
#ifndef RESURS_CRYPTO_H
#define RESURS_CRYPTO_H

#include <stddef.h>

#if defined(_WIN32)
#define RESURS_API __declspec(dllexport)
#else
#define RESURS_API __attribute__((visibility("default")))
#endif

// Ciphertext layout: [ key_version : 1 ][ AES-GCM ciphertext : plaintext length ][ GCM tag : 16 ]
// plaintext length = ciphertext_len - RESURS_KEY_VERSION_LEN - RESURS_TAG_LEN

#define RESURS_KEY_LEN 32            // AES-256
#define RESURS_NONCE_LEN 12          // GCM standard nonce
#define RESURS_TAG_LEN 16            // GCM tag, full 128-bit
#define RESURS_HMAC_LEN 32           // HMAC-SHA256 blind-index output
#define RESURS_KEY_VERSION_LEN 1     // key version prefix size 1 byte
#define RESURS_KEY_VERSION_CURRENT 1 // current key version for encryption

// Largest plaintext resurs_encrypt_pii (the NUL-terminated string API) will encrypt.
// ~40x the largest PII column (VARCHAR(200)), with headroom for TEXT
// fields, yet tight enough to catch a caller bug early. Enforced by the string
// wrapper itself, on top of (and tighter than) RESURS_MAX_RAW_LEN below.
#define RESURS_MAX_PLAINTEXT_LEN 8192 // 8 KiB

// Largest input resurs_encrypt_pii_raw will encrypt, and the largest plaintext
// resurs_decrypt_pii may produce - decrypt has no way to know which of the two
// encrypt paths produced a given ciphertext, so it must accept anything either
// one could have written. Bounds every internal allocation and keeps all length
// values well inside INT_MAX. Matches spring.servlet.multipart.max-file-size
// (10MB, application.properties) exactly - no headroom beyond what Spring
// already guarantees no upload can exceed.
#define RESURS_MAX_RAW_LEN 10485760 // 10 MiB

// Key file layout: [ AES-256 key : 32 ][ HMAC lookup key : 32 ] — exactly 64 raw bytes.

enum
{
    RESURS_OK = 0,
    RESURS_ERR_NOT_INIT = -1,     // encrypt/decrypt called before successful init
    RESURS_ERR_AUTH = -2,         // decrypt: GCM tag mismatch
    RESURS_ERR_KEY_IO = -3,       // init: key file missing / not 32 bytes
    RESURS_ERR_BUFFER_SMALL = -4, // output buffer too small
    RESURS_ERR_INVALID_ARG = -5,  // NULL where not allowed, etc.
    RESURS_ERR_INTERNAL = -6,     // unexpected OpenSSL error
    RESURS_ERR_KEY_VERSION = -7,  // wrong key version for decryption
};

#ifdef __cplusplus
extern "C"
{
#endif
    // Load the 64-byte key file (32 AES + 32 HMAC lookup) from key_file_path.
    // Call once at startup.
    // Returns RESURS_OK, RESURS_ERR_KEY_IO, RESURS_ERR_INVALID_ARG, RESURS_ERR_INTERNAL
    RESURS_API int resurs_crypto_init(const char *key_file_path);

    // Encrypt one PII string and prepend key-version byte
    // Returns RESURS_OK, RESURS_ERR_NOT_INIT, RESURS_ERR_INVALID_ARG,
    //         RESURS_ERR_BUFFER_SMALL, RESURS_ERR_INTERNAL
    // plaintext: NUL-terminated; strlen(plaintext) must not exceed RESURS_MAX_PLAINTEXT_LEN
    // nonce: caller passes exactly RESURS_NONCE_LEN bytes; nonce_len must equal it
    // nonce must come from a CSPRNG
    // reusing a (key, nonce) pair breaks GCM confidentiality and authentication
    // *ciphertext_len is in/out:
    //   on entry  — capacity of ciphertext_out in bytes
    //   on RESURS_ERR_BUFFER_SMALL — set to the number of bytes required
    //   on RESURS_OK — set to the number of bytes written
    //   (required size = RESURS_KEY_VERSION_LEN + strlen(plaintext) + RESURS_TAG_LEN)
    // To query the required size, pass ciphertext_out = NULL and *ciphertext_len = 0.
    RESURS_API int resurs_encrypt_pii(const char *plaintext,
                                      const unsigned char *nonce, size_t nonce_len,
                                      unsigned char *ciphertext_out, size_t *ciphertext_len);

    // Same contract as resurs_encrypt_pii, but takes an explicit data length
    // instead of strlen(): safe for data that is not a NUL-terminated string
    // (binary values, file bytes, anything that may contain 0x00).
    // data_len must not exceed RESURS_MAX_RAW_LEN (much larger than
    // RESURS_MAX_PLAINTEXT_LEN - this is the path file uploads go through).
    RESURS_API int resurs_encrypt_pii_raw(const unsigned char *data, size_t data_len,
                                          const unsigned char *nonce, size_t nonce_len,
                                          unsigned char *ciphertext_out, size_t *ciphertext_len);

    // Verify version byte + GCM tag and return plaintext
    // Returns RESURS_OK, RESURS_ERR_NOT_INIT, RESURS_ERR_INVALID_ARG, RESURS_ERR_KEY_VERSION,
    //         RESURS_ERR_BUFFER_SMALL, RESURS_ERR_AUTH, RESURS_ERR_INTERNAL
    // ciphertext_len must be in
    //   [RESURS_KEY_VERSION_LEN + RESURS_TAG_LEN,
    //    RESURS_KEY_VERSION_LEN + RESURS_MAX_RAW_LEN + RESURS_TAG_LEN]
    // (RESURS_MAX_RAW_LEN, not RESURS_MAX_PLAINTEXT_LEN - a ciphertext may have
    // come from either resurs_encrypt_pii or resurs_encrypt_pii_raw)
    // no trailing \0 written
    // nonce: must be the same 12 bytes used at encrypt; nonce_len must equal RESURS_NONCE_LEN
    // *plaintext_len is in/out:
    //   on entry  — capacity of plaintext_out in bytes
    //   on RESURS_ERR_BUFFER_SMALL — set to the number of bytes required
    //   on RESURS_OK — set to the plaintext length
    //   (required size = ciphertext_len - RESURS_KEY_VERSION_LEN - RESURS_TAG_LEN)
    // To query the required size, pass plaintext_out = NULL and *plaintext_len = 0.
    RESURS_API int resurs_decrypt_pii(const unsigned char *nonce, size_t nonce_len,
                                      const unsigned char *ciphertext, size_t ciphertext_len,
                                      char *plaintext_out, size_t *plaintext_len);

    // Deterministic HMAC-SHA256 of `data` under the private lookup key, for use
    // as a blind-index value in a WHERE clause. The same input always yields the
    // same 32 bytes. The module owns the key; the caller never sees it.
    // data may be empty (data_len == 0). data_len is the byte length of data.
    // *hmac_len is in/out:
    //   on entry  — capacity of hmac_out in bytes
    //   on RESURS_ERR_BUFFER_SMALL — set to RESURS_HMAC_LEN (the required size)
    //   on RESURS_OK — set to RESURS_HMAC_LEN (exactly that many bytes are written)
    // To query the required size, pass hmac_out = NULL and *hmac_len = 0.
    // Returns RESURS_OK, RESURS_ERR_NOT_INIT, RESURS_ERR_INVALID_ARG,
    //         RESURS_ERR_BUFFER_SMALL, RESURS_ERR_INTERNAL
    RESURS_API int resurs_hmac_sha256(const unsigned char *data, size_t data_len,
                                      unsigned char *hmac_out, size_t *hmac_len);

    // wipes the keys from memory (OPENSSL_cleanse)
    // safe to call multiple times and before init
    // after it, encrypt/decrypt/hmac return RESURS_ERR_NOT_INIT
    RESURS_API void resurs_crypto_shutdown(void);

#ifdef __cplusplus
}
#endif

#endif // RESURS_CRYPTO_H
