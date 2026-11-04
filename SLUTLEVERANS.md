# Resurs Slutleverans


Allt som slutleveransen kräver av repot `resurs-team2`, samlat på ett ställe. Samma innehåll finns i `README.md` och i mappen `docs/` på grenen `fix/documentation`.

## Installation, körning och testning (README)

Allt byggs och körs med `make` från repo-roten. Ett kommando bygger frontend (Vite), backend (Spring Boot 4.1, Java 25) och native-modulerna (CMake).

**Krav**

| Verktyg | Version | Behövs för |
| --- | --- | --- |
| Node.js + npm | 24 | frontend |
| JDK | 25 (Temurin) | backend; `./mvnw` laddar ner Maven själv |
| cmake, make, gcc, g++, libssl-dev | – | native-modulerna |
| Docker + Docker Compose | – | PostgreSQL och Mailpit |
| openssl | – | `cd infra && make keys` |

**Köra**

| Läge | Kommando | Adress |
| --- | --- | --- |
| Dev (H2 i minnet, hot reload) | `make dev` | http://localhost:5173 |
| Docker (PostgreSQL + Mailpit) | `cd infra`, `make keys`, `docker compose up -d --build` | http://localhost:8083, e-post http://localhost:8025 |
| Bara bygga | `make build` | resultat i `target/` |

`make keys` skapar krypterings- och audit-nyckeln i `infra/secrets/`. Utan dem startar inte compose.

**Testa**

| Kommando | Vad som körs |
| --- | --- |
| `make test` | allt nedan utom Playwright och `test-encryption` |
| `make test_frontend` | lint, vitest, build |
| `make test_backend` | `./mvnw test` mot H2 |
| `make test_native` | `ctest` för crypto- och audit-modulen |
| `make test-audit` | audit-IT:erna mot riktiga `libresurs_audit.so` |
| `make test-encryption` | `RealEncryptionIT` mot riktiga `libresurs_crypto.so` |
| `make playwrite-e2e` | Playwright-testerna i `frontend/e2e/` |

**Testinloggningar**

| Roll | Uppgifter |
| --- | --- |
| Företag (Malmö Fastigheter AB) | org.nr `556000-1234`, personnummer `20000101-1111` |
| Företag (Göteborg Handel AB) | org.nr `556000-5678` (får inte signera) |
| Handläggare | `karin@resurs.se` / `password123` |

Personnumret krävs från och med #226, som ligger på `develop`.

## Beslutslogg

v2 ersatte en Spring Boot 2.7-monolit (Java 11, MD5, PII i klartext, audit som JSON-blob) med en lagerindelad app, kryptering och en signerad audit-logg. Besluten är grupperade per område. Datum = commit-datum.

### Plattform och arkitektur

| Datum | Beslut | Motivering | Källa |
| --- | --- | --- | --- |
| 2026-08-26 | Spring Boot 3 och Java 21 | v1-stacken (Boot 2.7, Java 11) saknar support | #33 |
| 2026-09-07 | Vidare till Spring Boot 4 och Java 25 (LTS) | Senaste LTS, samma version i CI och Docker | #102 |
| 2026-08-26 | H2 för tester och dev, PostgreSQL i drift | Snabba tester och `make dev` utan Docker | #39 |
| 2026-08-27 | JPA med repository-lager i stället för JdbcTemplate | Tar bort SQL-strängar och SQL-injektionen | #41 |
| 2026-09-03 | En gemensam `Makefile` i repo-roten | Ett kommando för bygge, test och körning | #97 |
| 2026-09-07 | Spring Boot servar React-SPA:n | Bara tjänsten `app` får en publik adress | #100 |
| 2026-09-29 | `@Transactional` på service-lagret | Inga halvfärdiga rader vid fel | #185, #186 |
| 2026-09-28 | Paginering av ansökningslistan, 20 per sida | v1 hämtade allt utan gräns | #167 |

### Säkerhet och inloggning

| Datum | Beslut | Motivering | Källa |
| --- | --- | --- | --- |
| 2026-08-31 | Session-token via Spring Security filter chain | Ersätter kopierad sessionskontroll | #51, #54 |
| 2026-09-21 | Sessionstoken i httpOnly-cookies | JavaScript kommer inte åt token | #168 |
| 2026-09-08 | Argon2 för handläggarlösenord i stället för MD5 | MD5 är brutet och saknade salt | #115 |
| 2026-09-30 | Rate limiting på inloggning, per e-post och per IP | Försvårar lösenordsgissning | #193 |
| 2026-10-07 | BankID som utbytbar mock (`resurs.bankid.mode`) | Skarp BankID ligger utanför projektet | #200 |
| 2026-10-07 | Ansökan måste BankID-signeras innan den sparas | Inloggning ensam räcker inte | #200 |
| 2026-09-21 | Ingen PII i loggar och API-svar | Mindre exponering av personuppgifter | #178, #196 |

### Kryptering och audit (native-moduler)

| Datum | Beslut | Motivering | Källa |
| --- | --- | --- | --- |
| 2026-08-27 | Kryptering och audit-signering i C/C++ via JNA | Krav i v2-specen | #11 |
| 2026-09-09 | AES-256-GCM för PII, HMAC-SHA256 som blind index | Krypterad lagring men sökbart org.nr | #122, #130 |
| 2026-09-17 | Uppladdade dokument krypteras i permanent lagring | v1 sparade okrypterat i `/tmp` | #161 |
| 2026-09-14 | Audit-loggen blir en egen tabell | v1-bloben gick inte att söka i | #150 |
| 2026-09-30 | Hashkedja med Ed25519-signatur över audit-loggen | Manipulation av en post eller ordningen ska upptäckas | #177, #190 |
| 2026-10-12 | Audit-loggen är append-only i databasen | Skydd mot ändring och radering | #213, #214 |

Saknas native-biblioteken faller appen tillbaka på dummy-implementationer med en varning i loggen, så att den går att starta utan C++-verktyg. Docker-imagen bygger alltid biblioteken.

### Kreditbedömning och kommunikation

| Datum | Beslut | Motivering | Källa |
| --- | --- | --- | --- |
| 2026-09-01 | Scoring i egen service, en check per nyckeltal | v1 hade tre olika soliditetströsklar i en controller | #60, #64 |
| 2026-09-01 | Trösklarna i `application.properties` (`resurs.scoring.*`) | En plats att ändra affärsregler | #64 |
| 2026-09-16 | E-postnotiser; Mailpit lokalt, konsol i dev | Kunden får besked utan att logga in | #151 |
| 2026-09-28 | Uppskattad beslutstidpunkt (ETA) till kunden | Spårbarhet | #179, #208 |

### Beslut efter specialisternas feedforward

| Datum | Beslut | Motivering | Källa |
| --- | --- | --- | --- |
| 2026-10-28 | Kunden ser inte vilken handläggare som har ansökan | Exponera inte handläggaren | #223, #224 |
| 2026-11-03 | Personnummer vid inloggning och mockad kontroll av signeringsbehörighet | Verifiera vem som får signera | #226 (på `develop`) |
| 2026-11-03 | Ändring av kreditvärderingsregler och logg över dem ligger utanför projektet | Reglerna ligger i en fil på servern; ett DevOps-ärende | – |

## Teststatus

CI kör frontend-, backend-, native- och krypteringstesterna på varje PR mot `main` och `develop`. Fyra sviter körs bara lokalt. Här beskrivs vad som finns; aktuellt resultat syns som grön bock eller rött X på respektive PR.

| Svit | Kommando | Omfattning | I CI |
| --- | --- | --- | --- |
| Frontend: lint, vitest, build | `make test_frontend` | 2 vitest-filer | Ja |
| Backend mot H2 | `make test_backend` | 30 testklasser | Ja |
| Native crypto + audit | `make test_native` | 12 testprogram | Ja |
| Riktig kryptering | `make test-encryption` | `RealEncryptionIT` | Ja |
| Riktig audit-signering | `make test-audit` | `RealAuditSigningIT`, `SeedAuditSigningIT` | Nej |
| Playwright end-to-end | `make playwrite-e2e` | 3 spec-filer, 19 tester | Nej |
| Append-only-trigger i PostgreSQL | `./mvnw -Dtest=BlockModificationTriggerIT test` (kräver Docker) | 1 klass | Nej |
| Native med ASAN/UBSan, täckning | `cd native && make asan` / `make coverage` | samma 12 program | Nej |

**Vad testerna täcker**

- **Backend:** 9 integrationstester för hela HTTP-flödet (inloggning, ansökan, signering, livscykel, beslut, dokument, audit-logg). 11 servicetester, scoring-trösklar, 4 repositorytester och 4 säkerhetstester.
- **Native:** sandbox-test och ABI-test för init, roundtrip, bounds, buffers och HMAC (crypto) samt init, chain, verify och tamper (audit).
- **Playwright:** främst handläggarflödet: kön, detaljsidan, finansiella uppgifter, beslut, dokument, händelselogg och utloggning.

**Luckor**

- Fyra sviter körs inte i CI och måste köras lokalt före merge.
- Frontenden har bara 2 enhetstestfiler.
- `BlockModificationTriggerIT` har inget make-target.
- Personnummer-kontrollen från #226 saknar tester.

## Kända brister och avgränsningar

Den viktigaste bristen: kontrollen av vem som får signera görs bara i frontend, så ett direktanrop till API:t kommer förbi den.

**Avgränsningar (medvetet utanför projektet)**

| Område | Hur det är löst nu |
| --- | --- |
| BankID | Mock för inloggning och signering. Byts med `resurs.bankid.mode=real` plus en egen implementation; utan den startar inte appen |
| Behörighet att signera | Mock i frontend: bara `556000-1234` + `20000101-1111` får signera |
| Företagsvalidering (registrering, moms, F-skatt) | Finns inte; målet i v2-specen byggdes inte |
| PDF-tolkning av årsredovisningen | PDF:en sparas krypterad men läses inte. Scoringen bygger på ifyllda nyckeltal; handläggaren granskar dokumentet |
| Ändring av kreditvärderingsregler | Trösklarna ligger i `application.properties`. Admin-panel och ändringslogg är ett DevOps-ärende |
| Produktions-SMTP | Mailpit lokalt, konsol i `make dev`; ingen riktig mailserver |

**Kända brister**

- **Signeringsbehörigheten kontrolleras bara i frontend.** #226 ändrade inga backend-filer. Backend kräver fortfarande en BankID-signatur och firmatecknaren i databasen.
- **Företagsinloggningen är en vitlista** med två org.nr, eftersom BankID är mockat.
- **Databaslösenordet står i klartext** i `application.properties` och `docker-compose.yml`. Nycklarna är däremot Docker secrets.
- **Företag kommer åt audit-loggen** för sina egna ansökningar. Om de ska ha det är inte beslutat.
- **Utan native-biblioteken** krypteras inte PII, och audit-kedjan signeras med en känd testnyckel. Appen varnar men startar. Docker-imagen bygger alltid biblioteken.
- **Seed-data skapas vid varje uppstart**, även i drift (`PiiInitializer`).
- **Nyckelbyte kräver omkryptering som saknas.** Ny AES-nyckel gör befintlig PII oläsbar; ny HMAC-nyckel kräver att alla blind-index räknas om.
- **En ansökan kräver att företaget redan finns** i databasen.

Löst sedan v1: SQL-injektion, MD5-lösenord, PII i klartext, inga transaktioner, audit-logg som JSON-blob, utspridda scoring-trösklar, filer i `/tmp` och ingen paginering.

## Fallback för demon

Fungerar inte live-demon visar vi PowerPoint-presentationen, som går igenom hela flödet med skärmbilder.

**1. Presentationen.** Länk: **https://docs.google.com/presentation/d/1l1lOs247AJ6xOEGTwgcIDPgxOILQgSRezKCE8HDgU-c/edit?slide=id.p#slide=id.p** Ha den öppen innan demon börjar.

**2. Om bara drift-miljön ligger nere.**

| Miljö | Adress |
| --- | --- |
| stage (`develop`) | https://resurs-team2-dev.team.chas-challenge.comerit.se |
| prod (`main`) | https://resurs-team2.team.chas-challenge.comerit.se |
| lokalt med Docker | http://localhost:8083 (e-post: http://localhost:8025) |
| lokalt med `make dev` | http://localhost:5173 (ingen Mailpit) |

Starta den lokala stacken innan redovisningen, eftersom första bygget tar flera minuter.

**3. Demo-flöde**

1. Logga in som företag: `556000-1234` + `20000101-1111`.
2. Fyll i kreditansökan, ladda upp en PDF och signera med (mock-)BankID.
3. Visa statussidan: status och uppskattad beslutstidpunkt. Scoringen väntar 20 sekunder med flit.
4. Logga ut och logga in som handläggare: `karin@resurs.se` / `password123`.
5. Öppna ansökan: nyckeltal, scoring, dokument och händelselogg.
6. Fatta beslut.
7. Visa beslutsmejlet i Mailpit (bara med Docker).
8. &#32;Berätta om Kreditbedömning
9. Berätta om  kryptering och audit
10. Berätta om scoring och tekniska beslut

**Checklista före redovisningen**

- [ ] Presentationen är öppen och fungerar offline
- [ ] Stage eller prod svarar, eller den lokala stacken är igång
- [ ] Testinloggningarna fungerar

## Bearbetning av specialisternas feedforward

Tre av fem punkter är åtgärdade i koden, en är avgränsad och en behöver förtydligas. Vi skrev ner varje punkt i en åtgärdsplan och beslutade om den skulle åtgärdas, avgränsas eller förtydligas. Åtgärderna blev egna PR:er mot `develop`.

| Feedforward | Beslut | Vad vi gjorde | Källa |
| --- | --- | --- | --- |
| Vem får ändra kreditvärderingen? Logga ändringar | Avgränsat | Trösklarna ligger i en fil på servern. Admin-panel och ändringslogg är ett DevOps-ärende; ett första steg vore att spara en hash av filen i databasen | – |
| Vem får signera? Verifiera behörigheten | Åtgärdat med mock | Personnummer krävs vid inloggning; en mockad kontroll avgör om personen får signera för org.nr. Kontrollen görs bara i frontend | #226 (på `develop`) |
| Exponera inte handläggaren för kunden | Åtgärdat | Handläggarens namn är borttaget ur API-svaren och ur kundens statussida | #223, #224 |
| Tydligare skillnad mellan handläggarens och kundens dashboard | Åtgärdat | Frontenden är uppdaterad; inloggningssidan ändrades i #224 | #224 |
| Vad händer om fel person loggar in? | Behöver förtydligas | Ett företag ser bara sina egna ansökningar. Med #226 kan en obehörig person logga in men inte signera | – |

- [ ] Stäm av med specialisterna vad de menade med punkten om fel person som loggar in
