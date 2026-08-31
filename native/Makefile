# Wrapper around CMake. The real build configuration lives in
# CMakeLists.txt / crypto/CMakeLists.txt. This just gives a short command.
#
# NOTE: recipe lines below must be indented with a TAB, not spaces.

BUILD_DIR := build

.PHONY: all build test clean rebuild

all: build

build:
	cmake -S . -B $(BUILD_DIR)
	cmake --build $(BUILD_DIR)

test: build
	ctest --test-dir $(BUILD_DIR) --output-on-failure
	
test-v: build
	ctest --test-dir $(BUILD_DIR) -V

demo: build
	@./$(BUILD_DIR)/crypto/sandbox_test | sed -n '/AesGcmCipher demo/,/decrypted/p'

clean:
	rm -rf $(BUILD_DIR)

rebuild: clean build
