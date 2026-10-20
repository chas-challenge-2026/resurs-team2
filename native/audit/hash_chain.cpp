#include "hash_chain.hpp"
#include <memory>
#include <stdexcept>
#include <string>
#include <openssl/evp.h>

namespace resurs::audit {
namespace {

struct EvpMdCtxDeleter {
    void operator()(EVP_MD_CTX* c) const noexcept { EVP_MD_CTX_free(c); }
};
using EvpMdCtxPtr = std::unique_ptr<EVP_MD_CTX, EvpMdCtxDeleter>;

[[noreturn]] void throw_openssl(const char* what) {
    throw std::runtime_error(std::string("chainHash: ") + what);
}

}  // namespace

Hash chainHash(const Hash& prev, std::string_view entryJson) {
    EvpMdCtxPtr ctx{EVP_MD_CTX_new()};
    if (!ctx) {
        throw_openssl("EVP_MD_CTX_new failed");
    }

    if (EVP_DigestInit_ex(ctx.get(), EVP_sha256(), nullptr) != 1) {
        throw_openssl("EVP_DigestInit_ex failed");
    }
    if (EVP_DigestUpdate(ctx.get(), prev.data(), prev.size()) != 1) {
        throw_openssl("EVP_DigestUpdate (prev) failed");
    }
    if (EVP_DigestUpdate(ctx.get(), entryJson.data(), entryJson.size()) != 1) {
        throw_openssl("EVP_DigestUpdate (entry) failed");
    }

    Hash out{};
    unsigned int outlen = 0;
    if (EVP_DigestFinal_ex(ctx.get(), out.data(), &outlen) != 1) {
        throw_openssl("EVP_DigestFinal_ex failed");
    }
    if (outlen != kHashLen) {
        throw_openssl("unexpected digest length");
    }

    return out;
}

}  // namespace resurs::audit
