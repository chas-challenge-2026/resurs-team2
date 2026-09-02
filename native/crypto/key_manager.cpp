#include "key_manager.hpp"

#include <fstream>
#include <stdexcept>

#include <openssl/crypto.h>   // OPENSSL_cleanse

namespace resurs {

KeyManager& KeyManager::instance() {
    static KeyManager inst;
    return inst;
}

KeyManager::~KeyManager() {
    cleanse();
}

void KeyManager::loadFromBytes(const Key& key) {
    std::lock_guard<std::mutex> lock(mutex_);
    key_ = key;          // copy 32 bytes into the member
    loaded_ = true;
}

Key KeyManager::key() const {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!loaded_) {
        throw std::runtime_error("KeyManager: key not loaded");
    }
    return key_;          // returns a copy (by value)
}

bool KeyManager::isLoaded() const noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    return loaded_;
}

void KeyManager::cleanse() noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    OPENSSL_cleanse(key_.data(), key_.size());
    loaded_ = false;
}

void KeyManager::loadFromFile(const std::string& path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) {
        throw std::runtime_error("KeyManager: cannot open key file: " + path);
    }

    Key buf{};
    file.read(reinterpret_cast<char*>(buf.data()), buf.size());
    if (file.gcount() != static_cast<std::streamsize>(buf.size())) {
        throw std::runtime_error("KeyManager: key file must be exactly 32 bytes");
    }

    // Reject files LARGER than 32 bytes too: try to read one more byte.
    char extra = 0;
    file.read(&extra, 1);
    if (file.gcount() != 0) {
        throw std::runtime_error("KeyManager: key file must be exactly 32 bytes");
    }

    loadFromBytes(buf);       // takes the lock, copies in, sets loaded_
    OPENSSL_cleanse(buf.data(), buf.size());   // wipe the local copy
}

}
