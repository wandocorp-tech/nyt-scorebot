## MODIFIED Requirements

### Requirement: Configurable multi-channel list
The system SHALL accept a list of Discord channel configurations via application properties or environment variables. Each entry SHALL have a channel ID (`id`), a human-readable person name (`name`), and a Discord user ID (`user-id`). At least one channel entry MUST be provided or the application SHALL fail to start.

Channel IDs, user IDs, and player names SHALL NOT be hardcoded in committed properties files. The default `application.properties` SHALL use environment variable placeholders (e.g., `${DISCORD_CHANNEL_0_ID}`) or Spring Boot's externalized configuration mechanism.

#### Scenario: Configuration via environment variables
- **WHEN** the application starts with channel configuration provided via environment variables
- **THEN** the channel list is populated from those environment variables

#### Scenario: Configuration via external properties file
- **WHEN** the application starts with `--spring.config.additional-location=/etc/scorebot/application.properties`
- **THEN** the channel list is populated from the external file

#### Scenario: Missing channel configuration fails startup
- **WHEN** the application starts with no channel configuration provided
- **THEN** the application fails to start with a descriptive error message

### Requirement: Status channel ID externalized
The `discord.statusChannelId` property SHALL be configurable via environment variable (`${DISCORD_STATUS_CHANNEL_ID}`), not hardcoded in committed properties.

#### Scenario: Status channel from environment
- **WHEN** the `DISCORD_STATUS_CHANNEL_ID` environment variable is set
- **THEN** the application uses that value as the status channel ID
