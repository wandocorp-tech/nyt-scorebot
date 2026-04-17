## ADDED Requirements

### Requirement: Structured JSON log output for non-local profiles
The application SHALL output logs in JSON format when running with a non-local Spring profile (e.g., `prod`, `staging`). Each log entry SHALL include at minimum: timestamp, level, logger name, message, and thread name.

#### Scenario: Production profile outputs JSON logs
- **WHEN** the application runs with `spring.profiles.active=prod`
- **THEN** each log line is a valid JSON object containing `timestamp`, `level`, `logger_name`, `message`, and `thread_name` fields

### Requirement: Human-readable console output for local development
The application SHALL retain the default Spring Boot console log format (pattern-based, human-readable) when running with the `local` profile or no profile.

#### Scenario: Local development uses readable logs
- **WHEN** the application runs with no active profile or `spring.profiles.active=local`
- **THEN** log output uses the standard Spring Boot console pattern (not JSON)

### Requirement: Logback configuration via logback-spring.xml
Log format switching SHALL be configured via `logback-spring.xml` using Spring profile-based `<springProfile>` blocks. The `logstash-logback-encoder` library SHALL be used for JSON encoding.

#### Scenario: Logback config file exists
- **WHEN** the application module is built
- **THEN** `logback-spring.xml` exists in `nyt-scorebot-app/src/main/resources/`
