#ifndef RESURS_SECRET_FILE_HPP
#define RESURS_SECRET_FILE_HPP

#include <cstddef>
#include <cstdint>
#include <string>

namespace resurs {

// Reads a key file into the caller's buffer (which the caller wipes) and returns
// the number of bytes read: at most `cap`, so pass one byte more than the largest
// valid file to detect an oversized one.
//
// Throws std::runtime_error if the path cannot be opened, is not a regular file
// (FIFO, device, directory - these could block or never end), or is writable by
// group or others. A symlink to a regular file is accepted (Kubernetes secrets
// are symlinks). Owner and read bits are not checked: Docker/Kubernetes secret
// mounts commonly use a different owner or mode 0644/0444.
//
// Uses open/fstat/read directly, so no stdio/iostream buffer keeps a copy of
// the key bytes.
std::size_t readSecretFile(const std::string& path, std::uint8_t* buf, std::size_t cap);

}

#endif // RESURS_SECRET_FILE_HPP
