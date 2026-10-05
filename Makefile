# Unified build system for resurs-team2
#
# Targets:
#   clean          - Remove all build artifacts
#   build          - Build frontend and backend, copy artifacts to target/
#   test           - Run frontend lint, backend tests, native tests and the audit ITs
#   test-audit     - Run the audit scope against the real native module
#   dev            - Run Vite dev server (HMR) + backend with local profile
#   build-frontend - Build only the React frontend
#   build-backend  - Build only the Spring Boot backend
#   build-native   - Build only the C++ native module

ROOT         := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
FRONTEND_DIR := frontend
NATIVE_DIR   := native
BACKEND_DIR  := backend/ResursPortal
TARGET_DIR   := target
LIBS_DIR     := $(TARGET_DIR)/libs

.PHONY: clean build test test_frontend test_backend test_native native-build test-audit \
        test-encryption dev \
        build-frontend build-backend build-native package dev-vite dev-spring \
		playwrite-e2e
        # build-native

# ── Aggregate targets ─────────────────────────────────────────────

build: build-native build-frontend build-backend

# Alias used by the Dockerfile - same as `build`.
package: build

# Run Vite dev server (HMR on :5173) with Spring Boot (local profile on :8083) concurrently.
# Vite proxies /api -> :8083, so no CORS config is needed.
# Parallel make (-j2) lets make handle Ctrl-C: it forwards the signal to both
# children and waits for them to exit cleanly (no shell trap / kill 0 hacks).
dev:
	cd $(FRONTEND_DIR) && test -d node_modules || npm ci
	@echo "Starting Vite dev server (:5173) and Spring Boot (:8083)..."
	@echo "  Frontend: http://localhost:5173"
	@echo "  Spring:   http://localhost:8083"
	$(MAKE) -j2 dev-vite dev-spring

dev-vite:
	cd $(FRONTEND_DIR) && npm run dev

dev-spring:
	cd $(BACKEND_DIR) && ./mvnw -Plocal spring-boot:run \
		-Dspring-boot.run.profiles=local

test: test_frontend test_backend test_native test-audit

test_frontend:
	cd $(FRONTEND_DIR) && npm ci && npm run lint && npm run test && npm run build

test_backend:
	cd $(BACKEND_DIR) && ./mvnw test

test_native: native-build
	cd $(NATIVE_DIR) && $(MAKE) test

# The full audit scope: the audit ITs the default surefire run skips by name, covering
# signing and verification against the real Ed25519 module (RealAuditSigningIT) and the
# seeded chain that startup signs and re-signs in place (SeedAuditSigningIT). The C++
# half of the audit scope runs as part of test_native (audit_sandbox_test, audit_abi_test).
#
# Depends on build-native because the tests load the library from $(LIBS_DIR), which is
# where the JNA library path points.
test-audit: build-native
	cd $(BACKEND_DIR) && ./mvnw -Dtest='*Audit*IT' test

test-encryption: build-native
	cd $(BACKEND_DIR) && ./mvnw -Dtest=RealEncryptionIT test

playwrite-e2e:
	@trap 'fuser -k 8083/tcp 2>/dev/null || true' EXIT; \
	$(MAKE) dev-spring & \
	echo "Waiting on backend"; \
	until nc -z localhost 8083; do sleep 1; done; \
	echo "Backend is up"; \
	cd $(FRONTEND_DIR) && npx playwright test

clean:
	rm -rf $(TARGET_DIR)
	rm -rf $(FRONTEND_DIR)/dist
	cd $(NATIVE_DIR) && $(MAKE) clean
	cd $(BACKEND_DIR) && ./mvnw clean

# ── Sub-builds ────────────────────────────────────────────────────

# Shared by everything that needs the C++ module compiled, so make builds it once and
# in one place even under `make -j`: test_native, build-native and test-audit all reach
# the module through here rather than each invoking cmake in the same build dir.
native-build:
	cd $(NATIVE_DIR) && $(MAKE) build

# Both native libraries, because both are loaded by JNA at runtime and surefire points
# jna.library.path at $(LIBS_DIR). Copying only the crypto one leaves the audit signing
# falling back to the dummy at every start, silently.
build-native: native-build
	mkdir -p $(LIBS_DIR)
	cp $(NATIVE_DIR)/build/crypto/libresurs_crypto.so $(NATIVE_DIR)/build/audit/libresurs_audit.so $(LIBS_DIR)/

build-frontend:
	cd $(FRONTEND_DIR) && npm ci && npm run build
	mkdir -p $(TARGET_DIR)/frontend
	cp -r $(FRONTEND_DIR)/dist/* $(TARGET_DIR)/frontend/

build-backend:
	cd $(BACKEND_DIR) && ./mvnw package -DskipTests
	mkdir -p $(TARGET_DIR)
	cp $(BACKEND_DIR)/target/resurs-portal-1.0-SNAPSHOT.jar $(TARGET_DIR)/
