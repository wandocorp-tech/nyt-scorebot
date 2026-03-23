## ADDED Requirements

### Requirement: Configurable multi-channel list
The system SHALL accept a list of Discord channel configurations via application properties. Each entry SHALL have a channel ID (`id`) and a human-readable person name (`name`). At least one channel entry MUST be provided or the application SHALL fail to start.

#### Scenario: Valid multi-channel config
- **WHEN** `application.properties` contains two or more `discord.channels[*].id` and `discord.channels[*].name` entries
- **THEN** the bot subscribes to all listed channels at startup

#### Scenario: Missing channel config
- **WHEN** `discord.channels` is empty or absent
- **THEN** the application fails to start with a descriptive configuration error

### Requirement: Per-channel person attribution
The system SHALL associate each configured channel with exactly one person name. When a message is received on a channel, the system SHALL resolve the person name from the channel's configuration entry (not from the Discord author of the message).

#### Scenario: Message received on known channel
- **WHEN** a non-bot message arrives on a channel that is in the configured list
- **THEN** the system attributes the result to the person name mapped to that channel

#### Scenario: Message received on unknown channel
- **WHEN** a non-bot message arrives on a channel not in the configured list
- **THEN** the system ignores the message

### Requirement: Simultaneous subscription to all channels
The system SHALL subscribe to all configured channels within the same reactive event stream at startup, without requiring a restart when the config is unchanged.

#### Scenario: Bot subscribes at startup
- **WHEN** the application starts with N configured channels
- **THEN** the bot actively listens for messages on all N channels simultaneously
