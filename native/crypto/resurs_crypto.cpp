#include "resurs_crypto.h"
#include "key_manager.hpp"
#include "aes_gcm_cipher.hpp"

#include <stdexcept>
#include <cstring>
#include <string>
#include <vector>

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
        if (!resurs::KeyManager::instance().isLoaded())
        {
            return RESURS_ERR_NOT_INIT;
        }
        if (nonce == nullptr || plaintext == nullptr || 
            ciphertext_out == nullptr || ciphertext_len == nullptr)
        {
            return RESURS_ERR_INVALID_ARG;
        }

        try
        {
            resurs::Nonce nonce_arr{};
            std::memcpy(nonce_arr.data(), nonce, resurs::kNonceLen);

            resurs::Key key = resurs::KeyManager::instance().key();

            auto body = resurs::AesGcmCipher::encrypt(plaintext, key, nonce_arr);

            ciphertext_out[0] = RESURS_KEY_VERSION_CURRENT;
            std::memcpy(ciphertext_out + 1, body.data(), body.size());
            *ciphertext_len = RESURS_KEY_VERSION_LEN + body.size();

            return RESURS_OK;
        }
        catch (...)
        {
            return RESURS_ERR_INTERNAL;
        }
    }
    int resurs_decrypt_pii(const unsigned char *nonce, const unsigned char *ciphertext,
                           size_t ciphertext_len, char *plaintext_out, size_t *plaintext_len)
    {
        if (!resurs::KeyManager::instance().isLoaded())
        {
            return RESURS_ERR_NOT_INIT;
        }

        if (nonce == nullptr || ciphertext == nullptr ||
            plaintext_out == nullptr || plaintext_len == nullptr)
        {
            return RESURS_ERR_INVALID_ARG;
        }
        // Too short to even hold the version byte and the GCM tag.
        if (ciphertext_len < RESURS_KEY_VERSION_LEN + RESURS_TAG_LEN)
        {
            return RESURS_ERR_INVALID_ARG;
        }
        // Reject an unknown key version before touching OpenSSL.
        if (ciphertext[0] != RESURS_KEY_VERSION_CURRENT)
        {
            return RESURS_ERR_KEY_VERSION;
        }

        try
        {
            resurs::Nonce nonce_arr{};
            std::memcpy(nonce_arr.data(), nonce, resurs::kNonceLen);

            resurs::Key key = resurs::KeyManager::instance().key();

            // Drop the version byte; [body][tag] is what AesGcmCipher expects.
            std::vector<std::uint8_t> input(ciphertext + RESURS_KEY_VERSION_LEN,
                                            ciphertext + ciphertext_len);

            std::string plain = resurs::AesGcmCipher::decrypt(input, key, nonce_arr);

            std::memcpy(plaintext_out, plain.data(), plain.size());
            *plaintext_len = plain.size();

            return RESURS_OK;
        }
        catch (const resurs::AuthError &)
        {
            return RESURS_ERR_AUTH;
        }
        catch (...)
        {
            return RESURS_ERR_INTERNAL;
        }
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
