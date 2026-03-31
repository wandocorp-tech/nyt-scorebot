## Context

The project is currently a flat single-module Maven build. All 36 production classes and 17 test classes share one classpath. The `code-audit-cleanup` change (to be applied first) will have already tidied dead code, fixed bugs, and extracted utilities — leaving a clean baseline to restructure.

After the cleanup, the dependency graph between packages is:

```
model/* ← (no deps)
BotText ← (no deps)
entity/* ← model/*
repository/* ← entity/*
parser/* ← model/*
service/ScoreboardService ← repository/*, entity/*, model/*, PuzzleCalendar
service/StatusMessageBuilder ← BotText, entity/Scoreboard, model/GameResult
service/scoreboard/* ← BotText, entity/Scoreboard, model/*
service/StatusChannelService ← discord4j, DiscordChannelProperties, ScoreboardService, StatusMessageBuilder
service/ResultsChannelService ← discord4j, DiscordChannelProperties, ScoreboardService, ScoreboardRenderer
listener/* ← discord4j, DiscordChannelProperties, parser/*, service/*
DiscordConfig ← discord4j
config/DiscordChannelProperties ← (Spring only)
NytScorebotApplication ← (Spring Boot)
```

This maps cleanly to 5 modules with a strict dependency direction:

```
domain ← (nothing)
database ← domain
service ← domain, database
discord ← domain, service (+ discord4j)
app ← domain, database, service, discord
```

## Goals / Non-Goals

**Goals:**
- Enforce architectural boundaries at compile time via Maven module dependencies
- Isolate Discord4J to a single module (`discord`)
- Isolate JPA/Spring Data to `database` + downstream consumers
- Keep `domain` as a pure Java module with zero framework dependencies
- Preserve all existing tests and CI commands

**Non-Goals:**
- Changing any runtime behaviour
- Changing the package naming (classes keep their existing package names within each module)
- Publishing modules to a Maven repository
- Adding module-info.java (Java Platform Module System) — Maven modules only

## Decisions

### D1: 5 modules, not 4 — separate `app` module
The Spring Boot application class, `application.properties`, and the `SmokeTest` live in a dedicated `app` module that depends on all others. This keeps the executable JAR concern isolated and lets each sub-module have clean, focused test configurations.

**Alternative considered:** Put the application class in the `discord` module. Rejected because it would make `discord` depend on `database` (for Spring Boot autoconfiguration scanning), muddying the boundary.

### D2: `StatusChannelService` and `ResultsChannelService` move to the discord module
Both import `GatewayDiscordClient` and are fundamentally Discord integration code. Moving them out of the `service` package keeps the service module free of any Discord4J dependency.

Their package will change from `com.wandocorp.nytscorebot.service` to `com.wandocorp.nytscorebot.discord` (or stay in `service` but within the discord module — see D3).

### D3: Keep existing package names, just split across modules
Classes retain their current `com.wandocorp.nytscorebot.*` package hierarchy. What changes is which module's `src/main/java` they live in. This avoids changing every import statement in the codebase.

Exception: `StatusChannelService` and `ResultsChannelService` will move from `service` to `discord` package since they're moving modules and should reflect their new home.

**Alternative considered:** Rename all packages to match module names (e.g., `com.wandocorp.nytscorebot.domain.model`). Rejected — too much churn for no compile-time benefit.

### D4: Shared test utilities stay in the module that owns the class under test
`FixedPuzzleCalendar` (extracted in the cleanup change) lives in the `service` module's test sources, because `PuzzleCalendar` lives in the service module. If another module needs it, we use Maven's `test-jar` packaging, but in practice the only consumer is `ScoreboardServiceTest`.

### D5: Parent POM inherits Spring Boot parent; sub-modules inherit from the project parent
The root `pom.xml` keeps `<parent>spring-boot-starter-parent</parent>` and uses `<modules>` to list sub-modules. Sub-modules inherit dependency management. Only `app` uses `spring-boot-maven-plugin` to produce the executable JAR.

### D6: JaCoCo aggregation at root level
The root POM will use `jacoco:report-aggregate` so the existing ≥80% coverage check spans all modules as a single measurement.

## Risks / Trade-offs

- **Build time** — Multi-module builds add a few seconds of Maven overhead. Acceptable for a small project.
- **IDE import** — IntelliJ natively supports multi-module Maven. No issue expected.
- **Test isolation** — Tests that depend on Spring context (e.g., `SmokeTest`) must be in the `app` module where all beans are on the classpath. Unit tests with mocks work in any module.
- **Split packages** — Two modules cannot contribute to the same package if we ever add `module-info.java`. Since we're not doing JPMS (Non-Goal), this is fine for now. We have `service` in both the service and discord modules, which would conflict under JPMS. If JPMS is ever desired, the discord-side services would need a package rename.

## Open Questions

_(none — all decisions resolved)_
