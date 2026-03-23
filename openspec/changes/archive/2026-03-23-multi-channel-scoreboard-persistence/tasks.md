## 1. Dependencies & Configuration

- [x] 1.1 Add `spring-boot-starter-data-jpa` and `h2` dependencies to `pom.xml`
- [x] 1.2 Create `DiscordChannelProperties` `@ConfigurationProperties` class with a `List<ChannelConfig>` (fields: `id`, `name`)
- [x] 1.3 Enable `@ConfigurationPropertiesScan` (or `@EnableConfigurationProperties`) in the main application class
- [x] 1.4 Replace `discord.channel-id` in `application.properties` with `discord.channels[0].id` / `discord.channels[0].name` entries (one per person)
- [x] 1.5 Add H2 datasource and JPA config to `application.properties` (file-based URL, `ddl-auto=update`)

## 2. Persistence Layer — Entities

- [x] 2.1 Create `User` JPA entity (`id` auto-generated, `channelId` unique string, `name` string)
- [x] 2.2 Annotate `WordleResult`, `ConnectionsResult`, `StrandsResult`, and `CrosswordResult` as `@Embeddable` (add default no-arg constructors and column-attribute mappings as needed)
- [x] 2.3 Create `Scoreboard` JPA entity: `id` (auto), `date` (`LocalDate`), `complete` (`boolean`, default `false`), `@Embedded wordleResult` (nullable), `@Embedded connectionsResult` (nullable), `@Embedded strandsResult` (nullable), `@Embedded crosswordResult` (nullable), FK to `User`
- [x] 2.4 Add a unique constraint on `(user_id, date)` to `Scoreboard` to enforce one record per person per day
- [x] 2.5 Map the `@OneToMany` relationship from `User` to `Scoreboard` (fetch lazy, cascade persist/merge, ordered by `date`)

## 3. Persistence Layer — Repositories & Service

- [x] 3.1 Create `UserRepository` extending `JpaRepository<User, Long>` with `findByChannelId(String channelId)`
- [x] 3.2 Create `ScoreboardRepository` extending `JpaRepository<Scoreboard, Long>` with `findByUserAndDate(User user, LocalDate date)`
- [x] 3.3 Create `ScoreboardService` with method `saveResult(String channelId, String personName, GameResult result)` that: looks up or creates `User`, derives the date from the result (falling back to today), finds or creates the `Scoreboard` for `(user, date)`, sets the appropriate result field, and saves

## 4. Multi-Channel Listener

- [x] 4.1 Refactor `MessageListener` to inject `DiscordChannelProperties` instead of the single `@Value("${discord.channel-id}")`
- [x] 4.2 Build a `Set<Snowflake>` of all configured channel IDs at construction time
- [x] 4.3 Update the reactive filter in `subscribe()` to match any channel in the set
- [x] 4.4 Resolve the person name from the channel config when routing a parsed result to `ScoreboardService`
- [x] 4.5 Wrap the blocking `ScoreboardService.saveResult(...)` call in `Schedulers.boundedElastic()` to avoid blocking the reactive thread

## 5. Validation & Testing

- [x] 5.1 Verify application starts with multiple channels in config and subscribes without errors
- [x] 5.2 Verify that a message on a configured channel creates a `User` (if new) and a `Scoreboard` record in H2
- [x] 5.3 Verify that a second message from the same channel reuses the existing `User` and adds a second `Scoreboard`
- [x] 5.4 Verify that a message on an unconfigured channel is silently ignored
- [x] 5.5 Verify H2 data persists across a bot restart (file-based DB) — verified by config (`jdbc:h2:file:./data/scorebot`, `ddl-auto=update`)

## 6. Parser Unit Tests

- [x] 6.1 Wordle: parse 5-attempt result with no comment
- [x] 6.2 Wordle: parse 4-attempt result with a user comment
- [x] 6.3 Wordle: parse failed (X/6) result
- [x] 6.4 Connections: parse partially-completed result (4 mistakes, 2 groups solved, no comment) — duplicate-row bug fixed (`lastIndexOf`)
- [x] 6.5 Connections: parse completed result with 1 mistake and a user comment
- [x] 6.6 Connections: parse perfect result (0 mistakes)
- [x] 6.7 Strands: parse result with a hint used, no comment
- [x] 6.8 Strands: parse result with no hints and a user comment
- [x] 6.9 Strands: parse result with no hints and no comment
- [x] 6.10 Crossword (Daily): parse with date extracted from text; share URL stripped from comment
- [x] 6.11 Crossword (Midi): parse time, no date; share URL stripped from comment
- [x] 6.12 Crossword (Mini): parse time, no date; share URL stripped, user comment retained
