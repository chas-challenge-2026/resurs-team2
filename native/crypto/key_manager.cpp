#include "key_manager.hpp"

#include <algorithm>
#include <stdexcept>
#include <utility>

#include <openssl/crypto.h>   // OPENSSL_cleanse, CRYPTO_memcmp

#include "secret_file.hpp"

namespace resurs {

namespace {

// Reads a lookup key file: exactly kKeyLen raw bytes.
// Reads one byte more than needed, so a longer file is rejected too.
void readLookupKey(const std::string& path, SecretKey& out) {
    // buf is wiped on every exit, including the throws.
    SecretArray<kKeyLen + 1> buf;
    const std::size_t n = readSecretFile(path, buf.bytes().data(), buf.bytes().size());
    if (n != kKeyLen) {
        throw std::runtime_error("KeyManager: lookup key file must be exactly 32 bytes");
    }
    std::copy(buf.bytes().begin(), buf.bytes().begin() + kKeyLen, out.bytes().begin());
}

}

KeyManager& KeyManager::instance() {
    static KeyManager inst;
    return inst;
}

KeyManager::~KeyManager() {
    cleanse();
}

void KeyManager::load(std::unique_ptr<KeyRing> ring, const Key& lookupKey) {
    if (!ring) {
        throw std::invalid_argument("KeyManager: null key ring");
    }
    std::unique_ptr<KeyRing> old;   // destroyed (and wiped) after the lock is released
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (ring_) {
            // Already loaded: a second load must not change the blind index or
            // sidestep the reload rules. Repeating the same load is fine.
            if (CRYPTO_memcmp(lookupKey_.data(), lookupKey.data(), kKeyLen) != 0) {
                throw KeyRingRejected("KeyManager: a different lookup key is already loaded");
            }
            try {
                ring_->checkReplaceableBy(*ring);
            } catch (const std::runtime_error& e) {
                throw KeyRingRejected(e.what());
            }
        }
        old = std::exchange(ring_, std::move(ring));
        lookupKey_ = lookupKey;
    }
}

void KeyManager::loadFromFiles(const std::string& ringPath, const std::string& lookupPath) {
    // Read and validate both files before touching the loaded state.
    std::unique_ptr<KeyRing> ring = KeyRing::fromFile(ringPath);
    SecretKey lookup;
    readLookupKey(lookupPath, lookup);

    load(std::move(ring), lookup.bytes());
}

void KeyManager::reload(std::unique_ptr<KeyRing> ring) {
    if (!ring) {
        throw std::invalid_argument("KeyManager: null key ring");
    }
    // Declared before the lock, so the old ring is destroyed after the lock is
    // released. Callers still using an old key hold their own SecretKey copy.
    std::unique_ptr<KeyRing> old;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!ring_) {
            throw KeyNotLoaded("KeyManager: reload before load");
        }
        try {
            ring_->checkReplaceableBy(*ring);
        } catch (const std::runtime_error& e) {
            throw KeyRingRejected(e.what());
        }
        old = std::exchange(ring_, std::move(ring));
    }
}

void KeyManager::reloadFromFile(const std::string& ringPath) {
    // Parse outside the lock: a slow or broken file never blocks encrypt/decrypt.
    reload(KeyRing::fromFile(ringPath));
}

ActiveKey KeyManager::activeKey() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!ring_) {
        throw KeyNotLoaded("KeyManager: key not loaded");
    }
    const std::uint8_t v = ring_->active();
    return ActiveKey{v, SecretKey{ring_->key(v)}};   // copy is wiped when the caller is done
}

std::uint8_t KeyManager::activeVersion() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!ring_) {
        throw KeyNotLoaded("KeyManager: key not loaded");
    }
    return ring_->active();
}

SecretKey KeyManager::key(std::uint8_t version) const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!ring_) {
        throw KeyNotLoaded("KeyManager: key not loaded");
    }
    return SecretKey{ring_->key(version)};   // throws UnknownKeyVersion
}

SecretKey KeyManager::lookupKey() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!ring_) {
        throw KeyNotLoaded("KeyManager: key not loaded");
    }
    return SecretKey{lookupKey_};    // copy is wiped when the caller is done
}

bool KeyManager::isLoaded() const noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    return ring_ != nullptr;
}

void KeyManager::cleanse() noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    ring_.reset();   // ~KeyRing wipes the AES keys
    OPENSSL_cleanse(lookupKey_.data(), lookupKey_.size());
}

}
