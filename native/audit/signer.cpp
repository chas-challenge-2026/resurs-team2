#include "signer.hpp"
#include "hash_chain.hpp"

#include <string>
#include <string_view>
#include <stdexcept>
#include <memory>

#include <openssl/evp.h>

namespace resurs::audit
{
    namespace
    {
        struct EvpMdCtxDeleter
        {
            void operator()(EVP_MD_CTX *c) const noexcept
            {
                EVP_MD_CTX_free(c);
            }
        };
        struct EvpPkeyDeleter
        {
            void operator()(EVP_PKEY *p) const noexcept { EVP_PKEY_free(p); }
        };
        using EvpMdCtxPtr = std::unique_ptr<EVP_MD_CTX, EvpMdCtxDeleter>;
        using EvpPkeyPtr = std::unique_ptr<EVP_PKEY, EvpPkeyDeleter>;

        [[noreturn]] void throw_openssl(const char *what)
        {
            throw std::runtime_error(std::string("signer: ") + what);
        }
    }

    Signature sign(EVP_PKEY *privKey, const Hash &message)
    {
        EvpMdCtxPtr ctx{EVP_MD_CTX_new()};
        if (!ctx)
        {
            throw_openssl("EVP_MD_CTX_new failed");
        }
        // md = nullptr: Ed25519 does not take an external digest algorithm
        if (EVP_DigestSignInit(ctx.get(), nullptr, nullptr, nullptr, privKey) != 1)
        {
            throw_openssl("EVP_DigestSignInit failed");
        }

        Signature sig{};
        std::size_t siglen = sig.size();
        if (EVP_DigestSign(ctx.get(), sig.data(), &siglen, message.data(), message.size()) != 1)
        {
            throw_openssl("EVP_DigestSign failed");
        }
        if (siglen != kSigLen)
        {
            throw_openssl("unexpected signature length");
        }
        return sig;
    }

    bool verify(const PublicKey &pub, const Hash &message,
                const unsigned char *sig, std::size_t sigLen)
    {
        EvpPkeyPtr pkey{EVP_PKEY_new_raw_public_key(EVP_PKEY_ED25519, nullptr, pub.data(), pub.size())};
        if (!pkey)
        {
            throw_openssl("EVP_PKEY_new_raw_public_key failed");
        }

        EvpMdCtxPtr ctx{EVP_MD_CTX_new()};
        if (!ctx)
        {
            throw_openssl("EVP_MD_CTX_new failed");
        }
        if (EVP_DigestVerifyInit(ctx.get(), nullptr, nullptr, nullptr, pkey.get()) != 1)
        {
            throw_openssl("EVP_DigestVerifyInit failed");
        }

        const int rc = EVP_DigestVerify(ctx.get(), sig, sigLen,
                                        message.data(), message.size());
        if (rc == 1)
        {
            return true;
        }
        if (rc == 0)
        {
            return false;
        }
        throw_openssl("EVP_DigestVerify failed");
    }
    
    PublicKey rawPublicKey(EVP_PKEY *key)
    {
        PublicKey pub{};
        std::size_t len = pub.size();
        if (EVP_PKEY_get_raw_public_key(key, pub.data(), &len) != 1)
        {
            throw_openssl("EVP_PKEY_get_raw_public_key failed");
        }
        if (len != kPubKeyLen)
        {
            throw_openssl("unexpected public key length");
        }
        return pub;
    }

}