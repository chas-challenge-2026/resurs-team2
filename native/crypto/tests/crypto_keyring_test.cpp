// Unit tests for resurs::KeyRing: key ring file parsing and hot-reload rules.
// Compiles key_ring.cpp directly (internal C++ symbols are hidden in the .so).

#include "key_ring.hpp"
#include "test_util.h"

#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <fstream>
#include <memory>
#include <stdexcept>
#include <string>
#include <system_error>
#include <vector>

#include <sys/stat.h> // mkfifo
#include <unistd.h>   // getpid

namespace
{

    namespace fs = std::filesystem;
    using Image = std::vector<std::uint8_t>;

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

    // Distinct, recognisable test key for a version (seed changes the bytes).
    resurs::Key test_key(std::uint8_t version, std::uint8_t seed = 0)
    {
        resurs::Key k{};
        for (std::size_t i = 0; i < k.size(); ++i)
        {
            k[i] = static_cast<std::uint8_t>(version * 31 + seed * 7 + i);
        }
        return k;
    }

    struct Entry
    {
        std::uint8_t version;
        resurs::Key key;
    };

    // Builds a well-formed file image; tests then corrupt single fields.
    Image build(std::uint8_t active, const std::vector<Entry> &entries)
    {
        Image img(std::begin(resurs::kKeyRingMagic), std::end(resurs::kKeyRingMagic));
        img.push_back(resurs::kKeyRingFormat);
        img.push_back(active);
        img.push_back(static_cast<std::uint8_t>(entries.size()));
        for (const Entry &e : entries)
        {
            img.push_back(e.version);
            img.insert(img.end(), e.key.begin(), e.key.end());
        }
        return img;
    }

    Image build_versions(std::uint8_t active, const std::vector<std::uint8_t> &versions)
    {
        std::vector<Entry> entries;
        for (std::uint8_t v : versions)
        {
            entries.push_back({v, test_key(v)});
        }
        return build(active, entries);
    }

    std::unique_ptr<resurs::KeyRing> parse(const Image &img)
    {
        return resurs::KeyRing::parse(img.data(), img.size());
    }

    bool parse_fails(const Image &img)
    {
        return throws_as<std::runtime_error>([&]
                                             { (void)parse(img); });
    }

    // Writes bytes to a temp file on construction, removes it on destruction.
    class TempFile
    {
    public:
        explicit TempFile(const Image &bytes)
        {
            path_ = fs::temp_directory_path() /
                    ("resurs_keyring_" + std::to_string(getpid()) + "_" +
                     std::to_string(counter_++) + ".bin");
            {
                std::ofstream f(path_, std::ios::binary);
                f.write(reinterpret_cast<const char *>(bytes.data()),
                        static_cast<std::streamsize>(bytes.size()));
            }
            // 0600 regardless of umask: group/other-writable key files are rejected.
            fs::permissions(path_, fs::perms::owner_read | fs::perms::owner_write);
        }

        void set_mode(fs::perms p) const { fs::permissions(path_, p); }

        ~TempFile()
        {
            std::error_code ec;
            fs::remove(path_, ec);
        }

        TempFile(const TempFile &) = delete;
        TempFile &operator=(const TempFile &) = delete;

        std::string path() const { return path_.string(); }

    private:
        static inline int counter_ = 0;
        fs::path path_;
    };

    void test_parse_valid()
    {
        std::printf("\n--- KeyRing::parse: valid files ---\n");

        auto one = parse(build_versions(1, {1}));
        check(one->active() == 1 && one->count() == 1, "one version: active 1, count 1");
        check(one->key(1) == test_key(1), "one version: key bytes copied");

        // Versions in any order; active is not the largest.
        auto many = parse(build_versions(2, {3, 1, 2}));
        check(many->active() == 2 && many->count() == 3, "three versions: active 2, count 3");
        check(many->has(1) && many->has(2) && many->has(3) && !many->has(4),
              "three versions: has() exactly 1, 2, 3");
        check(many->key(3) == test_key(3) && many->key(1) == test_key(1),
              "three versions: each key under its own version");

        // Largest valid file: all 255 versions.
        std::vector<std::uint8_t> all;
        for (int v = 1; v <= 255; ++v)
        {
            all.push_back(static_cast<std::uint8_t>(v));
        }
        Image full = build_versions(255, all);
        check(full.size() == resurs::kKeyRingMaxLen, "255 versions == kKeyRingMaxLen bytes");
        auto max = parse(full);
        check(max->count() == 255 && max->key(255) == test_key(255), "255 versions parse");
    }

    void test_parse_invalid()
    {
        std::printf("\n--- KeyRing::parse: invalid files ---\n");

        const Image good = build_versions(1, {1, 2});

        check(throws_as<std::runtime_error>([]
                                            { (void)resurs::KeyRing::parse(nullptr, 0); }),
              "NULL data -> throws");

        check(parse_fails(Image(resurs::kKeyRingMaxLen + 1, 0)), "larger than max -> throws");
        check(parse_fails(Image{}), "empty -> throws");
        check(parse_fails(Image(good.begin(), good.begin() + resurs::kKeyRingHeaderLen)),
              "header only, no entry -> throws");

        Image bad = good;
        bad[0] = 'X';
        check(parse_fails(bad), "bad magic -> throws");

        bad = good;
        bad[4] = 2;
        check(parse_fails(bad), "unknown format -> throws");

        bad = build_versions(1, {1});
        bad[6] = 0; // count 0 with one entry's bytes present
        check(parse_fails(bad), "count 0 -> throws");

        bad = good;
        bad.pop_back();
        check(parse_fails(bad), "one byte short of count -> throws");

        bad = good;
        bad.push_back(0);
        check(parse_fails(bad), "one trailing byte -> throws");

        bad = good;
        bad[6] = 3; // claims 3 entries, holds 2
        check(parse_fails(bad), "count larger than entries -> throws");

        check(parse_fails(build_versions(1, {1, 0})), "version 0 -> throws");
        check(parse_fails(build_versions(1, {1, 2, 1})), "duplicate version -> throws");
        check(parse_fails(build_versions(3, {1, 2})), "active not in ring -> throws");
        check(parse_fails(build_versions(0, {1, 2})), "active 0 -> throws");

        // The old format: 64 raw bytes (AES + HMAC) with no header.
        check(parse_fails(Image(64, 0xAB)), "legacy 64-byte key file -> throws");
    }

    void test_key_lookup()
    {
        std::printf("\n--- KeyRing::key ---\n");

        auto ring = parse(build_versions(1, {1, 2}));
        check(throws_as<resurs::UnknownKeyVersion>([&]
                                                   { (void)ring->key(3); }),
              "unknown version -> UnknownKeyVersion");
        check(throws_as<resurs::UnknownKeyVersion>([&]
                                                   { (void)ring->key(0); }),
              "version 0 -> UnknownKeyVersion");
    }

    void test_replace_rules()
    {
        std::printf("\n--- KeyRing::checkReplaceableBy ---\n");

        auto current = parse(build_versions(1, {1}));

        auto rotated = parse(build_versions(2, {1, 2}));
        check(!throws_as<std::runtime_error>([&]
                                             { current->checkReplaceableBy(*rotated); }),
              "add v2 and make it active -> allowed");

        auto same = parse(build_versions(1, {1}));
        check(!throws_as<std::runtime_error>([&]
                                             { current->checkReplaceableBy(*same); }),
              "identical ring -> allowed");

        // After re-encryption: v1 is no longer active and may be removed.
        auto dropped = parse(build_versions(2, {2}));
        check(!throws_as<std::runtime_error>([&]
                                             { rotated->checkReplaceableBy(*dropped); }),
              "drop old inactive v1 -> allowed");

        auto changed = parse(build(2, {{1, test_key(1, 99)}, {2, test_key(2)}}));
        check(throws_as<std::runtime_error>([&]
                                            { current->checkReplaceableBy(*changed); }),
              "same version, different key bytes -> rejected");

        // Only the last byte differs: the compare must cover the whole key.
        resurs::Key almost = test_key(1);
        almost[resurs::kKeyLen - 1] ^= 0x01;
        auto last_byte = parse(build(1, {{1, almost}}));
        check(throws_as<std::runtime_error>([&]
                                            { current->checkReplaceableBy(*last_byte); }),
              "key differs only in the last byte -> rejected");

        auto no_active = parse(build_versions(3, {3}));
        check(throws_as<std::runtime_error>([&]
                                            { current->checkReplaceableBy(*no_active); }),
              "new ring lacks the current active version -> rejected");

        // Rolling back to an older ring that still has the active version is allowed.
        auto rollback = parse(build_versions(1, {1, 2}));
        check(!throws_as<std::runtime_error>([&]
                                             { rotated->checkReplaceableBy(*rollback); }),
              "switch active back to v1 (v2 kept) -> allowed");
    }

    void test_from_file()
    {
        std::printf("\n--- KeyRing::fromFile ---\n");

        TempFile ok(build_versions(2, {1, 2}));
        auto ring = resurs::KeyRing::fromFile(ok.path());
        check(ring->active() == 2 && ring->key(1) == test_key(1), "valid file loads");

        check(throws_as<std::runtime_error>([]
                                            { (void)resurs::KeyRing::fromFile("/nonexistent/resurs.keyring"); }),
              "missing file -> throws");

        TempFile empty(Image{});
        check(throws_as<std::runtime_error>([&]
                                            { (void)resurs::KeyRing::fromFile(empty.path()); }),
              "empty file -> throws");

        // A file bigger than the read buffer must not be truncated into a valid ring.
        std::vector<std::uint8_t> all;
        for (int v = 1; v <= 255; ++v)
        {
            all.push_back(static_cast<std::uint8_t>(v));
        }
        Image oversized = build_versions(1, all);
        oversized.push_back(0);
        TempFile big(oversized);
        check(throws_as<std::runtime_error>([&]
                                            { (void)resurs::KeyRing::fromFile(big.path()); }),
              "file one byte over max -> throws");

        TempFile legacy(Image(64, 0x11));
        check(throws_as<std::runtime_error>([&]
                                            { (void)resurs::KeyRing::fromFile(legacy.path()); }),
              "legacy 64-byte file -> throws");
    }

    // File type and permission checks (readSecretFile).
    void test_from_file_checks()
    {
        std::printf("\n--- KeyRing::fromFile: file type and permissions ---\n");

        using P = fs::perms;
        const Image good = build_versions(1, {1});
        auto loads = [](const std::string &path)
        {
            return !throws_as<std::runtime_error>([&]
                                                  { (void)resurs::KeyRing::fromFile(path); });
        };

        TempFile f(good);
        check(loads(f.path()), "mode 0600 -> loads");
        f.set_mode(P::owner_read);
        check(loads(f.path()), "mode 0400 -> loads");
        f.set_mode(P::owner_read | P::owner_write | P::group_read | P::others_read);
        check(loads(f.path()), "mode 0644 (Kubernetes default) -> loads");
        f.set_mode(P::owner_read | P::owner_write | P::group_write);
        check(!loads(f.path()), "mode 0620 (group-writable) -> throws");
        f.set_mode(P::owner_read | P::owner_write | P::group_read | P::group_write |
                   P::others_read | P::others_write);
        check(!loads(f.path()), "mode 0666 (world-writable) -> throws");
        f.set_mode(P::owner_read | P::owner_write);

        // Symlink to a regular 0600 file: accepted (Kubernetes secrets are symlinks).
        const fs::path link = fs::temp_directory_path() /
                              ("resurs_keyring_" + std::to_string(getpid()) + "_link");
        std::error_code ec;
        fs::remove(link, ec);
        fs::create_symlink(f.path(), link, ec);
        check(!ec && loads(link.string()), "symlink to a regular 0600 file -> loads");
        fs::remove(link, ec);

        check(!loads(fs::temp_directory_path().string()), "directory -> throws");
        check(!loads("/dev/null"), "character device -> throws");

        // A FIFO with no writer would block a plain open() forever. Must throw
        // at once instead (ctest TIMEOUT catches a regression that hangs).
        const fs::path fifo = fs::temp_directory_path() /
                              ("resurs_keyring_" + std::to_string(getpid()) + "_fifo");
        fs::remove(fifo, ec);
        if (mkfifo(fifo.c_str(), 0600) == 0)
        {
            check(!loads(fifo.string()), "FIFO -> throws without blocking");
            fs::remove(fifo, ec);
        }
        else
        {
            check(false, "mkfifo for the FIFO test");
        }
    }

}

int main()
{
    test_parse_valid();
    test_parse_invalid();
    test_key_lookup();
    test_replace_rules();
    test_from_file();
    test_from_file_checks();
    return test_summary();
}
