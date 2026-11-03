# Resurs Kreditansökan

B2B-kreditansökningsportal för Resurs Bank. Företag ansöker om kredit, laddar upp årsredovisning, och får ett kreditbeslut baserat på finansiella nyckeltal.

Ansökan är **spårbar**: statusflödet (`PENDING_DOCS → SCORING_IN_PROGRESS → UNDER_REVIEW → APPROVED/REJECTED`) och ett uppskattat beslutstidpunkt (`estimatedResolutionAt`, driven av `resurs.sla.*` i `application.properties`) exponeras i API:t.

## Snabbstart

Projektet byggs med en **enhetlig Makefile** som orkestrerar React-frontenden (Vite/npm), Spring Boot-backenden (Maven/`./mvnw`) och den framtida C++-modulen (CMake). Alla artefakter kopieras till en gemensam `target/`-katalog.

> Alla kommandon körs från **repo-roten**.

### Bygg allt (frontend + backend)

```bash
make build
```

Resultatet läggs i `target/`:

```
target/
  frontend/                     ← byggd React-SPA
  resurs-portal-1.0-SNAPSHOT.jar ← Spring Boot-jar (tjänar SPA:t)
```

### Starta i dev (HMR + lokalt H2)

```bash
make dev
```

- Vite dev-server med hot reload: [http://localhost:5173](http://localhost:5173)
- Spring Boot med `local`-profil (in-memory H2) och Vite-proxy för `/api`: [http://localhost:8083](http://localhost:8083)

Data i H2 nollställs vid omstart. `Ctrl+C` stoppar båda processerna.

### Med Docker (PostgreSQL)

```bash
cd infra && docker compose up -d
```

Det bygger hela projektet i en enda image (root-`Dockerfile` anropar `make package`) och startar PostgreSQL + appen. Appen servar React-SPA:t direkt.

> Öppna [http://localhost:8083](http://localhost:8083)

Se `DRIFT.md` för hur du uppdaterar `infra/docker-compose.yml` vid deployment till stage/prod. Använder du Docker för lokal utveckling kan du hålla koll på applikationsloggen med `docker compose logs -f --tail=100`.

### Testa e-post med Mailpit

`infra/docker-compose.override.yml` startar **Mailpit** tillsammans med appen när du kör Docker Compose. Appen skickar e-post via SMTP till Mailpit istället för en riktig mailserver:

|          | URL / port                              |
| -------- | --------------------------------------- |
| Web UI   | [http://localhost:8025](http://localhost:8025) |
| SMTP     | `localhost:1025`                        |

- Alla utskick (varsel om mottagen ansökan, statusuppdateringar och beslut) visas i Web UI:t.
- Eftersom seed-företagen saknar e-postadress härleds mottagaradressen från firmatecknarens namn, t.ex. `anders_karlsson@example.com`. Har adressen redan `@` används den som den är.
- I lokal dev (`make dev`, H2) finns ingen Mailpit — där används `resurs.email.provider=console` och utskicken loggas bara till konsolen.
- Vill du stänga av SMTP (t.ex. för att köra endast `db` + `app`) sätter du `RESURS_EMAIL_PROVIDER=console` som miljövariabel på `app`-tjänsten.

### Testa

```bash
make test
```

Kör frontend-lint och backend-tester.

### Övriga targets

| Target                | Beskrivning                               |
| --------------------- | ----------------------------------------- |
| `make build-frontend` | Bygg bara React-frontenden                |
| `make build-backend`  | Bygg bara Spring Boot-jaren               |
| `make clean`          | Ta bort alla byggartefakter               |
| `make package`        | Alias för `build` (används av Dockerfile) |

> C++-modulen (`native/`) är inte på denna branch ännu. När `CMakeLists.txt` läggs till aktiverar du den genom att kommentera in `build-native` i `Makefile`.

Öppna [http://localhost:8083](http://localhost:8083)

### Swagger (endast lokalt)

När appen körs med `-Plocal` finns Swagger UI tillgängligt på [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html). Swagger är endast inkluderat i lokala byggen och exkluderas från paketade artifact (`mvnw package`).

### Testinloggningar

| Roll                           | Uppgifter                         |
| ------------------------------ | --------------------------------- |
| Företag (Malmö Fastigheter AB) | Org.nr: `556000-1234`             |
| Företag (Göteborg Handel AB)   | Org.nr: `556000-5678`             |
| Handläggare                    | `karin@resurs.se` / `password123` |

## Mappstruktur

```
Makefile                     ← enhetligt byggsystem (clean/build/test/dev)
Dockerfile                   ← bygger hela projektet via `make package`

frontend/                    ← React + TypeScript + Vite (SPA)
  src/pages/                 ← Login, Backoffice, Documents, m.m.

backend/ResursPortal/        ← Spring Boot 3.5 Maven-projekt
  src/main/java/se/comerit/resurs/
    ResursPortalApplication.java
    api/v1/controller/       ← API endpoints
    audit/                   ← Auditlog relaterade klasser
    config/                  ← Configurations relaterade klasser
    entity/                  ← JPA entities
    exception/               ← Exception handlers
    rating/                  ← Kreditrating relaterade klasser
    repository/              ← JPA repositories
    security/                ← Säkerhetskonfiguration (JWT, CORS, m.m.)
  src/main/resources/
    application.properties   ← `v2`-profil aktiv som default
    application-local.properties

infra/
  docker-compose.yml         ← PostgreSQL + Spring Boot
  seed.sql                   ← schema + seed-data

native/
  README.md                  ← v2 C/C++ moduler (PII-kryptering, audit-signering)

```
