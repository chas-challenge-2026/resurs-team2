#ifndef RESURS_KEY_RING_HPP
#define RESURS_KEY_RING_HPP

#include <array>
#include <bitset>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>

#include "aes_gcm_cipher.hpp"   // for resurs::Key, kKeyLen

namespace resurs {

// Key ring file layout (all fields are single bytes, so there is no endianness):
//   [ magic "RSKR" : 4 ][ format : 1 ][ active : 1 ][ count : 1 ]
//   then `count` entries of [ version : 1 ][ AES-256 key : 32 ]
// The file size must be exactly kKeyRingHeaderLen + count * kKeyRingEntryLen.
// Versions are 1..255 (0 is never valid), unique, in any order; `active` must be
// one of them. The HMAC lookup key is NOT part of this file.
inline constexpr std::uint8_t kKeyRingMagic[4]  = {'R', 'S', 'K', 'R'};
inline constexpr std::uint8_t kKeyRingFormat    = 1;
inline constexpr std::size_t  kKeyRingHeaderLen = 4 + 1 + 1 + 1;
inline constexpr std::size_t  kKeyRingEntryLen  = 1 + kKeyLen;
inline constexpr std::size_t  kKeyRingMaxCount  = 255;
inline constexpr std::size_t  kKeyRingMaxLen    =
    kKeyRingHeaderLen + kKeyRingMaxCount * kKeyRingEntryLen;   // 8422

// Thrown by KeyRing::key() for a version the ring does not hold.
class UnknownKeyVersion : public std::runtime_error {
public:
    using std::runtime_error::runtime_error;
};

// An immutable set of versioned AES-256 keys plus the active (encrypt) version.
// Knows nothing about locking: KeyManager owns the current ring and swaps it.
// Always heap-allocated and never copied or moved, so key bytes exist in exactly
// one place; the destructor wipes them.
class KeyRing {
public:
    // Parses a key ring file image. Throws std::runtime_error on any violation
    // of the layout above. On throw nothing is left behind (partial ring is wiped).
    static std::unique_ptr<KeyRing> parse(const std::uint8_t* data, std::size_t len);

    // Reads at most kKeyRingMaxLen + 1 bytes from `path` (see readSecretFile for
    // the file type/permission checks) and calls parse().
    // Throws std::runtime_error if the file cannot be read or is invalid.
    static std::unique_ptr<KeyRing> fromFile(const std::string& path);

    std::uint8_t active() const noexcept { return active_; }
    bool has(std::uint8_t version) const noexcept { return present_[version]; }
    std::size_t count() const noexcept { return present_.count(); }

    // Key for `version`. Throws UnknownKeyVersion if the ring does not hold it.
    const Key& key(std::uint8_t version) const;

    // Rules for a hot reload: throws std::runtime_error if `next` may not
    // replace this ring, i.e. if
    //   - a version present in both rings has different key bytes, or
    //   - `next` does not hold this ring's active version.
    // Adding versions, changing `active` (also back to an older version, as a
    // rollback), and dropping other versions are allowed.
    void checkReplaceableBy(const KeyRing& next) const;

    ~KeyRing();

    KeyRing(const KeyRing&) = delete;
    KeyRing& operator=(const KeyRing&) = delete;
    KeyRing(KeyRing&&) = delete;
    KeyRing& operator=(KeyRing&&) = delete;

private:
    KeyRing() = default;

    std::array<Key, 256> keys_{};   // index == version
    std::bitset<256> present_;
    std::uint8_t active_ = 0;
};

}

#endif // RESURS_KEY_RING_HPP
