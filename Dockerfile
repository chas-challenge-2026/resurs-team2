# Unified Dockerfile for resurs-team2
#
# Multi-stage build:
#   1. frontend  – React app (Node 22, bookworm)
#   2. build     – Native C++ module + Spring Boot JAR (temurin 25 JDK, jammy)
#   3. runtime   – JRE only (temurin 25 JRE, jammy)
#
# The native .so is compiled on jammy (glibc 2.35) so it matches the runtime
# image's glibc, preventing undefined-symbol errors from a glibc version skew.

# ── Stage 1: React frontend ────────────────────────────────────
FROM node:22-bookworm AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./frontend/
RUN cd frontend && npm ci
COPY frontend/ frontend/
RUN cd frontend && npm run build

# ── Stage 2: Native C++ + Spring Boot JAR ──────────────────────
FROM eclipse-temurin:25-jdk-jammy AS build

RUN apt-get update && apt-get install -y --no-install-recommends \
        make gcc g++ cmake libssl-dev && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .
RUN make build-native && make build-backend

# ── Stage 3: Runtime ───────────────────────────────────────────
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN mkdir -p /tmp/uploads

COPY --from=build /app/target/ target/
COPY --from=frontend /app/frontend/dist/ target/frontend/

EXPOSE 8083
# Working dir is /app, so relative paths in application.properties resolve to
# /app/target/... (frontend static-locations and jna.library.path).
ENTRYPOINT ["java", "-jar", "target/resurs-portal-1.0-SNAPSHOT.jar"]
