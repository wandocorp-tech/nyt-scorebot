## 1. Release Workflow — Replace AI Notes with Commit Log

- [x] 1.1 In `release.yml`: remove the `Generate release notes via GitHub Models` step (the `ai_notes` step)
- [x] 1.2 In `release.yml`: rename the `Collect commits since last release` step output file remains `commits.txt`; update the `Create GitHub Release` step to use `body_path: commits.txt` instead of `body_path: release-notes.md`
- [x] 1.3 In `release.yml`: remove the `models: read` permission from the job-level `permissions` block
- [x] 1.4 In `pipeline.yml`: remove the `models: read` permission from the `release` job's `permissions` block

## 2. Announce Workflow — Remove Footer Link

- [x] 2.1 In `announce.yml`: remove the `release_url` input from the `workflow_call` and `workflow_dispatch` inputs blocks
- [x] 2.2 In `announce.yml`: remove the `FOOTER` variable and the footer section from the `Build Discord payload` step; update the `OVERHEAD` calculation to only account for the header and separator (remove `${#FOOTER}`)
- [x] 2.3 In `announce.yml`: update the `MESSAGE` `printf` to omit the footer (`printf '%s\n\n%s' "$HEADER" "$BODY"`)
- [x] 2.4 In `pipeline.yml`: remove the `release_url` input from the `announce` job's `with:` block

## 3. Remove SonarCloud

- [x] 3.1 In `build.yml`: remove the `SonarCloud scan` step entirely
- [x] 3.2 In `build.yml`: remove the `SONAR_TOKEN` entry from the `secrets:` block
- [x] 3.3 In `pipeline.yml`: remove the `SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}` line from the `build` job's `secrets:` block
- [x] 3.4 In `pom.xml`: remove the five `sonar.*` properties (`sonar.maven.plugin.version`, `sonar.projectKey`, `sonar.organization`, `sonar.host.url`, `sonar.coverage.jacoco.xmlReportPaths`)

## 4. Remove Deploy Approval Gate

- [x] 4.1 In `deploy.yml`: remove the `environment: production` line from the `deploy` job

## 5. Pipeline — Scope E2E Test to PRs Only

- [x] 5.1 In `pipeline.yml`: add `if: github.event_name == 'pull_request'` to the `test` job
- [x] 5.2 In `pipeline.yml`: change the `release` job's `needs: test` to `needs: build`

## 6. E2E Test — GHA Step Summary Logging

- [x] 6.1 In `EndToEndTest.java`: add `@Slf4j` annotation to the class (Lombok)
- [x] 6.2 In `EndToEndTest.java`: add import for `discord4j.core.object.entity.channel.GuildMessageChannel`
- [x] 6.3 In `EndToEndTest.java`: update `postTo()` to cast to `GuildMessageChannel` and call the new logging helper before `createMessage()`
- [x] 6.4 In `EndToEndTest.java`: add a private `logMessage(String channelName, String content)` helper that writes a markdown table row to `$GITHUB_STEP_SUMMARY` (append mode), writing the header row first when the file is empty or newly created; fall back to `log.info()` when `GITHUB_STEP_SUMMARY` is not set
