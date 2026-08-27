#include "resurs_crypto.h"


extern "C" {

int resurs_crypto_init(const char* key_file_path) {
    
    (void) key_file_path;
    return RESURS_ERR_NOT_INIT;
}

int resurs_encrypt_pii(const char* plaintext, unsigned char* nonce_out, 
    unsigned char* ciphertext_out, size_t* ciphertext_len) {

        (void) plaintext; 
        (void) nonce_out; 
        (void) ciphertext_out; 
        (void) ciphertext_len;
        return RESURS_ERR_NOT_INIT;
}

int resurs_decrypt_pii(const unsigned char* nonce, const unsigned char* ciphertext, 
    size_t ciphertext_len, char* plaintext_out, size_t* plaintext_len) {

        (void) nonce; 
        (void) ciphertext; 
        (void) ciphertext_len;
        (void) plaintext_out; 
        (void) plaintext_len;
        return RESURS_ERR_NOT_INIT;
}

void resurs_crypto_shutdown(void) {
}

} // extern "C"
