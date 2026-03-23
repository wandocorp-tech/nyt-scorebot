## 1. Service Layer

- [x] 1.1 Add `markComplete(String discordUserId, LocalDate date)` method to `ScoreboardService` that looks up the user by channel ID, finds their Scoreboard for the given date, sets `complete = true`, and saves it
- [x] 1.2 Return a descriptive enum/result from `markComplete` covering: `MARKED_COMPLETE`, `ALREADY_COMPLETE`, `NO_SCOREBOARD_FOR_DATE`, `USER_NOT_FOUND`

## 2. Slash Command Registration

- [x] 2.1 Create a `SlashCommandRegistrar` Spring `@Component` that on application startup calls `ApplicationService#createGlobalApplicationCommand` to register the `finished` command with description "Mark your scorecard as complete for today"
- [x] 2.2 Wire the registrar into the existing `GatewayDiscordClient` bean from `DiscordConfig`

## 3. Interaction Listener

- [x] 3.1 Create a `SlashCommandListener` Spring `@Component` that subscribes to `ChatInputInteractionEvent` from the `GatewayDiscordClient`
- [x] 3.2 Filter events to only handle the `finished` command (check `event.getCommandName()`)
- [x] 3.3 Extract the invoking user's Discord ID from the interaction event
- [x] 3.4 Call `ScoreboardService#markComplete` with today's GMT date and the Discord user ID
- [x] 3.5 Reply to the interaction with an ephemeral message based on the `markComplete` result:
  - `MARKED_COMPLETE` → "✅ Your scoreboard for today has been marked as complete!"
  - `ALREADY_COMPLETE` → "Your scoreboard was already marked complete for today."
  - `NO_SCOREBOARD_FOR_DATE` → "You haven't submitted any results for today yet."
  - `USER_NOT_FOUND` → "You are not a tracked user in this bot."

## 4. Tests

- [x] 4.1 Unit test `ScoreboardService#markComplete` for all four outcome cases
- [x] 4.2 Unit test `SlashCommandListener` interaction handling, verifying the correct ephemeral reply is sent for each outcome
- [x] 4.3 Ensure unit test coverage is >80%
- [x] 4.4 Update smoke test to have the test user invoke the /finished command
