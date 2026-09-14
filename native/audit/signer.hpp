#ifndef RESURS_AUDIT_SIGNER_HPP
#define RESURS_AUDIT_SIGNER_HPP

#include <array>
#include <cstdint>
#include <cstddef>

#include <openssl/evp.h>

#include "hash_chain.hpp"   // for resurs::audit::Hash

namespace resurs::audit {

inline constexpr std::size_t kSigLen    = 64;  // Ed25519 signature (fixed)
inline constexpr std::size_t kPubKeyLen = 32;  // Ed25519 public key (fixed)

using Signature = std::array<std::uint8_t, kSigLen>;
using PublicKey = std::array<std::uint8_t, kPubKeyLen>;

// Signs a 32-byte chain hash with an Ed25519 private key.
// privKey must be an EVP_PKEY of type EVP_PKEY_ED25519 (caller owns it).
// Throws std::runtime_error on any OpenSSL failure.
Signature sign(EVP_PKEY* privKey, const Hash& message);

// Verifies an Ed25519 signature over a 32-byte chain hash.
// Returns true/false - does NOT throw on a mismatched signature (the chain
// verifier must keep going and report an index, not unwind on the first bad
// entry). Throws std::runtime_error only for a genuine OpenSSL failure.
bool verify(const PublicKey& pub, const Hash& message,
            const unsigned char* sig, std::size_t sigLen);

// Extracts the raw 32-byte public key from an Ed25519 EVP_PKEY (private or
// public). Used by AuditKeyManager right after loading the private key.
PublicKey rawPublicKey(EVP_PKEY* key);

}  // namespace resurs::audit

#endif // RESURS_AUDIT_SIGNER_HPP
