# Wrapper around CMake. The real build configuration lives in
# CMakeLists.txt / crypto/CMakeLists.txt. This just gives a short command.
#
# NOTE: recipe lines below must be indented with a TAB, not spaces.

BUILD_DIR := build
ASAN_BUILD_DIR := build-asan

.PHONY: all build test clean rebuild asan

all: build

build:
	cmake -S . -B $(BUILD_DIR)
	cmake --build $(BUILD_DIR)

test: build
	ctest --test-dir $(BUILD_DIR) --output-on-failure
	
test-v: build
	ctest --test-dir $(BUILD_DIR) -V

demo: build
	@./$(BUILD_DIR)/crypto/sandbox_test | sed -n '/AesGcmCipher demo/,$$p'

# Separate Debug build with ASAN + UBSan, then run all tests in it.
asan:
	cmake -S . -B $(ASAN_BUILD_DIR) -DCMAKE_BUILD_TYPE=Debug -DRESURS_SANITIZE=ON
	cmake --build $(ASAN_BUILD_DIR)
	ctest --test-dir $(ASAN_BUILD_DIR) --output-on-failure

clean:
	rm -rf $(BUILD_DIR) $(ASAN_BUILD_DIR)

rebuild: clean build
