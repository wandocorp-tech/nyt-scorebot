## 1. Model & Polymorphism (Batch 1)

- [x] 1.1 Create `GameType` enum in `nyt-scorebot-domain` with values WORDLE, CONNECTIONS, STRANDS, MINI_CROSSWORD, MIDI_CROSSWORD, MAIN_CROSSWORD and a `label()` method returning the human-readable name
- [x] 1.2 Split `CrosswordResult` into base `CrosswordResult` (Mini/Midi) and `MainCrosswordResult extends CrosswordResult` (adds duo, lookups, checkUsed). Update `CrosswordParser` to return `MainCrosswordResult` for MAIN type
- [x] 1.3 Add abstract methods to `GameResult`: `gameType()`, `gameLabel()`, `isSuccess()`, `puzzleNumber()`. Implement in all subclasses (WordleResult, ConnectionsResult, StrandsResult, CrosswordResult, MainCrosswordResult)
- [x] 1.4 Replace all `instanceof` chains in `ScoreboardService`, `MessageListener`, `StreakService`, and `MainCrosswordScoreboard` with calls to the new polymorphic methods. Update `Scoreboard.mainCrosswordResult` field type to `MainCrosswordResult`
- [ ] 1.5 _(Deferred to Batch 3 — depends on Flyway)_ Normalize `Scoreboard` entity: remove all `@Embedded`/`@AttributeOverride` blocks, add `@OneToMany` relationship to a new `GameResultEntity` table using `@Inheritance(SINGLE_TABLE)` with `game_type` discriminator
- [ ] 1.6 _(Deferred to Batch 3 — depends on 1.5)_ Update `ScoreboardRepository` queries and `ScoreboardService` logic to work with the normalized game_result table (duplicate detection via game_type query, apply result via insert, allGamesPresent via count)
- [ ] 1.7 _(Deferred to after 1.5/1.6 — boxed primitives needed for @Embedded JPA)_ Replace boxed primitives (`Integer`, `Boolean`) with primitives in `WordleResult`, `ConnectionsResult`, `StrandsResult` fields where values are never semantically null
- [ ] 1.8 _(Deferred to Batch 4 — cascading change through ScoreboardRenderer/ResultsChannelService)_ Update `Streak.gameType` field from `String` to `GameType` enum with `@Enumerated(EnumType.STRING)`
- [x] 1.9 Update all tests for model changes: parser tests, service tests, renderer tests. Ensure ≥80% coverage maintained

## 2. Reactive & Concurrency Fixes (Batch 2)

- [ ] 2.1 Remove `.block()` from `DiscordConfig.java` bean creation — refactor to return `Mono<GatewayDiscordClient>` or use `@Bean` with deferred initialization
- [ ] 2.2 Remove `.block()` from `SlashCommandRegistrar.java` — chain reactively or use `@PostConstruct` with proper reactive handling
- [x] 2.3 Add `onError` consumers to all 8+ fire-and-forget `.subscribe()` calls in `StatusChannelService`, `ResultsChannelService`, `MessageListener`, `SlashCommandListener`, `StatusMessageListener`, `SlashCommandRegistrar`. Log errors with context
- [x] 2.4 Fix `Mono<?>` wildcard return types in `MessageListener.processMessage()` and `replyForOutcome()` — change to `Mono<Void>`
- [x] 2.5 Fix race condition in `ResultsChannelService`: replace `volatile LocalDate lastRefreshDate` with `AtomicReference<LocalDate>` and use `AtomicReference.set()`/`.get()` for thread-safe access
- [x] 2.6 Fix potential NPE in `MessageListener.isFromConfiguredUser()`: add null guard for `channelUserIdMap.get(channelId)`
- [x] 2.7 Update unit tests for reactive changes; verify error handlers are invoked on failures

## 3. Config, CI & Infrastructure (Batch 3)

- [x] 3.1 Add Flyway dependency to `nyt-scorebot-database/pom.xml` and configure `spring.flyway.baseline-on-migrate=true`, `baseline-version=1`
- [x] 3.2 Create `V1__baseline.sql` migration matching current Hibernate-generated DDL
- [ ] 3.3 Create `V2__normalize_game_results.sql` migration *(deferred — depends on entity normalization 1.5/1.6)*
- [x] 3.4 Change `spring.jpa.hibernate.ddl-auto` from `update` to `validate`
- [x] 3.5 Externalize Discord channel IDs, user IDs, player names, and status channel ID to environment variable placeholders in `application.properties` with default values for backward compatibility
- [x] 3.6 Add `spring-boot-starter-actuator` dependency to `nyt-scorebot-app/pom.xml`. Configure to expose only health endpoints (`management.endpoints.web.exposure.include=health`)
- [ ] 3.7 Add readiness probe that checks Discord gateway connection status *(deferred — requires SmartLifecycle integration)*
- [ ] 3.8 Implement graceful shutdown using Spring `SmartLifecycle` *(deferred — complex lifecycle change)*
- [ ] 3.9 Add `logstash-logback-encoder` dependency and create `logback-spring.xml` with JSON output for non-local profiles *(deferred — optional enhancement)*
- [x] 3.10 Restrict `pipeline.yml` triggers to `push: branches: [main]` and `pull_request: branches: [main]`
- [x] 3.11 Replace hardcoded `nyt-scorebot-app-1.0-SNAPSHOT.jar` in `build.yml`, `deploy.yml`, `release.yml` with glob patterns
- [x] 3.12 Modify `release.yml` to download the build artifact instead of running `mvn clean package` (with fallback)
- [x] 3.13 Align `sonar.projectKey` in root `pom.xml` to `wandocorp-tech_nyt-scorebot` to match CI workflow

## 4. Refactor Services & Commands (Batch 4)

- [ ] 4.1 Decompose `ScoreboardService.saveResult()` into focused private methods *(deferred — large refactor with high test impact)*
- [ ] 4.2 Refactor `ScoreboardRenderer.render()`: extract `determineLayout()` method, clarify variable names *(deferred — cosmetic, low priority)*
- [ ] 4.3 Create `SlashCommandHandler` interface. Extract handlers into separate classes *(deferred — large structural refactor)*
- [ ] 4.4 Extract `SlashCommandRegistrar.registerCommands()` into per-command builder methods *(deferred — cosmetic)*
- [x] 4.5 Extract shared `prepareContext()` method in `ResultsChannelService` to DRY the duplicated logic between `refresh()` and `refreshGame()`
- [ ] 4.6 Move `DiscordConfig.java` from root package to `config` subpackage *(deferred — affects package scanning)*
- [ ] 4.7 Reorganize `BotText` constants into semantic groups *(deferred — cosmetic, already well-organized with section comments)*
- [ ] 4.8 Rename `User.userId` field to `discordUserId` *(deferred — DB schema change)*
- [x] 4.9 Fix `SlashCommandListener` toggle context message: use `STATUS_CONTEXT_FLAG_UPDATED` for `/duo`, `/check`, `/lookups`
- [x] 4.10 Fix `@Order` documentation: align copilot-instructions.md and README with actual parser order (Crossword=3, Strands=4)
- [x] 4.11 Update tests for all refactored classes; verify ≥80% coverage maintained

## 5. Tests, Sonar & Cleanup (Batch 5)

- [ ] 5.1 Add Awaitility dependency and replace Thread.sleep() in EndToEndTest *(deferred — E2E test only)*
- [ ] 5.2 Inject channel IDs in EndToEndTest via @Value *(deferred — E2E test only)*
- [ ] 5.3 Add StringListConverter test for delimiter collision *(deferred — edge case)*
- [x] 5.4 Remove duplicate mock setup in `StatusChannelServiceTest` (line 50-51)
- [x] 5.5 Unify JaCoCo branch coverage threshold: set `nyt-scorebot-discord/pom.xml` minimum to 0.80 (matching root)
- [ ] 5.6 Fix `CrosswordParser.extractDate()`: change return type to `Optional<LocalDate>` *(deferred — cascading change through model layer)*
- [x] 5.7 Fix `ConnectionsParser.lastIndexOf` bug: use tracked forward search offsets instead of `content.lastIndexOf(line)`
- [x] 5.8 Refactor `GameResultParser` to idiomatic `flatMap` chain
- [x] 5.9 Fix `StrandsParser` formatting defect: add newline between methods
- [x] 5.10 Fix `ResultsChannelService` fully-qualified `new java.util.HashMap<>()` — add import statement
- [x] 5.11 Replace magic numbers in `SlashCommandRegistrar` with `ApplicationCommandOption.Type` enum values
- [x] 5.12 Add comment to `BotText.MAX_LINE_WIDTH = 33` explaining origin
- [x] 5.13 Add comment to `ScoreboardRenderer.PLAYER_COL_WIDTH = 15` documenting layout constraint
- [x] 5.14 Run full test suite and verify ≥80% JaCoCo coverage
