#include "key_manager.hpp"
#include "aes_gcm_cipher.hpp"
#include "hmac_sha256.hpp"

#include <cstring>
#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <limits>
#include <stdexcept>
#include <string>
#include <string_view>
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

    // Runs the AES-GCM + HMAC checks against one (aes, lookup) key pair.
    // Called once with random keys and once with the real generated key file,
    // so both paths exercise the same behaviour.
    void crypto_demo(const char *label,
                     const resurs::Key &aes,
                     const resurs::Key &lookup,
                     bool dump_keys)
    {
        std::printf("\n--- AesGcmCipher demo: %s ---\n", label);

        resurs::Nonce nonce{};
        RAND_bytes(nonce.data(), static_cast<int>(nonce.size()));

        const std::string plain = "556000-1234";
        const auto ct = resurs::AesGcmCipher::encrypt(plain, aes, nonce);

        check(ct.size() == plain.size() + resurs::kTagLen,
              "ciphertext length == plaintext + tag");
        check(std::memcmp(ct.data(), plain.data(), plain.size()) != 0,
              "ciphertext bytes differ from plaintext");
        const std::string back = resurs::AesGcmCipher::decrypt(ct, aes, nonce);
        check(back == plain, "encrypt -> decrypt round-trip");

        auto tampered = ct;
        tampered[0] ^= 0x01;
        check(throws_as<resurs::AuthError>([&]
                                           { resurs::AesGcmCipher::decrypt(tampered, aes, nonce); }),
              "tampered ciphertext -> AuthError");

        resurs::Key wrong_key = aes;
        wrong_key[0] ^= 0x01;
        check(throws_as<resurs::AuthError>([&]
                                           { resurs::AesGcmCipher::decrypt(ct, wrong_key, nonce); }),
              "wrong key -> AuthError");

        resurs::Nonce wrong_nonce = nonce;
        wrong_nonce[0] ^= 0x01;
        check(throws_as<resurs::AuthError>([&]
                                           { resurs::AesGcmCipher::decrypt(ct, aes, wrong_nonce); }),
              "wrong nonce -> AuthError");

        const resurs::Hmac h1 = resurs::hmacSha256(plain, lookup);
        const resurs::Hmac h2 = resurs::hmacSha256(plain, lookup);
        check(h1 == h2, "hmacSha256 deterministic for the same input");
        check(h1 != resurs::hmacSha256("556000-9999", lookup),
              "hmacSha256 differs for different input");

        // output
        std::printf("  plaintext      : \"%s\"\n", plain.c_str());
        if (dump_keys)
        {
            dump("aes key", aes.data(), aes.size());
            dump("lookup key", lookup.data(), lookup.size());
        }
        dump("nonce", nonce.data(), nonce.size());
        dump("ciphertext+tag", ct.data(), ct.size());
        dump("hmac", h1.data(), h1.size());
        std::printf("  decrypted      : \"%s\"\n", back.c_str());
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
    resurs::Key lk{};
    for (std::size_t i = 0; i < k.size(); ++i)
    {
        k[i] = static_cast<std::uint8_t>(i + 1);
        lk[i] = static_cast<std::uint8_t>(0x80 + i);
    }
    km.loadFromBytes(k, lk);
    check(km.key() == k, "loadFromBytes -> key() round-trip");
    check(km.lookupKey() == lk, "loadFromBytes -> lookupKey() round-trip");
    check(km.isLoaded(), "isLoaded() true after loadFromBytes");

    km.cleanse();
    check(!km.isLoaded(), "isLoaded() false after cleanse");
    check(throws_runtime_error([&]
                               { (void)km.key(); }),
          "key() after cleanse throws");
    check(throws_runtime_error([&]
                               { (void)km.lookupKey(); }),
          "lookupKey() after cleanse throws");

    TempKeyFile good(64);
    TempKeyFile too_short(63);
    TempKeyFile too_long(65);

    bool loaded_ok = true;
    try
    {
        km.loadFromFile(good.path());
    }
    catch (...)
    {
        loaded_ok = false;
    }
    check(loaded_ok, "loadFromFile(64 bytes) succeeds");

    check(throws_runtime_error([&]
                               { km.loadFromFile(too_short.path()); }),
          "loadFromFile(63 bytes) throws");
    check(throws_runtime_error([&]
                               { km.loadFromFile(too_long.path()); }),
          "loadFromFile(65 bytes) throws");
    check(throws_runtime_error([&]
                               { km.loadFromFile("/nonexistent/resurs.key"); }),
          "loadFromFile(missing) throws");

    km.cleanse();

    // --- hmacSha256 ---
    {
        resurs::Key hk{};
        RAND_bytes(hk.data(), static_cast<int>(hk.size()));

        const resurs::Hmac a1 = resurs::hmacSha256("556000-1234", hk);
        const resurs::Hmac a2 = resurs::hmacSha256("556000-1234", hk);
        const resurs::Hmac b = resurs::hmacSha256("556000-9999", hk);

        check(a1 == a2, "hmacSha256 is deterministic for the same input");
        check(a1 != b, "hmacSha256 differs for different input");

        resurs::Key hk2 = hk;
        hk2[0] ^= 0x01;
        check(resurs::hmacSha256("556000-1234", hk2) != a1,
              "hmacSha256 differs for a different key");

        bool empty_ok = true;
        try
        {
            (void)resurs::hmacSha256("", hk);
        }
        catch (...)
        {
            empty_ok = false;
        }
        check(empty_ok, "hmacSha256 accepts empty input");

        dump("hmac(556000-1234)", a1.data(), a1.size());
    }

    // --- AesGcmCipher rejects an input whose length would not fit an int ---
    {
        resurs::Key ek{};
        resurs::Nonce en{};
        RAND_bytes(ek.data(), static_cast<int>(ek.size()));
        RAND_bytes(en.data(), static_cast<int>(en.size()));

        // A string_view can describe a huge range without allocating it; 
        // the guard must trip on the length alone, before any OpenSSL call reads the data.
        const std::size_t too_big =
            static_cast<std::size_t>(std::numeric_limits<int>::max()) + 1;
        const std::string_view fake_huge{reinterpret_cast<const char *>(ek.data()), too_big};
        check(throws_as<std::runtime_error>([&]
                                            { resurs::AesGcmCipher::encrypt(fake_huge, ek, en); }),
              "AesGcmCipher::encrypt rejects an over-int-max input");
    }

    // --- AesGcmCipher demo, variation 1: random keys ---
    {
        resurs::Key rand_aes{};
        resurs::Key rand_lookup{};
        RAND_bytes(rand_aes.data(), static_cast<int>(rand_aes.size()));
        RAND_bytes(rand_lookup.data(), static_cast<int>(rand_lookup.size()));
        crypto_demo("random keys", rand_aes, rand_lookup, /*dump_keys=*/true);
    }

    // --- AesGcmCipher demo, variation 2: real generated key file ---
    {
#ifdef RESURS_REAL_KEY_FILE
        const char *real_key = RESURS_REAL_KEY_FILE;
        if (fs::exists(real_key))
        {
            bool loaded = true;
            try
            {
                km.loadFromFile(real_key);
            }
            catch (...)
            {
                loaded = false;
            }
            check(loaded, "real key file loads (exactly 64 bytes)");
            if (loaded)
            {
                crypto_demo("real key file", km.key(), km.lookupKey(), /*dump_keys=*/false);
                km.cleanse();
            }
        }
        else
        {
            std::printf("\n[SKIP] real key file not found: %s\n", real_key);
        }
#else
        std::printf("\n[SKIP] RESURS_REAL_KEY_FILE not defined\n");
#endif
    }

    std::printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}