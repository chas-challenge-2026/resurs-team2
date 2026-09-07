# Unified build system for resurs-team2
#
# Targets:
#   clean          - Remove all build artifacts
#   build          - Build frontend and backend, copy artifacts to target/
#   test           - Run frontend lint and backend tests
#   dev            - Run Vite dev server (HMR) + backend with local profile
#   build-frontend - Build only the React frontend
#   build-backend  - Build only the Spring Boot backend
#   build-native   - Build only the C++ native module

ROOT         := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
FRONTEND_DIR := frontend
NATIVE_DIR   := native
BACKEND_DIR  := backend/ResursPortal
TARGET_DIR   := target

.PHONY: clean build test test_frontend test_backend test_native dev \
        build-frontend build-backend build-native package dev-vite dev-spring
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

test: test_frontend test_backend test_native

test_frontend:
	cd $(FRONTEND_DIR) && npm ci && npm run lint && npm run build

test_backend:
	cd $(BACKEND_DIR) && ./mvnw test

test_native:
	cd $(NATIVE_DIR) && $(MAKE) test

clean:
	rm -rf $(TARGET_DIR)
	rm -rf $(FRONTEND_DIR)/dist
	cd $(NATIVE_DIR) && $(MAKE) clean
	cd $(BACKEND_DIR) && ./mvnw clean

# ── Sub-builds ────────────────────────────────────────────────────

build-native:
	cd $(NATIVE_DIR) && $(MAKE) build
	mkdir -p $(TARGET_DIR)/libs
	cp $(NATIVE_DIR)/build/crypto/libresurs_crypto.so $(TARGET_DIR)/libs/

build-frontend:
	cd $(FRONTEND_DIR) && npm ci && npm run build
	mkdir -p $(TARGET_DIR)/frontend
	cp -r $(FRONTEND_DIR)/dist/* $(TARGET_DIR)/frontend/

build-backend:
	cd $(BACKEND_DIR) && ./mvnw package -DskipTests
	mkdir -p $(TARGET_DIR)
	cp $(BACKEND_DIR)/target/resurs-portal-1.0-SNAPSHOT.jar $(TARGET_DIR)/
