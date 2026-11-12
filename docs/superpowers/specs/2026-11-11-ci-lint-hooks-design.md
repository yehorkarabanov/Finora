# CI, linting and pre-commit tooling: design

## Intent and why

Finora (Android, Kotlin, Compose, Hilt, Room, Gradle, JDK 25 toolchain) has about 80 JVM
unit test files in `app/src/test` and 20 emulator tests in `app/src/androidTest`, but no
automation: nothing runs them on GitHub, no linter or formatter exists, and nothing guards
commits. The owner is used to Python's ruff (many rules, autofix) plus pre-commit and wants
the same discipline for Kotlin: broken or unformatted code should be caught before it is
committed, and again on every pull request, as fast as possible.

## Goals

1. A GitHub Actions workflow that runs **all** tests (JVM unit and emulator) and the linters
   on every pull request and on pushes to `master`, optimised for speed (caching, parallel jobs).
2. Both **formatting** enforcement (with autofix) and **code-quality rules** (bug and smell
   detection), in the spirit of ruff.
3. A **pre-commit hook** that runs the formatter and static analysis on staged Kotlin files.
4. A **Makefile** with `make install-dev` (activates the hook) plus thin wrappers
   (`lint`, `format`, `test`) over Gradle.
5. The existing code **passes all rules from day one**: all current violations are fixed in a
   cleanup pass. No baseline file that hides them.

## Non-goals

- No release build, APK or artifact upload, no deploy, no signing in CI.
- No emulator matrix over several API levels.
- No branch-protection configuration (the owner will mark the checks required in GitHub
  settings themselves).
- No change to app behaviour. The cleanup is formatting and rule-driven edits only.

## Anti-goals (would make this a failure even if it technically works)

- CI that is slow because nothing is cached or because jobs run serially.
- Hiding existing violations behind a baseline or blanket suppressions.
- A hook so slow or noisy that people bypass it with `--no-verify`.
- CI starting red: tooling and cleanup land before the workflow is enforced.

## Decisions made with the owner

- Scope of tests: everything, JVM unit and emulator tests.
- The workflow runs tests and linters, nothing else (no build or lint-extras beyond that).
- Linting covers both formatting and code-quality rules, enforced locally (hook) **and** in CI.
- Existing violations are fixed up front; no baseline.
- A Makefile with `make install-dev` is wanted. The owner develops on Windows 11, where `make`
  is not preinstalled (install via scoop or choco); the Makefile only wraps `./gradlew`.

## Design (my calls, unless marked as the owner's)

### CI: `.github/workflows/ci.yml`

- Triggers: `pull_request` and `push` to `master`, no path filters. Concurrency group per ref with `cancel-in-progress`.
- Three parallel jobs on `ubuntu-latest`:
  1. **lint**: Spotless (ktlint) check, detekt, Android Lint.
  2. **unit**: `./gradlew testDebugUnitTest`.
  3. **instrumented**: `connectedDebugAndroidTest` on an x86_64 emulator, API 33 (= `minSdk`),
     via `reactivecircus/android-emulator-runner` with KVM enabled, headless, animations off.
- Speed levers:
  - `gradle/actions/setup-gradle`: caches dependencies, wrapper and build cache; cache writes
    only from `master`, PRs read-only. The existing `org.gradle.configuration-cache=true` is used.
  - JDK 25 (Temurin) installed with `actions/setup-java` so Gradle does not download a
    toolchain through foojay each run.
  - Cached AVD snapshot (written on `master`) so later runs skip cold boot.
  - Use the runner's preinstalled Android SDK; accept licenses once.
- Pin third-party actions to a full version tag or commit SHA.

### Linting

- **Formatting:** Spotless with ktlint (autofix via `spotlessApply`, check via `spotlessCheck`).
- **Static analysis:** detekt with the default rule set plus project config
  (`config/detekt/detekt.yml`), tuned for Compose and Hilt so idiomatic code
  (for example PascalCase `@Composable` functions, large Compose function bodies) is not flagged.
  Tuning must be by config rule settings, not by blanket disabling.
- **Android Lint:** run in CI's lint job with warnings as the project already tolerates;
  see "Left to the builder".
- Shared `.editorconfig` for ktlint style so editors and the formatter agree.

### Pre-commit hook

- Committed in `.githooks/pre-commit`; activated by `make install-dev`
  (`git config core.hooksPath .githooks`).
- Runs on staged `.kt` and `.kts` files only: Spotless apply (re-staging fixed files) then
  detekt on those files. Must be fast enough to use daily; no tests, no Android Lint in the hook.
- Must work from Git Bash on Windows (the owner's environment) and on Linux and macOS.

### Makefile

`install-dev`, `lint`, `format`, `test`; each wraps `./gradlew`. `make install-dev` also
prints a short success message and does nothing destructive.

### Order of work

1. Add Spotless, detekt, `.editorconfig` and detekt config.
2. Run the formatter, then fix remaining detekt and Android Lint findings across the codebase
   until all checks pass cleanly with no baseline.
3. Add the hook and Makefile.
4. Add the CI workflow and verify it green on a pull request.

## Calls made by the author (flagged for the owner to check)

- Build needs no secrets: no `google-services.json`, keystore or API keys exist in the app
  module, so CI needs no secrets and no signing. The only network-flavoured test is
  `RateRepositoryTest`; the builder should confirm it does not need real network access.
- `make test` runs JVM unit tests only. `make test-android` runs the emulator tests and
  fails with a clear message if no device is attached. The hook never runs tests.
- Emulator job: default (non-Play) x86_64 system image, a job timeout, and one retry on
  failure to absorb emulator flakiness. Whether the check is marked required is the owner's choice.
- Linters cover `src/main`, `src/test` and `src/androidTest`. Test sources get looser
  rules (backtick test names, magic numbers). Spotless covers `.kt` and `.kts` only.
- Hook budget: about 20-40 seconds with a warm Gradle daemon is acceptable.
- Partially staged files: the hook does not auto-fix and re-stage them. It runs the checks
  and fails with a message telling the developer to stage or stash, so unstaged hunks are
  never swept into a commit.
- The workflow has no path filters, so required checks never hang on docs-only PRs. Instead,
  the jobs themselves are cheap and cached.
  (This replaces the earlier "docs-only changes skipped" line.)

- Unsatisfiable rules (my call, owner not yet confirmed): per-rule threshold tuning in the
  detekt config plus narrow `@Suppress("Rule")` at individual sites with a one-line reason.
  No blanket disables, no baseline.
- Branch (my call, owner not yet confirmed): the implementation lands on its own branch off
  `master`, not on `denys/feat/ui-refurbished`, to avoid mixing with UI work.

## Left to the builder

- Exact tool versions, compatible with AGP 9.5.1, Kotlin 2.4.21 and JDK 25.
- Exact detekt and ktlint rule tuning (subject to the no-blanket-disable rule above).
- Whether Android Lint needs a `lint.xml` or baseline-free configuration to pass.
- Exact emulator system image and runner options, and cache key design.
- Hook implementation details (how staged files are passed to Gradle, runtime budget).
- Where individual cleanup fixes are committed (suggestion: separate commits for formatting-only
  changes versus rule fixes so review stays easy).
