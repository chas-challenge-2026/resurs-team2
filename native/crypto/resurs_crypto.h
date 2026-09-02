
#ifndef RESURS_CRYPTO_H
#define RESURS_CRYPTO_H

#include <stddef.h>

#if defined(_WIN32)
#  define RESURS_API __declspec(dllexport)
#else
#  define RESURS_API __attribute__((visibility("default")))
#endif

// Ciphertext layout: [ key_version : 1 ][ AES-GCM ciphertext : plaintext length ][ GCM tag : 16 ]
// plaintext length = ciphertext_len - RESURS_KEY_VERSION_LEN - RESURS_TAG_LEN

#define RESURS_KEY_LEN   32   // AES-256
#define RESURS_NONCE_LEN 12   // GCM standard nonce
#define RESURS_TAG_LEN   16   // GCM tag, full 128-bit
#define RESURS_KEY_VERSION_LEN 1 // key version prefix size 1 byte
#define RESURS_KEY_VERSION_CURRENT 1 // current key version for encryption

enum {
    RESURS_OK               =  0,
    RESURS_ERR_NOT_INIT     = -1, // encrypt/decrypt called before successful init
    RESURS_ERR_AUTH         = -2, // decrypt: GCM tag mismatch
    RESURS_ERR_KEY_IO       = -3, // init: key file missing / not 32 bytes
    RESURS_ERR_BUFFER_SMALL = -4, // output buffer too small
    RESURS_ERR_INVALID_ARG  = -5, // NULL where not allowed, etc.
    RESURS_ERR_INTERNAL     = -6, // unexpected OpenSSL error 
    RESURS_ERR_KEY_VERSION  = -7, // wrong key version for decryption
};

#ifdef __cplusplus
extern "C"{
#endif
    // Load the 32-byte key from key_file_path. Call once at startup.
    // Returns RESURS_OK, RESURS_ERR_KEY_IO, RESURS_ERR_INVALID_ARG, RESURS_ERR_INTERNAL
    RESURS_API int resurs_crypto_init(const char* key_file_path);

    // Encrypt one PII string and prepend key-version byte
    // Returns RESURS_OK, RESURS_ERR_NOT_INIT, RESURS_ERR_INVALID_ARG, RESURS_ERR_INTERNAL
    // ciphertext_out must be at least: RESURS_KEY_VERSION_LEN + strlen(plaintext) + RESURS_TAG_LEN
    // nonce: caller passes exactly RESURS_NONCE_LEN bytes 
    // nonce must come from a CSPRNG
    // reusing a (key, nonce) pair breaks GCM confidentiality and authentication
    // *ciphertext_len is output-only: set to the number of bytes written
    RESURS_API int resurs_encrypt_pii(const char* plaintext, const unsigned char* nonce,
                                  unsigned char* ciphertext_out, size_t* ciphertext_len);

    // Verify version byte + GCM tag and return plaintext
    // Returns RESURS_OK, RESURS_ERR_NOT_INIT, RESURS_ERR_INVALID_ARG, RESURS_ERR_KEY_VERSION, RESURS_ERR_AUTH, RESURS_ERR_INTERNAL
    // plaintext_out must be at least: ciphertext_len - RESURS_KEY_VERSION_LEN - RESURS_TAG_LEN
    // no trailing \0 written
    // nonce: must be the same 12 bytes used at encrypt
    // *plaintext_len is output-only: set to the plaintext length
    RESURS_API int resurs_decrypt_pii(const unsigned char* nonce, const unsigned char* ciphertext,
                                  size_t ciphertext_len, char* plaintext_out, size_t* plaintext_len);

    // wipes the key from memory (OPENSSL_cleanse)
    // safe to call multiple times and before init 
    // after it, encrypt/decrypt return RESURS_ERR_NOT_INIT
    RESURS_API void resurs_crypto_shutdown(void);
       
#ifdef __cplusplus
}
#endif

#endif // RESURS_CRYPTO_H

