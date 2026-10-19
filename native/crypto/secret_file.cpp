#include "secret_file.hpp"

#include <cerrno>
#include <stdexcept>

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

namespace resurs {

namespace {

// Closes the descriptor on every exit, including throws.
class FdGuard {
public:
    explicit FdGuard(int fd) noexcept : fd_(fd) {}
    ~FdGuard() { if (fd_ >= 0) ::close(fd_); }
    FdGuard(const FdGuard&) = delete;
    FdGuard& operator=(const FdGuard&) = delete;
    int get() const noexcept { return fd_; }
private:
    int fd_;
};

}

std::size_t readSecretFile(const std::string& path, std::uint8_t* buf, std::size_t cap) {
    // O_NONBLOCK: opening a FIFO with no writer returns at once instead of
    // blocking forever; fstat below then rejects it. No effect on regular files.
    FdGuard fd(::open(path.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC));
    if (fd.get() < 0) {
        throw std::runtime_error("cannot open key file: " + path);
    }

    struct stat st {};
    if (::fstat(fd.get(), &st) != 0) {
        throw std::runtime_error("cannot stat key file: " + path);
    }
    if (!S_ISREG(st.st_mode)) {
        throw std::runtime_error("key file is not a regular file: " + path);
    }
    if ((st.st_mode & (S_IWGRP | S_IWOTH)) != 0) {
        throw std::runtime_error("key file is writable by group or others: " + path);
    }

    std::size_t total = 0;
    while (total < cap) {
        const ssize_t n = ::read(fd.get(), buf + total, cap - total);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            throw std::runtime_error("read error on key file: " + path);
        }
        if (n == 0) {
            break;   // EOF
        }
        total += static_cast<std::size_t>(n);
    }
    return total;
}

}
