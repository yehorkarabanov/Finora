# CI, Linting and Pre-commit Tooling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run every test and linter on GitHub for each PR and push to `master`, with a fast cached pipeline, and give developers a pre-commit hook and `make install-dev`.

**Architecture:** Spotless (ktlint) and detekt are added to the Gradle build and the whole codebase is cleaned until both, plus Android Lint, pass with no baseline. A committed git hook and a thin Makefile call the same Gradle tasks locally. One GitHub Actions workflow runs three parallel jobs (lint, JVM unit tests, emulator tests) sharing a Gradle cache.

**Tech Stack:** Gradle (Kotlin DSL, version catalog), AGP 9.5.1, Kotlin 2.4.21, JDK 25, Spotless, ktlint, detekt, GitHub Actions, `gradle/actions/setup-gradle`, `reactivecircus/android-emulator-runner`.

**Spec:** `docs/superpowers/specs/2026-11-11-ci-lint-hooks-design.md`

## Global Constraints

- Existing code passes all rules; no baseline file, no blanket disables. Unsatisfiable rules: per-rule config tuning or narrow `@Suppress("Rule")` with a one-line reason.
- No app behaviour changes in the cleanup. Unit tests must stay green after every cleanup commit.
- CI: triggers `pull_request` and `push` to `master`, no path filters; concurrency cancel-in-progress per ref.
- CI jobs on `ubuntu-latest`: lint, unit (`testDebugUnitTest`), instrumented (`connectedDebugAndroidTest`, x86_64, API 33, non-Play image, job timeout, one retry).
- Speed: `gradle/actions/setup-gradle` (cache writes from `master` only), Temurin JDK 25 via `actions/setup-java`, cached AVD snapshot written on `master`, existing `org.gradle.configuration-cache=true` kept.
- Third-party actions pinned to a full version tag or commit SHA.
- Linters cover `src/main`, `src/test`, `src/androidTest`; tests get looser rules (backtick names, magic numbers). Spotless covers `.kt` and `.kts` only.
- Hook lives in `.githooks/pre-commit`, runs on staged `.kt`/`.kts` only, budget ~20-40 s warm, no tests, fails (does not auto-fix) on partially staged files, works from Git Bash on Windows.
- `make test` is JVM only; `make test-android` runs emulator tests and fails clearly with no device.
- No release build, APK upload, signing or secrets in CI.
- Never add a Co-Authored-By trailer to commits (owner's standing rule, overrides defaults).

## Review Focus

- Hook run when a staged file is also partly unstaged: must fail with a message, not stage the extra hunks.
- Hook run with no staged Kotlin files: must exit 0 immediately and fast.
- Hook run in a path or file name containing spaces: staged files must still be passed correctly.
- A PR that only changes `docs/`: all three CI jobs still run and report, so required checks cannot hang.
- `make test-android` with no emulator or device attached: must exit non-zero with a clear message, not hang.

## File Structure

- Modify `gradle/libs.versions.toml`, root `build.gradle.kts`, `app/build.gradle.kts`: add Spotless and detekt plugins and versions.
- Create `.editorconfig`: ktlint style, one source of truth for editors and Spotless.
- Create `config/detekt/detekt.yml`: detekt overrides for Compose/Hilt and tests.
- Modify Kotlin sources under `app/src/**`: cleanup only.
- Create `.githooks/pre-commit`, `Makefile`.
- Create `.github/workflows/ci.yml`.

---

### Task 1: Branch and lint tooling

**Files:**
- Create: `.editorconfig`, `config/detekt/detekt.yml`
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`

**Interfaces:**
- Produces: Gradle tasks `spotlessCheck`, `spotlessApply`, `detekt` (all runnable from the repo root), covering `src/main`, `src/test`, `src/androidTest`.

- [ ] **Step 1: Create branch `chore/ci-lint-hooks` from `master`, then cherry-pick spec commit `8a5b4d2` and this plan's commit onto it.**

Run: `git switch -c chore/ci-lint-hooks master && git cherry-pick 8a5b4d2 1073bd6`
Expected: branch contains both docs and nothing from `denys/feat/ui-refurbished`.

- [ ] **Step 2: Add Spotless (ktlint, `.kt` and `.kts`) and detekt to the version catalog and Gradle files.** Pick the newest releases that support Kotlin 2.4.21, AGP 9.5.1 and JDK 25. detekt reads `config/detekt/detekt.yml` on top of its defaults (`buildUponDefaultConfig = true`).
- [ ] **Step 3: Write `.editorconfig` for ktlint.** Relax rules only where the spec says: function naming for `@Composable` (PascalCase allowed) and backtick test names in test sources.
- [ ] **Step 4: Write `config/detekt/detekt.yml` with Compose/Hilt tuning and looser test-source rules (magic numbers, long methods in tests).** Tune by rule settings, no blanket disables.
- [ ] **Step 5: Verify the tasks exist and run.**

Run: `./gradlew spotlessCheck detekt`
Expected: tasks execute and report violations (a red result is expected here); no configuration errors.

- [ ] **Step 6: Commit** `Add Spotless and detekt configuration`

### Task 2: Formatting cleanup

**Files:**
- Modify: Kotlin sources under `app/src/**` (formatting only)

- [ ] **Step 1: Run `./gradlew spotlessApply`.**
- [ ] **Step 2: Fix by hand anything `spotlessApply` cannot auto-fix until `./gradlew spotlessCheck` prints BUILD SUCCESSFUL.**
- [ ] **Step 3: Verify behaviour unchanged.**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, same test count as on `master`.

- [ ] **Step 4: Commit** `Apply ktlint formatting` (formatting-only commit, kept separate for easy review).

### Task 3: detekt cleanup

**Files:**
- Modify: Kotlin sources under `app/src/**`; `config/detekt/detekt.yml` for threshold tuning

- [ ] **Step 1: Run `./gradlew detekt` and group findings by rule.**
- [ ] **Step 2: Fix findings rule by rule. Where a rule cannot be satisfied without a behaviour change, tune its threshold in `detekt.yml`, or add a narrow `@Suppress("Rule")` with a one-line reason at that site.** Re-run `spotlessCheck` after each batch so the two stay green together.
- [ ] **Step 3: Verify.**

Run: `./gradlew spotlessCheck detekt testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, no baseline file anywhere (`git ls-files | grep -i baseline` empty).

- [ ] **Step 4: Commit** `Fix detekt findings` (split into several commits by rule group if large).

### Task 4: Android Lint cleanup

**Files:**
- Modify: Kotlin/XML under `app/src/**`; create `app/lint.xml` only if a rule needs severity settings

- [ ] **Step 1: Run `./gradlew lintDebug`.** Fix findings; configure severity in `lint.xml` only for rules that cannot be fixed, with a comment explaining each.
- [ ] **Step 2: Verify.**

Run: `./gradlew lintDebug`
Expected: BUILD SUCCESSFUL, no new warnings failing the build.

- [ ] **Step 3: Commit** `Fix Android Lint findings`

### Task 5: Makefile and pre-commit hook

**Files:**
- Create: `.githooks/pre-commit`, `Makefile`

**Interfaces:**
- Consumes: Gradle tasks `spotlessCheck`, `spotlessApply`, `detekt` from Task 1.
- Produces: make targets `install-dev`, `lint`, `format`, `test`, `test-android`; hook script `.githooks/pre-commit` (POSIX sh/bash, executable bit set via `git update-index --chmod=+x`).

- [ ] **Step 1: Write the Makefile.** `install-dev` runs `git config core.hooksPath .githooks` and prints a short success line; `lint` = `./gradlew spotlessCheck detekt`; `format` = `./gradlew spotlessApply`; `test` = `./gradlew testDebugUnitTest`; `test-android` checks `adb devices` for an attached device first and exits non-zero with a clear message otherwise, then runs `./gradlew connectedDebugAndroidTest`.
- [ ] **Step 2: Write the hook.** Read staged `.kt`/`.kts` with `git diff --cached --name-only --diff-filter=ACM -z` (NUL-separated, handles spaces); exit 0 at once if none. If any staged file also has unstaged changes, exit 1 with a message to stage or stash. Otherwise run Spotless apply and detekt on those files, `git add` the fixed files, and exit non-zero if detekt fails.
- [ ] **Step 3: Verify each Review Focus case by hand.**

Run, in order: (a) `make install-dev`; (b) stage a badly formatted `.kt` file and `git commit` → formatted and committed, or blocked on detekt; (c) stage a file, then edit it again without staging and `git commit` → fails with the stage-or-stash message, index unchanged; (d) stage only a `.md` file and `git commit` → succeeds immediately; (e) stage a file whose name contains a space and a `.kt` extension and commit → processed; (f) `make test-android` with no device → non-zero exit and message.
Expected: each outcome as described.

- [ ] **Step 4: Commit** `Add pre-commit hook and Makefile`

### Task 6: CI workflow

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: Gradle tasks from Tasks 1-4 and the Makefile target names (jobs call `./gradlew` directly, not `make`).

- [ ] **Step 1: Write `ci.yml` with the triggers, concurrency and three jobs from Global Constraints.** All jobs: checkout, Temurin 25, `gradle/actions/setup-gradle` (cache write only on `master`). `lint` runs `spotlessCheck detekt lintDebug`; `unit` runs `testDebugUnitTest`; `instrumented` enables KVM, runs `reactivecircus/android-emulator-runner` with API 33, x86_64, headless, animations off, AVD snapshot cache saved on `master` only, one retry, and a job timeout.
- [ ] **Step 2: Lint the workflow, then commit `Add CI workflow`.**

Run: `actionlint .github/workflows/ci.yml` (or `gh workflow` equivalent if actionlint is not installed)
Expected: no errors.

- [ ] **Step 3: Push the branch, open a PR to `master`, and confirm all three jobs go green.**

Run: `gh pr checks --watch`
Expected: lint, unit, instrumented all pass. Note timings.

- [ ] **Step 4: Re-push a trivial change to confirm caches work.**

Expected: second run is noticeably faster (Gradle cache hit in logs; the emulator snapshot is only written on `master`, so confirm that after merge).

- [ ] **Step 5: Add the timing results to the PR description.**

---

## Self-Review notes

- Spec coverage: CI (Task 6), formatting plus quality plus Android Lint (Tasks 1-4), hook (Task 5), Makefile (Task 5), no baseline (Task 3 verification), order of work matches spec.
- Types and names: task names `spotlessCheck`, `spotlessApply`, `detekt`, `lintDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` used consistently.
- Not covered by tests: the emulator snapshot cache can only be confirmed after the first run on `master`.
