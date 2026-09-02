#include "resurs_crypto.h"
#include "key_manager.hpp"

#include <stdexcept>

extern "C"
{

    int resurs_crypto_init(const char *key_file_path)
    {
        if (key_file_path == nullptr)
        {
            return RESURS_ERR_INVALID_ARG;
        }

        // no C++ exception may cross the extern "C" boundary — map each to an error code
        try
        {
            resurs::KeyManager::instance().loadFromFile(key_file_path);
            return RESURS_OK;
        }
        catch (const std::runtime_error &)
        {
            return RESURS_ERR_KEY_IO;
        }
        catch (...) // catch everything else
        {
            return RESURS_ERR_INTERNAL;
        }
    }

    int resurs_encrypt_pii(const char *plaintext, const unsigned char *nonce,
                           unsigned char *ciphertext_out, size_t *ciphertext_len)
    {

        (void)plaintext;
        (void)nonce;
        (void)ciphertext_out;
        (void)ciphertext_len;
        return RESURS_ERR_NOT_INIT;
    }

    int resurs_decrypt_pii(const unsigned char *nonce, const unsigned char *ciphertext,
                           size_t ciphertext_len, char *plaintext_out, size_t *plaintext_len)
    {

        (void)nonce;
        (void)ciphertext;
        (void)ciphertext_len;
        (void)plaintext_out;
        (void)plaintext_len;
        return RESURS_ERR_NOT_INIT;
    }

    void resurs_crypto_shutdown(void)
    {
        try
        {
            resurs::KeyManager::instance().cleanse();
        }
        catch (...)
        {
            // shutdown has no way to report failure
            // make sure nothing escapes extern "C" 
        }
    }

} // extern "C"
