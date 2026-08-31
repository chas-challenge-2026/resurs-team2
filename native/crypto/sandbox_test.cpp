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

namespace {

namespace fs = std::filesystem;

int g_failures = 0;

void check(bool ok, const char* name) {
    std::printf("%s %s\n", ok ? "[PASS]" : "[FAIL]", name);
    if (!ok) {
        ++g_failures;
    }
}

// Prints a byte buffer as a labelled hex line (demo output only)
void dump(const char* label, const unsigned char* p, std::size_t n) {
    std::printf("  %-14s (%2zu)  ", label, n);
    for (std::size_t i = 0; i < n; ++i) {
        std::printf("%02x", p[i]);
    }
    std::printf("\n");
}


// True if fn() throws std::runtime_error (and nothing else)
template <typename Fn>
bool throws_runtime_error(Fn fn) {
    try {
        fn();
    } catch (const std::runtime_error&) {
        return true;
    } catch (...) {
        return false;
    }
    return false;
}

template <typename E, typename Fn>
bool throws_as(Fn fn) {
    try {
        fn();
    } catch (const E&) {
        return true;
    } catch (...) {
        return false;
    }
    return false;
}


// Creates a temp file with `nbytes` random bytes on construction
// removes it on destruction. Copy disabled
class TempKeyFile {
public:
    explicit TempKeyFile(std::size_t nbytes) {
        path_ = fs::temp_directory_path() /
                ("resurs_key_" + std::to_string(counter_++) + ".bin");

        std::vector<unsigned char> buf(nbytes);
        if (nbytes > 0 &&
            RAND_bytes(buf.data(), static_cast<int>(nbytes)) != 1) {
            throw std::runtime_error("RAND_bytes failed");
        }
        std::ofstream f(path_, std::ios::binary);
        f.write(reinterpret_cast<const char*>(buf.data()),
                static_cast<std::streamsize>(nbytes));
    }

    ~TempKeyFile() {
        std::error_code ec;
        fs::remove(path_, ec);   // noexcept overload - safe in a destructor
    }

    TempKeyFile(const TempKeyFile&) = delete;
    TempKeyFile& operator=(const TempKeyFile&) = delete;

    std::string path() const { return path_.string(); }

private:
    static inline int counter_ = 0;
    fs::path path_;
};

}

int main() {
    // --- KeyManager ---
    auto& km = resurs::KeyManager::instance();

    check(throws_runtime_error([&] { (void) km.key(); }),
          "key() before load throws");

    resurs::Key k{};
    for (std::size_t i = 0; i < k.size(); ++i) {
        k[i] = static_cast<std::uint8_t>(i + 1);
    }
    km.loadFromBytes(k);
    check(km.key() == k, "loadFromBytes -> key() round-trip");
    
    km.cleanse();
    check(throws_runtime_error([&] { (void) km.key(); }),
          "key() after cleanse throws");

    TempKeyFile good(32);
    TempKeyFile too_short(31);
    TempKeyFile too_long(33);

    bool loaded_ok = true;
    try {
        km.loadFromFile(good.path());
    } catch (...) {
        loaded_ok = false;
    }
    check(loaded_ok, "loadFromFile(32 bytes) succeeds");

    check(throws_runtime_error([&] { km.loadFromFile(too_short.path()); }),
          "loadFromFile(31 bytes) throws");
    check(throws_runtime_error([&] { km.loadFromFile(too_long.path()); }),
          "loadFromFile(33 bytes) throws");
    check(throws_runtime_error([&] { km.loadFromFile("/nonexistent/resurs.key"); }),
          "loadFromFile(missing) throws");

    km.cleanse();

     // --- AesGcmCipher ---
    {
        resurs::Key   ck{};
        resurs::Nonce nonce{};
        RAND_bytes(ck.data(),    static_cast<int>(ck.size()));
        RAND_bytes(nonce.data(), static_cast<int>(nonce.size()));

        const std::string plain = "556000-1234";
        const auto ct = resurs::AesGcmCipher::encrypt(plain, ck, nonce);

        check(ct.size() == plain.size() + resurs::kTagLen,
              "ciphertext length == plaintext + tag");
        check(std::memcmp(ct.data(), plain.data(), plain.size()) != 0,
              "ciphertext bytes differ from plaintext");
        const std::string back = resurs::AesGcmCipher::decrypt(ct, ck, nonce);
        check(back == plain, "encrypt -> decrypt round-trip");

        //demo output

        std::printf("--- AesGcmCipher demo ---\n");
        std::printf("  plaintext      : \"%s\"\n", plain.c_str());
        dump("key",        ck.data(), ck.size());
        dump("nonce",      nonce.data(), nonce.size());
        dump("ciphertext", ct.data(), plain.size());
        dump("tag",        ct.data() + plain.size(), resurs::kTagLen);
        std::printf("  decrypted      : \"%s\"\n", back.c_str());

        
        auto tampered = ct;
        tampered[0] ^= 0x01;
        check(throws_as<resurs::AuthError>(
                  [&] { resurs::AesGcmCipher::decrypt(tampered, ck, nonce); }),
              "tampered ciphertext -> AuthError");

        resurs::Key wrong = ck;
        wrong[0] ^= 0x01;
        check(throws_as<resurs::AuthError>(
                  [&] { resurs::AesGcmCipher::decrypt(ct, wrong, nonce); }),
              "wrong key -> AuthError");
    }

    std::printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}