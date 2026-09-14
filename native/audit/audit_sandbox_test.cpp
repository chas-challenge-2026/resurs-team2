#include "hash_chain.hpp"

#include <cstdio>
#include <cstring>

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

    std::printf("\n%d failure(s)\n", g_failures);
    return g_failures == 0 ? 0 : 1;
}
