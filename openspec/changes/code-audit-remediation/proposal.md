## Why

A comprehensive code audit (CODE_AUDIT.md, 2026-04-12) identified 45 findings across 9 categories — 6 Critical, 13 High, 18 Medium, and 8 Low. The findings span architecture (god class, instanceof chains), reactive/concurrency bugs (silent error swallowing, race conditions), 12-Factor violations (ddl-auto=update in prod, hardcoded config), fat classes, naming issues, code smells, test quality gaps, and CI/CD misconfigurations. Left unaddressed, these create maintenance drag, production risk, and barrier to adding new game types. The audit is fresh and the codebase is small (~3k LOC), making now the ideal time to remediate.

## What Changes

- **Normalize the Scoreboard entity**: Replace the 50+ column wide table with a normalized game-results table, eliminating `@AttributeOverride` duplication and the god-class smell (§1.1)
- **Introduce polymorphism on GameResult**: Add behavior methods to GameResult subclasses (`validate()`, `isAlreadySubmitted()`, `applyTo()`, `gameLabel()`, `gameType()`, `isSuccess()`) to replace 7 instanceof chains across 3 classes (§1.2)
- **Introduce a GameType enum**: Replace raw String game-type identifiers with a compile-time-safe enum, used in Streak entity and polymorphic dispatch (§5.6)
- **Split CrosswordResult**: Separate `MainCrosswordResult` (with duo/lookups/checkUsed fields) from `TimedCrosswordResult` for Mini/Midi (§1.4)
- **Fix reactive/concurrency bugs**: Remove `.block()` from bean init (§3.1), add `onError` to all `.subscribe()` calls (§3.2), fix `Mono<?>` → `Mono<Void>` (§3.3), fix race condition with `AtomicReference` (§3.4), null-safe `isFromConfiguredUser` (§3.5)
- **Add Flyway migrations**: Replace `ddl-auto=update` with versioned schema migrations (§2.1)
- **Externalize configuration**: Move Discord channel IDs and player config to environment variables (§2.3)
- **Add Spring Boot Actuator**: Health and readiness endpoints (§2.4)
- **Add graceful shutdown**: Proper Discord disconnect and resource cleanup on SIGTERM (§2.6)
- **Refactor fat classes**: Decompose `ScoreboardService.saveResult()` (§4.1), `ScoreboardRenderer.render()` (§4.2), split `SlashCommandListener` into per-command handlers (§4.3), extract command registration (§4.4), DRY `ResultsChannelService` (§4.5)
- **Fix naming and clarity**: Rename `User.userId` → `discordUserId` (§5.1), clarify renderer variables (§5.2), fix context message for toggles (§5.3), align parser `@Order` with docs (§5.4), document `MAX_LINE_WIDTH` (§5.5)
- **Fix code smells**: Use primitives where appropriate (§6.1), `Optional<LocalDate>` for `extractDate()` (§6.2), fix `ConnectionsParser.lastIndexOf` bug (§6.3), idiomatic `flatMap` chain (§6.4), formatting fixes (§6.5), fix fully-qualified import (§6.6), replace magic numbers (§6.7, §6.8)
- **Improve tests**: Replace `Thread.sleep()` with Awaitility (§7.1), inject channel IDs via Spring in E2E (§7.2), add delimiter collision tests for `StringListConverter` (§7.3), remove duplicate mock setup (§7.4)
- **Fix CI/CD**: Restrict pipeline triggers to main + PRs (§8.1), use `${project.version}` in CI (§8.2), reuse build artifact in release (§8.3), unify JaCoCo thresholds (§8.4), align Sonar project key (§8.5)
- **Address SonarCloud issues**: Fix 2 Critical (Mono<?> wildcards), 6 Major (sleep, regex, test quality), and batch minor issues (§9)

## Capabilities

### New Capabilities
- `flyway-migrations`: Versioned database schema management using Flyway, replacing Hibernate ddl-auto=update
- `actuator-health`: Spring Boot Actuator integration providing health and readiness endpoints
- `graceful-shutdown`: Proper lifecycle management with Discord disconnect and resource cleanup on shutdown
- `game-type-enum`: Compile-time-safe GameType enum replacing raw String game-type identifiers across entities and services
- `structured-logging`: JSON-formatted structured logging for log aggregation (§2.5)

### Modified Capabilities
- `user-scoreboard-persistence`: Scoreboard entity normalized from wide table to separate game-results table; GameResult subclasses gain polymorphic behavior methods; CrosswordResult split into Main/Timed variants
- `multi-channel-monitoring`: Channel configuration externalized to environment variables instead of committed properties file
- `crossword-scoreboards`: CrosswordResult split into MainCrosswordResult and TimedCrosswordResult with distinct field sets
- `ci-build-test`: Pipeline triggers restricted, version string centralized, JaCoCo thresholds unified, Sonar key aligned
- `release-creation`: Release workflow reuses build artifact instead of rebuilding
- `pipeline-orchestration`: E2E tests restricted to main branch; branch filter added

## Impact

- **Database**: Schema migration from wide Scoreboard table to normalized structure — requires Flyway baseline migration for existing data
- **All modules**: GameResult subclass API changes (new abstract methods) ripple through parser, service, listener, and renderer layers
- **nyt-scorebot-database**: New Flyway dependency, migration scripts, entity restructuring
- **nyt-scorebot-service**: ScoreboardService decomposition, StreakService refactor for GameType enum
- **nyt-scorebot-discord**: MessageListener, SlashCommandListener, ResultsChannelService, StatusChannelService — reactive fixes, refactoring, command handler split
- **nyt-scorebot-domain**: GameResult hierarchy changes, BotText reorganization, CrosswordResult split
- **nyt-scorebot-app**: Actuator dependency, externalized config, graceful shutdown, Flyway config
- **CI/CD**: All 4 workflow files modified (pipeline.yml, build.yml, deploy.yml, release.yml)
- **Dependencies**: New — Flyway, Spring Boot Actuator, Awaitility (test). No removals.
- **Breaking**: Database schema change requires migration; GameResult API change requires all parsers/services to implement new methods
