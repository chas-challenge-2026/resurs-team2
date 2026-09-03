# Resurs Kreditansökan

B2B-kreditansökningsportal för Resurs Bank. Företag ansöker om kredit, laddar upp årsredovisning, och får ett kreditbeslut baserat på finansiella nyckeltal.

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
    api/v1/controller/       ← ny REST-API (SPA använder /api/v1/...)
    controller/              ← legacy Thymeleaf (avstängd i `v2`-profilen)
    config/                  ← JnaConfig, SpaFallbackController
    security/                ← SecurityConfig (api-/v2-kedjor)
  src/main/resources/
    application.properties   ← `v2`-profil aktiv som default
    application-local.properties

infra/
  docker-compose.yml         ← PostgreSQL + Spring Boot
  seed.sql                   ← schema + seed-data

native/
  README.md                  ← v2 C/C++ moduler (PII-kryptering, audit-signering)

docs/
  architecture.md
  known-bugs.md
  README-pain-points.md
  v2-targets.md
```

## Avsiktliga anti-patterns (pedagogiska)

Detta är en **v1 spaghetti-kodbas** avsedd för studenter att refaktorera till v2.

Se `docs/known-bugs.md` för fullständig lista. Highlights:

1. BankID mock som hårdkodad if-sats
2. 800+ raders scoring-metod inline i controller
3. ~~SQL injection i handläggare-login~~
4. Audit log som JSON-blob (ingen separat tabell)
5. ~~JdbcTemplate direkt i varje controller~~
6. PDF sparas men parsas aldrig
7. PII i klartext
8. Ingen transaktion vid ansökningsskapande
9. Session-check copy-pastad i varje metod

## Vad ska ni bygga

Se `docs/v2-targets.md`.
