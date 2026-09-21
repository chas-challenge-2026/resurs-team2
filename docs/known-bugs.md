# Kända buggar och säkerhetsproblem – v1 → status idag

> Senast uppdaterad: 2026-09-21 · Listan beskriver de kända problemen från v1 samt deras
> status i dagens kod. **Kvarvarande** problem (med nuvarande filreferenser) finns nedan
> under "Aktuella öppna punkter" och utförligare i `docs/backend-audit.md`.

## Säkerhetsproblem – v1 (status)

### 1. SQL Injection – CaseWorker-login
- **v1:** `"SELECT * FROM case_workers WHERE email = '" + email + "' AND password = '" + md5 + "'"`
- **Status:** ✅ Åtgärdat — login använder parameteriserade JPA-uppslag (`findByEmail`
  via blind index på epost) + Argon2-verifiering i `AuthService`.

### 2. PII i klartext
- **v1:** Firmanamn, organisationsnummer och firmatecknare lagrades okrypterat i PostgreSQL.
- **Status:** ✅ Åtgärdat — PII-fält krypteras i vila med AES-256-GCM via nativ
  `libresurs_crypto.so` (JNA), uppslag sker via HMAC-baserade blind index
  (`org_number_index`, `email_index`). Kryptering krävs och är fail-closed för Postgres.
- ⚠️ **Kvar i någon mån:** se audit M1 (PII i loggar via `CompanyNotFoundException` +
  console-epost-provider).

### 3. MD5-lösenord
- **Status:** ✅ Åtgärdat och uppgraderat — MD5 borttaget; lösenord lagras/verifieras med
  **Argon2** (`Argon2PasswordEncoder`).

### 4. BankID-mock som hårdkodad if-sats
- **v1:** `if (orgNumber.equals("556000-1234") || orgNumber.equals("556000-5678"))`
- **Status:** ⚠️ Delvis åtgärdat — mock lyft till egen service (`BankIdService` /
  `MockBankIdService`) med tydligt gränssnitt, utbytbar mot skarp integration. Mocken är
  fortfarande en whitelist av två org.nummer (accepterat scope för tävlingen).

## Dataintegritet – v1 (status)

### 5. Ingen transaktion vid ansökningsskapande
- **v1:** Tre separata INSERT-satser (company, application, audit_log) utan BEGIN/COMMIT.
- **Status:** ✅ Åtgärdat för submit (`ApplicationService.submitApplication` är
  `@Transactional`). ⚠️ **Kvar:** `ScoringService.scoreApplication` (async) och
  `DocumentService.uploadDocument` är inte fullt transaktionella (se audit M4/M6).

### 6. Audit log ej sökbar/indexerad
- **v1:** JSON-blob i TEXT-kolumn på applications-raden, uppdaterad via stränghackning.
- **Status:** ✅ Ersatt — separat `audit_log`-tabell med `sequence_number`, `timestamp`,
  query-bar `entry` (krypterad i vila). ⚠️ **Kvar:** hashkedjan
  (`hash`/`previous_hash`) beräknas **inte** – `AuditLogService.computeHash` returnerar
  `""` (se audit H1). Ingen UNIQUE-begränsning på `(application_id, sequence_number)`.

## Affärslogik – v1 (status)

### 7. Inkonsistenta soliditetströsklar
- **v1:** 0.20 / 0.25 / 0.30 (+ 0.15) på olika ställen i 800+ raders controller-metod.
- **Status:** ✅ Åtgärdat — alla trösklar centraliserade i `ScoringConfig`
  (`resurs.scoring.*` i application.properties). ⚠️ **Ny typ av bugg:** `DebtRatioCheck`
  ger `OK` vid eget kapital = 0 (se audit H3).

### 8. PDF parsas inte
- **Fil idag:** `DocumentController.java` / `DocumentService.java`
- **Status:** ⚠️ Kvar – filer verifieras bara som `.pdf`-namn/MIME, innehållet läses
  aldrig. Utanför v2-scope: uppladdade dokument granskas manuellt av handläggare.

## Drift – v1 (status)

### 9. Filer i /tmp (åtgärdat)
- **Status:** ✅ Åtgärdat — filer lagras via `FileStorageService` (lokal disk under
  `/app/uploads` med Docker-volym, eller S3) och krypteras i vila via
  `EncryptedFileStorageService`.

### 10. Ingen pagination
- **v1:** `BackofficeController` hämtade ALLA UNDER_REVIEW-ansökningar utan LIMIT.
- **Status:** ⚠️ Kvar – `ApplicationService.listApplications` returnerar hela listan
  (`findByStatus`); paginerade queries finns i `ApplicationRepository` men är inte
  inkopplade (se audit T4).

---

## Aktuella öppna punkter (2026-09-21)

Prioriterat, se `docs/backend-audit.md` för detaljer:

- **H1** Audit-hashkedja ej implementerad (stub) – ingen manipulationsdetektion.
- **H2** `PiiInitializer` (alla non-test-profiler) seedar demo-case-worker
  `karin@resurs.se` / `password123` – kända credentials i deployade miljöer.
- **H3** `DebtRatioCheck` – eget kapital = 0 ger "OK" istället för avslag.
- **M2** Company saknar e-postfält; notifieringar går till konstruerade adresser.
- **T2** Ingen beräknad färdigställandetid / SLA för ansökan.
- **T4** Ingen pagination på list-endpoints.