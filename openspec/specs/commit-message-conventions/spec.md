# commit-message-conventions Specification

## Purpose
TBD - created by archiving change automate-release-notes-discord. Update Purpose after archive.

## Requirements

### Requirement: Conventional commit prefixes documented
The repository SHALL document a conventional-commit prefix convention in `.github/copilot-instructions.md` so that AI assistants and contributors author commit messages in a consistent, categorised form.

#### Scenario: Convention documented for AI assistants
- **WHEN** Copilot (or any AI assistant reading the instructions file) drafts a commit message
- **THEN** the instructions SHALL provide the allowed prefix set: `feat`, `fix`, `refactor`, `chore`, `ci`, `test`, `docs`, `build`

#### Scenario: Prefix semantics defined
- **WHEN** a contributor or AI assistant reads the convention
- **THEN** each prefix SHALL be defined briefly: `feat` (user-visible feature), `fix` (user-visible bug fix), `refactor` (internal restructure with no behaviour change), `chore` (housekeeping), `ci` (pipeline changes), `test` (test-only changes), `docs` (documentation), `build` (build/dependency changes)

### Requirement: No automated enforcement
The convention SHALL be guidance only; no commit-lint hook, pre-commit script, or PR-blocking GitHub Action SHALL enforce the format.

#### Scenario: Non-conventional commits are accepted
- **WHEN** a commit is pushed without a conventional prefix
- **THEN** the pipeline SHALL still build, test, deploy, and release without failure
