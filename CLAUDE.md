# CLAUDE.md

Guidance for Claude Code (and other AI coding assistants) working in this repository.

## What this is

JetBrains IDE plugin that adds a live preview tab for Marp Markdown presentations.
Kotlin / IntelliJ Platform 2024.3+. The behaviour spec is [`SPEC.md`](./SPEC.md) — read
it before making structural changes; it dictates class names, package paths, and the
phase commit history.

## Quick commands

```bash
./gradlew runIde                # sandbox IDE with the plugin loaded
./gradlew test                  # JUnit 5 unit tests
./gradlew check                 # tests + plugin metadata validation
./gradlew verifyPlugin          # Plugin Verifier across recommended IDE versions (~5-10 min)
./gradlew buildPlugin           # produces build/distributions/*.zip
./gradlew publishPlugin         # publish to JetBrains Marketplace (CI only)
```

JDK 17 toolchain. Kotlin 2.0.21. IntelliJ Platform Gradle Plugin 2.1.0.

## Architecture at a glance

```
.md opens
  → MarpFileDetector (frontmatter check, cached on modificationStamp)
  → MarpPreviewFileEditorProvider creates a secondary editor tab
  → MarpServerManager spawns `marp --server <projectRoot>` once per project
  → MarpPreviewPanel (JCEF) loads http://localhost:<port>/<relative-path>
  → DocumentListener + DebouncedRefresher reloads the browser on edits
```

Why `marp --server` and not `marp --watch` or one-shot: no intermediate HTML in the
user's tree, built-in WebSocket auto-reload, one process per project, path-based
routing.

The Node.js / Marp CLI lifecycle is managed by the **application-scoped**
`MarpApplicationService`. The server is **project-scoped** (`MarpServerManager`).
Settings are application-scoped (`MarpSettings`).

## Package layout

| Package | Responsibility |
|---|---|
| `app.marp.jetbrains.util` | `Frontmatter` (YAML parsing), `PathUtil`. Pure logic, easy to test. |
| `app.marp.jetbrains.detector` | `MarpFileDetector` — is this a Marp file? Reads first 2 KB. |
| `app.marp.jetbrains.cli` | `NodeJsDetector`, `ProcessUtil`, `MarpCliInstaller`, `MarpServerManager`. |
| `app.marp.jetbrains.preview` | `MarpPreviewPanel` (JCEF), `MarpPreviewFileEditor[Provider]`, `DebouncedRefresher`. |
| `app.marp.jetbrains.service` | `MarpApplicationService` (app-scoped), `MarpProjectService` (project-scoped). |
| `app.marp.jetbrains.settings` | `MarpSettings` (`PersistentStateComponent`), `Configurable`, `Component`. |
| `app.marp.jetbrains.notification` | `MarpNotifications` — all balloon notifications go through here. |

All registrations live in `src/main/resources/META-INF/plugin.xml`.

## Conventions

- **Kotlin only.** Match the class/method signatures in `SPEC.md` §5.6.
- **No telemetry.** Ever.
- **No exceptions thrown for environmental failures** — return null / `Result.failure`
  and log at DEBUG. The user gets feedback via balloon notifications, not stack traces.
- **`MarpBundle.properties` is the source of truth for user-facing strings.** Add new
  keys there before referencing them in code or XML.
- **JCEF panel lifecycle** is owned by `MarpPreviewPanel` (it `Disposer.register`s the
  browser). Always go through `JBCefBrowser`, never raw CEF.
- **Server scope is per project.** Don't try to share a server across projects, but a
  single server serves all `.md` files inside one project root.
- **Cache root is per OS** — see `MarpCliInstaller.resolveCacheRoot()`.

## Spec / phase tracking

Initial implementation followed `SPEC.md` §7, with one commit per phase:

- `Phase 0: scaffold` — gradle, plugin.xml stub, directory layout
- `Phase 1: file detection` — Frontmatter + MarpFileDetector
- `Phase 2: CLI lifecycle` — Node detection, install, notifications, app service
- `Phase 3: live preview` — server, debouncer, panel, file editor provider
- `Phase 4: settings` — MarpSettings + Configurable + Component
- `Phase 5: release` — README/CHANGELOG/CONTRIBUTING, icon, workflows, templates

Departures from spec, documented for future sessions:

1. `settings.gradle.kts` does **not** set
   `dependencyResolutionManagement { repositoriesMode = FAIL_ON_PROJECT_REPOS }`. The
   spec's combination with project-level repos added by
   `intellijPlatform { defaultRepositories() }` fails the policy; JetBrains' own plugin
   template also omits this block.
2. `MarpPlugin.kt` is listed in §4 but not specified in §5; all registration lives in
   `plugin.xml`, so this file was intentionally not created.

When changing behaviour described in `SPEC.md`, update `SPEC.md` (or note the
divergence here) — otherwise the spec drifts silently from reality.

## Git conventions

- **Author:** `Andreas Pachler <apachler@paan-systems.com>`. The local repo
  `.git/config` is set so commits from this checkout inherit that author.
- **Sign commits.** SSH signing is enabled at the repo level
  (`commit.gpgsign = true`, `gpg.format = ssh`,
  `user.signingkey = /root/.ssh/marp_signing` in the dev sandbox). The matching public
  key is registered on the `apachler` GitHub account as a *signing* key. Vigilant mode
  is on — unsigned commits will show up as "Unverified."
- **Never push to `main` directly.** Develop on
  `claude/implement-spec-phases-oTkfP` (or a successor feature branch). Open a PR.
- **Commit messages:** match the phase wording verbatim for spec phases; otherwise use
  conventional-style prefixes (`fix:`, `feat:`, `ci:`, `docs:`, `chore:`).

## CI / release

| Workflow | Trigger | What it does |
|---|---|---|
| `build.yml` | push to `main`, PR to `main` | `./gradlew check verifyPlugin`, uploads zip as artifact. |
| `release.yml` | tag `v*` | Runs `verify` gate, then builds and creates a **draft** GitHub Release with the zip. Does **not** auto-publish to Marketplace. |
| `release.yml` | `workflow_dispatch` (tag + confirm=yes) | Runs `verify` gate, then `./gradlew publishPlugin` against the chosen tag. |

Marketplace publishing is gated behind a manual `workflow_dispatch` where the user
must type `yes` in the `confirm` input. Tag pushes only ever produce draft GitHub
releases.

**Required GitHub secrets:**

- `JETBRAINS_MARKETPLACE_TOKEN` — plugin upload token.
- `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` — plugin signing.

## Sandbox limitations

This codebase is sometimes worked on inside an Anthropic Claude Code sandbox where
**JetBrains hosts are blocked** (`cache-redirector.jetbrains.com`,
`plugins.jetbrains.com` return 403). Consequences inside that sandbox:

- `./gradlew runIde`, `verifyPlugin`, `buildPlugin` all fail at dependency resolution.
- The pre-publish gate in `release.yml` cannot be exercised locally — rely on GitHub
  Actions for those checks.
- Unit tests under `src/test/...` that only use stdlib + JUnit are still runnable in
  the sandbox via `./gradlew test`, but `compileKotlin` itself depends on the IntelliJ
  Platform jars, so even `test` requires platform access.

When working inside the sandbox: limit yourself to file edits + reasoning, and let the
user verify on their own machine or via GitHub Actions.

## Testing strategy

| Component | Strategy |
|---|---|
| `Frontmatter` | Pure unit tests. |
| `MarpFileDetector` (text-only path) | Pure unit tests on `isMarp(text)`. |
| `MarpFileDetector` (`VirtualFile` path) | Needs IntelliJ test framework — covered manually in `runIde`. |
| `NodeJsDetector` | Sanity tests for bogus overrides; full coverage requires mocking the filesystem. |
| `MarpCliInstaller` | Manual integration check (real npm) — automated test would need a fake npm. |
| `MarpServerManager`, preview panel | Manual smoke test inside `runIde`. |

If you add automated tests for components that depend on `VirtualFile`/`Project`, use
`com.intellij.testFramework.fixtures.BasePlatformTestCase` and the
`testFramework(TestFrameworkType.Platform)` dependency already declared in
`build.gradle.kts`.

## Things to *not* do

- Don't add telemetry, opt-in or opt-out.
- Don't write intermediate HTML/PDF/PPTX files into the user's project — the server
  serves them via HTTP.
- Don't shell out to `node`/`npm` directly from production code; go through
  `MarpCliInstaller` / `MarpServerManager` so cache paths and PATH enrichment are
  consistent.
- Don't change `pluginSinceBuild` without checking that the bundled
  `org.intellij.plugins.markdown` plugin is present on all targeted IDEs of that
  build.
- Don't broaden `--allow-local-files` defaults — it stays opt-in, and the settings UI
  carries a warning label.

## References

- Marp CLI: https://github.com/marp-team/marp-cli
- IntelliJ Platform SDK: https://plugins.jetbrains.com/docs/intellij/welcome.html
- IntelliJ Platform Gradle Plugin 2.x: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
- JetBrains plugin signing: https://plugins.jetbrains.com/docs/intellij/plugin-signing.html
