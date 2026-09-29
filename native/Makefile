# Wrapper around CMake. The real build configuration lives in
# CMakeLists.txt / crypto/CMakeLists.txt. This just gives a short command.
#
# NOTE: recipe lines below must be indented with a TAB, not spaces.

BUILD_DIR := build

.PHONY: all build test clean rebuild demo demo-audit bench-audit

all: build

build:
	cmake -S . -B $(BUILD_DIR)
	cmake --build $(BUILD_DIR)

test: build
	ctest --test-dir $(BUILD_DIR) --output-on-failure
	
test-v: build
	ctest --test-dir $(BUILD_DIR) -V

demo-crypto: build
	@./$(BUILD_DIR)/crypto/sandbox_test | sed -n '/AesGcmCipher demo/,$$p'

demo-audit: build
	@cd $(BUILD_DIR)/audit && ./audit_demo

# Stress/benchmark harness, not a ctest. Override entry count: make bench-audit N=1000000
bench-audit: build
	@cd $(BUILD_DIR)/audit && ./audit_stress_test $(N)

clean:
	rm -rf $(BUILD_DIR)

rebuild: clean build
