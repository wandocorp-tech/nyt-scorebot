## ADDED Requirements

### Requirement: SmokeTest is deleted
The `SmokeTest.java` file SHALL be deleted from the project. It has been superseded by `EndToEndTest` and is no longer needed.

#### Scenario: SmokeTest file removed
- **WHEN** this change is applied
- **THEN** `nyt-scorebot-app/src/test/java/com/wandocorp/nytscorebot/SmokeTest.java` SHALL no longer exist in the codebase

### Requirement: SmokeTest exclusion references are removed
Any references to excluding `SmokeTest` in documentation or build commands SHALL be updated or removed.

#### Scenario: Documentation updated
- **WHEN** `README.md` or `copilot-instructions.md` reference `SmokeTest` exclusion flags
- **THEN** those references SHALL be updated to reflect the new test structure (unit tests exclude `EndToEndTest` instead)
