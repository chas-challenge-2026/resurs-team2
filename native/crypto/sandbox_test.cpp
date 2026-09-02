#include "key_manager.hpp"
#include "aes_gcm_cipher.hpp"

#include <cstring>
#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <stdexcept>
#include <string>
#include <system_error>
#include <vector>

#include <openssl/rand.h>

namespace
{

    namespace fs = std::filesystem;

    int g_failures = 0;

    void check(bool ok, const char *name)
    {
        std::printf("%s %s\n", ok ? "[PASS]" : "[FAIL]", name);
        if (!ok)
        {
            ++g_failures;
        }
    }

    // Prints a byte buffer as a labelled hex line (demo output only)
    void dump(const char *label, const unsigned char *p, std::size_t n)
    {
        std::printf("  %-14s (%2zu)  ", label, n);
        for (std::size_t i = 0; i < n; ++i)
        {
            std::printf("%02x", p[i]);
        }
        std::printf("\n");
    }

    // True if fn() throws std::runtime_error (and nothing else)
    template <typename Fn>
    bool throws_runtime_error(Fn fn)
    {
        try
        {
            fn();
        }
        catch (const std::runtime_error &)
        {
            return true;
        }
        catch (...)
        {
            return false;
        }
        return false;
    }

    template <typename E, typename Fn>
    bool throws_as(Fn fn)
    {
        try
        {
            fn();
        }
        catch (const E &)
        {
            return true;
        }
        catch (...)
        {
            return false;
        }
        return false;
    }

    // Creates a temp file with `nbytes` random bytes on construction
    // removes it on destruction. Copy disabled
    class TempKeyFile
    {
    public:
        explicit TempKeyFile(std::size_t nbytes)
        {
            path_ = fs::temp_directory_path() /
                    ("resurs_key_" + std::to_string(counter_++) + ".bin");

            std::vector<unsigned char> buf(nbytes);
            if (nbytes > 0 &&
                RAND_bytes(buf.data(), static_cast<int>(nbytes)) != 1)
            {
                throw std::runtime_error("RAND_bytes failed");
            }
            std::ofstream f(path_, std::ios::binary);
            f.write(reinterpret_cast<const char *>(buf.data()),
                    static_cast<std::streamsize>(nbytes));
        }

        ~TempKeyFile()
        {
            std::error_code ec;
            fs::remove(path_, ec); // noexcept overload - safe in a destructor
        }

        TempKeyFile(const TempKeyFile &) = delete;
        TempKeyFile &operator=(const TempKeyFile &) = delete;

        std::string path() const { return path_.string(); }

    private:
        static inline int counter_ = 0;
        fs::path path_;
    };

}

int main()
{
    // --- KeyManager ---
    auto &km = resurs::KeyManager::instance();

    check(!km.isLoaded(), "isLoaded() false before load");

    check(throws_runtime_error([&]
                               { (void)km.key(); }),
          "key() before load throws");

    resurs::Key k{};
    for (std::size_t i = 0; i < k.size(); ++i)
    {
        k[i] = static_cast<std::uint8_t>(i + 1);
    }
    km.loadFromBytes(k);
    check(km.key() == k, "loadFromBytes -> key() round-trip");
    check(km.isLoaded(), "isLoaded() true after loadFromBytes");

    km.cleanse();
    check(!km.isLoaded(), "isLoaded() false after cleanse");
    check(throws_runtime_error([&]
                               { (void)km.key(); }),
          "key() after cleanse throws");

    TempKeyFile good(32);
    TempKeyFile too_short(31);
    TempKeyFile too_long(33);

    bool loaded_ok = true;
    try
    {
        km.loadFromFile(good.path());
    }
    catch (...)
    {
        loaded_ok = false;
    }
    check(loaded_ok, "loadFromFile(32 bytes) succeeds");

    check(throws_runtime_error([&]
                               { km.loadFromFile(too_short.path()); }),
          "loadFromFile(31 bytes) throws");
    check(throws_runtime_error([&]
                               { km.loadFromFile(too_long.path()); }),
          "loadFromFile(33 bytes) throws");
    check(throws_runtime_error([&]
                               { km.loadFromFile("/nonexistent/resurs.key"); }),
          "loadFromFile(missing) throws");

    km.cleanse();

    // --- AesGcmCipher demo ---
    {
        resurs::Key demo_key{};
        resurs::Nonce demo_nonce{};
        RAND_bytes(demo_key.data(), static_cast<int>(demo_key.size()));
        RAND_bytes(demo_nonce.data(), static_cast<int>(demo_nonce.size()));

        const std::string plain = "556000-1234";
        const auto demo_ciphertext = resurs::AesGcmCipher::encrypt(plain, demo_key, demo_nonce);

        check(demo_ciphertext.size() == plain.size() + resurs::kTagLen,
              "ciphertext length == plaintext + tag");
        check(std::memcmp(demo_ciphertext.data(), plain.data(), plain.size()) != 0,
              "ciphertext bytes differ from plaintext");
        const std::string back = resurs::AesGcmCipher::decrypt(demo_ciphertext, demo_key, demo_nonce);
        check(back == plain, "encrypt -> decrypt round-trip");

        auto tampered = demo_ciphertext;
        tampered[0] ^= 0x01;
        check(throws_as<resurs::AuthError>([&]
                                           { resurs::AesGcmCipher::decrypt(tampered, demo_key, demo_nonce); }),
              "tampered ciphertext -> AuthError");

        resurs::Key wrong_key = demo_key;
        wrong_key[0] ^= 0x01;
        check(throws_as<resurs::AuthError>([&]
                                           { resurs::AesGcmCipher::decrypt(demo_ciphertext, wrong_key, demo_nonce); }),
              "wrong key -> AuthError");

        resurs::Nonce wrong_nonce = demo_nonce;
        wrong_nonce[0] ^= 0x01;
        check(throws_as<resurs::AuthError>([&]
                                           { resurs::AesGcmCipher::decrypt(demo_ciphertext, demo_key, wrong_nonce); }),
              "wrong nonce -> AuthError");

        // output

        std::printf("\n--- AesGcmCipher demo ---\n");
        std::printf("  plaintext      : \"%s\"\n", plain.c_str());
        dump("key", demo_key.data(), demo_key.size());
        dump("nonce", demo_nonce.data(), demo_nonce.size());
        dump("ciphertext", demo_ciphertext.data(), plain.size());
        dump("tag", demo_ciphertext.data() + plain.size(), resurs::kTagLen);
        dump("ciphertext+tag", demo_ciphertext.data(), demo_ciphertext.size());
        std::printf("length   :  plaintext=%zu  ciphertext+tag=%zu  tag=%zu\n", plain.size(), demo_ciphertext.size(), resurs::kTagLen);
        std::printf("decrypted:  \"%s\"\n", back.c_str());
    }

    std::printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}