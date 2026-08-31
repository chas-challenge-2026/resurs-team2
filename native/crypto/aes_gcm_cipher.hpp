#ifndef RESURS_AES_GCM_CIPHER_HPP
#define RESURS_AES_GCM_CIPHER_HPP

#include <array>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>
#include <stdexcept>

namespace resurs{
    inline constexpr std::size_t kKeyLen    =32; //AES-256
    inline constexpr std::size_t kNonceLen  =12; // gcm-nonce
    inline constexpr std::size_t kTagLen    =16; //gcm-tag
    
    using Key = std::array<std::uint8_t, kKeyLen>;
    using Nonce = std::array<std::uint8_t, kNonceLen>;

    // Thrown by AesGcmCipher::decrypt when the GCM tag does not verify:
    // the ciphertext was tampered with, or the key/nonce is wrong.
    class AuthError : public std::runtime_error {
    public:
        using std::runtime_error::runtime_error;
    };

    // Stateless AES-256-GCM
    // All methods will throw std::runtime_error on failure
    class AesGcmCipher{
    public:
    // Returns ciphertext with the 16 byte GCM tag
    static std::vector<std::uint8_t> encrypt(std::string_view plaintext, const Key& key, const Nonce& nonce);

    // Need ciphertext as 'input'
    // Throws if the GCM tag check fails (tampered data / wrong key or nonce).
    static std::string decrypt(const std::vector<std::uint8_t>& input, const Key& key, const Nonce& nonce);

    };

}

#endif // RESURS_AES_GCM_CIPHER_HPP