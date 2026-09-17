#include "resurs_audit.h"
#include "audit_key_manager.hpp"
#include "hash_chain.hpp"
#include "signer.hpp"

#include <cstring>

extern "C"
{

    int resurs_audit_init(const char *key_file_path)
    {
        if (key_file_path == nullptr)
        {
            return RESURS_AUDIT_ERR_INVALID_ARG;
        }
        try
        {
            resurs::audit::AuditKeyManager::instance().loadFromFile(key_file_path);
            return RESURS_AUDIT_OK;
        }
        catch (const std::exception &)
        {
            return RESURS_AUDIT_ERR_KEY_IO;
        }
    }

    int resurs_audit_chain_entry(const unsigned char *prev_hash,
                                 const char *entry_json, size_t entry_len,
                                 unsigned char *hash_out,
                                 unsigned char *signature_out, size_t *signature_len)
    {
        auto &mgr = resurs::audit::AuditKeyManager::instance();
        if (!mgr.isLoaded())
        {
            return RESURS_AUDIT_ERR_NOT_INIT;
        }
        if (entry_json == nullptr || hash_out == nullptr ||
            signature_out == nullptr || signature_len == nullptr)
        {
            return RESURS_AUDIT_ERR_INVALID_ARG;
        }
        try
        {
            resurs::audit::Hash prev = resurs::audit::kGenesisHash;
            if (prev_hash != nullptr)
            {
                std::memcpy(prev.data(), prev_hash, resurs::audit::kHashLen);
            }
            resurs::audit::Hash h = resurs::audit::chainHash(prev, {entry_json, entry_len});
            resurs::audit::Signature s = resurs::audit::sign(mgr.privateKey(), h);

            std::memcpy(hash_out, h.data(), resurs::audit::kHashLen);
            std::memcpy(signature_out, s.data(), resurs::audit::kSigLen);
            *signature_len = resurs::audit::kSigLen;
            return RESURS_AUDIT_OK;
        }
        catch (const std::exception &)
        {
            return RESURS_AUDIT_ERR_INTERNAL;
        }
    }

    int resurs_audit_verify_chain(const unsigned char *hashes,
                                  const unsigned char *signatures,
                                  const size_t *signature_lens,
                                  const char *entries, const size_t *entry_lens,
                                  size_t entry_count,
                                  const unsigned char *public_key,
                                  int *first_invalid_index)
    {
        if (hashes == nullptr || signatures == nullptr ||
            signature_lens == nullptr || entries == nullptr ||
            entry_lens == nullptr || entry_count == 0 ||
            public_key == nullptr || first_invalid_index == nullptr)
        {
            return RESURS_AUDIT_ERR_INVALID_ARG;
        }
        try
        {
            resurs::audit::PublicKey pub;
            std::memcpy(pub.data(), public_key, resurs::audit::kPubKeyLen);
            *first_invalid_index = -1;

            const char *entryCursor = entries;
            const unsigned char *sigCursor = signatures;

            for (size_t i = 0; i < entry_count; ++i)
            {
                resurs::audit::Hash prev = resurs::audit::kGenesisHash;
                if (i > 0)
                {
                    std::memcpy(prev.data(), hashes + (i - 1) * resurs::audit::kHashLen, resurs::audit::kHashLen);
                }

                resurs::audit::Hash expected = resurs::audit::chainHash(prev, {entryCursor, entry_lens[i]});

                resurs::audit::Hash stored;
                std::memcpy(stored.data(), hashes + i * resurs::audit::kHashLen, resurs::audit::kHashLen);

                if (expected != stored || !resurs::audit::verify(pub, stored, sigCursor, signature_lens[i]))
                {
                    *first_invalid_index = static_cast<int>(i);
                    break;
                }

                entryCursor += entry_lens[i];
                sigCursor += signature_lens[i];
            }

            return RESURS_AUDIT_OK;
        }
        catch (const std::exception &)
        {
            return RESURS_AUDIT_ERR_INTERNAL;
        }
    }

    void resurs_audit_shutdown(void)
    {
        try
        {
            resurs::audit::AuditKeyManager::instance().cleanse();
        }
        catch (...)
        {
        }
    }

} // extern "C"
