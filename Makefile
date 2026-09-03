# Unified build system for resurs-team2
#
# Targets:
#   clean          - Remove all build artifacts
#   build          - Build frontend and backend, copy artifacts to target/
#   test           - Run frontend lint and backend tests
#   dev            - Run Vite dev server (HMR) + backend with local profile
#   build-frontend - Build only the React frontend
#   build-backend  - Build only the Spring Boot backend
#   # build-native - Build only the C++ native module (uncomment when CMake is added)

ROOT         := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
FRONTEND_DIR := frontend
# NATIVE_DIR   := native
BACKEND_DIR  := backend/ResursPortal
TARGET_DIR   := target

.PHONY: clean build test dev \
        build-frontend build-backend
        # build-native

# ── Aggregate targets ─────────────────────────────────────────────

build: build-frontend build-backend
# build: build-native build-frontend build-backend

# Run Vite dev server (HMR on :5173) with Spring Boot (local profile on :8083) concurrently.
# Vite proxies /api -> :8083, so no CORS config is needed.
dev:
	cd $(FRONTEND_DIR) && test -d node_modules || npm ci
	$(MAKE) dev-run

dev-run:
	@echo "Starting Vite dev server (:5173) and Spring Boot (:8083)..."
	@echo "  Frontend: http://localhost:5173"
	@echo "  Spring:   http://localhost:8083"
	@trap 'kill 0' INT TERM; \
	(cd $(FRONTEND_DIR) && npm run dev) & \
	(cd $(BACKEND_DIR) && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local) & \
	wait

test: build-frontend
	cd $(FRONTEND_DIR) && npm run lint
	cd $(BACKEND_DIR) && ./mvnw test

clean:
	rm -rf $(TARGET_DIR)
	rm -rf $(FRONTEND_DIR)/dist
	# rm -rf $(NATIVE_DIR)/build
	cd $(BACKEND_DIR) && ./mvnw clean

# ── Sub-builds ────────────────────────────────────────────────────

# build-native:
# 	cd $(NATIVE_DIR) && cmake -S . -B build && cmake --build build
# 	mkdir -p $(TARGET_DIR)/libs
# 	cp $(NATIVE_DIR)/build/crypto/libresurs_crypto.so $(TARGET_DIR)/libs/

build-frontend:
	cd $(FRONTEND_DIR) && npm ci && npm run build
	mkdir -p $(TARGET_DIR)/frontend
	cp -r $(FRONTEND_DIR)/dist/* $(TARGET_DIR)/frontend/

build-backend:
	cd $(BACKEND_DIR) && ./mvnw package -DskipTests
	mkdir -p $(TARGET_DIR)
	cp $(BACKEND_DIR)/target/resurs-portal-1.0-SNAPSHOT.jar $(TARGET_DIR)/
