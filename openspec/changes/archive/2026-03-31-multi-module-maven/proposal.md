## Why

The project is a single-module Maven build with all classes in one `com.wandocorp.nytscorebot` package hierarchy. As the codebase grows, this makes it impossible to enforce architectural boundaries — any class can import any other. Splitting into modules enforces dependency direction at compile time: the domain module can never accidentally import Discord4J, the service layer can never touch the Discord client, etc.

## What Changes

- **BREAKING** (build only — no runtime API): Convert from a single-module Maven project to a multi-module parent POM with 5 sub-modules.

| Module | Artifact ID | Contents |
|---|---|---|
| **domain** | `nyt-scorebot-domain` | `model/*`, `BotText` — pure Java, no framework dependencies |
| **database** | `nyt-scorebot-database` | `entity/*`, `repository/*` — JPA entities and Spring Data repositories |
| **service** | `nyt-scorebot-service` | `service/ScoreboardService`, `service/PuzzleCalendar`, `service/StatusMessageBuilder`, `service/scoreboard/*`, `parser/*` — business logic with no Discord dependency |
| **discord** | `nyt-scorebot-discord` | `listener/*`, `service/StatusChannelService`, `service/ResultsChannelService`, `DiscordConfig`, `config/DiscordChannelProperties` — all Discord4J integration |
| **app** | `nyt-scorebot-app` | `NytScorebotApplication`, `application.properties` — Spring Boot entry point, assembles all modules |

- Move source files to their new module locations
- Create per-module `pom.xml` files with correct inter-module and external dependencies
- Convert the root `pom.xml` to a parent POM with `<modules>` section
- Move test files alongside their production counterparts in each module
- Shared test utilities (`FixedPuzzleCalendar`, etc.) live in the module that owns them or in a shared test-jar

## Capabilities

### New Capabilities
- `multi-module-build`: Maven multi-module project structure with enforced dependency boundaries between domain, database, service, discord, and app layers

### Modified Capabilities
_(none — all existing specs remain valid; this is a structural change only)_

## Impact

- **Build:** `mvn clean package` runs from the root and builds all modules in dependency order
- **Tests:** Each module runs its own unit tests; the `SmokeTest` moves to the app module (it requires the full Spring context)
- **JAR:** The app module produces the executable Spring Boot JAR; sub-modules produce regular JARs
- **CI:** Existing `mvn test` / `mvn verify` commands continue to work from the root
- **IDE:** IntelliJ will recognise the multi-module structure automatically
- **Dependencies:** Discord4J is only on the classpath of `nyt-scorebot-discord` and `nyt-scorebot-app`; Spring Data JPA is only on `nyt-scorebot-database` and downstream
