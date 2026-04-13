## ADDED Requirements

### Requirement: Health endpoint availability
The application SHALL expose a `/actuator/health` endpoint that returns HTTP 200 with status `UP` when the application is healthy.

#### Scenario: Healthy application responds to health check
- **WHEN** a GET request is made to `/actuator/health`
- **THEN** the response status is 200 and the body contains `{"status": "UP"}`

### Requirement: Readiness endpoint availability
The application SHALL expose a `/actuator/health/readiness` endpoint that returns HTTP 200 only when the application is ready to accept traffic (database connection established, Discord client connected).

#### Scenario: Application is ready
- **WHEN** the application has completed startup, the database is accessible, and the Discord gateway is connected
- **THEN** GET `/actuator/health/readiness` returns HTTP 200

#### Scenario: Application is not yet ready
- **WHEN** the application is still starting up or the Discord gateway has not yet connected
- **THEN** GET `/actuator/health/readiness` returns HTTP 503

### Requirement: Actuator dependency and minimal exposure
The application SHALL include `spring-boot-starter-actuator` as a dependency. Only health-related endpoints SHALL be exposed; all other actuator endpoints SHALL be disabled or unexposed by default.

#### Scenario: Non-health actuator endpoints are not exposed
- **WHEN** a GET request is made to `/actuator/env` or `/actuator/beans`
- **THEN** the response is HTTP 404
