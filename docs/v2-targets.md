# v2 Refaktorering – status

> Senast uppdaterad: 2026-09-21 · Detta var ursprungligen målbilden för v2-refaktoreringen.
> V2 är i stora drag **genomförd** (se `docs/architecture.md` för nuläget); tabellen nedan
> visar mål → faktiskt status. Kvarvarande luckor är listade i `docs/backend-audit.md`.

## Teknisk stack – status

| Komponent | v1 (åtgärdat) | Status v2-idag |
|-----------|---------------|----------------|
| Spring Boot | 2.7.18 | 4.1.1 ✅ |
| Java | 11 | 25 ✅ |
| Databasåtkomst | JdbcTemplate i controllers | JPA/Hibernate, repository-pattern ✅ |
| Frontend | Thymeleaf + Bootstrap 3 + jQuery | React 18 + TypeScript + Vite ✅ |
| Auth | BankID mock (hårdkodad if-sats) | `BankIdService` som egen service (mockad, utbytbar) ✅ |
| Lösenord | MD5 | Argon2 via Spring Security ✅ |
| Audit log | JSON-blob i TEXT-kolumn | Separat `audit_log`-tabell med sekvensnummer ✅ · **hashkedja saknas** ⚠️ |
| Företagsvalidering | Mockad utan felhantering | Delvis – se notering * |
| PII | Klartext | Krypterat på hot path (AES-256-GCM, C/C++ via JNA) ✅ |
| Transaktioner | Ingen | `@Transactional` på submit/decision ✅ · scoring/dokument ⚠️ |
| Session-check | Copy-paste i varje metod | Spring Security filter chain ✅ |

\* En separat `CompanyValidationService` är inte byggd; företagsmatchning sker via
blind index på org.nummer i `CompanyRepository`. Mock av extern validering finns inte som
egen tjänst.

## Arkitektur (aktuell)

```
Browser (React SPA)
  └── REST /api/v1/** → Spring Boot 4.1 (Java 25)
        ├── SecurityFilterChain (stateless, session-tokens, @PreAuthorize)
        ├── Controller (tunn)            → api/v1/controller/
        ├── Service (@Transactional)     → api/v1/service/
        │     ├── ScoringService (konfigurerbara trösklar)
        │     ├── AuditLogService (audit_log-tabell; hashkedja = stub, se audit)
        │     ├── BankIdService (mockad klient, utbytbar)
        │     └── EmailService (e-post via EmailProvider)
        ├── Repository (JPA, Spring Data)
        └── JNA Bridge → native/libresurs_crypto.so (AES-256-GCM, HMAC blind index)
```

## Delmål – status

| # | Delmål | Status |
|---|--------|--------|
| 1 | Extrahera ScoringService med konfigurerbara trösklar | ✅ `ScoringService` + `ScoringConfig` (`resurs.scoring.*`) + enhetstester |
| 2 | Separat audit_log-tabell | ✅ `audit_log`-tabell med `sequence_number`; ⚠️ hashkedjan ej implementerad |
| 3 | Spring Security | ✅ Filterchain, Argon2, role-baserad åtkomst |
| 4 | JNA-integration (native/) | ✅ `libresurs_crypto.so` (AES-256-GCM + HMAC-SHA256) byggd och testad · ❌ **audit-signering ej byggd** (se native/README.md) |
| 5 | BankID-mock som egen service | ✅ `BankIdService` / `MockBankIdService` (whitelist, utbytbar) |
| 6 | @Transactional + optimistic locking | ⚠️ Submit/decision omslutna av transaktioner; scoring är **inte** `@Transactional`; ingen optimistic locking |
| 7 | E-postnotifiering (Spring Mail) | ✅ `EmailService` + Smtp/Console-provider; ⚠️ Company saknar e-postfält (se audit M2) |
| 8 | Extern företagsvalidering som egen service | ❌ ej byggd som egen tjänst (delvis täckt av blind-index-uppslag) |