## ADDED Requirements

### Requirement: Triple crown is awarded for winning all three crosswords in one day

The system SHALL award a triple crown to a player when that player is the winner of the Mini Crossword, the Midi Crossword, and the Main Crossword for the same puzzle date. Wordle, Connections, and Strands SHALL NOT be considered.

The crown SHALL be derived from the `ComparisonOutcome` values already produced by the crossword comparison logic, not recomputed from raw solve times.

#### Scenario: One player wins all three crosswords

- **GIVEN** the Mini, Midi, and Main crossword comparisons for a date all resolve to `ComparisonOutcome.Win`
- **AND** all three name the same player as winner
- **THEN** the system SHALL award a triple crown to that player for that date

#### Scenario: Wins are split between players

- **GIVEN** one player wins the Mini and Midi crosswords and the other player wins the Main crossword
- **THEN** the system SHALL NOT award a triple crown to either player

#### Scenario: A crossword is tied

- **GIVEN** the Mini and Midi crosswords resolve to `Win` for the same player
- **AND** the Main crossword resolves to `ComparisonOutcome.Tie`
- **THEN** the system SHALL NOT award a triple crown

#### Scenario: A crossword is nuked

- **GIVEN** the Mini and Main crosswords resolve to `Win` for the same player
- **AND** the Midi crossword resolves to `ComparisonOutcome.Nuke`
- **THEN** the system SHALL NOT award a triple crown

#### Scenario: A crossword is still awaiting a submission

- **GIVEN** the Mini and Midi crosswords resolve to `Win` for the same player
- **AND** the Main crossword resolves to `ComparisonOutcome.WaitingFor`
- **THEN** the system SHALL NOT award a triple crown

#### Scenario: Non-crossword games are ignored

- **GIVEN** a player wins all three crosswords for a date
- **AND** that player loses the Wordle, Connections, and Strands comparisons for the same date
- **THEN** the system SHALL still award a triple crown

### Requirement: A win by forfeit or disqualification counts toward the crown

A `ComparisonOutcome.Win` with a null `differentialLabel` — produced when the opponent failed, was disqualified, or did not submit — SHALL count toward the triple crown exactly as a win with a time differential does.

#### Scenario: Opponent no-shows every crossword

- **GIVEN** the opponent submitted no crossword results for a closed date
- **AND** all three crosswords resolve to `Win` for the remaining player following midnight forfeit application
- **THEN** the system SHALL award a triple crown to that player

#### Scenario: Mixed differential and forfeit wins

- **GIVEN** the Mini crossword resolves to `Win(player, "+0:12")`
- **AND** the Midi crossword resolves to `Win(player, null)` because the opponent was disqualified
- **AND** the Main crossword resolves to `Win(player, "+4:31")`
- **THEN** the system SHALL award a triple crown to that player

### Requirement: The winner's duo flag blocks the crown

If the triple crown winner's own Main Crossword result carries `duo = true`, the system SHALL NOT award the crown, because the Main Crossword was not solved alone.

A `duo = true` flag on the **losing** player's Main Crossword result SHALL NOT block the crown. This asymmetry is deliberate and differs from `WinStreakService.applyOutcome`, which considers both players' duo flags.

#### Scenario: Winner marked duo on the Main crossword

- **GIVEN** a player would otherwise be awarded a triple crown
- **AND** that player's own `MainCrosswordResult.duo` is `true`
- **THEN** the system SHALL NOT award a triple crown

#### Scenario: Loser marked duo on the Main crossword

- **GIVEN** a player would otherwise be awarded a triple crown
- **AND** the winner's own `MainCrosswordResult.duo` is `false` or unset
- **AND** the losing player's `MainCrosswordResult.duo` is `true`
- **THEN** the system SHALL award a triple crown to the winner

#### Scenario: Duo flag is absent

- **GIVEN** a player would otherwise be awarded a triple crown
- **AND** the winner's `MainCrosswordResult.duo` is null
- **THEN** the system SHALL treat the flag as not set and SHALL award a triple crown

### Requirement: The celebration is posted last, as its own message

When a triple crown is awarded, the system SHALL post a dedicated celebration message to the results channel. The message SHALL be posted after all game scoreboards and after the win-streak summary, so that it is the final message of the day's results block.

The message SHALL be tracked in a dedicated slot so that repeated renders do not produce duplicates. All display text SHALL be sourced from `BotText`.

#### Scenario: Crown awarded during a results refresh

- **WHEN** a results refresh awards a triple crown and no crown message is currently tracked
- **THEN** the system SHALL post a new message containing the celebration text and the winner's display name
- **AND** SHALL store the resulting message ID against the crown slot
- **AND** SHALL post it after the win-streak summary write has been issued

#### Scenario: Refresh repeats while the crown still stands

- **GIVEN** a crown message is already tracked for the date
- **WHEN** a further results refresh again awards the crown to the same player
- **THEN** the system SHALL NOT post a second crown message

#### Scenario: No crown awarded

- **WHEN** a results refresh does not award a triple crown and no crown message is tracked
- **THEN** the system SHALL NOT post any crown message

### Requirement: The celebration is deleted when the crown is revoked

If a change to the day's results means a previously awarded triple crown is no longer earned — for example a `/duo`, `/check`, or `/lookups` flag change that alters a crossword outcome — the system SHALL delete the tracked crown message and clear the slot, leaving no trace of the revoked crown.

Deletion SHALL be serialized through the same per-slot write chain used for posts and edits, so a delete cannot overtake an in-flight post and orphan a message. Deleting a message that no longer exists SHALL be tolerated without error.

#### Scenario: Winner sets /duo after the crown was posted

- **GIVEN** a crown message is tracked for the winning player
- **WHEN** that player sets `/duo` on their Main Crossword result and the Main board is refreshed
- **THEN** the system SHALL delete the tracked crown message
- **AND** SHALL clear the tracked crown message ID
- **AND** SHALL NOT post a replacement or a revocation notice

#### Scenario: A flag change flips a crossword outcome to a loss

- **GIVEN** a crown message is tracked for the winning player
- **WHEN** a `/check` or `/lookups` flag change causes one crossword to resolve to a loss or a tie for that player
- **THEN** the system SHALL delete the tracked crown message and clear the slot

#### Scenario: Crown is revoked and then re-earned

- **GIVEN** a crown message was deleted because the crown was revoked
- **WHEN** a subsequent flag change restores the sweep to the same player
- **THEN** the system SHALL post a new crown message and track its ID

#### Scenario: Tracked crown message was already deleted out-of-band

- **GIVEN** a crown message ID is tracked but the message has been deleted manually in Discord
- **WHEN** the system attempts to delete it during revocation
- **THEN** the system SHALL clear the tracked ID without propagating an error
- **AND** SHALL log the condition

### Requirement: The crown is evaluated during the midnight results post

The triple crown SHALL be evaluated as part of the midnight rollover for the closing date, after crossword win-streak forfeits have been applied and after the day's boards have been force-posted.

This ordering is required so that a sweep achieved by forfeit is recognised, since a non-submission only becomes a forfeit win at midnight.

#### Scenario: Forfeit sweep recognised at midnight

- **GIVEN** neither player triggered the both-finished results post during the day
- **AND** one player won every crossword they contested and the opponent did not submit the rest
- **WHEN** the midnight rollover runs for that date
- **THEN** forfeits SHALL be applied before the crown is evaluated
- **AND** the system SHALL post the crown message after the force-posted boards and win-streak summary

#### Scenario: Midnight evaluation does not duplicate an intra-day crown

- **GIVEN** a crown message was already posted during the day for that date
- **WHEN** the midnight rollover evaluates the crown for the same date and the same winner
- **THEN** the system SHALL NOT post a second crown message

#### Scenario: Crown evaluation failure does not block the rollover

- **WHEN** crown evaluation throws during the midnight rollover
- **THEN** the system SHALL log the error
- **AND** SHALL still reset the status board for the new day
