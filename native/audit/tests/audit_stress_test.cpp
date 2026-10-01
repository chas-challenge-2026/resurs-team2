// Stress/benchmark harness for resurs_audit_verify_chain on a large synthetic
// chain. Not a correctness test (see audit_sandbox_test.cpp / audit_abi_test.c
// for that) and not part of `ctest` - run manually, Release build, no
// sanitizers, to get honest timing numbers.
//
//  make bench-audit N=x defalt x=500000

#include "resurs_audit.h"
#include "hash_chain.hpp"
#include "signer.hpp"
#include "audit_key_manager.hpp"

#include <array>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <memory>
#include <string>
#include <vector>

#include <sys/resource.h>

#include <openssl/evp.h>
#include <openssl/pem.h>

namespace {

using Clock = std::chrono::steady_clock;

double ms_since(Clock::time_point start) {
    return std::chrono::duration<double, std::milli>(Clock::now() - start).count();
}

std::string format_ms(double ms) {
    char buf[64];
    if (ms >= 1000.0) {
        std::snprintf(buf, sizeof(buf), "%.1f ms (%.2f s)", ms, ms / 1000.0);
    } else {
        std::snprintf(buf, sizeof(buf), "%.1f ms", ms);
    }
    return buf;
}

long peak_rss_kb() {
    struct rusage ru{};
    getrusage(RUSAGE_SELF, &ru);
    return ru.ru_maxrss; // KB on Linux
}

// Local RAII wrapper - signer.cpp's own EvpPkeyDeleter is in an anonymous
// namespace there and not reachable from this file (same situation as
// audit_sandbox_test.cpp).
struct EvpPkeyDeleter {
    void operator()(EVP_PKEY* p) const noexcept { EVP_PKEY_free(p); }
};
using EvpPkeyPtr = std::unique_ptr<EVP_PKEY, EvpPkeyDeleter>;

EvpPkeyPtr generate_ed25519_key() {
    EvpPkeyPtr key{EVP_PKEY_Q_keygen(nullptr, nullptr, "ED25519")};
    if (!key) {
        std::fprintf(stderr, "EVP_PKEY_Q_keygen failed\n");
        std::exit(1);
    }
    return key;
}

// Writes an Ed25519 private key as an unencrypted PEM file on construction,
// removes it on destruction.
class TempPemFile {
public:
    explicit TempPemFile(EVP_PKEY* key) {
        path_ = std::filesystem::temp_directory_path() /
                "resurs_audit_stress_key.pem";
        std::unique_ptr<BIO, decltype(&BIO_free)> bio(
            BIO_new_file(path_.string().c_str(), "w"), &BIO_free);
        if (!bio ||
            PEM_write_bio_PrivateKey(bio.get(), key, nullptr, nullptr, 0, nullptr, nullptr) != 1) {
            std::fprintf(stderr, "TempPemFile: failed to write PEM key\n");
            std::exit(1);
        }
    }
    ~TempPemFile() {
        std::error_code ec;
        std::filesystem::remove(path_, ec);
    }
    std::string path() const { return path_.string(); }

private:
    std::filesystem::path path_;
};

} // namespace

int main(int argc, char** argv) {
    const size_t entry_count = (argc > 1) ? static_cast<size_t>(std::stoull(argv[1])) : 500000;
    std::printf("resurs_audit stress test: %zu entries\n\n", entry_count);

    // --- setup: real key, through the same init path the ABI expects ---
    const EvpPkeyPtr key = generate_ed25519_key();
    const TempPemFile pem(key.get());

    if (resurs_audit_init(pem.path().c_str()) != RESURS_AUDIT_OK) {
        std::fprintf(stderr, "resurs_audit_init failed\n");
        return 1;
    }

    const long rss_before_gen = peak_rss_kb();

    // --- step 1: generate the chain via resurs_audit_chain_entry ---
    std::vector<std::string> entry_texts;
    entry_texts.reserve(entry_count);
    std::vector<std::array<unsigned char, RESURS_AUDIT_HASH_LEN>> hashes;
    hashes.reserve(entry_count);
    std::vector<std::array<unsigned char, RESURS_AUDIT_SIG_LEN>> signatures;
    signatures.reserve(entry_count);

    const auto gen_start = Clock::now();
    for (size_t i = 0; i < entry_count; ++i) {
        entry_texts.push_back(
            "{\"actor\":\"user1\",\"action\":\"login\",\"ts\":1234567890,\"seq\":" +
            std::to_string(i) + "}");

        const unsigned char* prev = i == 0 ? nullptr : hashes.back().data();

        std::array<unsigned char, RESURS_AUDIT_HASH_LEN> hash_out{};
        std::array<unsigned char, RESURS_AUDIT_SIG_LEN> sig_out{};
        size_t sig_len = 0;

        const int rc = resurs_audit_chain_entry(
            prev, entry_texts.back().data(), entry_texts.back().size(),
            hash_out.data(), sig_out.data(), &sig_len);
        if (rc != RESURS_AUDIT_OK) {
            std::fprintf(stderr, "resurs_audit_chain_entry failed at %zu (rc=%d)\n", i, rc);
            return 1;
        }
        hashes.push_back(hash_out);
        signatures.push_back(sig_out);
    }
    const double gen_ms = ms_since(gen_start);
    const long rss_after_gen = peak_rss_kb();

    // --- step 2: flatten into the buffers resurs_audit_verify_chain expects ---
    const auto flatten_start = Clock::now();

    std::vector<unsigned char> flat_hashes(entry_count * RESURS_AUDIT_HASH_LEN);
    std::vector<unsigned char> flat_sigs(entry_count * RESURS_AUDIT_SIG_LEN);
    std::vector<size_t> sig_lens(entry_count, RESURS_AUDIT_SIG_LEN);
    std::vector<size_t> entry_lens(entry_count);
    std::string flat_entries;
    flat_entries.reserve(entry_count * 150);

    for (size_t i = 0; i < entry_count; ++i) {
        std::memcpy(flat_hashes.data() + i * RESURS_AUDIT_HASH_LEN,
                    hashes[i].data(), RESURS_AUDIT_HASH_LEN);
        std::memcpy(flat_sigs.data() + i * RESURS_AUDIT_SIG_LEN,
                    signatures[i].data(), RESURS_AUDIT_SIG_LEN);
        entry_lens[i] = entry_texts[i].size();
        flat_entries += entry_texts[i];
    }
    const double flatten_ms = ms_since(flatten_start);
    const long rss_after_flatten = peak_rss_kb();

    // --- step 3: the actual ABI call under test ---
    int first_invalid = -2;
    const auto verify_start = Clock::now();
    const int rc = resurs_audit_verify_chain(
        flat_hashes.data(), flat_sigs.data(), sig_lens.data(),
        flat_entries.data(), entry_lens.data(),
        entry_count, flat_entries.size(), flat_sigs.size(),
        resurs::audit::AuditKeyManager::instance().publicKey().data(),
        &first_invalid);
    const double verify_ms = ms_since(verify_start);
    const long rss_after_verify = peak_rss_kb();

    // --- step 4: breakdown - hash-chain recompute only vs. signature verify only,
    // calling the internal functions directly (same math resurs_audit_verify_chain
    // does, just isolated). Uses parsePublicKey() once, same as the fixed
    // resurs_audit_verify_chain - NOT the const PublicKey& overload, which would
    // silently re-parse the key on every call and measure the old code path.
    const resurs::audit::PublicKey pub = resurs::audit::AuditKeyManager::instance().publicKey();
    const resurs::audit::EvpPkeyPtr parsedPub = resurs::audit::parsePublicKey(pub);

    const auto hash_only_start = Clock::now();
    {
        resurs::audit::Hash prev = resurs::audit::kGenesisHash;
        for (size_t i = 0; i < entry_count; ++i) {
            prev = resurs::audit::chainHash(prev, entry_texts[i]);
        }
    }
    const double hash_only_ms = ms_since(hash_only_start);

    const auto sig_only_start = Clock::now();
    {
        for (size_t i = 0; i < entry_count; ++i) {
            resurs::audit::Hash h{};
            std::memcpy(h.data(), flat_hashes.data() + i * RESURS_AUDIT_HASH_LEN, RESURS_AUDIT_HASH_LEN);
            (void)resurs::audit::verify(parsedPub.get(), h, flat_sigs.data() + i * RESURS_AUDIT_SIG_LEN, RESURS_AUDIT_SIG_LEN);
        }
    }
    const double sig_only_ms = ms_since(sig_only_start);

    resurs_audit_shutdown();

    // --- report ---
    std::printf("chain generation (resurs_audit_chain_entry x%zu): %s\n", entry_count, format_ms(gen_ms).c_str());
    std::printf("flatten into ABI buffers:                        %s\n", format_ms(flatten_ms).c_str());
    std::printf("resurs_audit_verify_chain (full call):           %s  (rc=%d, first_invalid=%d)\n",
                format_ms(verify_ms).c_str(), rc, first_invalid);
    std::printf("  breakdown, isolated:\n");
    std::printf("    chainHash only (%zux):                       %s\n", entry_count, format_ms(hash_only_ms).c_str());
    std::printf("    verify (signature) only (%zux):              %s\n", entry_count, format_ms(sig_only_ms).c_str());
    std::printf("\n");
    std::printf("peak RSS before generation: %ld KB\n", rss_before_gen);
    std::printf("peak RSS after generation:  %ld KB\n", rss_after_gen);
    std::printf("peak RSS after flatten:     %ld KB\n", rss_after_flatten);
    std::printf("peak RSS after verify:      %ld KB\n", rss_after_verify);
    std::printf("flat buffers size (hashes+sigs+entries+lens): %.1f MB\n",
                (flat_hashes.size() + flat_sigs.size() + flat_entries.size() +
                 sig_lens.size() * sizeof(size_t) + entry_lens.size() * sizeof(size_t)) /
                    (1024.0 * 1024.0));

    return (rc == RESURS_AUDIT_OK && first_invalid == -1) ? 0 : 1;
}
