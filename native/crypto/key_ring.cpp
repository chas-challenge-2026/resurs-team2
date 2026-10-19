#include "key_ring.hpp"

#include <cstring>

#include <openssl/crypto.h>   // OPENSSL_cleanse, CRYPTO_memcmp

#include "secret_file.hpp"
#include "secure_buffer.hpp"

namespace resurs {

KeyRing::~KeyRing() {
    OPENSSL_cleanse(keys_.data(), sizeof(keys_));
}

std::unique_ptr<KeyRing> KeyRing::parse(const std::uint8_t* data, std::size_t len) {
    if (data == nullptr) {
        throw std::runtime_error("KeyRing: no data");
    }
    if (len > kKeyRingMaxLen) {
        throw std::runtime_error("KeyRing: file too large");
    }
    if (len < kKeyRingHeaderLen + kKeyRingEntryLen) {
        throw std::runtime_error("KeyRing: file too small");
    }
    if (std::memcmp(data, kKeyRingMagic, sizeof(kKeyRingMagic)) != 0) {
        throw std::runtime_error("KeyRing: bad magic (not a key ring file)");
    }
    if (data[4] != kKeyRingFormat) {
        throw std::runtime_error("KeyRing: unsupported format " + std::to_string(data[4]));
    }

    const std::uint8_t active = data[5];
    const std::size_t count = data[6];
    if (count == 0) {
        throw std::runtime_error("KeyRing: no keys");
    }
    if (len != kKeyRingHeaderLen + count * kKeyRingEntryLen) {
        throw std::runtime_error("KeyRing: size does not match key count");
    }

    // make_unique cannot reach the private constructor.
    // From here on, any throw destroys the ring, which wipes what was copied in.
    std::unique_ptr<KeyRing> ring(new KeyRing());

    const std::uint8_t* entry = data + kKeyRingHeaderLen;
    for (std::size_t i = 0; i < count; ++i, entry += kKeyRingEntryLen) {
        const std::uint8_t version = entry[0];
        if (version == 0) {
            throw std::runtime_error("KeyRing: version 0 is not allowed");
        }
        if (ring->present_[version]) {
            throw std::runtime_error("KeyRing: duplicate version " + std::to_string(version));
        }
        std::memcpy(ring->keys_[version].data(), entry + 1, kKeyLen);
        ring->present_[version] = true;
    }

    if (active == 0 || !ring->present_[active]) {
        throw std::runtime_error("KeyRing: active version " + std::to_string(active) +
                                 " has no key");
    }
    ring->active_ = active;
    return ring;
}

std::unique_ptr<KeyRing> KeyRing::fromFile(const std::string& path) {
    // One byte more than the largest valid file, so an oversized file is
    // detected by parse() instead of being silently truncated.
    // buf is wiped on every exit, including the throws below.
    SecretArray<kKeyRingMaxLen + 1> buf;
    const std::size_t n = readSecretFile(path, buf.bytes().data(), buf.bytes().size());
    return parse(buf.bytes().data(), n);
}

const Key& KeyRing::key(std::uint8_t version) const {
    if (!present_[version]) {
        throw UnknownKeyVersion("KeyRing: unknown key version " + std::to_string(version));
    }
    return keys_[version];
}

void KeyRing::checkReplaceableBy(const KeyRing& next) const {
    if (!next.present_[active_]) {
        throw std::runtime_error("KeyRing: new ring drops the active version " +
                                 std::to_string(active_));
    }
    for (std::size_t v = 1; v < keys_.size(); ++v) {
        // Constant-time compare: the result must not depend on where the bytes differ.
        if (present_[v] && next.present_[v] &&
            CRYPTO_memcmp(keys_[v].data(), next.keys_[v].data(), kKeyLen) != 0) {
            throw std::runtime_error("KeyRing: version " + std::to_string(v) +
                                     " has a different key in the new ring");
        }
    }
}

}
