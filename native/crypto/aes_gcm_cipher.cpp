#include "aes_gcm_cipher.hpp"
#include <string>
#include <memory>
#include <stdexcept>
#include <openssl/evp.h>

namespace resurs
{
    namespace
    {
        struct EvpCtxDeleter
        {
            void operator()(EVP_CIPHER_CTX *c) const noexcept
            {
                EVP_CIPHER_CTX_free(c);
            }
        };
        using EvpCtxPtr = std::unique_ptr<EVP_CIPHER_CTX, EvpCtxDeleter>;

        [[noreturn]] void throw_openssl(const char *what)
        {
            throw std::runtime_error(std::string("AesGcmCipher: ") + what);
        }

    }
    std::vector<std::uint8_t> AesGcmCipher::encrypt(std::string_view plaintext, const Key &key, const Nonce &nonce)
    {
        EvpCtxPtr ctx{EVP_CIPHER_CTX_new()};

        if (!ctx)
        {
            throw_openssl("EVP_CIPHER_CTX_new failed");
        }

        // 1. Algorithm only; key/nonce come in step 3.
        if (EVP_EncryptInit_ex(ctx.get(), EVP_aes_256_gcm(), nullptr, nullptr, nullptr) != 1)
        {
            throw_openssl("EVP_EncryptInit_ex (cipher) failed");
        }

        // 2. Declare nonce length explicitly.
        if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_IVLEN, static_cast<int>(kNonceLen), nullptr) != 1)
        {
            throw_openssl("EVP_CTRL_GCM_SET_IVLEN failed");
        }

        // 3. Bind key + nonce.
        if (EVP_EncryptInit_ex(ctx.get(), nullptr, nullptr, key.data(), nonce.data()) != 1)
        {
            throw_openssl("EVP_EncryptInit_ex (key/iv) failed");
        }

        // Output layout: [ciphertext == plaintext length] [16-byte tag].
        std::vector<std::uint8_t> out(plaintext.size() + kTagLen);

        // 4. Encrypt body.
        int len = 0;
        if (EVP_EncryptUpdate(ctx.get(), out.data(), &len, reinterpret_cast<const unsigned char *>(plaintext.data()),
                              static_cast<int>(plaintext.size())) != 1)
        {
            throw_openssl("EVP_EncryptUpdate failed");
        }
        int ciphertext_len = len;

        // 5. Finalise (no extra bytes for GCM, but mandatory).
        if (EVP_EncryptFinal_ex(ctx.get(), out.data() + ciphertext_len, &len) != 1)
        {
            throw_openssl("EVP_EncryptFinal_ex failed");
        }
        ciphertext_len += len;

        // 6. Append the auth tag.
        if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_GET_TAG, static_cast<int>(kTagLen), out.data() + ciphertext_len) != 1)
        {
            throw_openssl("EVP_CTRL_GCM_GET_TAG failed");
        }

        out.resize(static_cast<std::size_t>(ciphertext_len) + kTagLen);
        return out;
    }

    std::string AesGcmCipher::decrypt(const std::vector<std::uint8_t> &input, const Key &key, const Nonce &nonce)
    {
        if (input.size() < kTagLen)
        {
            throw_openssl("ciphertext shorter than the GCM tag");
        }
        const std::size_t ct_len = input.size() - kTagLen;
        const unsigned char *ct = input.data();
        const unsigned char *tag = input.data() + ct_len;

        EvpCtxPtr ctx{EVP_CIPHER_CTX_new()};
        if (!ctx)
        {
            throw_openssl("EVP_CIPHER_CTX_new failed");
        }

        if (EVP_DecryptInit_ex(ctx.get(), EVP_aes_256_gcm(), nullptr, nullptr, nullptr) != 1)
        {
            throw_openssl("EVP_DecryptInit_ex (cipher) failed");
        }
        if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_IVLEN, static_cast<int>(kNonceLen), nullptr) != 1)
        {
            throw_openssl("EVP_CTRL_GCM_SET_IVLEN failed");
        }
        if (EVP_DecryptInit_ex(ctx.get(), nullptr, nullptr, key.data(), nonce.data()) != 1)
        {
            throw_openssl("EVP_DecryptInit_ex (key/iv) failed");
        }

        std::string out(ct_len, '\0');
        int len = 0;
        if (EVP_DecryptUpdate(ctx.get(), reinterpret_cast<unsigned char *>(out.data()), &len, ct, static_cast<int>(ct_len)) != 1)
        {
            throw_openssl("EVP_DecryptUpdate failed");
        }
        int plaintext_len = len;

        // Must set the expected tag BEFORE Final.
        if (EVP_CIPHER_CTX_ctrl(ctx.get(), EVP_CTRL_GCM_SET_TAG, static_cast<int>(kTagLen), const_cast<unsigned char *>(tag)) != 1)
        {
            throw_openssl("EVP_CTRL_GCM_SET_TAG failed");
        }

        // Final returns 0 (not just != 1) when the tag does not verify.
        if (EVP_DecryptFinal_ex(ctx.get(), reinterpret_cast<unsigned char *>(out.data()) + plaintext_len, &len) != 1)
        {
            throw AuthError("authentication failed (tampered data or wrong key/nonce)");
        }
        plaintext_len += len;

        out.resize(static_cast<std::size_t>(plaintext_len));
        return out;
    }

}