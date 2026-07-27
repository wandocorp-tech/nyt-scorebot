## ADDED Requirements

### Requirement: Data-correcting migrations are gated on immutable identifiers

A migration that corrects or seeds rows for a specific player SHALL identify that player by an immutable key — `app_user.discord_user_id` or `app_user.channel_id` — and SHALL NOT gate on `app_user.name`.

`app_user.name` is written from the `discord.channels[n].name` configuration property, which carries an environment override and can be changed at any time without a code change. A migration gated on it silently matches zero rows when the configured name differs from the persisted one, and Flyway records the migration as successful because a zero-row `UPDATE` is not an error.

The human-readable player name MAY appear in a comment for readability.

#### Scenario: Migration targets a specific player
- **WHEN** a migration corrects rows belonging to one player
- **THEN** its `WHERE` clause SHALL match on `discord_user_id` or `channel_id`
- **AND** SHALL NOT match on `name`

#### Scenario: Configured display name changes after the migration is written
- **GIVEN** a migration gated on `discord_user_id`
- **WHEN** the configured display name for that player is later changed
- **THEN** the migration SHALL still resolve the correct player

### Requirement: Data-correcting migrations are covered by a test that seeds the corrupted state

Every migration that corrects existing data SHALL have a test that seeds a database with the pre-correction state, runs the migration, and asserts the corrected values.

A migration test that runs only against an empty schema cannot distinguish "corrected the data" from "matched zero rows", which is how two prior correction attempts were recorded as successful while changing nothing.

#### Scenario: Corrective migration is verified against seeded data
- **GIVEN** a migration that corrects existing rows
- **WHEN** its test runs
- **THEN** the test SHALL insert rows representing the known-bad state before the migration is applied
- **AND** SHALL assert the specific corrected values afterwards

#### Scenario: Migration is re-run against already-corrected data
- **GIVEN** a corrective migration has already been applied
- **WHEN** the same correction logic is applied a second time
- **THEN** the data SHALL be unchanged, and the migration SHALL NOT error

#### Scenario: Migration runs against a database that never held the bad data
- **WHEN** a corrective migration runs against a database with no matching rows
- **THEN** the migration SHALL complete without error and write nothing

### Requirement: Applied migrations are never edited

A migration file that has been applied to a deployed database SHALL NOT be modified. Flyway stores a checksum per applied migration and fails validation on mismatch, which prevents the application from starting.

Corrections to an applied migration SHALL be delivered as a new, higher-versioned migration that supersedes it.

#### Scenario: A previously applied migration is found to be defective
- **WHEN** an applied migration is discovered to have matched zero rows or written incorrect values
- **THEN** the fix SHALL be delivered as a new migration file with a higher version number
- **AND** the defective migration file SHALL be left byte-for-byte unchanged

#### Scenario: A superseding migration re-applies skipped work
- **GIVEN** an earlier migration silently applied to no rows
- **WHEN** the superseding migration runs
- **THEN** it SHALL perform both the original migration's intended work and the correction

### Requirement: Rebuilding a derived statistic never regresses a manually seeded value

When a migration rebuilds a derived aggregate — such as a `crossword_history_stats` personal best — from raw result rows, it SHALL preserve any manually seeded value that is better than the rebuilt one, rather than overwriting it.

Manually seeded personal bests may represent solves that predate the bot and therefore have no corresponding `game_result` row. A bare recompute would silently discard them.

#### Scenario: Seeded personal best is faster than any recorded result
- **GIVEN** a `crossword_history_stats` bucket holds a manually seeded `pb_seconds`
- **AND** no `game_result` row for that bucket is as fast as the seeded value
- **WHEN** a migration rebuilds that bucket from `game_result`
- **THEN** the bucket's `pb_seconds` SHALL retain the seeded value

#### Scenario: A recorded result beats the seeded personal best
- **GIVEN** a `crossword_history_stats` bucket holds a manually seeded `pb_seconds`
- **AND** a `game_result` row for that bucket is faster
- **WHEN** a migration rebuilds that bucket
- **THEN** the bucket's `pb_seconds` SHALL be set to the faster recorded value

#### Scenario: Sample count and sum are rebuilt from recorded results only
- **WHEN** a migration rebuilds a `crossword_history_stats` bucket
- **THEN** `sample_count` and `sum_seconds` SHALL be derived solely from qualifying `game_result` rows
- **AND** SHALL NOT include any manually seeded personal best that has no corresponding result row
