## ADDED Requirements

### Requirement: Shared emoji row validation utility
The system SHALL provide a single shared utility method for validating that a string line consists entirely of emojis from a specified set. This utility SHALL be used by all game scoreboard implementations instead of duplicating the validation inline.

The utility SHALL accept:
- The line string to validate
- A `Set<Integer>` of allowed Unicode codepoint values
- An `int` expected count (`-1` to accept any positive count; a specific value to require exact count)

The utility SHALL return `false` for blank lines.

#### Scenario: Line with only allowed emojis — exact count expected
- **WHEN** a non-blank line consists of exactly N emojis all in the allowed set, and expected count is N
- **THEN** the utility returns `true`

#### Scenario: Line with only allowed emojis — variable count
- **WHEN** a non-blank line consists of one or more emojis all in the allowed set, and expected count is -1
- **THEN** the utility returns `true`

#### Scenario: Line contains a disallowed codepoint
- **WHEN** a line contains any character whose codepoint is not in the allowed set
- **THEN** the utility returns `false`

#### Scenario: Wrong emoji count
- **WHEN** a non-blank line has only allowed emojis but a count different from the expected count
- **THEN** the utility returns `false`

#### Scenario: Blank line
- **WHEN** the input line is blank (empty or whitespace only)
- **THEN** the utility returns `false`

### Requirement: Game scoreboard implementations use shared validation
Each `GameComparisonScoreboard` implementation (`WordleScoreboard`, `ConnectionsScoreboard`, `StrandsScoreboard`) SHALL delegate emoji row filtering to the shared utility rather than implementing its own codepoint iteration loop.

#### Scenario: Wordle emoji row extraction
- **WHEN** `WordleScoreboard.emojiGridRows()` is called on a scoreboard with a raw Wordle share
- **THEN** it returns the same rows as before (using the shared utility internally)

#### Scenario: Connections emoji row extraction
- **WHEN** `ConnectionsScoreboard.emojiGridRows()` is called on a scoreboard with a raw Connections share
- **THEN** it returns the same rows as before (using the shared utility internally)

#### Scenario: Strands emoji row extraction
- **WHEN** `StrandsScoreboard.emojiGridRows()` is called on a scoreboard with a raw Strands share
- **THEN** it returns the same rows as before (using the shared utility internally)
