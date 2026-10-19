#include "key_manager.hpp"
#include "aes_gcm_cipher.hpp"
#include "hmac_sha256.hpp"
#include "test_util.h"

#include <cstring>
#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <limits>
#include <memory>
#include <stdexcept>
#include <string>
#include <string_view>
#include <system_error>
#include <utility>
#include <vector>

#include <openssl/rand.h>

namespace
{

    namespace fs = std::filesystem;

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

    // Shows decrypted bytes as text (for checks and printing)
    std::string_view as_view(const resurs::SecureBytes &b)
    {
        return {reinterpret_cast<const char *>(b.data()), b.size()};
    }

    // Runs the AES-GCM + HMAC checks against one (aes, lookup) key pair.
    // Called once with random keys and once with the real generated key file,
    // so both paths exercise the same behaviour.
    void test_aes_gcm_with_keys(const char *label,
                                const resurs::Key &aes,
                                const resurs::Key &lookup)
    {
        std::printf("\n--- AesGcmCipher: %s ---\n", label);

        resurs::Nonce nonce{};
        RAND_bytes(nonce.data(), static_cast<int>(nonce.size()));

        const std::string plain = "556000-1234";
        const auto ct = resurs::AesGcmCipher::encrypt(plain, aes, nonce);

        check(ct.size() == plain.size() + resurs::kTagLen,
              "ciphertext length == plaintext + tag");
        check(std::memcmp(ct.data(), plain.data(), plain.size()) != 0,
              "ciphertext bytes differ from plaintext");
        const resurs::SecureBytes back = resurs::AesGcmCipher::decrypt(ct, aes, nonce);
        check(as_view(back) == plain, "encrypt -> decrypt round-trip");

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
            {
                std::ofstream f(path_, std::ios::binary);
                f.write(reinterpret_cast<const char *>(buf.data()),
                        static_cast<std::streamsize>(nbytes));
            }
            // 0600 regardless of umask: group/other-writable key files are rejected.
            fs::permissions(path_, fs::perms::owner_read | fs::perms::owner_write);
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

    // Key ring file image: header + one entry per (version, key). See key_ring.hpp.
    std::vector<std::uint8_t> ring_image(std::uint8_t active,
                                         const std::vector<std::pair<std::uint8_t, resurs::Key>> &entries)
    {
        std::vector<std::uint8_t> img(std::begin(resurs::kKeyRingMagic), std::end(resurs::kKeyRingMagic));
        img.push_back(resurs::kKeyRingFormat);
        img.push_back(active);
        img.push_back(static_cast<std::uint8_t>(entries.size()));
        for (const auto &[version, key] : entries)
        {
            img.push_back(version);
            img.insert(img.end(), key.begin(), key.end());
        }
        return img;
    }

    std::unique_ptr<resurs::KeyRing> make_ring(std::uint8_t active,
                                               const std::vector<std::pair<std::uint8_t, resurs::Key>> &entries)
    {
        const std::vector<std::uint8_t> img = ring_image(active, entries);
        return resurs::KeyRing::parse(img.data(), img.size());
    }

    // Writes the given bytes to a temp file; removes it on destruction.
    class TempBytesFile
    {
    public:
        explicit TempBytesFile(const std::vector<std::uint8_t> &bytes)
        {
            path_ = fs::temp_directory_path() /
                    ("resurs_sandbox_" + std::to_string(counter_++) + ".bin");
            {
                std::ofstream f(path_, std::ios::binary);
                f.write(reinterpret_cast<const char *>(bytes.data()),
                        static_cast<std::streamsize>(bytes.size()));
            }
            fs::permissions(path_, fs::perms::owner_read | fs::perms::owner_write);
        }

        ~TempBytesFile()
        {
            std::error_code ec;
            fs::remove(path_, ec);
        }

        TempBytesFile(const TempBytesFile &) = delete;
        TempBytesFile &operator=(const TempBytesFile &) = delete;

        std::string path() const { return path_.string(); }

    private:
        static inline int counter_ = 0;
        fs::path path_;
    };

    void test_key_manager()
    {
        auto &km = resurs::KeyManager::instance();

        resurs::Key k1{}, k2{}, lk{};
        for (std::size_t i = 0; i < k1.size(); ++i)
        {
            k1[i] = static_cast<std::uint8_t>(i + 1);
            k2[i] = static_cast<std::uint8_t>(0x40 + i);
            lk[i] = static_cast<std::uint8_t>(0x80 + i);
        }

        check(!km.isLoaded(), "isLoaded() false before load");
        check(throws_as<resurs::KeyNotLoaded>([&]
                                              { (void)km.activeKey(); }),
              "activeKey() before load throws KeyNotLoaded");
        check(throws_as<resurs::KeyNotLoaded>([&]
                                              { (void)km.key(1); }),
              "key(1) before load throws KeyNotLoaded");
        check(throws_as<resurs::KeyNotLoaded>([&]
                                              { (void)km.lookupKey(); }),
              "lookupKey() before load throws KeyNotLoaded");
        check(throws_as<resurs::KeyNotLoaded>([&]
                                              { km.reload(make_ring(1, {{1, k1}})); }),
              "reload() before load throws KeyNotLoaded");

        km.load(make_ring(1, {{1, k1}}), lk);
        check(km.isLoaded(), "isLoaded() true after load");
        {
            const resurs::ActiveKey active = km.activeKey();
            check(active.version == 1 && active.key.bytes() == k1, "activeKey() == (1, k1)");
        }
        check(km.key(1).bytes() == k1, "key(1) == k1");
        check(km.lookupKey().bytes() == lk, "lookupKey() round-trip");
        check(throws_as<resurs::UnknownKeyVersion>([&]
                                                   { (void)km.key(2); }),
              "key(2) not in ring throws UnknownKeyVersion");

        km.reload(make_ring(2, {{1, k1}, {2, k2}}));
        {
            const resurs::ActiveKey active = km.activeKey();
            check(active.version == 2 && active.key.bytes() == k2, "after reload: activeKey() == (2, k2)");
        }
        check(km.key(1).bytes() == k1, "after reload: key(1) still k1");
        check(km.lookupKey().bytes() == lk, "after reload: lookup key unchanged");

        check(throws_as<resurs::KeyRingRejected>([&]
                                                 { km.reload(make_ring(2, {{1, k2}, {2, k2}})); }),
              "reload with different v1 bytes throws KeyRingRejected");
        check(throws_as<resurs::KeyRingRejected>([&]
                                                 { km.reload(make_ring(1, {{1, k1}})); }),
              "reload dropping active v2 throws KeyRingRejected");
        check(km.activeVersion() == 2 && km.key(1).bytes() == k1,
              "rejected reloads leave the ring unchanged");

        // A second load while loaded: only compatible keys (one init per Spring context).
        resurs::Key other_lk = lk;
        other_lk[0] ^= 0x01;
        check(throws_as<resurs::KeyRingRejected>([&]
                                                 { km.load(make_ring(2, {{1, k1}, {2, k2}}), other_lk); }),
              "load with a different lookup key while loaded throws KeyRingRejected");
        check(throws_as<resurs::KeyRingRejected>([&]
                                                 { km.load(make_ring(1, {{1, k1}}), lk); }),
              "load that drops the active version while loaded throws KeyRingRejected");
        check(km.lookupKey().bytes() == lk && km.activeVersion() == 2,
              "rejected loads leave the keys unchanged");
        check(!throws_as<std::runtime_error>([&]
                                             { km.load(make_ring(2, {{1, k1}, {2, k2}}), lk); }),
              "repeating the same load while loaded is allowed");

        km.cleanse();
        check(!km.isLoaded(), "isLoaded() false after cleanse");
        check(throws_as<resurs::KeyNotLoaded>([&]
                                              { (void)km.key(1); }),
              "key(1) after cleanse throws KeyNotLoaded");
        check(throws_as<resurs::KeyNotLoaded>([&]
                                              { (void)km.lookupKey(); }),
              "lookupKey() after cleanse throws KeyNotLoaded");

        TempBytesFile ring(ring_image(1, {{1, k1}}));
        TempBytesFile lookup(std::vector<std::uint8_t>(lk.begin(), lk.end()));
        TempKeyFile lookup_short(31);
        TempKeyFile lookup_long(33);
        TempKeyFile legacy(64);

        bool loaded_ok = true;
        try
        {
            km.loadFromFiles(ring.path(), lookup.path());
        }
        catch (...)
        {
            loaded_ok = false;
        }
        check(loaded_ok && km.key(1).bytes() == k1 && km.lookupKey().bytes() == lk,
              "loadFromFiles(ring, 32-byte lookup) loads both");

        check(throws_runtime_error([&]
                                   { km.loadFromFiles(ring.path(), lookup_short.path()); }),
              "loadFromFiles(31-byte lookup) throws");
        check(throws_runtime_error([&]
                                   { km.loadFromFiles(ring.path(), lookup_long.path()); }),
              "loadFromFiles(33-byte lookup) throws");
        check(throws_runtime_error([&]
                                   { km.loadFromFiles(legacy.path(), lookup.path()); }),
              "loadFromFiles(legacy 64-byte file as ring) throws");
        check(throws_runtime_error([&]
                                   { km.loadFromFiles("/nonexistent/resurs.keyring", lookup.path()); }),
              "loadFromFiles(missing ring) throws");
        check(km.key(1).bytes() == k1 && km.lookupKey().bytes() == lk,
              "failed loadFromFiles keeps the loaded keys");

        km.cleanse();
    }

    void test_hmac()
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
    }

    // SecureBytes: a move hands over the buffer instead of copying it
    void test_secure_bytes_move()
    {
        resurs::SecureBytes a(4);
        a.data()[0] = 0x42;
        const std::uint8_t *before = a.data();

        resurs::SecureBytes b(std::move(a));
        check(b.data() == before && b.size() == 4 && b.data()[0] == 0x42,
              "SecureBytes move keeps the same buffer");
        check(a.data() == nullptr && a.size() == 0,
              "SecureBytes moved-from is empty");
    }

    // AesGcmCipher rejects an input whose length would not fit an int
    void test_aes_rejects_over_int_max()
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

    void test_aes_gcm_random_keys()
    {
        resurs::Key rand_aes{};
        resurs::Key rand_lookup{};
        RAND_bytes(rand_aes.data(), static_cast<int>(rand_aes.size()));
        RAND_bytes(rand_lookup.data(), static_cast<int>(rand_lookup.size()));
        test_aes_gcm_with_keys("random keys", rand_aes, rand_lookup);
    }

    // Leaves KeyManager cleansed on every path, like the other tests.
    void test_aes_gcm_real_key_file()
    {
#if defined(RESURS_REAL_KEY_RING_FILE) && defined(RESURS_REAL_LOOKUP_KEY_FILE)
        auto &km = resurs::KeyManager::instance();
        const char *real_ring = RESURS_REAL_KEY_RING_FILE;
        const char *real_lookup = RESURS_REAL_LOOKUP_KEY_FILE;
        if (fs::exists(real_ring) && fs::exists(real_lookup))
        {
            bool loaded = true;
            try
            {
                km.loadFromFiles(real_ring, real_lookup);
            }
            catch (...)
            {
                loaded = false;
            }
            check(loaded, "real key ring + lookup key files load");
            if (loaded)
            {
                const resurs::ActiveKey active = km.activeKey();
                test_aes_gcm_with_keys("real key files", active.key.bytes(), km.lookupKey().bytes());
            }
            km.cleanse();
        }
        else
        {
            std::printf("\n[SKIP] real key files not found: %s, %s\n", real_ring, real_lookup);
        }
#else
        std::printf("\n[SKIP] RESURS_REAL_KEY_RING_FILE / RESURS_REAL_LOOKUP_KEY_FILE not defined\n");
#endif
    }

}

int main()
{
    test_key_manager();
    test_hmac();
    test_secure_bytes_move();
    test_aes_rejects_over_int_max();
    test_aes_gcm_random_keys();
    test_aes_gcm_real_key_file();

    return test_summary();
}
