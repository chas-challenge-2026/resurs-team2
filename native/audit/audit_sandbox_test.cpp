#include "hash_chain.hpp"
#include "signer.hpp"

#include <cstdio>
#include <cstring>
#include <memory>

#include <openssl/evp.h>

namespace {

int g_failures = 0;

void check(bool ok, const char *name)
{
    std::printf("%s %s\n", ok ? "[PASS]" : "[FAIL]", name);
    if (!ok)
    {
        ++g_failures;
    }
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

} // namespace

int main()
{
    using resurs::audit::chainHash;
    using resurs::audit::kGenesisHash;
    using resurs::audit::Hash;

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

    std::printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}
