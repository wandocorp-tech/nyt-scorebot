## Context

The current pipeline (`build → test → deploy → release`) deploys the JAR to the Pi, restarts the service, then publishes a GitHub Release as a side-effect using `generate_release_notes: true`. Players see the bot restart with no announcement; release notes (when they exist) are buried on GitHub. Commit messages are freeform, so even GitHub's auto-generated notes are low-signal.

Existing facts that constrain the design:
- `DISCORD_TOKEN` already exists as a repo secret but is high-privilege (full bot) and currently scoped to the `test` job only.
- `DISCORD_RELEASE_WEBHOOK_URL` does not yet exist; needs creation in Discord (Server Settings → Integrations → Webhooks).
- GitHub Models (`models.github.ai`) is GA in Actions since April 2025, authenticates with `GITHUB_TOKEN`, requires `models: read` permission.
- The existing `release-creation` spec asserts the release workflow is **independent of the pipeline** — but `pipeline.yml` already calls it from the pipeline. This drift should be reconciled as part of this change.
- The pipeline runs on `push: main` (and `pull_request: main`). The release/deploy/announce jobs are gated on `github.ref == 'refs/heads/main'`.
- Version identity is `v${{ github.run_number }}` (e.g., `v42`); the JAR remains `1.0-SNAPSHOT`.

## Goals / Non-Goals

**Goals:**
- Players see a concise, human-readable announcement in a dedicated Discord channel every time a new version deploys to the Pi.
- Release notes are AI-generated from commits since the last tag, filtered to user-facing changes (`feat`, `fix`, `refactor`, `docs`).
- The bot itself remains untouched — all logic is in CI.
- The full-fidelity GitHub Release remains the source of truth; the Discord post links to it.
- Commit messages become more meaningful via Copilot guidance.

**Non-Goals:**
- No retroactive announcements for past releases.
- No bot-side awareness of its own version (deferred — would be a separate change).
- No interactive release commands (e.g., `/changelog` slash command) — out of scope.
- No Discord rich embeds in v1 (plain message with markdown is sufficient and simpler).
- No retry/backoff for the announce step — if Discord is down, the deploy still succeeds and the release is still published; the missed announcement is acceptable loss.

## Decisions

### Decision 1: Webhook over Bot Token for Discord posting
**Choice:** Use a Discord webhook URL (`DISCORD_RELEASE_WEBHOOK_URL`).
**Alternatives:**
- Bot token + channel ID: would require giving the announce job access to `DISCORD_TOKEN`, expanding its blast radius.
- Posting from inside the bot on startup: requires bot-side version tracking, DB schema changes, and creates a startup race with the GitHub Release publication.

**Rationale:** Webhooks are scoped to a single channel, can be revoked independently of the bot, and require zero application code. The existing `DISCORD_TOKEN` never reaches the announce job.

### Decision 2: GitHub Models for AI generation
**Choice:** Call `https://models.github.ai/inference/chat/completions` with `GITHUB_TOKEN` (model: `openai/gpt-4o-mini` for cost/quality balance).
**Alternatives:**
- OpenAI API directly: requires a paid API key as a new secret; no advantage over the GitHub-native option.
- Local summarisation script (no AI): fragile, can't summarise prose well.
- GitHub's built-in `generate_release_notes: true`: produces raw PR titles only; quality depends entirely on titles and offers no filtering.

**Rationale:** Free for repos within standard rate limits, no new secret required, model selection is flexible.

### Decision 3: Pipeline reorder — `release` before `deploy`
**Choice:** New order: `build → test → release → deploy → announce`.
**Alternatives:**
- Keep `deploy → release` and have the announce step embed the AI notes directly (no link): loses the canonical GitHub Release as a permanent record.
- Run `release` and `deploy` in parallel: fragile if release fails after deploy succeeds.

**Rationale:** Ensures the GitHub Release URL exists when the announce step runs, so the "Full release notes →" link is never broken. Deploy still gates announce, so a failed deploy never produces an announcement.

### Decision 4: Conventional commits enforced via documentation, not tooling
**Choice:** Document the convention in `.github/copilot-instructions.md` and rely on Copilot + reviewer discipline. No commitlint, no pre-commit hook.
**Alternatives:**
- `commitlint` + Husky pre-commit hook: heavy for a single-maintainer hobby project.
- GitHub Action that fails PRs with non-conventional commits: friction without proportional benefit.

**Rationale:** The AI prompt is tolerant of non-conventional commits (it can still summarise them, just less precisely). Hard enforcement is overkill; Copilot guidance shifts the median toward conventional without adding ceremony.

### Decision 5: Filter housekeeping commits in the prompt, not in `git log`
**Choice:** Pass all commits since the last tag to the model and instruct it to ignore `chore`, `ci`, `test`, `build` prefixes in the output.
**Alternatives:**
- Pre-filter via `grep -vE '^(chore|ci|test):'` before sending: brittle if commit messages don't follow the convention.

**Rationale:** Lets the model see context (e.g., a `chore:` that genuinely changed user behaviour can still be surfaced). Cleaner prompt engineering than fragile shell pipelines.

### Decision 6: Discord message structure
**Choice:** Plain markdown message, format:
```
🚀 nyt-scorebot {version} deployed!

{ai-generated-bullets}

📋 Full release notes → {github-release-url}
```
Truncated/summarised to stay under Discord's 2000-character limit. The model is prompted to keep output ≤1500 chars to leave headroom.

**Alternatives:** Discord rich embed — more visual polish but adds JSON complexity and isn't needed for a small audience.

### Decision 7: Announce job uses `appleboy/discord-action` or plain `curl`
**Choice:** Plain `curl` POST to the webhook URL — no third-party action dependency, trivial to maintain.

## Risks / Trade-offs

- **AI hallucination** → Mitigation: include explicit instructions in the prompt to summarise only what's in the commits, never invent features. Discord post links to the canonical GitHub Release for verification.
- **Model rate limits / outage** → Mitigation: if the `release` job fails because Models is unavailable, the entire pipeline fails — no broken half-state. A retry of the workflow recovers.
- **Webhook URL leakage** → Mitigation: webhook is scoped to one channel, can be regenerated in Discord with two clicks. Far smaller blast radius than bot token leakage.
- **First run has no previous tag** → Mitigation: fallback to `git log` of the last N commits when `git describe --tags --abbrev=0` returns nothing.
- **Commit message quality lag** → Trade-off: early releases will have weaker notes until conventional commits become habitual. Acceptable.
- **Spec drift between `release-creation` spec and current `pipeline.yml`** → Mitigation: this change explicitly reconciles the drift by updating both specs (modified capability deltas).
- **2000-char limit on Discord** → Mitigation: prompt instructs model to keep output ≤1500 chars; the announce step truncates with an ellipsis as a backstop.

## Migration Plan

1. Create the Discord webhook (manual, in Discord UI) and add `DISCORD_RELEASE_WEBHOOK_URL` to repo secrets.
2. Land Copilot instruction update (no functional impact).
3. Land workflow changes (release.yml AI step + pipeline.yml reorder + announce.yml).
4. Trigger the pipeline via `workflow_dispatch` to validate end-to-end before the next natural push to main.

**Rollback:** Revert the workflow files; the webhook can be left in place (deleting it is one click in Discord).

## Open Questions

None blocking — all design decisions have been made with the user during exploration.
