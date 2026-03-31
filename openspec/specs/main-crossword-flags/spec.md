## ADDED Requirements

### Requirement: MainCrosswordResult model with flag fields
The system SHALL use a `MainCrosswordResult` type (extending `CrosswordResult`) for the main crossword result. `MainCrosswordResult` SHALL include three additional fields beyond those inherited from `CrosswordResult`:
- `duo` (Boolean, default null) — whether the crossword was completed as a duo
- `lookups` (Integer, default null) — the number of lookups used during the solve
- `checkUsed` (Boolean, default null) — whether the check feature was used

The `Scoreboard` entity's `dailyCrosswordResult` field SHALL use `MainCrosswordResult` instead of `CrosswordResult`.

#### Scenario: Main crossword result stored with no flags
- **WHEN** a main crossword result is parsed and saved without any flag commands issued
- **THEN** the `MainCrosswordResult` is persisted with `duo`, `lookups`, and `checkUsed` all null

#### Scenario: Main crossword result stored with flags
- **WHEN** a main crossword result is saved and the user subsequently sets duo=true and lookups=2
- **THEN** the `MainCrosswordResult` is persisted with `duo=true`, `lookups=2`, `checkUsed=null`

### Requirement: /duo slash command toggles duo flag
The system SHALL register a `/duo` slash command. When invoked, it SHALL toggle the `duo` flag on the invoking user's main crossword result for today. The command SHALL require that a main crossword result has already been submitted for today. The reply SHALL be ephemeral and confirm the new state.

#### Scenario: Set duo flag when not already set
- **WHEN** a tracked user invokes `/duo` and today's scoreboard has a main crossword result with `duo` null or false
- **THEN** the system sets `duo=true` and replies with a confirmation that duo has been marked

#### Scenario: Clear duo flag when already set
- **WHEN** a tracked user invokes `/duo` and today's scoreboard has a main crossword result with `duo=true`
- **THEN** the system sets `duo=false` and replies with a confirmation that duo has been cleared

#### Scenario: No main crossword result submitted
- **WHEN** a tracked user invokes `/duo` but today's scoreboard has no main crossword result
- **THEN** the system replies with an ephemeral error message indicating no main crossword result exists for today

#### Scenario: No scoreboard for today
- **WHEN** a tracked user invokes `/duo` but has no scoreboard record for today
- **THEN** the system replies with an ephemeral error message indicating no results have been submitted today

#### Scenario: Untracked user invokes /duo
- **WHEN** a user who is not a configured/tracked user invokes `/duo`
- **THEN** the system replies with an ephemeral error message indicating the user is not tracked

### Requirement: /lookups slash command sets lookup count
The system SHALL register a `/lookups` slash command that accepts a required integer argument (`count`). When invoked, it SHALL set the `lookups` field on the invoking user's main crossword result for today to the provided value. Setting the value to 0 SHALL clear the field (set to null). The command SHALL require that a main crossword result has already been submitted for today.

#### Scenario: Set lookups count
- **WHEN** a tracked user invokes `/lookups 3` and today's scoreboard has a main crossword result
- **THEN** the system sets `lookups=3` and replies with an ephemeral confirmation

#### Scenario: Clear lookups with zero
- **WHEN** a tracked user invokes `/lookups 0` and today's scoreboard has a main crossword result
- **THEN** the system sets `lookups=null` and replies with an ephemeral confirmation that lookups have been cleared

#### Scenario: No main crossword result submitted
- **WHEN** a tracked user invokes `/lookups 2` but today's scoreboard has no main crossword result
- **THEN** the system replies with an ephemeral error message indicating no main crossword result exists for today

#### Scenario: Negative lookups value
- **WHEN** a tracked user invokes `/lookups -1`
- **THEN** the system replies with an ephemeral error message indicating the value must be non-negative

### Requirement: /check slash command toggles check flag
The system SHALL register a `/check` slash command. When invoked, it SHALL toggle the `checkUsed` flag on the invoking user's main crossword result for today. The command SHALL require that a main crossword result has already been submitted for today. The reply SHALL be ephemeral and confirm the new state.

#### Scenario: Set check flag when not already set
- **WHEN** a tracked user invokes `/check` and today's scoreboard has a main crossword result with `checkUsed` null or false
- **THEN** the system sets `checkUsed=true` and replies with a confirmation that check has been marked

#### Scenario: Clear check flag when already set
- **WHEN** a tracked user invokes `/check` and today's scoreboard has a main crossword result with `checkUsed=true`
- **THEN** the system sets `checkUsed=false` and replies with a confirmation that check has been cleared

#### Scenario: No main crossword result submitted
- **WHEN** a tracked user invokes `/check` but today's scoreboard has no main crossword result
- **THEN** the system replies with an ephemeral error message indicating no main crossword result exists for today

### Requirement: Slash commands refresh results and status channels
After any successful flag set/clear operation, the system SHALL refresh both the status channel and the results channel to reflect the updated flag state.

#### Scenario: Flag set triggers refresh
- **WHEN** a user successfully sets or clears any crossword flag via a slash command
- **THEN** the status channel and results channel are refreshed to display the updated information
