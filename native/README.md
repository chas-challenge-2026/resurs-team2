# native/ – C/C++-moduler för v2

Denna katalog innehåller den nativa C/C++-modulen `libresurs_crypto.so` som anropas från
Java via JNA (Java Native Access). Modulen byggs med CMake (`native/Makefile`) och
produceras av `make build-native` i repo-roten till `target/libs/`.

> Senast uppdaterad: 2026-09-21 · **Status:** PII-krypteringsmodulen är **implementerad och
> testad**. Audit-signeringsmodulen är **planerad, ej byggd** (Java-sidan har en stub —
> se nedan).

## 1. PII-kryptering: `libresurs_crypto.so` ✅ implementerad

AES-256-GCM-kryptering av känsliga uppgifter (org.nr, företagsnamn, firmatecknare,
finansiell info) på hot path, samt HMAC-SHA256-baserade blind index för uppslag utan
klartext.

**Blob-layout:** `[nonce:12][key_version:1][ciphertext][tag:16]` – krypteras till base64 i
textkolumnen av `PiiAttributeConverter` / `AmountAttributeConverter`.
Filer krypteras med `encryptRaw` (samma layout) via `EncryptedFileStorageService`.

**C-snitt (deklarerade i `ResursCryptoLibrary.java`):**

```c
int resurs_crypto_init(const char* keyFilePath);      // ladda 64-byte nyckelfil
int resurs_encrypt_pii(const char* plaintext, ...);    // AES-256-GCM
int resurs_encrypt_pii_raw(const void* data, size_t dataLen, ...); // binär data (filer)
int resurs_decrypt_pii(const void* nonce, size_t nonceLen, const void* ciphertext, ...);
int resurs_hmac_sha256(const void* data, size_t dataLen, ...);      // blind index
void resurs_crypto_shutdown();
```

**Implementering (C/C++):** `native/crypto/`
- `aes_gcm_cipher.cpp`   – OpenSSL `EVP_aes_256_gcm`, per-operation-nonce
- `key_manager.cpp`      – mutex-skyddad singleton, nyckel i "key not loaded"-tillstånd,
  `OPENSSL_cleanse` vid nedrivning
- `hmac_sha256.cpp`      – HMAC-SHA256 för blind index
- `resurs_crypto.cpp`    – exekverbart grensitt mot JNA
- `abi_test.c` / `sandbox_test.cpp` – inhemska tester (`make test_native`)

**Nyckellagring:**
- Nyckelfilen är exakt **64 byte**: `[0..32)` AES-256, `[32..64)` HMAC-nyckel.
- Genereras med `cd infra && make keys` (vägrar skriva över befintlig; halv-rotation med
  `make key-aes` / `make key-hmac`).
- Monteras i Docker som secret `/run/secrets/resurs_crypto_key`, nås av appen via
  `RESURS_CRYPTO_KEY_PATH=${resurs.jna.key.path}`.
- Nonce genereras per operation och lagras med ciphertext.
- Nycklar **får inte** ligga i samma förvaringsutrymme som ciphertext (databas).

**Fail-closed:** `ResursCryptoConfig` vägrar starta om en produktionsdatabas (Postgres) är
konfigurerad utan att den nativa modulen går att ladda. Lokalt/test använder
`DummyCryptoService` + `PlainPiiCodec` (test-profiler).

## 2. Audit-signering: hashkedja ❌ ej implementerad (planerad)

Säker signering av audit-loggen med hashkedjor, för att upptäcka manipulation i efterhand.

**Syfte:** I v1 var audit-loggen osignerad (JSON-blob utan index). Målet är att varje
audit-post hashas ihop med föregående posts hash så att manipulation av en enskild post
eller av kedjans ordning upptäcks vid verifiering.

**Status idag:**
- Databassidan finns: separat `audit_log`-tabell med `sequence_number`, `hash`,
  `previous_hash`, `entry`, `timestamp` (se `schema.sql` / `infra/seed.sql`).
- Java-sidan är en **stub**: `AuditLogService.computeHash(...)` returnerar `""`
  (`// TODO: Implement actual hash computation`). Samtliga `hash`/`previous_hash`-rader
  är därmed tomma.
- **Ingen** `libresurs_audit.so` är byggd och inga `resurs_audit_*`-funktioner finns i C.

**När det byggs:** planera en C-modul med `resurs_audit_chain_entry(prev_hash, entry_json)`
(HMAC/SHA-256 av `prev_hash || entry_json`) och en verifieringsfunktion, exponerad via en
JNA-bridge. Nyckelhantering enligt samma princip som PII-krypteringen (separat
förvaring). Se `docs/backend-audit.md` (H1) för rekommenderad åtgärd.

## Kompilering

Modulen byggs via `native/Makefile` (CMake), inte som ad-hoc `gcc`-kommandon:

```bash
# från repo-roten:
make build-native        # → target/libs/libresurs_crypto.so
make test_native         # kör C/C++-tester (abi_test, sandbox_test)
make test-encryption     # kör Java RealEncryptionIT mot den nativa modulen
```

Kräver `cmake`, `gcc/g++` och `libssl-dev`.

## JNA-integration (Java)

- `config/JnaConfig.java` – sätter `jna.library.path` från `resurs.jna.library.path`
  (default `target/libs`, relativt arbetskatalogen).
- `config/ResursCryptoLibrary.java` – JNA-interface som `extends Library`.
- `api/v1/service/ResursCryptoServiceImpl.java` – konkret service; `ResursCryptoService`
  är gränssnittet som resten av appen använder.
- `config/ResursCryptoConfig.java` – komposition + fail-closed vid saknad modul.

## Status (sammanfattning)

- [x] `libresurs_crypto.so` – **implementerad och testad** (AES-256-GCM, HMAC blind index)
- [x] JNA-bridge (`ResursCryptoLibrary`) – **implementerad**
- [x] Fält- och filkryptering i vila via modulen – **implementerad**
- [ ] `libresurs_audit.so` / audit-signering – **ej byggd**; Java-sidan är en stub
  (`AuditLogService.computeHash`), se `docs/backend-audit.md` H1