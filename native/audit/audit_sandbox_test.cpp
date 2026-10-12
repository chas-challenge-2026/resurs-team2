#include "audit_key_manager.hpp"
#include "hash_chain.hpp"
#include "signer.hpp"

#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <memory>
#include <stdexcept>
#include <string>
#include <system_error>

#include <openssl/evp.h>
#include <openssl/pem.h>

namespace
{

    int g_failures = 0;

    void check(bool ok, const char *name)
    {
        std::printf("%s %s\n", ok ? "[PASS]" : "[FAIL]", name);
        if (!ok)
        {
            ++g_failures;
        }
    }

    // True if fn() throws std::runtime_error (and nothing else).
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

    // Local RAII wrapper for EVP_PKEY - signer.cpp's own EvpPkeyDeleter lives in
    // an anonymous namespace there and is not visible from this file.
    struct EvpPkeyDeleter
    {
        void operator()(EVP_PKEY *p) const noexcept { EVP_PKEY_free(p); }
    };
    using EvpPkeyPtr = std::unique_ptr<EVP_PKEY, EvpPkeyDeleter>;

    EvpPkeyPtr generate_ed25519_key()
    {
        EvpPkeyPtr key{EVP_PKEY_Q_keygen(nullptr, nullptr, "ED25519")};
        if (!key)
        {
            std::fprintf(stderr, "EVP_PKEY_Q_keygen failed\n");
            std::abort();
        }
        return key;
    }

    // Writes an Ed25519 private key as an unencrypted PEM file on construction,
    // removes it on destruction. Copy disabled.
    class TempPemFile
    {
    public:
        explicit TempPemFile(EVP_PKEY *key)
        {
            path_ = std::filesystem::temp_directory_path() /
                    ("resurs_audit_key_" + std::to_string(counter_++) + ".pem");

            std::unique_ptr<BIO, decltype(&BIO_free)> bio(
                BIO_new_file(path_.string().c_str(), "w"), &BIO_free);
            if (!bio ||
                PEM_write_bio_PrivateKey(bio.get(), key, nullptr, nullptr, 0, nullptr, nullptr) != 1)
            {
                throw std::runtime_error("TempPemFile: failed to write PEM key");
            }
        }

        ~TempPemFile()
        {
            std::error_code ec;
            std::filesystem::remove(path_, ec); // noexcept overload - safe in a destructor
        }

        TempPemFile(const TempPemFile &) = delete;
        TempPemFile &operator=(const TempPemFile &) = delete;

        std::string path() const { return path_.string(); }

    private:
        static inline int counter_ = 0;
        std::filesystem::path path_;
    };

    // Writes arbitrary text (not a PEM key) on construction, removes it on
    // destruction - for the "not a valid PEM file" negative test.
    class TempGarbageFile
    {
    public:
        explicit TempGarbageFile(const std::string &contents)
        {
            path_ = std::filesystem::temp_directory_path() /
                    ("resurs_audit_garbage_" + std::to_string(counter_++) + ".txt");
            std::ofstream f(path_);
            f << contents;
        }

        ~TempGarbageFile()
        {
            std::error_code ec;
            std::filesystem::remove(path_, ec);
        }

        TempGarbageFile(const TempGarbageFile &) = delete;
        TempGarbageFile &operator=(const TempGarbageFile &) = delete;

        std::string path() const { return path_.string(); }

    private:
        static inline int counter_ = 0;
        std::filesystem::path path_;
    };

} // namespace

int main()
{
    using resurs::audit::chainHash;
    using resurs::audit::Hash;
    using resurs::audit::kGenesisHash;

    // --- known-answer test: SHA-256 of 32 zero bytes, no entry data ---
    // reference: `head -c 32 /dev/zero | sha256sum`
    const Hash expected_genesis = {
        0x66, 0x68, 0x7a, 0xad, 0xf8, 0x62, 0xbd, 0x77,
        0x6c, 0x8f, 0xc1, 0x8b, 0x8e, 0x9f, 0x8e, 0x20,
        0x08, 0x97, 0x14, 0x85, 0x6e, 0xe2, 0x33, 0xb3,
        0x90, 0x2a, 0x59, 0x1d, 0x0d, 0x5f, 0x29, 0x25,
    };
    const Hash genesis_hash = chainHash(kGenesisHash, "");
    check(genesis_hash == expected_genesis,
          "chainHash(genesis, \"\") matches known SHA-256 answer");

    // --- determinism: same input -> same hash ---
    const Hash h1 = chainHash(kGenesisHash, "entry-a");
    const Hash h2 = chainHash(kGenesisHash, "entry-a");
    check(h1 == h2, "chainHash is deterministic for the same input");

    // --- sensitivity to entryJson: different entry -> different hash ---
    const Hash h3 = chainHash(kGenesisHash, "entry-b");
    check(h1 != h3, "chainHash differs for different entryJson");

    // --- sensitivity to prev: different predecessor -> different hash ---
    Hash other_prev = kGenesisHash;
    other_prev[0] ^= 0x01;
    const Hash h4 = chainHash(other_prev, "entry-a");
    check(h1 != h4, "chainHash differs for a different prev hash");

    // --- signer: sign / verify / rawPublicKey ---
    {
        using resurs::audit::PublicKey;
        using resurs::audit::rawPublicKey;
        using resurs::audit::sign;
        using resurs::audit::Signature;
        using resurs::audit::verify;

        const EvpPkeyPtr key = generate_ed25519_key();

        const PublicKey pub = rawPublicKey(key.get());
        check(pub.size() == resurs::audit::kPubKeyLen,
              "rawPublicKey returns a 32-byte Ed25519 public key");

        Hash msg{};
        msg.fill(0xAB);

        const Signature sig = sign(key.get(), msg);
        check(sig.size() == resurs::audit::kSigLen,
              "sign returns a 64-byte Ed25519 signature");

        check(verify(pub, msg, sig.data(), sig.size()),
              "sign -> verify round-trip succeeds");

        // Tampered message: the signature no longer matches.
        Hash tampered_msg = msg;
        tampered_msg[0] ^= 0x01;
        check(!verify(pub, tampered_msg, sig.data(), sig.size()),
              "verify fails for a tampered message");

        // Tampered signature: no longer matches the original message.
        Signature tampered_sig = sig;
        tampered_sig[0] ^= 0x01;
        check(!verify(pub, msg, tampered_sig.data(), tampered_sig.size()),
              "verify fails for a tampered signature");

        // Wrong public key: a different key pair's signature must not verify.
        const EvpPkeyPtr other_key = generate_ed25519_key();
        const PublicKey other_pub = rawPublicKey(other_key.get());
        check(!verify(other_pub, msg, sig.data(), sig.size()),
              "verify fails for the wrong public key");
    }

    // --- AuditKeyManager ---
    {
        using resurs::audit::AuditKeyManager;
        using resurs::audit::PublicKey;
        using resurs::audit::rawPublicKey;
        using resurs::audit::sign;
        using resurs::audit::Signature;
        using resurs::audit::verify;

        auto &km = AuditKeyManager::instance();
        km.cleanse(); // start from a clean, known state regardless of test order

        check(!km.isLoaded(), "AuditKeyManager isLoaded() false before load");
        check(throws_runtime_error([&]
                                   { (void)km.privateKey(); }),
              "AuditKeyManager privateKey() before load throws");
        check(throws_runtime_error([&]
                                   { (void)km.publicKey(); }),
              "AuditKeyManager publicKey() before load throws");

        // loadFromPKey seam, ownership transferred to the manager.
        EvpPkeyPtr owned_key = generate_ed25519_key();
        EVP_PKEY *raw_key = owned_key.release();
        const PublicKey expected_pub = rawPublicKey(raw_key);
        km.loadFromPKey(raw_key);

        check(km.isLoaded(), "AuditKeyManager isLoaded() true after loadFromPKey");
        check(km.privateKey() == raw_key,
              "AuditKeyManager privateKey() returns the loaded key");
        check(km.publicKey() == expected_pub,
              "AuditKeyManager publicKey() matches rawPublicKey of the loaded key");

        km.cleanse();
        check(!km.isLoaded(), "AuditKeyManager isLoaded() false after cleanse");
        check(throws_runtime_error([&]
                                   { (void)km.privateKey(); }),
              "AuditKeyManager privateKey() after cleanse throws");

        check(throws_runtime_error([&]
                                   { km.loadFromFile("/nonexistent/resurs-audit.key"); }),
              "AuditKeyManager loadFromFile(missing) throws");

        TempGarbageFile garbage("not a pem file at all\n");
        check(throws_runtime_error([&]
                                   { km.loadFromFile(garbage.path()); }),
              "AuditKeyManager loadFromFile(garbage) throws");

        // loadFromFile on a real Ed25519 PEM, then sign/verify through it.
        EvpPkeyPtr file_key = generate_ed25519_key();
        TempPemFile pem(file_key.get());

        bool loaded_ok = true;
        try
        {
            km.loadFromFile(pem.path());
        }
        catch (...)
        {
            loaded_ok = false;
        }
        check(loaded_ok, "AuditKeyManager loadFromFile(valid Ed25519 PEM) succeeds");

        if (loaded_ok)
        {
            check(km.isLoaded(), "AuditKeyManager isLoaded() true after loadFromFile");

            Hash msg{};
            msg.fill(0xCD);
            const Signature sig = sign(km.privateKey(), msg);
            check(verify(km.publicKey(), msg, sig.data(), sig.size()),
                  "sign/verify round-trip with a key loaded via loadFromFile");
        }

        km.cleanse();
        km.cleanse(); // safe to call twice
        check(!km.isLoaded(), "AuditKeyManager isLoaded() false after double cleanse");
    }

    std::printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}
