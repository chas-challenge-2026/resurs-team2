#include "hmac_sha256.hpp"

#include <memory>
#include <stdexcept>
#include <string>

#include <openssl/core_names.h>   // OSSL_MAC_PARAM_DIGEST
#include <openssl/evp.h>          // EVP_MAC*
#include <openssl/params.h>       // OSSL_PARAM

namespace resurs {

namespace {

struct EvpMacDeleter {
    void operator()(EVP_MAC* m) const noexcept { EVP_MAC_free(m); }
};
struct EvpMacCtxDeleter {
    void operator()(EVP_MAC_CTX* c) const noexcept { EVP_MAC_CTX_free(c); }
};
using EvpMacPtr    = std::unique_ptr<EVP_MAC, EvpMacDeleter>;
using EvpMacCtxPtr = std::unique_ptr<EVP_MAC_CTX, EvpMacCtxDeleter>;

[[noreturn]] void throw_openssl(const char* what) {
    throw std::runtime_error(std::string("hmacSha256: ") + what);
}

}  // namespace

Hmac hmacSha256(std::string_view data, const Key& key) {
    EvpMacPtr mac{EVP_MAC_fetch(nullptr, "HMAC", nullptr)};
    if (!mac) {
        throw_openssl("EVP_MAC_fetch(HMAC) failed");
    }

    EvpMacCtxPtr ctx{EVP_MAC_CTX_new(mac.get())};
    if (!ctx) {
        throw_openssl("EVP_MAC_CTX_new failed");
    }

    // Select the underlying digest. The cast is required by the OpenSSL API;
    // "SHA256" is a string literal and is not modified.
    char digest[] = "SHA256";
    OSSL_PARAM params[] = {
        OSSL_PARAM_construct_utf8_string(OSSL_MAC_PARAM_DIGEST, digest, 0),
        OSSL_PARAM_construct_end(),
    };

    if (EVP_MAC_init(ctx.get(), key.data(), key.size(), params) != 1) {
        throw_openssl("EVP_MAC_init failed");
    }
    if (EVP_MAC_update(ctx.get(),
                       reinterpret_cast<const unsigned char*>(data.data()),
                       data.size()) != 1) {
        throw_openssl("EVP_MAC_update failed");
    }

    Hmac out{};
    std::size_t outlen = 0;
    if (EVP_MAC_final(ctx.get(), out.data(), &outlen, out.size()) != 1) {
        throw_openssl("EVP_MAC_final failed");
    }
    if (outlen != kHmacLen) {
        throw_openssl("unexpected HMAC output length");
    }

    return out;
}

}  // namespace resurs
