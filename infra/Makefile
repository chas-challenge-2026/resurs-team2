# Crypto key management for the native module (issue #11).
#
# The key file is exactly 64 raw bytes:
#   [ 0..32) AES-256 key
#   [32..64) HMAC-SHA256 lookup key (blind index)
# Docker Compose mounts it into the app container at /run/secrets/resurs_crypto_key.
# See ../native/README.md.

KEY := secrets/resurs-crypto.key
NEW := $(KEY).new

# Audit-signing key (issue #11, native/audit/): an Ed25519 PEM private key
# used to sign the hash-chained audit log so tampering with an entry (or
# its order) can be detected. Docker Compose mounts it into the app
# container at /run/secrets/resurs_audit_key. See ../native/README.md.
AUDIT_KEY := secrets/resurs-audit.key
AUDIT_PUB := secrets/resurs-audit.pub

.PHONY: keys key keys-crypto key-aes new-key-aes key-hmac new-key-hmac hmac show-keys-crypto help up down check-key \
        keys-audit show-key-audit require-crypto-key require-audit-key

help:
	@echo "make up          - start the stack (checks the crypto + audit keys first)"
	@echo "make down         - stop the stack"
	@echo "make keys        - generate whichever secrets (crypto + audit) are still missing"
	@echo "make keys-crypto - generate only the crypto key   (refuses to overwrite)"
	@echo "make keys-audit  - generate only the audit key    (refuses to overwrite)"
	@echo "make key-aes     - rotate only the AES half   -> $(NEW)"
	@echo "make key-hmac    - rotate only the HMAC half  -> $(NEW)"
	@echo "make show-keys-crypto - SHA-256 fingerprint of each half (no key bytes printed)"
	@echo "make show-key-audit   - SHA-256 fingerprint of the public key (no key bytes printed)"

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
	@if [ ! -s "$(AUDIT_KEY)" ]; then \
		printf '\n  Audit signing key missing: %s/%s\n  Generate it:  cd infra && make keys-audit\n\n' "$(CURDIR)" "$(AUDIT_KEY)"; \
		exit 1; \
	fi

# --- provision everything (idempotent: skips whichever key already exists) ---

keys:
	@if [ -e "$(KEY)" ]; then \
		echo "$(KEY) already exists - skipping (rotate with make key-aes / make key-hmac)"; \
	else \
		$(MAKE) keys-crypto; \
	fi
	@if [ -e "$(AUDIT_KEY)" ]; then \
		echo "$(AUDIT_KEY) already exists - skipping (remove it and re-run make keys-audit to regenerate)"; \
	else \
		$(MAKE) keys-audit; \
	fi

# --- full crypto key (hard refuse - call this directly to be explicit) -------

key: keys-crypto
keys-crypto:
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
key-aes: require-crypto-key
	@umask 077 && { openssl rand 32; tail -c 32 "$(KEY)"; } > "$(NEW)"
	@chmod 600 "$(NEW)"
	@echo "wrote $(NEW): new AES half + current HMAC half"
	@echo "WARNING: swapping this in makes every existing PII ciphertext undecryptable."
	@echo "apply with:  mv $(NEW) $(KEY)"

new-key-hmac hmac: key-hmac
key-hmac: require-crypto-key
	@umask 077 && { head -c 32 "$(KEY)"; openssl rand 32; } > "$(NEW)"
	@chmod 600 "$(NEW)"
	@echo "wrote $(NEW): current AES half + new HMAC half"
	@echo "WARNING: swapping this in requires recomputing every blind-index value."
	@echo "apply with:  mv $(NEW) $(KEY)"

# --- audit-signing key (Ed25519 PEM) ------------------------------------------

keys-audit:
	@if [ -e "$(AUDIT_KEY)" ]; then \
		echo "refusing to overwrite existing $(AUDIT_KEY)"; \
		exit 1; \
	fi
	@mkdir -p secrets
	@umask 077 && openssl genpkey -algorithm ED25519 -out "$(AUDIT_KEY)"
	@chmod 600 "$(AUDIT_KEY)"
	@openssl pkey -in "$(AUDIT_KEY)" -pubout -out "$(AUDIT_PUB)"
	@echo "wrote $(AUDIT_KEY) (private, keep secret) + $(AUDIT_PUB) (distribute to the auditor)"
	@echo "NOTE: rotating this key does NOT invalidate old signed entries - they"
	@echo "      remain valid under the OLD public key. Distribute the new"
	@echo "      $(AUDIT_PUB) to whoever verifies the chain."

# --- inspection --------------------------------------------------------------

show-keys-crypto: require-crypto-key
	@printf 'aes  half: %s\n'  "$$(head -c 32 "$(KEY)" | openssl dgst -sha256 -r | cut -d' ' -f1)"
	@printf 'hmac half: %s\n'  "$$(tail -c 32 "$(KEY)" | openssl dgst -sha256 -r | cut -d' ' -f1)"

require-crypto-key:
	@test -e "$(KEY)" || { echo "$(KEY) not found - run 'make keys-crypto' first"; exit 1; }
	@test "$$(wc -c < "$(KEY)")" = "64" || { echo "$(KEY) is not 64 bytes"; exit 1; }

show-key-audit: require-audit-key
	@printf 'public key fingerprint: %s\n' \
		"$$(openssl pkey -in "$(AUDIT_KEY)" -pubout -outform DER | openssl dgst -sha256 -r | cut -d' ' -f1)"

require-audit-key:
	@test -e "$(AUDIT_KEY)" || { echo "$(AUDIT_KEY) not found - run 'make keys-audit' first"; exit 1; }
