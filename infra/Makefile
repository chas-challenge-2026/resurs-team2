# Crypto key management for the native module (issue #11).
#
# The key file is exactly 64 raw bytes:
#   [ 0..32) AES-256 key
#   [32..64) HMAC-SHA256 lookup key (blind index)
# Docker Compose mounts it into the app container at /run/secrets/resurs_crypto_key.
# See ../native/README.md.

KEY := secrets/resurs-crypto.key
NEW := $(KEY).new

.PHONY: keys key key-aes new-key-aes key-hmac new-key-hmac hmac show-key help up down check-key

help:
	@echo "make up          - start the stack (checks the crypto key first)"
	@echo "make down         - stop the stack"
	@echo "make keys        - generate a fresh 64-byte key file (refuses to overwrite)"
	@echo "make key-aes     - rotate only the AES half   -> $(NEW)"
	@echo "make key-hmac    - rotate only the HMAC half  -> $(NEW)"
	@echo "make show-key    - SHA-256 fingerprint of each half (no key bytes printed)"

# --- run the stack ----------------------------------------------------------

up: check-key
	docker compose up -d --build

down:
	docker compose down

# Fail on the host with a clear message before docker compose hits the bind mount.
check-key:
	@if [ ! -s "$(KEY)" ]; then \
		printf '\n  Crypto key missing: %s/%s\n  Generate it:  cd infra && make keys\n\n' "$(CURDIR)" "$(KEY)"; \
		exit 1; \
	fi

# --- full key ------------------------------------------------------------------

key: keys
keys:
	@if [ -e "$(KEY)" ]; then \
		echo "refusing to overwrite existing $(KEY)"; \
		echo "to rotate one half instead: make key-aes  |  make key-hmac"; \
		exit 1; \
	fi
	@mkdir -p secrets
	@umask 077 && openssl rand 64 > "$(KEY)"
	@chmod 600 "$(KEY)"
	@echo "wrote $(KEY) ($$(wc -c < "$(KEY)") bytes)"

# --- half rotation -----------------------------------------------------------
# Always writes to $(NEW); never touches the live key silently.
# Review, then:  mv $(NEW) $(KEY)

new-key-aes: key-aes
key-aes: require-key
	@umask 077 && { openssl rand 32; tail -c 32 "$(KEY)"; } > "$(NEW)"
	@chmod 600 "$(NEW)"
	@echo "wrote $(NEW): new AES half + current HMAC half"
	@echo "WARNING: swapping this in makes every existing PII ciphertext undecryptable."
	@echo "apply with:  mv $(NEW) $(KEY)"

new-key-hmac hmac: key-hmac
key-hmac: require-key
	@umask 077 && { head -c 32 "$(KEY)"; openssl rand 32; } > "$(NEW)"
	@chmod 600 "$(NEW)"
	@echo "wrote $(NEW): current AES half + new HMAC half"
	@echo "WARNING: swapping this in requires recomputing every blind-index value."
	@echo "apply with:  mv $(NEW) $(KEY)"

# --- inspection --------------------------------------------------------------

show-key: require-key
	@printf 'aes  half: %s\n'  "$$(head -c 32 "$(KEY)" | openssl dgst -sha256 -r | cut -d' ' -f1)"
	@printf 'hmac half: %s\n'  "$$(tail -c 32 "$(KEY)" | openssl dgst -sha256 -r | cut -d' ' -f1)"

.PHONY: require-key
require-key:
	@test -e "$(KEY)" || { echo "$(KEY) not found - run 'make keys' first"; exit 1; }
	@test "$$(wc -c < "$(KEY)")" = "64" || { echo "$(KEY) is not 64 bytes"; exit 1; }
