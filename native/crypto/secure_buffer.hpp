#ifndef RESURS_SECURE_BUFFER_HPP
#define RESURS_SECURE_BUFFER_HPP

#include <array>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <utility>

#include <openssl/crypto.h> // OPENSSL_cleanse

namespace resurs
{
    // A fixed-size secret, such as a key. Wiped when it goes out of scope,
    // even if an exception is thrown.
    //
    // Copy and move are disabled: for std::array both would duplicate the
    // secret. Returning it by value still works (C++17 copy elision).
    template <std::size_t N>
    class SecretArray
    {
    public:
        SecretArray() = default; // zero-filled
        explicit SecretArray(const std::array<std::uint8_t, N> &src) : bytes_(src) {}

        ~SecretArray() { OPENSSL_cleanse(bytes_.data(), bytes_.size()); }

        SecretArray(const SecretArray &) = delete;
        SecretArray &operator=(const SecretArray &) = delete;
        SecretArray(SecretArray &&) = delete;
        SecretArray &operator=(SecretArray &&) = delete;

        std::array<std::uint8_t, N> &bytes() noexcept { return bytes_; }
        const std::array<std::uint8_t, N> &bytes() const noexcept { return bytes_; }

    private:
        std::array<std::uint8_t, N> bytes_{};
    };

    // A heap buffer for secret data, such as a decrypted plaintext. Wiped when
    // it goes out of scope.
    //
    // Its size is fixed, so unlike std::string it never leaves an old copy
    // behind when growing. It can be moved (the pointer is handed over) but
    // not copied.
    class SecureBytes
    {
    public:
        explicit SecureBytes(std::size_t size)
            : data_(std::make_unique<std::uint8_t[]>(size)), size_(size) {} // zero-filled

        ~SecureBytes()
        {
            if (data_)
            {
                OPENSSL_cleanse(data_.get(), size_);
            }
        }

        SecureBytes(SecureBytes &&other) noexcept
            : data_(std::move(other.data_)), size_(std::exchange(other.size_, 0)) {}

        SecureBytes(const SecureBytes &) = delete;
        SecureBytes &operator=(const SecureBytes &) = delete;
        SecureBytes &operator=(SecureBytes &&) = delete;

        std::uint8_t *data() noexcept { return data_.get(); }
        const std::uint8_t *data() const noexcept { return data_.get(); }
        std::size_t size() const noexcept { return size_; }

    private:
        std::unique_ptr<std::uint8_t[]> data_;
        std::size_t size_;
    };
}

#endif // RESURS_SECURE_BUFFER_HPP
