#ifndef RESURS_KEY_MANAGER_HPP
#define RESURS_KEY_MANAGER_HPP

#include <mutex>
#include <string>

#include "aes_gcm_cipher.hpp"   // for resurs::Key

namespace resurs{
    class KeyManager{
    public:

    static KeyManager& instance();

    // Load exactly 2 * kKeyLen (64) bytes from a file: the first 32 bytes are the
    // AES-256 key, the next 32 are the HMAC lookup key for blind indexing.
    // Throws std::runtime_error if the file is missing or not exactly 64 bytes.
    void loadFromFile (const std::string& path);


    // Seam for tests / for loadFromFile. Copies both keys in.
    void loadFromBytes (const Key& aesKey, const Key& lookupKey);

    // AES-256 key. Throws std::runtime_error if no key has been loaded.
    Key key() const;

    // HMAC lookup key (blind index). Throws std::runtime_error if not loaded.
    Key lookupKey() const;

    // Returns true once the keys have been loaded (loadFromFile / loadFromBytes),
    // false before the first load and after cleanse(). Thread-safe.
    bool isLoaded() const noexcept;


    // Wipe both keys from memory (OPENSSL_cleanse). Safe to call anytime.
    void cleanse() noexcept;

    KeyManager (const KeyManager&)=delete;
    KeyManager& operator = (const KeyManager&)=delete;


    private:
    KeyManager() = default;
    ~KeyManager();

    mutable std::mutex mutex_;
    Key  key_{};          // AES-256
    Key  lookupKey_{};    // HMAC-SHA256 blind-index key
    bool loaded_ = false;
    };
}
#endif //RESURS_KEY_MANAGER_HPP
