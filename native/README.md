# native/, C/C++ Moduler för v2

Denna katalog innehåller (i v2) C/C++ nativmodulerna som anropas från Java via JNA (Java Native Access).

## Planerade moduler

### 1. PII-kryptering: `libresurs_crypto.so`

AES-256-GCM kryptering av känsliga uppgifter (org.nr, personuppgifter, finansiell info) på hot path.

**Syfte:** I v1 lagras firmanamn, organisationsnummer och firmatecknare i klartext (`ApplicationController.java`, kommentar: `// TODO: encrypt PII before go-live`). Nyckeln ska lagras separat från databasen, inte i samma förvaringsutrymme som ciphertext.

**Ciphertext-format:** `[ key_version : 1 byte ][ AES-GCM ciphertext : plaintext-längd ][ GCM tag : 16 bytes ]`.
`resurs_decrypt_pii` läser version-byten först och returnerar `RESURS_ERR_KEY_VERSION`
om den inte känns igen, innan den ens rör GCM-taggen.

**Storleksgränser:**
- `resurs_encrypt_pii` (NUL-terminerad sträng): `strlen(plaintext)` ≤ `RESURS_MAX_PLAINTEXT_LEN` (8 KiB) - satt med marginal över den största PII-kolumnen (`VARCHAR(200)`), men snävt nog att fånga en anroparbugg tidigt.
- `resurs_encrypt_pii_raw` / `resurs_decrypt_pii` (binärdata, t.ex. filuppladdningar): upp till `RESURS_MAX_RAW_LEN` (10 MiB) - matchar `spring.servlet.multipart.max-file-size` exakt.
- `resurs_decrypt_pii` accepterar `ciphertext_len` upp till `RESURS_MAX_RAW_LEN`, inte `RESURS_MAX_PLAINTEXT_LEN` - en given ciphertext kan ha kommit från endera encrypt-vägen, och decrypt kan inte se vilken.

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
// innehålla 0x00 (t.ex. filuppladdningar).
int resurs_encrypt_pii_raw(
    const unsigned char* data,
    size_t data_len,
    const unsigned char* nonce,
    size_t nonce_len,
    unsigned char* ciphertext_out,
    size_t* ciphertext_len
);

// Dekryptera PII-sträng: verifierar nyckelversion (RESURS_ERR_KEY_VERSION
// om okänd) + GCM-tag, returnerar klartext utan avslutande \0. Samma nonce
// som vid krypteringen krävs.
int resurs_decrypt_pii(
    const unsigned char* nonce,
    size_t nonce_len,
    const unsigned char* ciphertext,
    size_t ciphertext_len,
    char* plaintext_out,
    size_t* plaintext_len
);

// Deterministisk HMAC-SHA256 av data under modulens privata lookup-nyckel,
// för användning som blind-index i en WHERE-sats (sökbar utan att
// dekryptera varje rad). Samma indata ger alltid samma 32 bytes.
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

**JNA Bridge (Java):**
```java
public interface ResursCryptoLibrary extends Library {
    ResursCryptoLibrary INSTANCE = Native.load("resurs_crypto", ResursCryptoLibrary.class);

    int resurs_crypto_init(String keyFilePath);

    int resurs_encrypt_pii(
        String plaintext,
        byte[] nonce,
        int nonceLen,
        byte[] ciphertextOut,
        IntByReference ciphertextLen
    );

    int resurs_encrypt_pii_raw(
        byte[] data,
        int dataLen,
        byte[] nonce,
        int nonceLen,
        byte[] ciphertextOut,
        IntByReference ciphertextLen
    );

    int resurs_decrypt_pii(
        byte[] nonce,
        int nonceLen,
        byte[] ciphertext,
        int ciphertextLen,
        byte[] plaintextOut,
        IntByReference plaintextLen
    );

    int resurs_hmac_sha256(
        byte[] data,
        int dataLen,
        byte[] hmacOut,
        IntByReference hmacLen
    );

    void resurs_crypto_shutdown();
}
```

**Nyckellagring:**
**Nyckellagring:**
- Nyckelfil: 64 raw bytes (...), laddas via `resurs_crypto_init`. Genereras med `cd infra && make keys-crypto`.
- Lagringsplats idag: lokal fil (`infra/secrets/resurs-crypto.key`), monterad som Docker-secret, separat från databasen. 
- Planerat: HashiCorp Vault eller AWS KMS (ej implementerat).
- Nonce genereras per krypteringsoperation och lagras tillsammans med ciphertext

### 2. Audit-signering: `libresurs_audit.so`

Säker signering av audit-loggen med hashkedjor, för att upptäcka manipulation i efterhand.

**Syfte:** I v1 är audit-loggen osignerad (JSON-blob i en kolumn utan index). En rad kan ändras eller raderas i efterhand utan att det syns. I v2 ska varje audit-post hashas ihop med föregående posts hash (hashkedja) och signeras, så att manipulation av en enskild post eller av kedjans ordning går att upptäcka vid verifiering.

**Funktioner:**
```c
// Ladda in den privata Ed25519-signeringsnyckeln (PEM) från key_file_path.
// Anropas en gång vid uppstart, innan resurs_audit_chain_entry.
int resurs_audit_init(const char* key_file_path);

// Beräkna hash för en audit-post och kedja den till föregående post, samt
// signera hashen. hash_out = SHA-256(prev_hash || entry_json).
// signature_out är en Ed25519-signatur (alltid 64 bytes).
int resurs_audit_chain_entry(
    const unsigned char* prev_hash,     // 32 bytes, SHA-256 av föregående post (NULL för första posten i kedjan)
    const char* entry_json,             // audit-postens innehåll: tidsstämpel, regel-ID, indata, utfall
    size_t entry_len,
    unsigned char* hash_out,            // 32 bytes, SHA-256(prev_hash || entry_json)
    unsigned char* signature_out,       // Ed25519-signatur av hash_out
    size_t* signature_len
);

// Verifiera en kedja av audit-poster, hittar första manipulerade posten om någon.
//
// Utökad utöver den ursprungliga specen ovan: entries/entry_lens skickas in
// så att verifieraren kan omberäkna varje hash från postens faktiska
// innehåll, inte bara kontrollera signaturerna. Utan detta skulle
// manipulation av en posts innehåll (utan att ändra hash eller signatur)
// gå obemärkt förbi.
//
// Kräver INTE föregående resurs_audit_init - verifieringen använder enbart
// den publika nyckeln som skickas in här, inte den privata nyckeln som
// resurs_audit_chain_entry behöver.
int resurs_audit_verify_chain(
    const unsigned char* hashes,        // entry_count * 32 bytes, hashkedjan i ordning
    const unsigned char* signatures,    // signaturer i samma ordning
    const size_t* signature_lens,
    const char* entries,                // entry_count JSON-blobbar i följd
    const size_t* entry_lens,           // längden på var post i entries
    size_t entry_count,
    const unsigned char* public_key,
    int* first_invalid_index            // -1 om kedjan är giltig, annars index på första manipulerade posten
);

// Suddar den privata signeringsnyckeln ur minnet. Säkert att anropa flera
// gånger och före init. Därefter returnerar chain_entry NOT_INIT
// (verify_chain påverkas inte - den behöver aldrig init, se ovan).
void resurs_audit_shutdown(void);
```

**JNA Bridge (Java):**
```java
public interface ResursAuditLibrary extends Library {
    ResursAuditLibrary INSTANCE = Native.load("resurs_audit", ResursAuditLibrary.class);

    int resurs_audit_init(String keyFilePath);

    int resurs_audit_chain_entry(
        byte[] prevHash,
        String entryJson,
        int entryLen,
        byte[] hashOut,
        byte[] signatureOut,
        IntByReference signatureLen
    );

    int resurs_audit_verify_chain(
        byte[] hashes,
        byte[] signatures,
        int[] signatureLens,
        byte[] entries,
        int[] entryLens,
        int entryCount,
        byte[] publicKey,
        IntByReference firstInvalidIndex
    );

    void resurs_audit_shutdown();
}
```

**Nyckellagring:**
- Nyckelformat: Ed25519 privat nyckel i PEM, laddas via `resurs_audit_init`. Genereras med `cd infra && make keys-audit` (se `infra/Makefile`); ligger lokalt i `infra/secrets/resurs-audit.key`, monterad som Docker-secret - samma princip som för PII-krypteringsnyckeln (se ovan, inklusive samma framtida avsikt att flytta till ett externt valv).
- Hashkedja: SHA-256. Signaturalgoritm: Ed25519 över varje 32-byte-hash i kedjan.
- Publik nyckel (`infra/secrets/resurs-audit.pub`) kan distribueras fritt för verifiering, t.ex. till revisor eller tillsynsmyndighet - att rotera den privata nyckeln gör INTE tidigare signerade poster ogiltiga, de förblir giltiga under den gamla publika nyckeln.

## Kompilering

Bygget styrs av CMake (kräver libssl-dev / OpenSSL) och bygger båda modulerna
samt deras tester i ett steg:

```bash
cd native
cmake -B build
cmake --build build
```

Resultat: `build/crypto/libresurs_crypto.so` och `build/audit/libresurs_audit.so`.
Kör testsviterna med `ctest --test-dir build --output-on-failure`, eller de
narrerade demos med `make demo-crypto` / `make demo-audit` (se `native/Makefile`).

## JNA Integration Guide

1. Lägg till JNA i pom.xml:
```xml
<dependency>
    <groupId>net.java.dev.jna</groupId>
    <artifactId>jna</artifactId>
    <version>5.13.0</version>
</dependency>
```

2. Placera `.so`-filer i `/usr/local/lib/` eller ange sökväg via `-Djna.library.path`

3. Definiera Java-interface som extends `Library`

4. Anropa via `Native.load("resurs_crypto", ResursCryptoLibrary.class)` respektive `Native.load("resurs_audit", ResursAuditLibrary.class)`

## Status

- [x] libresurs_crypto.so, implementerad (v2)
- [x] libresurs_audit.so, implementerad (v2)
- [x] JNA bridge för crypto, implementerad (v2)
- [ ] JNA bridge för audit, ej implementerad 
