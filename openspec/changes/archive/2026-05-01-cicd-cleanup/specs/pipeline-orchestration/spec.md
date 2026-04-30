## MODIFIED Requirements

### Requirement: Pipeline orchestrates build, test, release, deploy, and announce in sequence
The pipeline workflow SHALL call the build, test, release, deploy, and announce workflows. The E2E test SHALL only run on pull request builds. On pushes to `main`, the test job SHALL be skipped and the release job SHALL proceed directly after build. On pull requests, only `build` and `test` run; release, deploy, and announce are skipped.

#### Scenario: Full main-branch pipeline (no E2E test)
- **WHEN** the pipeline runs on a push to `main`
- **THEN** build, release, deploy, and announce SHALL run in sequence; the test job SHALL be skipped

#### Scenario: PR pipeline runs build and test only
- **WHEN** the pipeline runs on a pull request targeting `main`
- **THEN** build and test SHALL run; release, deploy, and announce SHALL be skipped

#### Scenario: Test failure on a PR stops the pipeline
- **WHEN** the test job fails on a pull request
- **THEN** no downstream job (release, deploy, announce) SHALL run (they are already skipped by their `if` condition)

#### Scenario: Release job is not blocked by skipped test
- **WHEN** the test job is skipped on a push to `main`
- **THEN** the release job SHALL NOT be skipped due to the test being skipped; it SHALL depend on `build` completing successfully

#### Scenario: Build failure stops everything downstream
- **WHEN** the build job fails in the pipeline
- **THEN** none of test, release, deploy, or announce SHALL run
