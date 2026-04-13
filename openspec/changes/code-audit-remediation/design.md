## Context

NYT Scorebot is a Spring Boot Discord bot (~3k LOC, 5 Maven modules) that parses and persists New York Times daily puzzle results (Wordle, Connections, Strands, Mini/Midi/Main Crossword) posted in Discord channels. A comprehensive code audit (CODE_AUDIT.md) identified 45 findings: 6 Critical, 13 High, 18 Medium, 8 Low — spanning architecture, reactive correctness, 12-Factor compliance, code quality, test quality, and CI/CD.

The codebase is small but tightly coupled: adding a new game type today requires editing 7+ files across 4 modules. The Scoreboard entity has 50+ columns via `@Embedded`/`@AttributeOverride`. Seven `instanceof` chains discriminate on `GameResult` subtypes. Reactive error handling is absent (fire-and-forget `.subscribe()` with no `onError`). Production uses `ddl-auto=update` with a file-based H2 database.

Constraints:
- Two-player design is intentional and permanent (per owner decision)
- H2 file-based database is acceptable for this scale; Postgres migration is a future consideration
- Must maintain ≥80% JaCoCo coverage threshold
- Discord4J reactive stack must be respected (no `.block()` in reactive paths)

## Goals / Non-Goals

**Goals:**
- Eliminate all 6 Critical findings (Scoreboard god class, instanceof chains, ddl-auto=update, blocking beans, silent subscribe errors)
- Fix all 13 High findings (race conditions, type safety, fat methods, naming)
- Address all 18 Medium findings (code smells, naming, test quality, CI)
- Fix all 8 Low findings (magic numbers, docs, formatting)
- Introduce Flyway for schema migration management
- Add Spring Boot Actuator for operational visibility
- Make the codebase extensible: adding a new game type should require only a new parser + GameResult subclass
- Maintain or improve test coverage (≥80%)

**Non-Goals:**
- Migrating from H2 to Postgres/MySQL (noted for future; not in this change)
- Rewriting the two-player architecture to support N players (explicitly out of scope per owner)
- Rewriting Discord4J to a different Discord library
- Adding new game types (this change makes it easier; doesn't add any)
- Full SonarCloud zero-issue target (39 Minor issues will be batch-addressed but some Lombok/style items may remain)

## Decisions

### D1: Normalize Scoreboard via a `game_result` join table (not separate per-game tables)

**Choice:** Single `game_result` table with a `game_type` discriminator column + JSON or typed columns for game-specific data.

**Alternative considered:** One table per game type (e.g., `wordle_result`, `connections_result`). Rejected because it creates N tables and N repository interfaces; queries like "are all games submitted?" become N-way joins.

**Alternative considered:** Keep the wide table but use `@MappedSuperclass` for crossword variants. Rejected because it doesn't solve the 50+ column / `@AttributeOverride` problem.

**Rationale:** A single `game_result` table with JPA `@Inheritance(SINGLE_TABLE)` and a `game_type` discriminator keeps queries simple (one table scan for a day's results) while allowing each subclass to define only its own columns. The existing `Scoreboard` entity becomes a thin wrapper holding `date`, `userId`, `finished`, and a `@OneToMany` to `GameResultEntity`.

### D2: Polymorphism via abstract methods on GameResult (not Visitor pattern)

**Choice:** Add abstract methods directly to `GameResult`: `gameType()`, `gameLabel()`, `validate(PuzzleCalendar)`, `isSuccess()`. Each subclass implements its own logic.

**Alternative considered:** Visitor pattern with `GameResultVisitor` interface. Rejected — adds boilerplate for only 4-6 game types, and callers already know they want a single behavior dispatch, not double dispatch.

**Alternative considered:** Strategy map (`Map<Class<? extends GameResult>, Function<...>>`). Rejected — loses compile-time safety and makes it easy to forget a mapping.

**Rationale:** Direct abstract methods are the simplest fix for the instanceof chains. The compiler enforces that every new GameResult subclass implements all required methods. This is the textbook Clean Code fix for the "switch smell."

### D3: GameType enum as the single source of truth for game identity

**Choice:** `GameType` enum in `nyt-scorebot-domain` with values: `WORDLE`, `CONNECTIONS`, `STRANDS`, `MINI_CROSSWORD`, `MIDI_CROSSWORD`, `MAIN_CROSSWORD`. Used in `Streak.gameType` (`@Enumerated(STRING)`), as JPA discriminator values, in `GameResult.gameType()`, and in `BotText` label lookups.

**Rationale:** Eliminates raw String game-type identifiers. Compile-time safety; IDE autocomplete; exhaustive switch expressions in Java 17+.

### D4: Flyway with baseline migration (not clean start)

**Choice:** Add Flyway with `spring.flyway.baseline-on-migrate=true` and `baseline-version=0`. First migration (`V1__baseline.sql`) creates the schema matching the current Hibernate-generated DDL. Subsequent migrations (V2, V3...) normalize the schema.

**Alternative considered:** Liquibase. Rejected — Flyway is simpler for SQL-first migrations and the team has no existing preference.

**Rationale:** `baseline-on-migrate` allows Flyway to adopt the existing H2 database without data loss. The baseline migration is a no-op on existing installs but creates the schema on fresh installs.

### D5: Split CrosswordResult into MainCrosswordResult + TimedCrosswordResult

**Choice:** `TimedCrosswordResult` holds shared fields (time, date, crosswordType). `MainCrosswordResult extends TimedCrosswordResult` adds `duo`, `lookups`, `checkUsed`.

**Alternative considered:** Keep one class with Optional fields. Rejected — violates SRP and the null fields are semantically misleading.

**Rationale:** Clean separation. The existing `main-crossword-flags` spec already introduced MainCrosswordResult; this change completes the split by making TimedCrosswordResult the base for Mini/Midi.

### D6: Per-command handler classes for slash commands (not annotation-based routing)

**Choice:** Extract each slash command into its own `@Component` class implementing a `SlashCommandHandler` interface with `name()` and `handle(ChatInputInteractionEvent)` methods. `SlashCommandListener` becomes a dispatcher that routes by command name.

**Rationale:** SRP — each command's logic is isolated. Adding a new command = adding a new class. No monolithic 209-line listener.

### D7: Awaitility for E2E test synchronization (not custom polling)

**Choice:** Replace `Thread.sleep()` calls with `Awaitility.await().atMost(...)until(...)` assertions.

**Rationale:** Industry standard for async test synchronization. Eliminates flakiness from fixed sleeps while keeping tests readable.

### D8: Keep structured logging as a lightweight change

**Choice:** Add `logstash-logback-encoder` dependency and configure `logback-spring.xml` with JSON output for non-local profiles. Local/dev profile retains human-readable console output.

**Rationale:** Minimal change that enables log aggregation. Profile-based config means dev experience is unaffected.

## Risks / Trade-offs

- **[Risk] Database migration on existing data** → Flyway baseline migration must exactly match current Hibernate DDL. Mitigation: generate DDL from current Hibernate config and diff against migration script. Back up H2 file before first run.
- **[Risk] Scoreboard normalization changes query patterns** → All code that reads Scoreboard.wordleResult etc. must be updated to query through the GameResult collection. Mitigation: change is mechanical; comprehensive test suite validates.
- **[Risk] Abstract methods on GameResult break all subclasses** → Every GameResult subclass must implement new methods. Mitigation: compiler enforces this; existing tests validate behavior.
- **[Risk] Reactive refactoring may introduce subtle bugs** → Removing `.block()` and adding error handlers changes execution flow. Mitigation: E2E test covers the critical path; unit tests mock reactive chains.
- **[Trade-off] Single game_result table may have sparse columns** → Mini/Midi crosswords don't use duo/lookups/checkUsed columns. Accepted: the sparsity is minimal (3 nullable columns) and the query simplicity benefit outweighs.
- **[Trade-off] Awaitility adds a test dependency** → Accepted: it's a well-maintained, widely-used library that eliminates a class of flaky tests.

## Migration Plan

The work is organized into 5 sequential batches (as defined in CODE_AUDIT.md §12):

1. **Batch 1 — Model & Polymorphism**: GameType enum, CrosswordResult split, GameResult abstract methods, Scoreboard normalization. This is the foundation; all other batches may depend on it.
2. **Batch 2 — Reactive & Concurrency**: Fix blocking beans, subscribe error handling, Mono<?> types, race condition, NPE guard.
3. **Batch 3 — Config, CI & Infra**: Flyway, externalized config, Actuator, graceful shutdown, CI workflow fixes.
4. **Batch 4 — Refactor Services & Commands**: Decompose fat classes, extract command handlers, DRY helpers, naming fixes.
5. **Batch 5 — Tests, Sonar & Cleanup**: Awaitility, Sonar fixes, test improvements, formatting, minor code smells.

**Rollback:** Each batch is a separate branch/PR. If a batch introduces regressions, revert the PR. The Flyway migration (Batch 3) includes a rollback script (`U2__rollback_normalize.sql`).

## Open Questions

- **Q1:** Should the normalized `game_result` table use JPA `@Inheritance(SINGLE_TABLE)` or `@Inheritance(JOINED)`? Single table is simpler but has nullable columns; joined is normalized but requires joins. **Leaning:** SINGLE_TABLE for query simplicity at this scale.
- **Q2:** For H2 → Postgres future migration, should Flyway migrations be written in H2-compatible SQL or Postgres-compatible SQL? **Leaning:** H2-compatible for now since that's the current database; add Postgres variants when the migration happens.
