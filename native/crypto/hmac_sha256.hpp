#ifndef RESURS_HMAC_SHA256_HPP
#define RESURS_HMAC_SHA256_HPP

#include <array>
#include <cstdint>
#include <string_view>

#include "aes_gcm_cipher.hpp"   // for resurs::Key

namespace resurs {

inline constexpr std::size_t kHmacLen = 32;   // HMAC-SHA256 output

using Hmac = std::array<std::uint8_t, kHmacLen>;

// Deterministic HMAC-SHA256 over `data` with `key` (32 bytes).
// The same (data, key) always yields the same 32 bytes — that determinism is
// what makes the output usable as a blind-index lookup value in a WHERE clause.
// Throws std::runtime_error on any OpenSSL failure.
Hmac hmacSha256(std::string_view data, const Key& key);

}

#endif // RESURS_HMAC_SHA256_HPP
