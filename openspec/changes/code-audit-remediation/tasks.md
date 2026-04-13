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

- [ ] 3.1 Add Flyway dependency to `nyt-scorebot-database/pom.xml` and configure `spring.flyway.baseline-on-migrate=true`, `baseline-version=0`
- [ ] 3.2 Create `V1__baseline.sql` migration matching current Hibernate-generated DDL (generate from current schema)
- [ ] 3.3 Create `V2__normalize_game_results.sql` migration to create `game_result` table, migrate data from embedded columns, and drop old columns
- [ ] 3.4 Change `spring.jpa.hibernate.ddl-auto` from `update` to `validate` in application.properties
- [ ] 3.5 Externalize Discord channel IDs, user IDs, player names, and status channel ID to environment variable placeholders in `application.properties` (e.g., `${DISCORD_CHANNEL_0_ID}`)
- [ ] 3.6 Add `spring-boot-starter-actuator` dependency to `nyt-scorebot-app/pom.xml`. Configure to expose only health endpoints (`management.endpoints.web.exposure.include=health`)
- [ ] 3.7 Add readiness probe that checks Discord gateway connection status
- [ ] 3.8 Implement graceful shutdown using Spring `SmartLifecycle`: disconnect Discord gateway, allow pending messages to flush (configurable timeout via `spring.lifecycle.timeout-per-shutdown-phase`)
- [ ] 3.9 Add `logstash-logback-encoder` dependency and create `logback-spring.xml` with JSON output for non-local profiles, human-readable for local/default
- [ ] 3.10 Restrict `pipeline.yml` triggers to `push: branches: [main]` and `pull_request: branches: [main]`
- [ ] 3.11 Replace hardcoded `nyt-scorebot-app-1.0-SNAPSHOT.jar` in `build.yml`, `deploy.yml`, `release.yml` with dynamically resolved `${project.version}`
- [ ] 3.12 Modify `release.yml` to download the build artifact instead of running `mvn clean package`
- [ ] 3.13 Align `sonar.projectKey` in root `pom.xml` to `wandocorp-tech_nyt-scorebot` to match CI workflow

## 4. Refactor Services & Commands (Batch 4)

- [ ] 4.1 Decompose `ScoreboardService.saveResult()` into focused private methods: `validatePuzzleNumber()`, `resolveOrCreateUser()`, `resolveOrCreateScoreboard()`, `checkDuplicate()`, `applyAndSave()`, `updateStreaks()`, `checkAutoFinish()`
- [ ] 4.2 Refactor `ScoreboardRenderer.render()`: extract `determineLayout()` method, clarify variable names (`leftSb`/`rightSb` → `longerGridSb`/`shorterGridSb` or add comments)
- [ ] 4.3 Create `SlashCommandHandler` interface with `name()` and `handle(ChatInputInteractionEvent)` methods. Extract `/finished`, `/duo`, `/lookups`, `/check`, `/streak` into separate handler classes. Refactor `SlashCommandListener` to a dispatcher
- [ ] 4.4 Extract `SlashCommandRegistrar.registerCommands()` into per-command builder methods or a data-driven registration loop
- [ ] 4.5 Extract shared `prepareContext()` method in `ResultsChannelService` to DRY the duplicated logic between `refresh()` and `refreshGame()`
- [ ] 4.6 Move `DiscordConfig.java` from root package to `config` subpackage
- [ ] 4.7 Reorganize `BotText` constants into semantic groups (nested classes or enum-based): emoji, messages, commands, labels
- [ ] 4.8 Rename `User.userId` field to `discordUserId` and update all references
- [ ] 4.9 Fix `SlashCommandListener` toggle context message: use a distinct message for `/duo`, `/check` toggles instead of `STATUS_CONTEXT_PLAYER_FINISHED`
- [ ] 4.10 Fix `@Order` documentation: align copilot-instructions.md and README with actual parser order (Crossword=3, Strands=4)
- [ ] 4.11 Update tests for all refactored classes; verify ≥80% coverage maintained

## 5. Tests, Sonar & Cleanup (Batch 5)

- [ ] 5.1 Add Awaitility dependency to test scope and replace all `Thread.sleep()` calls in `EndToEndTest` with `Awaitility.await().atMost(...).until(...)` polling assertions
- [ ] 5.2 Inject channel IDs in `EndToEndTest` via `@Value` or `@Autowired DiscordChannelProperties` instead of hardcoded constants
- [ ] 5.3 Add `StringListConverter` test for delimiter collision: verify round-trip with `List.of("a,b", "c")` fails or is handled by an encoding scheme. Implement encoding fix if needed
- [ ] 5.4 Remove duplicate mock setup in `StatusChannelServiceTest` (line 50-51)
- [ ] 5.5 Unify JaCoCo branch coverage threshold: set `nyt-scorebot-discord/pom.xml` minimum to 0.80 (matching root) and ensure coverage meets the bar
- [ ] 5.6 Fix `CrosswordParser.extractDate()`: change return type to `Optional<LocalDate>` and update callers
- [ ] 5.7 Fix `ConnectionsParser.lastIndexOf` bug: use tracked offsets or regex anchors instead of `content.lastIndexOf(line)` to avoid incorrect position for duplicate emoji rows
- [ ] 5.8 Refactor `GameResultParser` to idiomatic `flatMap` chain: `.flatMap(p -> p.parse(content, discordAuthor).stream()).findFirst()`
- [ ] 5.9 Fix `StrandsParser` formatting defect: add newline between closing brace and `extractComment()` method declaration
- [ ] 5.10 Fix `ResultsChannelService` fully-qualified `new java.util.HashMap<>()` — add import statement
- [ ] 5.11 Replace magic numbers in `SlashCommandRegistrar` (`.type(4)`, `.type(3)`) with named constants from `ApplicationCommandOptionType`
- [ ] 5.12 Add comment to `BotText.MAX_LINE_WIDTH = 33` explaining the origin (Discord monospace/two-player layout constraint)
- [ ] 5.13 Add comment to `ScoreboardRenderer.PLAYER_COL_WIDTH = 15` documenting that longer names should be truncated; optionally enforce max name length in config validation
- [ ] 5.14 Run full test suite and verify ≥80% JaCoCo coverage. Run SonarCloud analysis and verify Critical/Major issues are resolved
