## MODIFIED Requirements

### Requirement: Configurable multi-channel list
The system SHALL accept a list of Discord channel configurations via application properties. Each entry SHALL have a channel ID (`id`), a human-readable person name (`name`), and an authorised Discord user ID (`userId`). An optional top-level `statusChannelId` property MAY be provided to designate a separate read-only status channel. At least one channel entry MUST be provided or the application SHALL fail to start.

#### Scenario: Valid multi-channel config
- **WHEN** `application.properties` contains two or more `discord.channels[*].id` and `discord.channels[*].name` entries
- **THEN** the bot subscribes to all listed channels at startup

#### Scenario: Missing channel config
- **WHEN** `discord.channels` is empty or absent
- **THEN** the application fails to start with a descriptive configuration error

#### Scenario: Status channel ID configured
- **WHEN** `discord.statusChannelId` is set
- **THEN** the bot also subscribes to the status channel to enforce read-only access

#### Scenario: Status channel ID absent
- **WHEN** `discord.statusChannelId` is not set
- **THEN** no status channel behaviour is activated and existing channel monitoring is unaffected
