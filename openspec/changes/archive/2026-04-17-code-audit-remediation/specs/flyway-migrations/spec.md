## ADDED Requirements

### Requirement: Flyway dependency and auto-configuration
The application SHALL include Flyway as a dependency and Spring Boot's Flyway auto-configuration SHALL manage schema migrations on startup. Hibernate `ddl-auto` SHALL be set to `validate` (not `update` or `create`).

#### Scenario: Application starts with Flyway managing schema
- **WHEN** the application starts
- **THEN** Flyway executes any pending migrations before JPA entity manager initialization, and Hibernate validates the schema matches entity mappings

### Requirement: Baseline migration for existing databases
Flyway SHALL be configured with `spring.flyway.baseline-on-migrate=true` and `baseline-version=0`. The first migration (`V1__baseline.sql`) SHALL create the full schema matching the current Hibernate-generated DDL.

#### Scenario: First run on existing H2 database
- **WHEN** the application starts against an existing H2 database that was previously managed by `ddl-auto=update`
- **THEN** Flyway baselines the database at version 0 and applies V1 as a no-op (schema already exists), leaving existing data intact

#### Scenario: First run on fresh database
- **WHEN** the application starts against an empty database
- **THEN** Flyway runs V1__baseline.sql to create the full schema from scratch

### Requirement: Normalization migration
A Flyway migration (`V2__normalize_game_results.sql`) SHALL transform the wide Scoreboard table into a normalized structure with a separate `game_result` table. The migration SHALL preserve all existing data.

#### Scenario: Existing scoreboard data is migrated
- **WHEN** migration V2 runs against a database with existing Scoreboard rows containing embedded game results
- **THEN** each non-null game result is extracted into a row in the `game_result` table, linked to the original Scoreboard by foreign key, and the original embedded columns are dropped

### Requirement: Migration scripts are versioned in source control
All Flyway migration scripts SHALL reside in `nyt-scorebot-database/src/main/resources/db/migration/` and be committed to source control.

#### Scenario: Migration files are in the expected location
- **WHEN** the build runs
- **THEN** migration scripts matching `V*__*.sql` exist under `db/migration/` in the database module's resources
