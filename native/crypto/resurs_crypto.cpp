#include "resurs_crypto.h"
#include "key_manager.hpp"
#include "aes_gcm_cipher.hpp"
#include "hmac_sha256.hpp"

#include <cstring>
#include <string>
#include <string_view>
#include <vector>

namespace
{
    // Shared out-buffer contract for the extern "C" entry points.
    //
    // *out_len is in/out: on entry it is the caller-provided buffer capacity; on
    // return we set it either to the required size (RESURS_ERR_BUFFER_SMALL) or,
    // by the caller, to the number of bytes actually written (RESURS_OK).
    //
    // Returns:
    //   RESURS_OK               - buffer is large enough, caller may proceed
    //   RESURS_ERR_INVALID_ARG  - out == NULL but a non-zero capacity was claimed
    //   RESURS_ERR_BUFFER_SMALL - buffer too small; *out_len set to the required size
    //                             (this also serves the "query the size" form:
    //                              out == NULL with *out_len == 0)
    int check_out_capacity(const void *out, size_t *out_len, size_t required)
    {
        if (out == nullptr && *out_len != 0)
        {
            return RESURS_ERR_INVALID_ARG;
        }
        if (*out_len < required)
        {
            *out_len = required;
            return RESURS_ERR_BUFFER_SMALL;
        }
        return RESURS_OK;
    }
}

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

    int resurs_encrypt_pii(const char *plaintext,
                           const unsigned char *nonce, size_t nonce_len,
                           unsigned char *ciphertext_out, size_t *ciphertext_len)
    {
        if (!resurs::KeyManager::instance().isLoaded())
        {
            return RESURS_ERR_NOT_INIT;
        }
        if (plaintext == nullptr || nonce == nullptr || ciphertext_len == nullptr)
        {
            return RESURS_ERR_INVALID_ARG;
        }
        // The caller owns a raw pointer; its length cannot be probed, so it must
        // be declared and must match exactly.
        if (nonce_len != resurs::kNonceLen)
        {
            return RESURS_ERR_INVALID_ARG;
        }

        const size_t plain_len = std::strlen(plaintext);
        if (plain_len > RESURS_MAX_PLAINTEXT_LEN)
        {
            return RESURS_ERR_INVALID_ARG;
        }

        const size_t required = RESURS_KEY_VERSION_LEN + plain_len + RESURS_TAG_LEN;
        const int cap = check_out_capacity(ciphertext_out, ciphertext_len, required);
        if (cap != RESURS_OK)
        {
            return cap;
        }

        try
        {
            resurs::Nonce nonce_arr{};
            std::memcpy(nonce_arr.data(), nonce, resurs::kNonceLen);

            resurs::Key key = resurs::KeyManager::instance().key();

            auto body = resurs::AesGcmCipher::encrypt(
                std::string_view{plaintext, plain_len}, key, nonce_arr);

            // Defensive: the cipher output must fit the capacity we just checked.
            if (RESURS_KEY_VERSION_LEN + body.size() > *ciphertext_len)
            {
                return RESURS_ERR_INTERNAL;
            }

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

    int resurs_decrypt_pii(const unsigned char *nonce, size_t nonce_len,
                           const unsigned char *ciphertext, size_t ciphertext_len,
                           char *plaintext_out, size_t *plaintext_len)
    {
        if (!resurs::KeyManager::instance().isLoaded())
        {
            return RESURS_ERR_NOT_INIT;
        }

        if (nonce == nullptr || ciphertext == nullptr || plaintext_len == nullptr)
        {
            return RESURS_ERR_INVALID_ARG;
        }
        if (nonce_len != resurs::kNonceLen)
        {
            return RESURS_ERR_INVALID_ARG;
        }
        // Too short to even hold the version byte and the GCM tag.
        if (ciphertext_len < RESURS_KEY_VERSION_LEN + RESURS_TAG_LEN)
        {
            return RESURS_ERR_INVALID_ARG;
        }
        // Reject an oversized blob before allocating a buffer to copy it into.
        if (ciphertext_len >
            RESURS_KEY_VERSION_LEN + RESURS_MAX_PLAINTEXT_LEN + RESURS_TAG_LEN)
        {
            return RESURS_ERR_INVALID_ARG;
        }
        // Reject an unknown key version before touching OpenSSL.
        if (ciphertext[0] != RESURS_KEY_VERSION_CURRENT)
        {
            return RESURS_ERR_KEY_VERSION;
        }

        // GCM plaintext length equals the ciphertext-body length exactly.
        const size_t required =
            ciphertext_len - RESURS_KEY_VERSION_LEN - RESURS_TAG_LEN;
        const int cap = check_out_capacity(plaintext_out, plaintext_len, required);
        if (cap != RESURS_OK)
        {
            return cap;
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

            // Defensive: never write past the capacity we just checked.
            if (plain.size() > *plaintext_len)
            {
                return RESURS_ERR_INTERNAL;
            }

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

    int resurs_hmac_sha256(const unsigned char *data, size_t data_len,
                           unsigned char *hmac_out, size_t *hmac_len)
    {
        if (!resurs::KeyManager::instance().isLoaded())
        {
            return RESURS_ERR_NOT_INIT;
        }
        // data may be empty, but the pointer must be valid; hmac_len is required.
        if (data == nullptr || hmac_len == nullptr)
        {
            return RESURS_ERR_INVALID_ARG;
        }
        // Blind-index inputs are single column values; bound the work like plaintext.
        if (data_len > RESURS_MAX_PLAINTEXT_LEN)
        {
            return RESURS_ERR_INVALID_ARG;
        }

        const int cap = check_out_capacity(hmac_out, hmac_len, RESURS_HMAC_LEN);
        if (cap != RESURS_OK)
        {
            return cap;
        }

        try
        {
            resurs::Key lookup_key = resurs::KeyManager::instance().lookupKey();

            resurs::Hmac mac = resurs::hmacSha256(
                {reinterpret_cast<const char *>(data), data_len}, lookup_key);

            std::memcpy(hmac_out, mac.data(), RESURS_HMAC_LEN);
            *hmac_len = RESURS_HMAC_LEN;

            return RESURS_OK;
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
