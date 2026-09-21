# Pain Points – Resurs Kreditansökan (v1 → status idag)

> Senast uppdaterad: 2026-09-21 · Ursprungligen en lista över v1:s smärtpunkter. Här finns
> status för varje punkt i dagens kod. Se `docs/backend-audit.md` för det aktuella läget
> i detalj.

## Vad som fungerar (i dag)

- **Grundläggande ansökningsflöde:** Företag loggar in (BankID mock) → fyller i finansiella
  nyckeltal → scoring körs → beslut returneras (asynkront, ~20 s)
- **Scoringbeslut:** APPROVED / UNDER_REVIEW / REJECTED med förklaring
- **Dokumentuppladdning:** Filer laddas upp, sparas på persistent lagring och krypteras i
  vila (innehållet parsas dock inte)
- **Handläggargränssnitt:** Karin kan se UNDER_REVIEW-ansökningar och fatta beslut
  (tilldelning "first interaction wins")
- **Audit log:** Händelser loggas i en separat, sökbar `audit_log`-tabell
- **Säkerhet:** PII krypterat i vila (AES-256-GCM via JNA), Argon2-lösenord,
  session-tokens med fingerprint-bindning och rotation

## Smärtpunkter v1 → status

### Samtida ansökningar (✅ åtgärdat)
**v1:** Ingen locking – dubbla company-INSERT kunde kasta UNIQUE-exception och lämna
halvfärdiga rader.
**Nu:** Submit är `@Transactional` och company-uppslag sker via unikt blind index.
⚠️ Kvar sticker ut: samtidiga audit-append kan få dubbla sekvensnummer (ingen
UNIQUE-begränsning, se audit M5).

### PDF-parsning saknas (⚠️ kvar, utanför scope)
Systemet accepterar PDF-filer men läser dem aldrig. Scoring baseras på manuellt inmatade
siffror; årsredovisningens innehåll verifieras aldrig automatiskt. Utanför v2-scope –
uppladdade dokument granskas manuellt av handläggare vid gränsfall.

### Audit log inte sökbar (✅ åtgärdat)
**v1:** JSON-blob på applications-raden → full table scan.
**Nu:** Separat `audit_log`-tabell med sekvensnummer och tidsstämplar, hämtas via
`/api/v1/applications/{id}/audit-log`. ⚠️ Hashkedjan för manipulationsdetektion är dock
**inte implementerad** (se audit H1).

### /tmp rensas (✅ åtgärdat)
Filer sparas nu via `FileStorageService` på persistent volym (`/app/uploads` lokalt/S3)
och krypteras i vila med `EncryptedFileStorageService`. Inga 404-filer efter omstart.

### Magic numbers i scoring (✅ åtgärdat)
Trösklar och branschfaktorer centraliserade i `ScoringConfig` (`resurs.scoring.*`).
⚠️ Ny hittad logikbugg: noll eget kapital ger OK i skuldsättningsgraden (audit H3).

### Session check copy-paste (✅ åtgärdat)
Ersatt av Spring Security filter chain + `@PreAuthorize`. Sessioner är opaqua
server-sidiga tokens med rotation, expiry och stölddetektering.

### Ingen e-postnotifiering (✅ åtgärdat, med förbehåll)
`EmailService` skickar vid ansökan, statusändring och beslut (console/SMTP).
⚠️ Company saknar e-postfält – mottagaradress konstrueras ur firmatecknarens namn
(se audit M2).

### SQL injection (✅ åtgärdat)
Handläggar-login använder parameteriserade JPA-uppslag (blind index) + Argon2.

---

## Kvarvarande smärtor (2026-09-21)

1. Ingen beräknad färdigställandetid / SLA för ansökan (audit T2) – kunden kan inte
   uppskatta när beslut kommer.
2. Statusreflektion: under den ~20 s långa asynkrona scoringen står status som
   `PENDING_DOCS` ("väntar på dokument") trots att scoring pågår (audit T1).
3. Ingen pagination på list-endpoints (audit T4).
4. `PiiInitializer` seedar kända demo-credentials i alla non-test-profiler (audit H2).
5. Case worker-tilldelning verkställs inte vid dokumentåtkomst (audit M3).