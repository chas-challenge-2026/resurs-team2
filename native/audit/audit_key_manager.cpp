#include "audit_key_manager.hpp"
#include "signer.hpp"

#include <memory>
#include <stdexcept>

#include <openssl/pem.h>

namespace resurs::audit {

namespace {

struct BioDeleter {
    void operator()(BIO* b) const noexcept { BIO_free(b); }
};
using BioPtr = std::unique_ptr<BIO, BioDeleter>;

}  // namespace

AuditKeyManager& AuditKeyManager::instance() {
    static AuditKeyManager inst;
    return inst;
}

AuditKeyManager::~AuditKeyManager() {
    cleanse();
}

void AuditKeyManager::loadFromFile(const std::string& path) {
    BioPtr bio{BIO_new_file(path.c_str(), "r")};
    if (!bio) {
        throw std::runtime_error("AuditKeyManager: cannot open key file: " + path);
    }

    EVP_PKEY* raw = PEM_read_bio_PrivateKey(bio.get(), nullptr, nullptr, nullptr);
    if (!raw) {
        throw std::runtime_error("AuditKeyManager: not a valid PEM private key: " + path);
    }

    if (EVP_PKEY_base_id(raw) != EVP_PKEY_ED25519) {
        EVP_PKEY_free(raw);
        throw std::runtime_error("AuditKeyManager: key is not Ed25519: " + path);
    }

    loadFromPKey(raw);   // takes ownership
}

void AuditKeyManager::loadFromPKey(EVP_PKEY* key) {
    std::lock_guard<std::mutex> lock(mutex_);

    PublicKey pub = rawPublicKey(key);   // throws if key is malformed - do this BEFORE freeing the old one

    if (pkey_ != nullptr) {
        EVP_PKEY_free(pkey_);
    }
    pkey_ = key;
    pub_ = pub;
    loaded_ = true;
}

EVP_PKEY* AuditKeyManager::privateKey() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!loaded_) {
        throw std::runtime_error("AuditKeyManager: key not loaded");
    }
    return pkey_;
}

PublicKey AuditKeyManager::publicKey() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!loaded_) {
        throw std::runtime_error("AuditKeyManager: key not loaded");
    }
    return pub_;
}

bool AuditKeyManager::isLoaded() const noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    return loaded_;
}

void AuditKeyManager::cleanse() noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    if (pkey_ != nullptr) {
        EVP_PKEY_free(pkey_);
        pkey_ = nullptr;
    }
    pub_ = PublicKey{};
    loaded_ = false;
}

}  // namespace resurs::audit
