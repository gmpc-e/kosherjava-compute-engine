# kosherjava-compute-engine — Claude Working Reference

## Role
Kotlin/JVM multi-module Gradle library + Ktor REST server that wraps KosherJava to compute
halachic times (zmanim) for any location and date. Part of the **Zmanim** project.

GitHub: `gmpc-e/kosherjava-compute-engine`

## Tech Stack
- Kotlin 2.0.21, JVM 17
- KosherJava 2.5.0
- Ktor (REST server, port 8080)
- Gradle with configuration cache enabled

## Module Layout
```
settings.gradle.kts        — includes :core-engine, :profiles, :rest-api
core-engine/               — ZmanimComputer, internal custom methods, profile service impl
profiles/                  — ProfilesService API, profile loading/validation
rest-api/                  — Ktor app (Main.kt, Module.kt, routes)
```

## Key Files
| File | Purpose |
|------|---------|
| `core-engine/src/main/kotlin/.../engine/internal/InternalMethodId.kt` | Enum of all custom method IDs (`SFARADI_OR_HACHAIM.*`) |
| `core-engine/src/main/kotlin/.../engine/internal/InternalMethodComputer.kt` | Dispatches to shita-specific computers |
| `core-engine/src/main/kotlin/.../engine/internal/SfaradiOrHachaim.kt` | Actual computation logic for Or HaChaim Sefardi |
| `core-engine/src/main/kotlin/.../engine/internal/InternalMethodRegistry.kt` | Registry; maps ID strings → descriptors and routes `compute()` calls |
| `core-engine/src/main/kotlin/.../engine/model/ZmanimComputer.kt` | Core KosherJava wrapper |
| `profiles/src/main/resources/profiles/` | Profile JSON files |
| `rest-api/src/main/kotlin/.../rest/Module.kt` | Ktor module wiring routes |

## Published Artifacts
```
com.elad.halacha:core-engine:0.1.2-SNAPSHOT
com.elad.halacha:profiles:0.1.2-SNAPSHOT
```
Publish: `./gradlew publishToMavenLocal`

## Build Commands
```bash
./gradlew build                        # full build + tests
./gradlew :core-engine:test            # core-engine tests only
./gradlew :rest-api:test               # E2E tests (requires server)
./gradlew :rest-api:run                # start Ktor server on :8080
./gradlew publishToMavenLocal          # publish snapshots for local consumption
```

## Profiles
Active profiles in `profiles/src/main/resources/profiles/`:
- `or-hachaim-sefardi.json` — Or HaChaim Sefardi shita (primary)
- `chazon-shamaim.json` — Hazon Shamaim shita

Profile JSON structure:
- Top-level `key`, `displayName`, `methods` array
- Each method entry has an `id` string and a `params` map for per-method config
- `params` keys vary by method (e.g. `astroDegrees`, `minutesAfterSunset`,
  `misheyakirOffsetHoursFromSeaLevelSunrise`)

## Internal Custom Methods (SFARADI_OR_HACHAIM namespace)
All custom methods live under the `SFARADI_OR_HACHAIM.*` ID prefix.

**Adding a new internal method — touch exactly 4 files:**
1. `InternalMethodId.kt` — add enum entry with `id` and `display` strings
2. `InternalMethodComputer.kt` — add dispatch case if a new computer class is needed
3. `SfaradiOrHachaim.kt` — implement the computation logic
4. `InternalMethodRegistry.kt` — add the new `InternalMethodId` entry to the `when` block

## Branching Strategy
- `main` — stable, releasable
- `feat/*` — feature branches; merge to main via PR
