#ifndef RESURS_KEY_MANAGER_HPP
#define RESURS_KEY_MANAGER_HPP

#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>

#include "aes_gcm_cipher.hpp"   // for resurs::Key
#include "key_ring.hpp"
#include "secure_buffer.hpp"

namespace resurs{
    // A key copy from KeyManager, wiped when it goes out of scope.
    using SecretKey = SecretArray<kKeyLen>;

    // The key to encrypt with, together with the version byte to stamp on the
    // ciphertext. Taken in one locked step, so a concurrent reload can never pair
    // one version's byte with another version's key.
    struct ActiveKey {
        std::uint8_t version;
        SecretKey key;
    };

    // Thrown when keys are needed but none are loaded (before init / after cleanse).
    class KeyNotLoaded : public std::runtime_error {
    public:
        using std::runtime_error::runtime_error;
    };

    // Thrown by reload when the new ring is valid on its own but may not replace
    // the current one (see KeyRing::checkReplaceableBy).
    class KeyRingRejected : public std::runtime_error {
    public:
        using std::runtime_error::runtime_error;
    };

    class KeyManager{
    public:

    static KeyManager& instance();

    // Startup load: the AES key ring file (see key_ring.hpp) and the HMAC lookup
    // key file (exactly kKeyLen raw bytes). Both files are read before anything
    // changes, so on throw the previous state is kept.
    // If keys are already loaded (e.g. one init per Spring context), the new keys
    // must be compatible: same lookup key bytes, and the ring must pass the reload
    // rules - otherwise KeyRingRejected. After cleanse() any keys may be loaded.
    void loadFromFiles(const std::string& ringPath, const std::string& lookupPath);

    // Seam for tests / for loadFromFiles. Takes the ring, copies the lookup key in.
    // Same rules as loadFromFiles.
    void load(std::unique_ptr<KeyRing> ring, const Key& lookupKey);

    // Hot reload of the AES key ring only; the lookup key is never touched.
    // Throws KeyNotLoaded before a load, KeyRingRejected if the new ring may not
    // replace the current one, std::runtime_error if the file is invalid.
    // On any throw the current ring stays in use.
    void reloadFromFile(const std::string& ringPath);
    void reload(std::unique_ptr<KeyRing> ring);

    // Active version and a copy of its key. Throws KeyNotLoaded.
    ActiveKey activeKey() const;

    // Active version only. Throws KeyNotLoaded.
    std::uint8_t activeVersion() const;

    // AES key for `version`. Throws KeyNotLoaded, or UnknownKeyVersion if the
    // current ring does not hold that version.
    SecretKey key(std::uint8_t version) const;

    // HMAC lookup key (blind index). Throws KeyNotLoaded.
    SecretKey lookupKey() const;

    // Returns true once keys have been loaded, false before the first load and
    // after cleanse(). Thread-safe.
    bool isLoaded() const noexcept;

    // Wipe all keys from memory (OPENSSL_cleanse). Safe to call anytime.
    void cleanse() noexcept;

    KeyManager (const KeyManager&)=delete;
    KeyManager& operator = (const KeyManager&)=delete;


    private:
    KeyManager() = default;
    ~KeyManager();

    mutable std::mutex mutex_;
    std::unique_ptr<KeyRing> ring_;   // AES keys by version; null == not loaded
    Key  lookupKey_{};                // HMAC-SHA256 blind-index key
    };
}
#endif //RESURS_KEY_MANAGER_HPP
