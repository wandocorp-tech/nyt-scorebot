## ADDED Requirements

### Requirement: Graceful Discord disconnect on shutdown
The application SHALL register a shutdown hook that gracefully disconnects the Discord gateway client when the JVM receives SIGTERM or SIGINT. Pending message sends SHALL be allowed to complete (up to a configurable timeout) before disconnect.

#### Scenario: Application receives SIGTERM
- **WHEN** the application receives a SIGTERM signal
- **THEN** the Discord gateway client disconnects gracefully, and the application exits with code 0

#### Scenario: Pending messages complete before shutdown
- **WHEN** SIGTERM is received while a status channel message is being sent
- **THEN** the message send completes (or times out after the configured grace period) before the Discord client disconnects

### Requirement: Spring lifecycle integration
The graceful shutdown SHALL integrate with Spring's `@PreDestroy` or `SmartLifecycle` mechanism rather than a raw JVM shutdown hook, to ensure proper ordering with other Spring-managed resources (database connections, Flyway, etc.).

#### Scenario: Database connections close after Discord disconnect
- **WHEN** the application shuts down
- **THEN** the Discord client disconnects before the Spring DataSource is destroyed

### Requirement: Configurable shutdown timeout
The shutdown grace period SHALL be configurable via `spring.lifecycle.timeout-per-shutdown-phase` (default: 10 seconds). If pending operations do not complete within this period, they SHALL be cancelled.

#### Scenario: Shutdown timeout exceeded
- **WHEN** a pending Discord operation takes longer than the configured timeout
- **THEN** the operation is cancelled and shutdown proceeds
