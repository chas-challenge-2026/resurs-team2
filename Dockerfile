# Unified Dockerfile for resurs-team2
#
# Builds the entire project (React frontend + C++ native module + Spring Boot
# backend) with a single `make package` call against the root Makefile, then
# serves everything from Spring Boot.
#
# Build logic lives in the root Makefile. This image only provides the toolchain
# (Node, JDK 21, make, cmake/g++) and delegates to `make package`.

# --- Build stage: toolchain + `make package` ---
# Debian trixie includes Node 22 + make, and ships OpenJDK 21 natively.
FROM node:22-trixie AS build

# Add JDK 21 and the C++ toolchain needed by the native module.
RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-25-jdk \
        make \
        gcc \
        g++ \
        cmake \
        libssl-dev

WORKDIR /app
COPY . .
RUN make package

# --- Runtime: Spring serves static frontend + API (+ native module) ---
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN mkdir -p /tmp/uploads

COPY --from=build /app/target/ ./target/

EXPOSE 8083
# Working dir is /app, so relative paths in application.properties resolve to
# /app/target/... (frontend static-locations and jna.library.path).
ENTRYPOINT ["java", "-jar", "target/resurs-portal-1.0-SNAPSHOT.jar"]
