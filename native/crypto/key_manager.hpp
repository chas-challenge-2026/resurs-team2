#ifndef RESURS_KEY_MANAGER_HPP
#define RESURS_KEY_MANAGER_HPP

#include <mutex>
#include <string>

#include "aes_gcm_cipher.hpp"   // for resurs::Key

namespace resurs{
    class KeyManager{
    public:

    static KeyManager& instance();

    // Load exactly kKeyLen bytes from a file. Throws std::runtime_error
    // if the file is missing or not exactly kKeyLen bytes.
    void loadFromFile (const std::string& path);


    // Seam for tests / for loadFromFile. Copies the given key in.
    void loadFromBytes (const Key& key);

    // Throws std::runtime_error if no key has been loaded.
    Key key() const;

    // Wipe the key from memory (OPENSSL_cleanse). Safe to call anytime.
    void cleanse() noexcept;

    KeyManager (const KeyManager&)=delete;
    KeyManager& operator = (const KeyManager&)=delete;


    private:
    KeyManager() = default;
    ~KeyManager();

    mutable std::mutex mutex_;
    Key  key_{};
    bool loaded_ = false;   
    };
}
#endif //RESURS_KEY_MANAGER_HPP
