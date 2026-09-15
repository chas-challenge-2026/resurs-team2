#ifndef RESURS_AUDIT_KEY_MANAGER_HPP
#define RESURS_AUDIT_KEY_MANAGER_HPP

#include <mutex>
#include <string>

#include <openssl/evp.h>

#include "signer.hpp"   // for resurs::audit::PublicKey

namespace resurs::audit {

class AuditKeyManager {
public:
    static AuditKeyManager& instance();

    // Load an Ed25519 private key from a PEM file. Caches the derived public
    // key (rawPublicKey) so publicKey() never needs the private key again.
    // Throws std::runtime_error if the file is missing, unreadable, not a
    // valid PEM private key, or not an Ed25519 key.
    void loadFromFile(const std::string& path);

    // Seam for tests: takes ownership of an already-loaded EVP_PKEY (e.g.
    // from EVP_PKEY_Q_keygen). Frees any previously held key first.
    void loadFromPKey(EVP_PKEY* key);

    // The private key, for sign(). Throws std::runtime_error if not loaded.
    EVP_PKEY* privateKey() const;

    // The cached public key, for distributing to auditors. Throws
    // std::runtime_error if not loaded.
    PublicKey publicKey() const;

    // Returns true once a key has been loaded, false before the first load
    // and after cleanse(). Thread-safe.
    bool isLoaded() const noexcept;

    // Frees the private key (EVP_PKEY_free) and clears the cached public
    // key. Safe to call anytime, including before any load.
    void cleanse() noexcept;

    AuditKeyManager(const AuditKeyManager&) = delete;
    AuditKeyManager& operator=(const AuditKeyManager&) = delete;

private:
    AuditKeyManager() = default;
    ~AuditKeyManager();

    mutable std::mutex mutex_;
    EVP_PKEY* pkey_ = nullptr;
    PublicKey pub_{};
    bool loaded_ = false;
};

}  // namespace resurs::audit

#endif // RESURS_AUDIT_KEY_MANAGER_HPP
