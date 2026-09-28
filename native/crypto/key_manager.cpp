#include "key_manager.hpp"

#include <algorithm>
#include <array>
#include <cstdint>
#include <fstream>
#include <stdexcept>

#include <openssl/crypto.h>   // OPENSSL_cleanse

namespace resurs {

namespace {
constexpr std::size_t kFileLen = 2 * kKeyLen;   // 32 AES + 32 HMAC lookup
}

KeyManager& KeyManager::instance() {
    static KeyManager inst;
    return inst;
}

KeyManager::~KeyManager() {
    cleanse();
}

void KeyManager::loadFromBytes(const Key& aesKey, const Key& lookupKey) {
    std::lock_guard<std::mutex> lock(mutex_);
    key_ = aesKey;
    lookupKey_ = lookupKey;
    loaded_ = true;
}

SecretKey KeyManager::key() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!loaded_) {
        throw std::runtime_error("KeyManager: key not loaded");
    }
    return SecretKey{key_};          // copy is wiped when the caller is done
}

SecretKey KeyManager::lookupKey() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!loaded_) {
        throw std::runtime_error("KeyManager: key not loaded");
    }
    return SecretKey{lookupKey_};    // copy is wiped when the caller is done
}

bool KeyManager::isLoaded() const noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    return loaded_;
}

void KeyManager::cleanse() noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    OPENSSL_cleanse(key_.data(), key_.size());
    OPENSSL_cleanse(lookupKey_.data(), lookupKey_.size());
    loaded_ = false;
}

void KeyManager::loadFromFile(const std::string& path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) {
        throw std::runtime_error("KeyManager: cannot open key file: " + path);
    }

    // buf, aes and lookup are wiped on every exit, including the throws below.
    SecretArray<kFileLen> buf;
    file.read(reinterpret_cast<char*>(buf.bytes().data()), kFileLen);
    if (file.gcount() != static_cast<std::streamsize>(kFileLen)) {
        throw std::runtime_error("KeyManager: key file must be exactly 64 bytes");
    }

    // Reject files LARGER than 64 bytes too: try to read one more byte.
    char extra = 0;
    file.read(&extra, 1);
    if (file.gcount() != 0) {
        throw std::runtime_error("KeyManager: key file must be exactly 64 bytes");
    }

    SecretKey aes;
    SecretKey lookup;
    std::copy(buf.bytes().begin(), buf.bytes().begin() + kKeyLen, aes.bytes().begin());
    std::copy(buf.bytes().begin() + kKeyLen, buf.bytes().end(), lookup.bytes().begin());

    loadFromBytes(aes.bytes(), lookup.bytes());   // takes the lock, copies in, sets loaded_
}

}
