# Wrapper around CMake. The real build configuration lives in
# CMakeLists.txt / crypto/CMakeLists.txt. This just gives a short command.
#
# NOTE: recipe lines below must be indented with a TAB, not spaces.

BUILD_DIR := build
ASAN_BUILD_DIR := $(BUILD_DIR)/asan

.PHONY: all build test test-v clean rebuild demo-crypto demo-audit bench-audit asan

all: build

build:
	cmake -S . -B $(BUILD_DIR)
	cmake --build $(BUILD_DIR)

test: build
	ctest --test-dir $(BUILD_DIR) --output-on-failure
	
test-v: build
	ctest --test-dir $(BUILD_DIR) -V

demo-crypto: build
	@cd $(BUILD_DIR)/crypto && ./crypto_demo

demo-audit: build
	@cd $(BUILD_DIR)/audit && ./audit_demo

# Stress/benchmark harness, not a ctest. Override entry count: make bench-audit N=1000000
bench-audit: build
	@cd $(BUILD_DIR)/audit/tests && ./audit_stress_test $(N)

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

# Separate Debug build with ASAN + UBSan, then run all tests in it.
# Lives inside build/, so it is git-ignored and removed by clean.
asan:
	cmake -S . -B $(ASAN_BUILD_DIR) -DCMAKE_BUILD_TYPE=Debug -DRESURS_SANITIZE=ON
	cmake --build $(ASAN_BUILD_DIR)
	ctest --test-dir $(ASAN_BUILD_DIR) --output-on-failure

clean:
	rm -rf $(BUILD_DIR)

rebuild: clean build
