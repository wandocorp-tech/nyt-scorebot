## ADDED Requirements

### Requirement: Spangram position field
`StrandsResult` SHALL store a `spangram position` field representing the 1-based index of the 🟡 (spangram) emoji in the player's flattened emoji sequence (reading all rows left-to-right, top-to-bottom).

#### Scenario: Spangram at start of sequence
- **WHEN** a Strands result has emojis `🟡🔵🔵🔵🔵🔵🔵` (spangram is the first emoji)
- **THEN** the spangram position SHALL be `1`

#### Scenario: Spangram in the middle of sequence
- **WHEN** a Strands result has emojis `🔵💡🔵🟡🔵🔵💡🔵🔵` (spangram is the 4th emoji)
- **THEN** the spangram position SHALL be `4`

### Requirement: Spangram position computed at parse time
`StrandsParser` SHALL compute the spangram position when parsing a Strands result and pass it to the `StrandsResult` constructor. The position SHALL NOT require re-parsing raw content at scoreboard-build time.

#### Scenario: Parser produces spangram position
- **WHEN** a Strands message is parsed with spangram emoji at the 4th position in the emoji sequence
- **THEN** the resulting `StrandsResult` SHALL have `getSpangramPosition()` returning `4`

### Requirement: Spangram position persistence
The `spangram_position` field SHALL be persisted via the existing `@Embedded` / `@AttributeOverride` pattern in the `Scoreboard` entity with column name `strands_spangram_position`.

#### Scenario: Database column exists
- **WHEN** the application starts with `ddl-auto=update`
- **THEN** the `scoreboard` table SHALL contain a `strands_spangram_position` column
