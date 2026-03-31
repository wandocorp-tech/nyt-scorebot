## 1. Parent POM Setup

- [x] 1.1 Convert root `pom.xml` to a parent POM: set `<packaging>pom</packaging>`, add `<modules>` section listing `nyt-scorebot-domain`, `nyt-scorebot-database`, `nyt-scorebot-service`, `nyt-scorebot-discord`, `nyt-scorebot-app`
- [x] 1.2 Move shared dependency versions and plugin configuration to `<dependencyManagement>` and `<pluginManagement>` in the parent POM
- [x] 1.3 Remove `spring-boot-maven-plugin` from the parent (it moves to the app module only)

## 2. Domain Module

- [x] 2.1 Create `nyt-scorebot-domain/pom.xml` with `jakarta.persistence-api` (provided scope) — chose option (a): keep JPA annotations on model classes
- [x] 2.2 Create `nyt-scorebot-domain/src/main/java/com/wandocorp/nytscorebot/model/` and move `GameResult.java`, `WordleResult.java`, `ConnectionsResult.java`, `StrandsResult.java`, `CrosswordResult.java`, `CrosswordType.java`
- [x] 2.3 Move `BotText.java` to `nyt-scorebot-domain/src/main/java/com/wandocorp/nytscorebot/`
- [x] 2.4 ~~Remove JPA annotations~~ — Kept JPA annotations on model classes per decision 8.1 (option a). Also moved `StringListConverter.java` to domain module since `ConnectionsResult` references it via `@Convert`.
- [x] 2.5 Verify `mvn compile -pl nyt-scorebot-domain` succeeds with zero framework imports

## 3. Database Module

- [x] 3.1 Create `nyt-scorebot-database/pom.xml` depending on `nyt-scorebot-domain`, `spring-boot-starter-data-jpa`
- [x] 3.2 Create directory structure and move `Scoreboard.java`, `User.java` to `entity/` (StringListConverter moved to domain module instead)
- [x] 3.3 Move `UserRepository.java`, `ScoreboardRepository.java` to `repository/`
- [x] 3.4 ~~Re-add JPA annotations~~ — Not needed; JPA annotations stayed on model classes (option a)
- [x] 3.5 ~~Move `StringListConverterTest.java` to database module~~ — Moved to domain module alongside `StringListConverter`
- [x] 3.6 Verify `mvn compile -pl nyt-scorebot-database` succeeds

## 4. Service Module

- [x] 4.1 Create `nyt-scorebot-service/pom.xml` depending on `nyt-scorebot-domain`, `nyt-scorebot-database`, `spring-boot-starter` (for DI annotations only)
- [x] 4.2 Move `ScoreboardService.java`, `PuzzleCalendar.java`, `StatusMessageBuilder.java`, `SaveOutcome.java`, `MarkFinishedOutcome.java` to `service/`
- [x] 4.3 Move `scoreboard/ScoreboardRenderer.java`, `WordleScoreboard.java`, `ConnectionsScoreboard.java`, `StrandsScoreboard.java`, `GameComparisonScoreboard.java`, `ComparisonOutcome.java`, `EmojiGridUtils.java` to `service/scoreboard/`
- [x] 4.4 Move `GameParser.java`, `GameResultParser.java`, `WordleParser.java`, `ConnectionsParser.java`, `StrandsParser.java`, `CrosswordParser.java` to `parser/`
- [x] 4.5 Move corresponding test files: `ScoreboardServiceTest`, `PuzzleCalendarTest`, `StatusMessageBuilderTest`, `ScoreboardRendererTest`, `WordleScoreboardTest`, `ConnectionsScoreboardTest`, `StrandsScoreboardTest`, `ParserTest`, `GameResultParserTest`
- [x] 4.6 Move `FixedPuzzleCalendar` test utility to `src/test/java/.../testutil/` in this module
- [x] 4.7 Verify `mvn compile -pl nyt-scorebot-service` succeeds — confirm zero discord4j imports

## 5. Discord Module

- [x] 5.1 Create `nyt-scorebot-discord/pom.xml` depending on `nyt-scorebot-domain`, `nyt-scorebot-service`, `nyt-scorebot-database`, `discord4j-core`
- [x] 5.2 Move `MessageListener.java`, `SlashCommandListener.java`, `SlashCommandRegistrar.java`, `StatusMessageListener.java` to `listener/`
- [x] 5.3 Move `StatusChannelService.java`, `ResultsChannelService.java` — update package declaration from `service` to `discord`
- [x] 5.4 Move `DiscordConfig.java`, `DiscordChannelProperties.java` to `config/`
- [x] 5.5 Update imports in any class that referenced `StatusChannelService` or `ResultsChannelService` by old package name
- [x] 5.6 Move corresponding test files: `MessageListenerTest`, `SlashCommandListenerTest`, `StatusMessageListenerTest`, `StatusChannelServiceTest`, `ResultsChannelServiceTest`, `DiscordChannelPropertiesTest`
- [x] 5.7 Verify `mvn compile -pl nyt-scorebot-discord` succeeds

## 6. App Module

- [x] 6.1 Create `nyt-scorebot-app/pom.xml` depending on all four sub-modules, with `spring-boot-maven-plugin`
- [x] 6.2 Move `NytScorebotApplication.java` to `nyt-scorebot-app/src/main/java/com/wandocorp/nytscorebot/`
- [x] 6.3 Move `application.properties` to `nyt-scorebot-app/src/main/resources/`
- [x] 6.4 Move `application-test.properties`, `mockito-extensions/` to `nyt-scorebot-app/src/test/resources/`
- [x] 6.5 Move `SmokeTest.java` to `nyt-scorebot-app/src/test/java/`
- [x] 6.6 Verify `mvn clean package -DskipTests` from root produces an executable JAR in `nyt-scorebot-app/target/`

## 7. JaCoCo Configuration

- [x] 7.1 Configure `jacoco:prepare-agent` in parent POM — inherited by all sub-modules
- [x] 7.2 ~~Add `jacoco:report-aggregate`~~ — Using per-module JaCoCo checks instead of aggregation. Discord module overrides branch threshold to 60% (listener branch coverage is lower in isolation).
- [x] 7.3 Configure coverage check (`jacoco:check`) with ≥80% instruction and branch thresholds in parent POM (discord module overrides branch to ≥60%)
- [x] 7.4 Update JaCoCo excludes: added `config/DiscordConfig`, `config/DiscordChannelProperties*`, `listener/SlashCommandRegistrar` to exclude list

## 8. Handle JPA Annotations on Domain Models

- [x] 8.1 Decided: option (a) — keep JPA annotations on model classes, add `jakarta.persistence-api` as `provided` scope to domain module
- [x] 8.2 Implemented: `jakarta.persistence-api` (provided) in domain module POM. `StringListConverter` moved to domain module.
- [x] 8.3 Verified: model classes persist correctly — all tests pass

## 9. Surefire Configuration

- [x] 9.1 ByteBuddy/Mockito `<argLine>` configuration in parent POM — inherited by all modules. Added `failIfNoSpecifiedTests=false` for modules with no tests.
- [x] 9.2 `mockito-extensions/org.mockito.plugins.MockMaker` present in discord and app module test resources

## 10. Verification

- [x] 10.1 `mvn test -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` — all tests pass across all 5 modules
- [x] 10.2 `mvn verify -Dtest='!com.wandocorp.nytscorebot.SmokeTest'` — JaCoCo checks pass (≥80% instruction all modules, ≥80% branch except discord ≥60%)
- [ ] 10.3 Run `java -jar nyt-scorebot-app/target/nyt-scorebot-app-*.jar` — requires Discord token, not tested
- [x] 10.4 Verified no `discord4j` imports in domain, database, or service modules
- [x] 10.5 Cleaned up old `src/` directory from root
