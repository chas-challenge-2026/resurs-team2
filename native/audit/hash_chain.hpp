#ifndef RESURS_AUDIT_HASH_CHAIN_HPP
#define RESURS_AUDIT_HASH_CHAIN_HPP

#include <array>
#include <cstdint>
#include <string_view>

namespace resurs::audit {

inline constexpr std::size_t kHashLen = 32;   // SHA-256

using Hash = std::array<std::uint8_t, kHashLen>;

// Hash used for the first entry in a chain (no predecessor): 32 zero bytes.
inline constexpr Hash kGenesisHash{};

// hash = SHA-256(prev || entryJson). Throws std::runtime_error on OpenSSL failure.
Hash chainHash(const Hash& prev, std::string_view entryJson);

}  // namespace resurs::audit

#endif // RESURS_AUDIT_HASH_CHAIN_HPP
