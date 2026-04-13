## MODIFIED Requirements

### Requirement: Pipeline triggers scoped to main branch and pull requests
The CI/CD pipeline SHALL trigger only on pushes to the `main` branch and on pull request events targeting `main`. Pushes to feature branches SHALL NOT trigger the full pipeline.

#### Scenario: Push to main triggers pipeline
- **WHEN** a commit is pushed to the `main` branch
- **THEN** the full pipeline (build, test, deploy, release) runs

#### Scenario: Pull request triggers build and test
- **WHEN** a pull request targeting `main` is opened or updated
- **THEN** the build and test jobs run (but not deploy or release)

#### Scenario: Push to feature branch does not trigger pipeline
- **WHEN** a commit is pushed to a branch other than `main`
- **THEN** no pipeline workflow runs

### Requirement: Version string centralized via Maven property
The JAR artifact name SHALL be derived from `${project.version}` in all CI/CD workflow files. The version string SHALL NOT be hardcoded in workflow YAML files. CI workflows SHALL use `mvn help:evaluate` or a similar mechanism to resolve the version dynamically.

#### Scenario: Version bump requires only POM change
- **WHEN** the version is bumped in the root `pom.xml`
- **THEN** all CI workflows automatically use the new version without manual edits

### Requirement: JaCoCo coverage thresholds unified
All modules SHALL use the same JaCoCo instruction and branch coverage minimum of 80%. Module-specific overrides below 80% SHALL be removed unless explicitly documented with justification.

#### Scenario: Discord module meets 80% threshold
- **WHEN** the `nyt-scorebot-discord` module's coverage is checked
- **THEN** the minimum branch coverage threshold is 80% (not 60%)

### Requirement: Sonar project key aligned between POM and CI
The `sonar.projectKey` property SHALL have the same value in both the root `pom.xml` and the CI workflow file. The canonical value SHALL be `wandocorp-tech_nyt-scorebot`.

#### Scenario: Sonar key consistent
- **WHEN** the Sonar analysis runs
- **THEN** the project key `wandocorp-tech_nyt-scorebot` is used regardless of whether it comes from POM or CI override
