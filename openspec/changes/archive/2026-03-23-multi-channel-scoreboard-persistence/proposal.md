## Why

The bot currently monitors a single Discord channel and logs parsed game results without persisting them. Supporting multiple channels — each mapped to a real person — and storing results in a database enables score tracking and future leaderboard features.

## What Changes

- Replace the single `discord.channel-id` property with a configurable list of channel-to-person mappings
- Introduce a `User` entity representing a person, identified by their associated channel
- Introduce a `Scoreboard` entity representing one parsed game result tied to a `User`
- Persist `User` and `Scoreboard` records to an embedded H2 database via Spring Data JPA
- On each parsed message, look up or create the `User` for that channel, then save the result as a new `Scoreboard` entry

## Capabilities

### New Capabilities
- `multi-channel-monitoring`: Configure a list of Discord channels, each mapped to a person's display name; the bot subscribes to all configured channels simultaneously
- `user-scoreboard-persistence`: `User` and `Scoreboard` JPA entities with a one-to-many relationship, persisted to H2; on every successful parse, a `Scoreboard` record is created and associated with the correct `User`

### Modified Capabilities
<!-- No existing spec-level requirements are changing -->

## Impact

- `application.properties`: replace `discord.channel-id` with a structured channel list (e.g., `discord.channels[0].id` / `discord.channels[0].name`)
- `MessageListener`: updated to subscribe to multiple channels and route results to the persistence layer
- New JPA entities: `User`, `Scoreboard`
- New Spring Data repositories: `UserRepository`, `ScoreboardRepository`
- New `ScoreboardService`: orchestrates user lookup/creation and scoreboard persistence
- Dependencies added: `spring-boot-starter-data-jpa`, `h2`
