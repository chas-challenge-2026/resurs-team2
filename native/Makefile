# Wrapper around CMake. The real build configuration lives in
# CMakeLists.txt / crypto/CMakeLists.txt. This just gives a short command.
#
# NOTE: recipe lines below must be indented with a TAB, not spaces.

BUILD_DIR := build

.PHONY: all build test clean rebuild demo demo-audit

all: build

build:
	cmake -S . -B $(BUILD_DIR)
	cmake --build $(BUILD_DIR)

test: build
	ctest --test-dir $(BUILD_DIR) --output-on-failure
	
test-v: build
	ctest --test-dir $(BUILD_DIR) -V

demo-crypto: build
	@./$(BUILD_DIR)/crypto/tests/crypto_sandbox_test | sed -n '/AesGcmCipher demo/,$$p'

demo-audit: build
	@cd $(BUILD_DIR)/audit && ./audit_demo

# Line/branch coverage of the crypto and audit modules from the ctest suites.
# Separate build dir (inside build/, so it is git-ignored and removed by clean).
# Old .gcda counters are deleted first, so each run reports only that run.
.PHONY: coverage
COVERAGE_DIR := $(BUILD_DIR)/coverage

coverage:
	cmake -S . -B $(COVERAGE_DIR) -DRESURS_COVERAGE=ON -DCMAKE_BUILD_TYPE=Debug
	cmake --build $(COVERAGE_DIR)
	find $(COVERAGE_DIR) -name '*.gcda' -delete
	ctest --test-dir $(COVERAGE_DIR) --output-on-failure
	mkdir -p $(COVERAGE_DIR)/report
	gcovr --root . --object-directory $(COVERAGE_DIR) \
		--filter 'crypto/' --filter 'audit/' \
		--exclude '.*/tests/' --exclude '.*_test\.(c|cpp)$$' --exclude '.*_demo\.c$$' \
		--exclude-throw-branches --exclude-unreachable-branches \
		--txt --html-details $(COVERAGE_DIR)/report/index.html --print-summary
	@echo "HTML report: $(COVERAGE_DIR)/report/index.html"

clean:
	rm -rf $(BUILD_DIR)

rebuild: clean build
