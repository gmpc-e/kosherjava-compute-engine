# KosherJava Compute Engine

A modular Kotlin/JVM REST API engine wrapping the [KosherJava Zmanim](https://github.com/KosherJava/zmanim) library.
Computes halachic times of day (zmanim) for any location and date, with support for multiple halachic opinions (shitot) via configurable profiles.

**Tech stack:** Kotlin 2.0.21 · JVM 17 · Ktor 2.3.12 (Netty) · Jackson · KosherJava Zmanim 2.5.0 · Gradle (Kotlin DSL)

---

## Quick Start

```bash
./gradlew clean build        # compile + run tests
./gradlew :rest-api:run      # start server on port 8080
```

The server listens on `http://localhost:8080`. Override with `PORT` env var.

---

## Modules

| Module | Purpose |
|---|---|
| **`core-engine`** | Core computation logic: wraps KosherJava calendars, custom internal methods (Sfaradi/Or HaChaim shita), Shabbat times computer |
| **`profiles`** | Profile data model, JSON storage (`src/main/resources/profiles/`), validation |
| **`rest-api`** | Ktor HTTP server exposing all functionality as REST endpoints |

### Dependency graph

```
rest-api  →  core-engine  →  profiles
         →  profiles
```

---

## REST API Endpoints

### Health & Discovery

| Method | Path | Description |
|---|---|---|
| GET | `/health` | Health check (returns engine + profiles version) |
| GET | `/methods` | List all available KosherJava external methods (~100+) |
| GET | `/internal-methods` | List custom internal methods (Sfaradi/Or HaChaim shita) |

### Single Zman Compute

| Method | Path | Description |
|---|---|---|
| GET | `/compute/{method}` | Compute by enum: `SUNRISE`, `SUNSET`, `SEA_LEVEL_SUNSET` |
| GET | `/compute/by-name?method=...` | Compute by exact KosherJava method name (e.g., `getSunrise`) |
| POST | `/internal-methods/compute` | Compute a single internal method by ID |

**Common query params:** `date` (YYYY-MM-DD, default: today), `lat`, `lon`, `elev` (default: 0), `tz` (IANA, default: system)

### Profiles

| Method | Path | Description |
|---|---|---|
| GET | `/profiles` | List stored profiles (key, displayName, labels) |
| GET | `/profiles/{key}` | Get full profile JSON |
| GET | `/profiles/{key}/compute?...` | Compute all times in a stored profile |
| POST | `/profiles/compute?...` | Compute an ad-hoc profile (POST body = profile JSON) |
| POST | `/profiles/validate` | Validate a profile JSON (no persistence) |

---

## Profiles

Profiles are JSON files stored in `profiles/src/main/resources/profiles/`. Each profile defines an ordered list of zmanim to compute, with each entry targeting either:

- **`EXTERNAL_NAME`** — a KosherJava method (e.g., `getSunrise`, `getMinchaGedola`)
- **`INTERNAL`** — a custom computation (e.g., `SFARADI_OR_HACHAIM.SOF_ZMAN_SHMA_GRA_ASTRO`)

Active profiles are listed in `profiles/src/main/resources/profiles/index.json`.

### Current profiles

| Key | Name | Description |
|---|---|---|
| `chazon-shamaim` | חזון שמיים | Hazon Shamaim |
| `or-hachaim-sefardi` | אור החיים – ספרדי | Or HaChaim Sefardi — mixed external + internal methods |

### Profile JSON structure

```json
{
  "version": 1,
  "key": "or-hachaim-sefardi",
  "displayName": "אור החיים – ספרדי",
  "labels": { "he": "...", "en": "..." },
  "times": [
    {
      "id": "sunriseSeaLevel",
      "label": { "he": "זריחה מישורית", "en": "Sea-level Sunrise" },
      "target": { "kind": "EXTERNAL_NAME", "externalMethod": "getSeaLevelSunrise" }
    },
    {
      "id": "szsGra",
      "label": { "he": "סו\"ז קריאת שמע גר\"א", "en": "Sof Zman Shema (GRA)" },
      "target": { "kind": "INTERNAL", "internalMethodId": "SFARADI_OR_HACHAIM.SOF_ZMAN_SHMA_GRA_ASTRO" }
    }
  ]
}
```

---

## Shabbat Times

When computing a profile, the engine automatically appends Shabbat-related times based on the requested date:

| Day | Item appended | ID | Description |
|---|---|---|---|
| **Friday** | הדלקת נרות | `candleLighting` | Candle lighting (18 min before sunset, configurable) |
| **Saturday** | צאת שבת | `shabbatEnds` | Shabbat ends (Tzais / nightfall) |
| **Other days** | *(nothing)* | — | No Shabbat items added |

These items appear as regular entries in the `results[]` array with `resolution.kind = "SHABBAT"`.
Times are location-aware — computed from the same `lat`/`lon`/`tz` as all other profile times.

Implementation: `core-engine/.../engine/calendar/ShabbatTimesComputer.kt`

---

## Internal Methods (Custom Shitot)

The engine includes custom zmanim computations under the **Sfaradi / Or HaChaim** shita, beyond what KosherJava provides out of the box:

| ID | Description |
|---|---|
| `SFARADI_OR_HACHAIM.ALOS_72_ZMANIYOT_ASTRO` | Alos — 72 zmaniyot minutes before sea-level sunrise |
| `SFARADI_OR_HACHAIM.MISHEYAKIR_SHAOT_ZMANIYOT_ASTRO` | Misheyakir — zmanit hours from sea-level sunrise (default −1.1h) |
| `SFARADI_OR_HACHAIM.SOF_ZMAN_SHMA_GRA_ASTRO` | Sof Zman Shema (GRA) — 3 zmaniyot hours, sea-level day |
| `SFARADI_OR_HACHAIM.SOF_ZMAN_TEFILLA_GRA_ASTRO` | Sof Zman Tefilla (GRA) — 4 zmaniyot hours, sea-level day |
| `SFARADI_OR_HACHAIM.SOF_ZMAN_TEFILLA_MGA_72_ZMANIYOT_ASTRO` | Sof Zman Tefilla (MGA) — 4 zmaniyot hours, expanded day |
| `SFARADI_OR_HACHAIM.TZAIS_VARIANTS_DEGREES_4_9` | Tzais by degrees (e.g., 4.9°) |
| `SFARADI_OR_HACHAIM.TZAIS_13P5_ZMANIYOT_ASTRO` | Tzais — 13.5 zmanit minutes after sea-level sunset |

Implementation: `core-engine/.../engine/internal/`

---

## Testing

### Run all tests

```bash
./gradlew test
```

### E2E tests (rest-api module)

| Test class | What it tests |
|---|---|
| `E2E_HealthTest` | `GET /health` returns 200 |
| `E2E_MethodsTest` | `GET /methods` — top 10 match golden snapshot |
| `E2E_ComputeEnumTest` | `GET /compute/SUNSET` — golden snapshot (Tel Aviv, 2025-09-09) |
| `E2E_ComputeByNameTest` | `GET /compute/by-name?method=getSeaLevelSunset` — golden snapshot |
| `E2E_InternalMethodsSmokeTest` | `POST /profiles/compute` — mixed profile with INTERNAL + EXTERNAL_NAME |
| `E2E_ProfilesComputeByKeyTest` | `GET /profiles/{key}/compute` — stored profile contract |

Golden snapshots are in `rest-api/src/test/resources/golden/`.

---

## CLI Examples

All examples use Tel Aviv coordinates (`32.0853, 34.7818`) and `Asia/Jerusalem` timezone.

### Health check
```bash
curl http://localhost:8080/health
```

### List available methods
```bash
curl http://localhost:8080/methods | python3 -m json.tool
```

### Compute a single zman by name
```bash
curl "http://localhost:8080/compute/by-name?method=getSunrise&date=2026-02-20&lat=32.0853&lon=34.7818&tz=Asia/Jerusalem"
```

### Compute by enum
```bash
curl "http://localhost:8080/compute/SUNSET?date=2026-02-20&lat=32.0853&lon=34.7818&tz=Asia/Jerusalem"
```

### List profiles
```bash
curl http://localhost:8080/profiles
```

### Compute a stored profile (Friday — includes candle lighting)
```bash
curl "http://localhost:8080/profiles/or-hachaim-sefardi/compute?date=2026-02-20&lat=32.0853&lon=34.7818&tz=Asia/Jerusalem"
```

### Compute a stored profile (Saturday — includes Shabbat ends)
```bash
curl "http://localhost:8080/profiles/or-hachaim-sefardi/compute?date=2026-02-21&lat=32.0853&lon=34.7818&tz=Asia/Jerusalem"
```

### Compute a stored profile (weekday — no Shabbat items)
```bash
curl "http://localhost:8080/profiles/or-hachaim-sefardi/compute?date=2026-02-22&lat=32.0853&lon=34.7818&tz=Asia/Jerusalem"
```

### Validate a profile
```bash
curl -X POST http://localhost:8080/profiles/validate \
  -H "Content-Type: application/json" \
  -d @profiles/src/main/resources/profiles/or-hachaim-sefardi.json
```

---

## Project Structure

```
kosherjava-compute-engine/
├── core-engine/src/main/kotlin/com/elad/halacha/engine/
│   ├── model/          ComputeModels, ComputeRequest/Result
│   ├── compute/        ZmanimComputer (enum + by-name), MethodRegistry
│   ├── internal/       InternalMethodId, InternalMethodComputer, SfaradiOrHachaim
│   ├── calendar/       ShabbatTimesComputer, BannerLite (legacy POC)
│   └── profiles/       ProfilesServiceImpl
├── profiles/src/main/
│   ├── kotlin/.../profiles/   ProfileModels, ProfileStore, ProfileValidator
│   └── resources/profiles/    *.json profile files + index.json
├── rest-api/src/
│   ├── main/kotlin/.../rest/  Main, Module (routes), DTOs, ProfileStore, ProfileValidator
│   └── test/                  E2E tests + golden snapshots
├── build.gradle.kts           Root build (group: com.elad.halacha)
├── settings.gradle.kts        Module includes
└── gradle.properties          Shared versions
```

---

## Publishing (local)

```bash
./gradlew :core-engine:publishToMavenLocal
./gradlew :profiles:publishToMavenLocal
```

Artifacts: `com.elad.halacha:core-engine:0.1.2-SNAPSHOT`, `com.elad.halacha:profiles:0.1.2-SNAPSHOT`
