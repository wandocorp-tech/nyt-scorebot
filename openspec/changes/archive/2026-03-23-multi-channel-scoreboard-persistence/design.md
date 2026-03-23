## Context

The bot is a Spring Boot application using Discord4J. Currently it subscribes to a single Discord channel (configured via `discord.channel-id`), parses NYT game results, and logs them. There is no persistence layer.

The change introduces multi-channel support and an H2-backed persistence layer so that every parsed result is stored and associated with the correct person.

## Goals / Non-Goals

**Goals:**
- Allow any number of channels to be configured, each mapped to a person's display name
- Subscribe to all configured channels at startup
- Persist every successfully parsed game result as a `Scoreboard` record linked to a `User`
- Use an embedded H2 database so no external infrastructure is required

**Non-Goals:**
- Exposing scores via a REST API or UI (future concern)
- Authentication or multi-guild support
- Modifying how game results are parsed (parsers are unchanged except for comment-extraction bug fixes)
- Migrating or back-filling historical messages

## Decisions

### D1: Channel-to-person mapping via indexed properties
**Choice**: Use Spring Boot's `@ConfigurationProperties` with a `List<ChannelConfig>` binding (`discord.channels[0].id`, `discord.channels[0].name`).  
**Rationale**: Natural fit for structured lists in `application.properties`; type-safe and easy to validate. Avoids custom parsing of comma-separated strings.  
**Alternative considered**: Map syntax (`discord.channels.CHANNEL_ID=name`) — rejected because key order is non-deterministic and Snowflake IDs as map keys look awkward.

### D2: User identified by channel ID; messages filtered by configured Discord user ID
**Choice**: The `User` entity stores the channel's Snowflake ID as its primary identifier and a `userId` field for the owner's Discord user ID. The `MessageListener` filters incoming messages by matching the author's Discord user ID against the configured `user-id` per channel — replacing the previous `isBot()` guard entirely.  
**Rationale**: Filtering by the specific owner's user ID is more precise than rejecting bots generically, and naturally allows the bot itself to post messages on test channels (by configuring the bot's own user ID). `user-id` is mandatory; startup fails if any channel entry omits it.  
**Alternative considered**: `isBot()` filter — rejected because it prevents self-posting in smoke tests and is less intentional than an explicit owner ID allow-list.

### D3: Scoreboard is date-keyed with one @Embedded result per game type
**Choice**: `Scoreboard` is a single JPA entity with a `date` (`LocalDate`) field and four nullable `@Embedded` fields — one for each game type (`wordleResult`, `connectionsResult`, `strandsResult`, `crosswordResult`). Each game result class is annotated `@Embeddable`. A `(user, date)` unique constraint ensures at most one `Scoreboard` per person per day. On each new parse, `ScoreboardService` does a find-or-create by `(user, date)` and sets the appropriate field.  
**Rationale**: Directly models the spec ("one of each result as fields"); keeps a single table; avoids a join to retrieve a day's full results. `@Embeddable` avoids turning the existing model classes into full JPA entities.  
**Alternative considered**: Separate `@OneToOne` entity per result type — rejected because it adds four extra tables and join complexity for a simple embed use-case.  
**Alternative considered**: Flat columns per game field (e.g., `wordle_attempts`, `wordle_completed`, …) — rejected in favour of using `@Embeddable` to keep the field grouping explicit and co-located with the existing model classes.

### D5: Live smoke tests via `mvn test` with real Discord connection
**Choice**: Validation tasks 5.2–5.4 are implemented as a `@SpringBootTest @ActiveProfiles("test")` suite that connects to real Discord, posts messages to dedicated test channels using the bot's own identity, and asserts on the H2 database.  
**Rationale**: Exercises the full end-to-end path (Discord event → listener → service → DB) without mocking. Test channels are configured with the bot's own `user-id` so the bot's self-posted messages pass the userId filter.  
**Test DB**: A separate `jdbc:h2:file:./data/scorebot-smoke-test` with `ddl-auto=create-drop` isolates test data from the production DB.  
**5.5 (restart persistence)**: Verified by configuration — the production DB uses `jdbc:h2:file:./data/scorebot` with `ddl-auto=update`, which survives restarts by design.

### D6: @Embeddable fields use wrapper types (Integer / Boolean)
**Choice**: All primitive `int`/`boolean` fields in `WordleResult`, `ConnectionsResult`, `StrandsResult`, and `CrosswordResult` were changed to `Integer`/`Boolean`.  
**Rationale**: When a `Scoreboard` row has no result for a given game type, all its embedded columns are `NULL`. Hibernate 6 cannot assign `NULL` to a primitive field, causing `IllegalArgumentException` at load time. Wrapper types allow the all-null case and let Hibernate correctly return `null` for the unused embedded object.

### D7: Parser comment extraction fixed for Wordle, Crossword, and Strands
**Choice**: Each parser now skips its result body (emoji grid rows for Wordle/Strands, trailing `!` for Crossword) before extracting the comment. Crossword additionally strips the NYT share URL from the comment — the URL is already captured in `rawContent`, so placing it in `comment` would be redundant and misleading.  
**Rationale**: Previously, the comment field incorrectly contained the emoji grid, punctuation, or the share URL. The fix ensures `comment` holds only text the user deliberately added after their game result.
**Choice**: A `ScoreboardService` bean receives a `GameResult` + channel ID, looks up or creates the `User`, builds and saves the `Scoreboard`.  
**Rationale**: Keeps `MessageListener` thin (only Discord event wiring) and makes persistence logic independently testable.

### D8: Parser unit tests covering all four parsers with real sample data
**Choice**: A `ParserTest` class (plain JUnit 5, no Spring context) exercises each parser with three real samples covering a range of outcomes (attempts, failures, comments, puzzle numbers, game types). Assertions cover every parsed field including `rawContent` and `comment`.  
**Rationale**: Isolates parser logic from the full Spring/Discord stack; runs in milliseconds and provides a regression net for future parser changes.

## Risks / Trade-offs

- **H2 data loss on restart** → Acceptable for now; configure file-based H2 (`jdbc:h2:file:./data/scorebot`) so data survives restarts.
- **Channel config drift** → If a channel is removed from config, its historical `User`/`Scoreboard` records remain orphaned in the DB. → Acceptable; records are never deleted automatically.
- **Concurrent message burst** → Spring Data saves are synchronous on the reactive event thread via `flatMap`. For low-volume personal use this is fine; blocking calls should be wrapped with `Schedulers.boundedElastic()` if throughput becomes a concern.

## Migration Plan

1. Add JPA + H2 dependencies to `pom.xml`
2. Update `application.properties`: remove `discord.channel-id`, add `discord.channels[*]` entries
3. Create entities, repositories, service, config class
4. Update `MessageListener` to use the new multi-channel config and call `ScoreboardService`
5. Start the bot — H2 will auto-create schema via `spring.jpa.hibernate.ddl-auto=update`

**Rollback**: Revert to previous commit; the old `discord.channel-id` property still works with the old listener.
