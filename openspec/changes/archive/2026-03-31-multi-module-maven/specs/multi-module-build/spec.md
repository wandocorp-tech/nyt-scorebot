## ADDED Requirements

### Requirement: Domain module has no framework dependencies
The `nyt-scorebot-domain` module SHALL contain `model/*` and `BotText` classes only. It SHALL NOT declare dependencies on Spring, JPA, Discord4J, or any other framework. It MUST compile with only the Java standard library.

#### Scenario: Domain module compiles independently
- **WHEN** `mvn compile` is run on `nyt-scorebot-domain` alone
- **THEN** compilation succeeds with no Spring, JPA, or Discord4J on the classpath

#### Scenario: Domain module contains expected classes
- **WHEN** the domain module's source tree is examined
- **THEN** it contains `GameResult`, `WordleResult`, `ConnectionsResult`, `StrandsResult`, `CrosswordResult`, `CrosswordType`, and `BotText` — and nothing else

### Requirement: Database module depends only on domain
The `nyt-scorebot-database` module SHALL contain `entity/*` and `repository/*` classes. Its only inter-module dependency SHALL be `nyt-scorebot-domain`. External dependencies SHALL be limited to Spring Data JPA and the H2 driver.

#### Scenario: Database module compiles with domain on classpath
- **WHEN** `mvn compile` is run on `nyt-scorebot-database`
- **THEN** compilation succeeds with `nyt-scorebot-domain` and Spring Data JPA on the classpath, but no Discord4J

#### Scenario: Database module contains expected classes
- **WHEN** the database module's source tree is examined
- **THEN** it contains `Scoreboard`, `User`, `StringListConverter`, `UserRepository`, `ScoreboardRepository` — and nothing else

### Requirement: Service module depends only on domain and database
The `nyt-scorebot-service` module SHALL contain business logic classes including `ScoreboardService`, `PuzzleCalendar`, `StatusMessageBuilder`, `scoreboard/*` renderers, and `parser/*` classes. Its inter-module dependencies SHALL be `nyt-scorebot-domain` and `nyt-scorebot-database`. It SHALL NOT depend on Discord4J.

#### Scenario: Service module compiles without Discord4J
- **WHEN** `mvn compile` is run on `nyt-scorebot-service`
- **THEN** compilation succeeds without Discord4J on the classpath

#### Scenario: Service module contains expected classes
- **WHEN** the service module's source tree is examined
- **THEN** it contains `ScoreboardService`, `PuzzleCalendar`, `StatusMessageBuilder`, `SaveOutcome`, `MarkFinishedOutcome`, `GameResultParser`, `WordleParser`, `ConnectionsParser`, `StrandsParser`, `CrosswordParser`, `GameParser`, `ScoreboardRenderer`, `WordleScoreboard`, `ConnectionsScoreboard`, `StrandsScoreboard`, `GameComparisonScoreboard`, `ComparisonOutcome`, and `EmojiGridUtils`

### Requirement: Discord module contains all Discord4J integration
The `nyt-scorebot-discord` module SHALL contain all classes that import from `discord4j.*`. This includes `listener/*`, `StatusChannelService`, `ResultsChannelService`, `DiscordConfig`, and `DiscordChannelProperties`. Its inter-module dependencies SHALL be `nyt-scorebot-domain` and `nyt-scorebot-service`.

#### Scenario: No Discord4J imports outside the discord module
- **WHEN** all modules except `nyt-scorebot-discord` and `nyt-scorebot-app` are searched for `import discord4j`
- **THEN** zero matches are found

#### Scenario: Discord module compiles with expected dependencies
- **WHEN** `mvn compile` is run on `nyt-scorebot-discord`
- **THEN** compilation succeeds with `nyt-scorebot-domain`, `nyt-scorebot-service`, and Discord4J on the classpath

### Requirement: App module assembles the executable JAR
The `nyt-scorebot-app` module SHALL contain only `NytScorebotApplication` and `application.properties`. It SHALL depend on all other modules and produce the executable Spring Boot JAR via `spring-boot-maven-plugin`.

#### Scenario: Executable JAR builds successfully
- **WHEN** `mvn clean package -DskipTests` is run from the root
- **THEN** `nyt-scorebot-app/target/nyt-scorebot-app-*.jar` is produced and is executable via `java -jar`

#### Scenario: All tests pass from root
- **WHEN** `mvn test -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` is run from the root
- **THEN** all unit tests across all modules pass

### Requirement: JaCoCo coverage aggregated across modules
The build SHALL aggregate JaCoCo coverage data from all modules and enforce the existing ≥80% threshold on instruction and branch coverage at the bundle level.

#### Scenario: Coverage check enforced on verify
- **WHEN** `mvn verify -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` is run from the root
- **THEN** the JaCoCo check passes with ≥80% instruction and branch coverage across all modules combined
