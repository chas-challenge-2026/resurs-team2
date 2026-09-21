# Arkitektur – Resurs Kreditansökan (nuvarande)

> Senast uppdaterad: 2026-09-21 · Beskriver **nuläget** (v2). Den tidigare v1-arkitekturen
> (Spring Boot 2.7, JdbcTemplate i controllers, Thymeleaf + jQuery) är helt ersatt.
> Se även `docs/backend-audit.md` för en säkerhets- och spårbarhetsgranskning av det
> aktuella läget.

## Översikt

Spring Boot-monolith som serverar både React-SPA:t och REST-API:et (`/api/v1/**`). All
affärslogik ligger i ett service-lager, persistens sker via JPA-repositories, och PII
krypteras fältvis på hot path genom en nativ C/C++-modul via JNA.

## Stack

- **Backend:** Spring Boot 4.1.1, Java 25
- **Frontend:** React + TypeScript + Vite (SPA), byggs i samma image
- **Databas:** PostgreSQL 12 (Docker) / H2 `local`-profil (minne)
- **ORM:** Spring Data JPA (repositories), schema via `schema.sql` (H2) / `infra/seed.sql` (Postgres)
- **Auth:** Opaqua session-tokens (256-bit, SHA-256-hashade server-side), Argon2 för
  handläggarlösenord, BankID-mock som egen service (`BankIdService`)
- **Kryptering:** Nativ `libresurs_crypto.so` (AES-256-GCM + HMAC-SHA256 blind index) via JNA
- **E-post:** `EmailProvider` – console (default) eller SMTP (Mailpit i Docker override)
- **Lagring:** `FileStorageService` – lokal disk (`/app/uploads`, Docker-volym) eller S3,
  wrapper `EncryptedFileStorageService` krypterar filer i vila

## Komponentdiagram

```
Browser (React SPA, servas av Spring Boot)
  └── REST /api/v1/** → Spring Boot 4.1 (Java 25)
        ├── SecurityFilterChain (stateless, session-tokens)
        │     └── SessionTokenAuthenticationFilter → SessionTokenStore (in-memory)
        ├── Controller (tunn)  ─ api/v1/controller/
        │     ├── AuthController        (login/refresh/logout)
        │     ├── ApplicationController (ansökan, listning, detalj)
        │     ├── DecisionController    (beslut av handläggare)
        │     ├── DocumentController    (uppladdning/nedladdning)
        │     └── AuditController       (audit-log)
        ├── Service (@Transactional, affärslogik)  ─ api/v1/service/
        │     ├── ApplicationService          (skapa/list/visa; async scoring efter commit)
        │     ├── ScoringService              (kör alla ScoringCheck)
        │     ├── DecisionService             (manuellt beslut)
        │     ├── CaseWorkerAssignmentService ("first interaction wins")
        │     ├── AuditLogService             (skriver audit_log-tabellen; hashkedja = stub, se audit)
        │     ├── BankIdService (mock)        (utbytbar mot skarp integration)
        │     ├── EmailService + EmailProvider
        │     └── ResursCryptoService (JNA)   (encryptPii/decryptPii/blindIndex/encryptRaw)
        ├── Repository (Spring Data JPA)      ─ repository/
        ├── Entity  ─ entity/ (PiiAttributeConverter krypterar fält, blind-index-listeners)
        └── JNA Bridge → native/libresurs_crypto.so (AES-256-GCM, HMAC-SHA256)
```

## Datamodell

```
companies    (org_number*, company_name*, authorized_signatory*, org_number_index BYTEA UNIQUE)
case_workers (name*, email*, password(Argon2), email_index BYTEA UNIQUE)
applications (company_id, case_worker_id, requested_amount*, purpose*, status,
              decision, decision_reason, scoring_result, financial_data, created_at, updated_at)
documents    (uuid, application_id, filename*, original_filename*, doc_type, uploaded_at)
audit_log    (id UUID, application_id, sequence_number, hash, previous_hash, entry*, timestamp)
             (*) kolumner med `*` krypteras i vila via PiiCodec.
```

## Säkerhet

- **PII i vila:** fältvis AES-256-GCM (`[nonce|key_version|ciphertext|tag]`, base64 i
  textkolumn). Nyckel: 64-byte fil (`32 B AES + 32 B HMAC`), monteras som Docker-secret;
  `make keys` i `infra/` genererar utan att skriva över. Appen vägrar starta med Postgres
  om den nativa modulen saknas (fail-closed).
- **Uppslag utan klartext:** blind index (HMAC-SHA256 av kanoniserat värde) på
  org.nummer/epost – `findByOrgNumber`/`findByEmail` jämför aldrig krypterad text.
- **Sessions:** 15 min idle-expiry (sliding), 1 h absolut cap, enanvänds-refresh-rotation,
  UA+IP-fingerprint, misstänkt stöld → revocation. In-memory: sessioner tappas vid omstart.
- **Lösenord:** Argon2 (password encoder). Inget MD5/BCrypt kvar.
- **Auktorisering:** `@PreAuthorize("hasRole(...)")` per endpoint; COMPANY ser bara egna
  ansökningar (icke-existens läcker inte), HANDLÄGGARE ser allt + tilldelning via
  `CaseWorkerAssignmentService`.

## Audit-log

- Separat `audit_log`-tabell med per-ansökan `sequence_number` och `timestamp`, sökbart
  via `/api/v1/applications/{id}/audit-log`.
- Entries serialiseras som JSON med `action`-diskriminator
  (`APPLICATION_CREATED`, `SCORING_RUN`, `MANUAL_DECISION`, `WORKER_ASSIGNED`).
- **Känd lucka:** hashkedjan (`hash`/`previous_hash`) är **inte implementerad** –
  `AuditLogService.computeHash` returnerar `""`. Manipulationsdetektion saknas därmed.

## Scoring

- `ScoringService` kör alla `ScoringCheck`-beanar (soliditet, likviditet, skuldsättning,
  marginal, kassaflöde, räntetäckning, extra/risk-signaler) och `DecisionEngine`
  sammanställer `APPROVED`/`UNDER_REVIEW`/`REJECTED`.
- Alla trösklar och branschfaktorer i `application.properties` under `resurs.scoring.*`
  (inga magic numbers i kod).
- Scoring körs **asynkront** 20 s efter att ansökan committats
  (`resurs.scoring.delay-ms`, `ApplicationService.scheduleScoringAfterCommit`). Under den
  tiden står status kvar som `PENDING_DOCS` – se audit (T1).

## Kända öppna punkter

Se `docs/backend-audit.md` för fullständig lista. Höjdpunkter:

1. Audit-hashkedjan är en stub (H1)
2. `PiiInitializer` seedar kända demo-credentials i alla non-test-profiler (H2)
3. `DebtRatioCheck` ger OK vid eget kapital = 0 (H3)
4. Ingen beräknad färdigställandetid / SLA för ansökan (T2)
5. E-post går till konstruerade adresser – Company saknar e-postfält (M2)
6. Ingen pagination på list-endpoints (T4)
7. Postgres 12 är EOL; DB-lösen hårdkodat i komposition (L-lista)