
#ifndef RESURS_CRYPTO_H
#define RESURS_CRYPTO_H

#include <stddef.h>

#if defined(_WIN32)
#  define RESURS_API __declspec(dllexport)
#else
#  define RESURS_API __attribute__((visibility("default")))
#endif

#define RESURS_KEY_LEN   32   // AES-256
#define RESURS_NONCE_LEN 12   // GCM standard nonce
#define RESURS_TAG_LEN   16   // GCM tag, full 128-bit

enum {
    RESURS_OK               =  0,
    RESURS_ERR_NOT_INIT     = -1, // encrypt/decrypt called before successful init
    RESURS_ERR_AUTH         = -2, // decrypt: GCM tag mismatch
    RESURS_ERR_KEY_IO       = -3, // init: key file missing / not 32 bytes
    RESURS_ERR_BUFFER_SMALL = -4, // output buffer too small
    RESURS_ERR_INVALID_ARG  = -5, // NULL where not allowed, etc.
    RESURS_ERR_INTERNAL     = -6, // unexpected OpenSSL error 
};

#ifdef __cplusplus
extern "C"{
#endif
    // Load the 32-byte key from key_file_path. Call once at startup.
    // Returns RESURS_OK or RESURS_ERR_KEY_IO or RESURS_ERR_INVALID_ARG or RESURS_ERR_INTERNAL.
    RESURS_API int resurs_crypto_init(const char* key_file_path);

    RESURS_API int resurs_encrypt_pii(const char* plaintext, unsigned char* nonce_out,
                                  unsigned char* ciphertext_out, size_t* ciphertext_len);

    RESURS_API int resurs_decrypt_pii(const unsigned char* nonce, const unsigned char* ciphertext,
                                  size_t ciphertext_len, char* plaintext_out, size_t* plaintext_len);

    RESURS_API void resurs_crypto_shutdown(void);
       
#ifdef __cplusplus
}
#endif

#endif // RESURS_CRYPTO_H

