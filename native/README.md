# native/, C/C++ Moduler för v2

Denna katalog innehåller två delade bibliotek skrivna i C++17 med ett C-API
(`extern "C"`). Inga C++-undantag passerar API-gränsen:
varje fel mappas till en felkod.

## Moduler

### 1. PII-kryptering: `libresurs_crypto.so`

AES-256-GCM-kryptering (`EVP_aes_256_gcm`) och HMAC-SHA256 för blind-index (`EVP_MAC`).

**Ciphertext-format:** `[ key_version : 1 byte ][ AES-GCM ciphertext : plaintext-längd ][ GCM tag : 16 bytes ]`.
`resurs_decrypt_pii` läser version-byten först och returnerar `RESURS_ERR_KEY_VERSION`
om den inte känns igen, innan den ens rör GCM-taggen.

**Nonce:** 12 bytes (`RESURS_NONCE_LEN`), skickas in av anroparen och måste komma från en
CSPRNG. Modulen genererar inte nonce och lägger den inte i ciphertext-bufferten - anroparen
måste spara den och skicka in samma nonce vid dekryptering.

**Storleksgränser** (i bytes, inte tecken; överskridande ger `RESURS_ERR_INVALID_ARG`):

- `resurs_encrypt_pii` (NUL-terminerad sträng): `strlen(plaintext)` ≤ `RESURS_MAX_PLAINTEXT_LEN` (8 KiB). Snävare än `_raw` med avsikt, för att fånga en anroparbugg tidigt.
- `resurs_encrypt_pii_raw` (binärdata): `data_len` ≤ `RESURS_MAX_RAW_LEN` (10 MiB).
- `resurs_decrypt_pii`: `ciphertext_len` från 17 (1 + 0 + 16) upp till 1 + `RESURS_MAX_RAW_LEN` + 16 - en given ciphertext kan ha kommit från endera encrypt-vägen, och decrypt kan inte se vilken.
- `resurs_hmac_sha256`: `data_len` ≤ `RESURS_MAX_PLAINTEXT_LEN` (8 KiB).

**Funktioner:**

```c
// Läs in 64-byte-nyckelfilen (32 AES + 32 HMAC lookup) från key_file_path.
// Anropas en gång vid uppstart. Modulen äger nyckeln efter detta - den
// skickas INTE in per anrop till encrypt/decrypt/hmac nedan.
int resurs_crypto_init(const char* key_file_path);

// Kryptera en PII-sträng (NUL-terminerad, max RESURS_MAX_PLAINTEXT_LEN
// bytes) och stämpla med aktuell nyckelversion (se Ciphertext-format ovan).
// nonce måste vara CSPRNG-genererad; att återanvända (nyckel, nonce) bryter
// GCM:s sekretess och autenticitet.
// *ciphertext_len är in/out: kapacitet in, faktisk längd ut. Anropa med
// ciphertext_out = NULL och *ciphertext_len = 0 för att fråga efter
// den nödvändiga buffertstorleken (RESURS_ERR_BUFFER_SMALL).
int resurs_encrypt_pii(
    const char* plaintext,
    const unsigned char* nonce,     // 12 bytes (GCM)
    size_t nonce_len,
    unsigned char* ciphertext_out,
    size_t* ciphertext_len
);

// Samma kontrakt som resurs_encrypt_pii, men tar en explicit längd (max
// RESURS_MAX_RAW_LEN bytes) istället för strlen() - för binärdata som kan
// innehålla 0x00.
int resurs_encrypt_pii_raw(
    const unsigned char* data,
    size_t data_len,
    const unsigned char* nonce,
    size_t nonce_len,
    unsigned char* ciphertext_out,
    size_t* ciphertext_len
);

// Dekrypt: verifierar nyckelversion (RESURS_ERR_KEY_VERSION om okänd)
// + GCM-tag (RESURS_ERR_AUTH), returnerar klartext utan avslutande \0.
// Samma nonce som vid krypteringen krävs.
int resurs_decrypt_pii(
    const unsigned char* nonce,
    size_t nonce_len,
    const unsigned char* ciphertext,
    size_t ciphertext_len,
    char* plaintext_out,
    size_t* plaintext_len
);

// Deterministisk HMAC-SHA256 av data under modulens privata lookup-nyckel,
// för användning som blind-index (sökbar utan att dekryptera varje rad).
// Samma indata ger alltid samma 32 bytes. data_len får vara 0.
int resurs_hmac_sha256(
    const unsigned char* data,
    size_t data_len,
    unsigned char* hmac_out,
    size_t* hmac_len
);

// Suddar nycklarna ur minnet (OPENSSL_cleanse). Säkert att anropa flera
// gånger och före init. Därefter returnerar encrypt/decrypt/hmac NOT_INIT.
void resurs_crypto_shutdown(void);
```

**Felkoder** (`resurs_crypto.h`):

| Kod | Värde | Betydelse |
| --- | --- | --- |
| `RESURS_OK` | 0 | OK |
| `RESURS_ERR_NOT_INIT` | -1 | anrop före lyckad `init` (eller efter `shutdown`) |
| `RESURS_ERR_AUTH` | -2 | decrypt: GCM-taggen stämmer inte |
| `RESURS_ERR_KEY_IO` | -3 | init: nyckelfilen kan inte läsas eller är inte exakt 64 bytes |
| `RESURS_ERR_BUFFER_SMALL` | -4 | utbufferten är för liten; längdparametern sätts till nödvändig storlek |
| `RESURS_ERR_INVALID_ARG` | -5 | NULL där det inte är tillåtet, fel nonce-längd, för stor indata m.m. |
| `RESURS_ERR_INTERNAL` | -6 | oväntat OpenSSL-fel |
| `RESURS_ERR_KEY_VERSION` | -7 | decrypt: okänd nyckelversion |

**Nyckelfil:** exakt 64 raw bytes: `[ AES-256-nyckel : 32 ][ HMAC lookup-nyckel : 32 ]`.
Filer som är kortare eller längre avvisas av `resurs_crypto_init` (`RESURS_ERR_KEY_IO`).

### 2. Audit-signering: `libresurs_audit.so`

Hashkedja (SHA-256) där varje 32-byte-hash signeras med Ed25519, så att manipulation av en
enskild post eller av kedjans ordning går att upptäcka vid verifiering. Modulen tolkar inte
postens innehåll; `entry_json` behandlas som `entry_len` bytes.

**Funktioner:**

```c
// Ladda in den privata Ed25519-signeringsnyckeln (PEM) från key_file_path.
// Anropas en gång vid uppstart, innan resurs_audit_chain_entry.
int resurs_audit_init(const char* key_file_path);

// Beräkna hash för en audit-post och kedja den till föregående post, samt
// signera hashen. hash_out = SHA-256(prev_hash || entry_json).
// signature_out är en Ed25519-signatur (alltid 64 bytes).
// *signature_len är enbart utdata (sätts till 64), värdet in läses inte.
int resurs_audit_chain_entry(
    const unsigned char* prev_hash,     // 32 bytes, föregående posts hash (NULL för första posten = 32 nollbytes)
    const char* entry_json,             // postens bytes
    size_t entry_len,
    unsigned char* hash_out,            // 32 bytes, SHA-256(prev_hash || entry_json)
    unsigned char* signature_out,       // minst 64 bytes, Ed25519-signatur av hash_out
    size_t* signature_len
);

// Verifiera en kedja av audit-poster, hittar första manipulerade posten om någon.
//
// entries/entry_lens skickas in så att verifieraren kan omberäkna varje hash
// från postens faktiska innehåll, inte bara kontrollera signaturerna. 
// Utan detta skulle manipulation av en posts innehåll (
// utan att ändra hash eller signatur) gå obemärkt förbi
//
// Kräver INTE föregående resurs_audit_init - verifieringen använder enbart
// den publika nyckeln som skickas in här, inte den privata nyckeln som
// resurs_audit_chain_entry behöver.
//
// entries_len/signatures_len: den verkliga storleken på respektive buffert.
// Utan dessa skulle en entry_lens/signature_lens som inte stämmer med
// buffertens verkliga storlek orsaka en out-of-bounds-läsning i modulen.
// Funktionen returnerar INVALID_ARG om entry_count > signatures_len / 64,
// om summan av entry_lens överskrider entries_len, eller om någon
// signature_lens[i] inte är 64.
int resurs_audit_verify_chain(
    const unsigned char* hashes,        // entry_count * 32 bytes, hashkedjan i ordning
    const unsigned char* signatures,    // signaturer i samma ordning
    const size_t* signature_lens,
    const char* entries,                // entry_count poster i följd
    const size_t* entry_lens,           // längden på var post i entries
    size_t entry_count,                 // måste vara > 0
    size_t entries_len,                 // total storlek på entries-buffern (bytes), > 0
    size_t signatures_len,              // total storlek på signatures-buffern (bytes), > 0
    const unsigned char* public_key,    // 32 bytes, rå Ed25519 publik nyckel
    int* first_invalid_index            // -1 om kedjan är giltig, annars index på första manipulerade posten
);

// Frigör den privata signeringsnyckeln (EVP_PKEY_free). Säkert att anropa
// flera gånger och före init. Därefter returnerar chain_entry NOT_INIT.
void resurs_audit_shutdown(void);
```

**Felkoder** (`resurs_audit.h`):

| Kod | Värde | Betydelse |
| --- | --- | --- |
| `RESURS_AUDIT_OK` | 0 | OK (för `verify_chain`: verifieringen kördes klart, oavsett resultat) |
| `RESURS_AUDIT_ERR_NOT_INIT` | -1 | `chain_entry` före lyckad `init` (eller efter `shutdown`) |
| `RESURS_AUDIT_ERR_INVALID_ARG` | -2 | NULL där det inte är tillåtet, fel längd m.m. |
| `RESURS_AUDIT_ERR_KEY_IO` | -3 | init: filen kan inte öppnas, är ingen giltig PEM-privatnyckel, eller nyckeln är inte Ed25519 |
| `RESURS_AUDIT_ERR_BUFFER_SMALL` | -4 | definierad i headern, returneras inte av någon funktion idag |
| `RESURS_AUDIT_ERR_INTERNAL` | -5 | oväntat OpenSSL-fel |

**Nycklar:**

- Signeringsnyckel: Ed25519 privat nyckel i PEM, laddas via `resurs_audit_init`.
- Verifiering: den råa 32-byte publika nyckeln skickas in per anrop till `resurs_audit_verify_chain`. Poster signerade med en tidigare nyckel verifieras därför fortfarande med den gamla publika nyckeln - att byta signeringsnyckel gör inte tidigare poster ogiltiga.

## Kompilering

Kräver CMake ≥ 3.22, en C++17-kompilator och OpenSSL 3 med utvecklingsfiler
(`EVP_MAC`-API:t i `crypto/hmac_sha256.cpp` finns först från OpenSSL 3.0).
Bygget bygger båda modulerna samt deras tester i ett steg:

```bash
cd native
cmake -B build
cmake --build build
```

Resultat: `build/crypto/libresurs_crypto.so` och `build/audit/libresurs_audit.so`.

Samma sak via `native/Makefile`:

| Kommando (i `native/`) | Gör |
| --- | --- |
| `make build` | CMake-bygge i `build/` |
| `make demo-crypto` / `make demo-audit` | narrerade demos |
| `make clean` / `make rebuild` | tar bort `build/` / bygger om |

## Tests

### Test suites

Båda modulerna har två sorters tester, definierade i `crypto/tests/CMakeLists.txt` och
`audit/tests/CMakeLists.txt`:

| Test | Testar | Byggs mot |
| --- | --- | --- |
| `crypto_sandbox_test` | interna C++-klasser (`KeyManager`, `AesGcmCipher`, HMAC) | modulens källfiler direkt, eftersom interna symboler är dolda i `.so` |
| `audit_sandbox_test` | interna C++-klasser (hashkedja, signer, `AuditKeyManager`) | modulens källfiler direkt |
| `crypto_abi_{init,roundtrip,buffers,raw,bounds,hmac}_test` | C-API:t, ett körbart test per område | `libresurs_crypto.so` |
| `audit_abi_{init,chain,verify,tamper}_test` | C-API:t, ett körbart test per område | `libresurs_audit.so` |

### Running the tests

| Kommando (i `native/`) | Gör |
| --- | --- |
| `make test` | bygger och kör alla tester (`ctest --output-on-failure`) |
| `make test-v` | samma, med utförlig utskrift (`ctest -V`) |
| `make asan` | Debug-bygge med ASAN + UBSan i `build/asan/` och kör alla tester; ett UBSan-fynd avbryter testet (`-fno-sanitize-recover=all`) |
| `make coverage` | täckningsrapport, se nedan |

### Coverage

`make coverage` bygger med gcov-instrumentering (`-DRESURS_COVERAGE=ON`, Debug) i
`build/coverage/`, raderar gamla `.gcda`-räknare, kör alla tester och skriver en rapport
med gcovr (måste vara installerat). Rapporten omfattar `crypto/` och `audit/`,
utan testfiler och demos

HTML-rapport: `build/coverage/report/index.html`.

| File | Lines | Exec | Coverage |
| --- | ---: | ---: | ---: |
| `audit/audit_key_manager.cpp` | 52 | 49 | 94 % |
| `audit/hash_chain.cpp` | 21 | 13 | 61 % |
| `audit/resurs_audit.cpp` | 64 | 54 | 84 % |
| `audit/signer.cpp` | 44 | 32 | 72 % |
| `crypto/aes_gcm_cipher.cpp` | 62 | 46 | 74 % |
| `crypto/hmac_sha256.cpp` | 29 | 21 | 72 % |
| `crypto/key_manager.cpp` | 52 | 52 | 100 % |
| `crypto/resurs_crypto.cpp` | 114 | 93 | 81 % |
| `crypto/secure_buffer.hpp` | 17 | 17 | 100 % |
| **Total** | **455** | **377** | **82 %** |

| Metric | Coverage |
| --- | --- |
| Lines | 82.9 % (377 out of 455) |
| Functions | 94.7 % (54 out of 57) |
| Branches | 74.8 % (258 out of 345) |

## Status

- [x] libresurs_crypto.so, implementerad (v2)
- [x] libresurs_audit.so, implementerad (v2)
