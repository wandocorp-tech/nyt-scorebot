## 1. Prerequisites (manual setup, outside CI)

- [ ] 1.1 Create a new dedicated Discord channel for release announcements
- [ ] 1.2 Create a Discord webhook for that channel (Server Settings → Integrations → Webhooks → New Webhook), name it "nyt-scorebot releases"
- [ ] 1.3 Add the webhook URL as repository secret `DISCORD_RELEASE_WEBHOOK_URL` in GitHub repo settings

## 2. Commit message convention

- [x] 2.1 Add a "Commit message conventions" section to `.github/copilot-instructions.md` documenting the prefix set (`feat`, `fix`, `refactor`, `chore`, `ci`, `test`, `docs`, `build`) and short semantics for each
- [x] 2.2 Add a one-line example in the same section showing the format `<type>: <subject>`

## 3. AI release notes in release.yml

- [x] 3.1 Add `permissions: models: read` (alongside existing `contents: write`) to the release job in `.github/workflows/release.yml`
- [x] 3.2 Add a checkout step with `fetch-depth: 0` so the full git history and tags are available
- [x] 3.3 Add a step that determines the previous tag (`git describe --tags --abbrev=0` with fallback to last 50 commits if no tag exists) and produces the commit list as a step output
- [x] 3.4 Add a step that POSTs to `https://models.github.ai/inference/chat/completions` using `GITHUB_TOKEN`, model `openai/gpt-4o-mini`, with a system prompt instructing: summarise commits as user-facing release notes, omit `chore`/`ci`/`test`/`build` unless user-visible, output bullet points, ≤1500 chars, never invent features
- [x] 3.5 Capture the model response into an environment file / step output (handle JSON escaping safely)
- [x] 3.6 Update the `softprops/action-gh-release@v2` step: set `generate_release_notes: false` and pass the AI-generated notes via `body:`
- [ ] 3.7 Test the release workflow standalone via `workflow_dispatch` with a test version tag

## 4. Pipeline reorder

- [x] 4.1 In `.github/workflows/pipeline.yml`, change the `release` job to depend on `test` (`needs: test`) instead of `deploy`
- [x] 4.2 Change the `deploy` job to depend on `release` (`needs: release`) instead of `test`
- [x] 4.3 Verify both `release` and `deploy` jobs retain their `if: github.ref == 'refs/heads/main'` gates
- [x] 4.4 Confirm the JAR artifact (`app-jar`) remains downloadable by both `release` and `deploy` (it should — they both use `actions/download-artifact@v4`)

## 5. Discord announce job

- [x] 5.1 Create `.github/workflows/announce.yml` as a reusable workflow (`workflow_call`) accepting inputs `version` (string) and `release_url` (string), and secret `DISCORD_RELEASE_WEBHOOK_URL`
- [x] 5.2 Add a step that fetches the GitHub Release body for the version tag using `gh release view <tag> --json body --jq .body`
- [x] 5.3 Add a step that builds the Discord message payload: header `🚀 nyt-scorebot {version} deployed!`, blank line, AI bullet list, blank line, `📋 Full release notes → {release_url}`
- [x] 5.4 Add length guard: if the assembled message exceeds 2000 chars, truncate the bullet section to fit and append `…`
- [x] 5.5 Add a step that POSTs the payload to `${{ secrets.DISCORD_RELEASE_WEBHOOK_URL }}` via `curl -X POST -H "Content-Type: application/json" -d <payload>`
- [x] 5.6 Wrap the curl in `continue-on-error: true` and log non-2xx responses so a Discord outage doesn't fail the pipeline
- [x] 5.7 In `pipeline.yml`, add an `announce` job with `needs: deploy`, `if: github.ref == 'refs/heads/main'`, calling `./.github/workflows/announce.yml` with the version and release URL, passing the `DISCORD_RELEASE_WEBHOOK_URL` secret

## 6. Spec reconciliation

- [x] 6.1 (Performed by archive step, no manual spec edits during implementation — leave specs in `openspec/specs/` to be updated when the change is archived)

## 7. Verification

- [ ] 7.1 Push to a feature branch and open a PR — confirm only `build` and `test` jobs run (no release/deploy/announce)
- [ ] 7.2 Merge to `main` — confirm full pipeline runs in order `build → test → release → deploy → announce`
- [ ] 7.3 Verify the GitHub Release for the new tag exists with AI-generated notes as its body
- [ ] 7.4 Verify the dedicated Discord channel received the formatted announcement with header, bullets, and a working link to the GitHub Release
- [ ] 7.5 Manually break the webhook URL secret temporarily (or use a clearly invalid URL in a test branch) to confirm the announce job logs the failure but the pipeline still reports success

## 8. Documentation

- [x] 8.1 Update `README.md` (or the relevant section) noting the new release-notes channel, the webhook secret requirement, and the pipeline order
- [x] 8.2 Add a brief operator note explaining how to rotate the Discord webhook if it is leaked
