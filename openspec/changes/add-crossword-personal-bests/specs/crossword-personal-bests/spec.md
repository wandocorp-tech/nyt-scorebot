## ADDED Requirements

### Requirement: Personal-best storage with manual seeding

The system SHALL persist a `personal_best` row per `(user, gameType, dayOfWeek)` triple, where `gameType` is one of `MINI_CROSSWORD`, `MIDI_CROSSWORD`, `MAIN_CROSSWORD`, and `dayOfWeek` is the puzzle's day-of-week for `MAIN_CROSSWORD` rows and a non-null sentinel value for `MINI_CROSSWORD` and `MIDI_CROSSWORD` rows so that the database unique constraint `(user_id, game_type, day_of_week)` treats every row as distinct.

Each row SHALL store:
- `best_seconds` (integer, the personal-best solve time),
- `best_date` (date, NULL allowed for manually-seeded rows),
- `source` (string, either `'manual'` or `'computed'`).

Operators SHALL be able to insert or update rows directly via SQL once. Manually-seeded rows have `source = 'manual'` and the bot SHALL preserve them according to the recompute rules below.

#### Scenario: Operator seeds a Main per-day-of-week PB once
- **WHEN** an operator runs `INSERT INTO personal_best (user_id, game_type, day_of_week, best_seconds, best_date, source) VALUES (1, 'MAIN_CROSSWORD', 'SATURDAY', 1500, NULL, 'manual')` and the bot restarts
- **THEN** the row remains intact, the renderer reads `1500` as the user's Saturday Main PB, and PB-break detection on subsequent Saturday saves treats `1500` as the value to beat

#### Scenario: Mini and Midi rows share the no-DoW sentinel
- **WHEN** a Mini PB row exists for a user and a Midi PB row exists for the same user
- **THEN** both rows have a non-null `day_of_week` sentinel and coexist with any Main per-day-of-week rows for the same user without violating the `(user_id, game_type, day_of_week)` uniqueness

### Requirement: PB recompute on save uses clean results only

After every successful crossword result save (Mini, Midi, or Main), the system SHALL invoke a personal-best recompute for that `(user, gameType, dayOfWeek)`. The recompute SHALL:

- Treat the candidate result as eligible only when it is *clean*: for Main, `duo` is not true AND `checkUsed` is not true AND `lookups` is null or zero; for Mini and Midi, every saved result is clean by definition (no flag fields exist).
- Skip the candidate entirely when it is not clean.
- When eligible and there is no existing row, insert a new `source = 'computed'` row.
- When eligible and an existing row exists, replace `best_seconds` and `best_date` only when the candidate is *strictly* faster (`candidateSeconds < existing.bestSeconds`). When such a replacement happens to a row with `source = 'manual'`, the row's `source` SHALL transition to `'computed'`.
- When the candidate is not strictly faster, leave the existing row unchanged regardless of source.

#### Scenario: Clean Main result faster than current PB updates the row
- **GIVEN** a Saturday `personal_best` row for user A exists with `best_seconds = 1000`, `source = 'computed'`
- **WHEN** user A saves a Saturday Main result with `totalSeconds = 800`, `duo = null`, `checkUsed = null`, `lookups = null`
- **THEN** the row is updated to `best_seconds = 800`, `best_date = <today>`, `source = 'computed'`

#### Scenario: Assisted Main result is ignored for PB
- **GIVEN** a Tuesday `personal_best` row for user A exists with `best_seconds = 900`
- **WHEN** user A saves a Tuesday Main result with `totalSeconds = 600` and `lookups = 2`
- **THEN** the row remains at `best_seconds = 900` and no `NewPb` outcome is produced

#### Scenario: Faster clean result beats a manually-seeded row and transitions ownership
- **GIVEN** a Friday `personal_best` row for user A exists with `best_seconds = 1200`, `source = 'manual'`, `best_date = NULL`
- **WHEN** user A saves a clean Friday Main result with `totalSeconds = 1100`
- **THEN** the row is updated to `best_seconds = 1100`, `best_date = <today>`, `source = 'computed'`

#### Scenario: Slower clean result does not displace a manually-seeded row
- **GIVEN** a Friday `personal_best` row for user A exists with `best_seconds = 1200`, `source = 'manual'`
- **WHEN** user A saves a clean Friday Main result with `totalSeconds = 1300`
- **THEN** the row remains at `best_seconds = 1200`, `source = 'manual'`

#### Scenario: First-ever clean result for a game creates the row
- **GIVEN** no `personal_best` row exists for user A on Mini
- **WHEN** user A saves a Mini result with `totalSeconds = 60`
- **THEN** a new row is inserted with `gameType = 'MINI_CROSSWORD'`, the no-DoW sentinel, `best_seconds = 60`, `best_date = <today>`, `source = 'computed'`

### Requirement: PB-break announcement message

When a save produces a new computed personal best (either by inserting the first row or by strictly improving an existing row), the system SHALL post a separate, non-code-block follow-up message to the results channel announcing the new PB. The message SHALL include the player's display name, the game label, and the new time, and SHALL include the prior best when one existed and the day-of-week label for Main only.

#### Scenario: First-ever PB announces without prior value
- **WHEN** user A saves their first-ever clean Mini result with `totalSeconds = 90`
- **THEN** the results channel receives a follow-up message identifying user A's Mini PB as `1:30` and omitting the "(was …)" segment

#### Scenario: Improved Main PB announces with prior and day-of-week
- **GIVEN** a Saturday `personal_best` row for user A exists with `best_seconds = 1000`
- **WHEN** user A saves a clean Saturday Main result with `totalSeconds = 900`
- **THEN** the results channel receives a follow-up message identifying user A's Main (Saturday) PB as `15:00` and noting the prior best as `16:40`

#### Scenario: Beating a manually-seeded value still announces
- **GIVEN** a Wednesday `personal_best` row for user A exists with `best_seconds = 1200`, `source = 'manual'`
- **WHEN** user A saves a clean Wednesday Main result with `totalSeconds = 1100`
- **THEN** the results channel receives a PB-break follow-up message

#### Scenario: Non-improvement does not announce
- **WHEN** user A saves a clean result that is slower than or equal to the existing PB
- **THEN** no PB-break message is posted

#### Scenario: Assisted result never announces
- **WHEN** user A saves a Main result with any of `duo = true`, `checkUsed = true`, or `lookups > 0`
- **THEN** no PB-break message is posted regardless of the time

### Requirement: One-time historical backfill on startup

On application startup, when the `personal_best` table contains zero rows with `source = 'computed'`, the system SHALL walk every persisted scoreboard in chronological order and populate `source = 'computed'` PB rows from clean results only, treating Mini and Midi as one row per user per game and Main as one row per user per day-of-week. The backfill SHALL NOT post any PB-break announcement messages, SHALL NOT modify rows whose `source = 'manual'`, and SHALL be a no-op when at least one `source = 'computed'` row already exists.

#### Scenario: First launch with history populates computed rows
- **GIVEN** the bot has historical scoreboards but no `source = 'computed'` rows in `personal_best`
- **WHEN** the application starts
- **THEN** computed PB rows are inserted reflecting each user's fastest clean result per game (and per day-of-week for Main) and no follow-up channel messages are posted

#### Scenario: Manual rows survive backfill
- **GIVEN** a `source = 'manual'` row exists with `best_seconds = 1500` and the historical clean fastest is `1600`
- **WHEN** the backfill runs
- **THEN** the manual row remains at `best_seconds = 1500`, `source = 'manual'`

#### Scenario: Re-running backfill is a no-op
- **GIVEN** the table already contains at least one `source = 'computed'` row
- **WHEN** the application restarts
- **THEN** the backfill performs no scans and no writes
