# TODO

Living backlog for **Marp for JetBrains**. Items are tagged by priority and grouped by
milestone. The top section is everything that must happen before v0.1.0 ships;
everything below it can be deferred.

**Priority legend**

- **P0** — Blocking the next milestone. Reliability or correctness defect, or
  prerequisite to publish.
- **P1** — Important. Visible to users; degrades experience but doesn't break it.
- **P2** — Nice-to-have. Quality / polish / future-proofing.
- **P3** — Speculative or research; revisit before committing.

---

## 0. Bootstrap (one-time setup before publishing)

### 0.1 GitHub repository

- [ ] **P0** Set repository description, homepage URL (Marketplace listing once live),
      and topics (`jetbrains-plugin`, `marp`, `markdown`, `presentation`).
- [ ] **P0** Configure repository secrets in `Settings → Secrets and variables → Actions`:
  - [ ] `JETBRAINS_MARKETPLACE_TOKEN` — upload-scoped token from
        https://plugins.jetbrains.com → Account → My Tokens.
  - [ ] `CERTIFICATE_CHAIN` — PEM-encoded plugin signing certificate chain.
  - [ ] `PRIVATE_KEY` — PEM-encoded RSA private key for signing.
  - [ ] `PRIVATE_KEY_PASSWORD` — passphrase for `PRIVATE_KEY`.
- [ ] **P0** Register the SSH signing public key (`marp_signing.pub`) on the
      `apachler` GitHub account as a **Signing Key** (not authentication). Confirm
      Vigilant Mode is enabled (`Settings → SSH and GPG keys → Vigilant Mode`).
- [ ] **P0** Enable branch protection on `main`:
  - [ ] Require PR before merging.
  - [ ] Require status check `build.yml` to pass.
  - [ ] Require signed commits.
  - [ ] Block force-pushes and deletions.
- [ ] **P1** Enable **Dependabot security alerts** and **Dependabot version updates**
      (config already in `.github/dependabot.yml`).
- [ ] **P1** Enable **GitHub Discussions** for user Q&A — friendlier than Issues for
      "how do I?" questions.
- [ ] **P1** Add `CODEOWNERS` (`* @apachler`) so reviews route correctly once others
      contribute.
- [ ] **P2** Configure issue template chooser (currently single bug + feature
      templates — fine for now, revisit after first 20 issues).
- [ ] **P2** Enable GitHub Pages off `/docs` for user-facing documentation once it
      grows past the README.

### 0.2 JetBrains Marketplace

- [ ] **P0** Create an account on https://plugins.jetbrains.com (if not already).
- [ ] **P0** Reserve plugin ID `app.marp.jetbrains` by uploading the first build via
      `./gradlew publishPlugin` (private/draft channel) before competitors do.
- [ ] **P0** Generate the plugin signing keypair per
      https://plugins.jetbrains.com/docs/intellij/plugin-signing.html and stash:
      certificate chain, private key, passphrase.
- [ ] **P0** Generate a permanent Marketplace upload token; record its expiry date in
      this file when it's known.
- [ ] **P1** Prepare Marketplace listing assets:
  - [ ] 3-5 screenshots (1280×800 PNG, dark + light) — primary preview, split view,
        settings page, export menu placeholder.
  - [ ] Long description (Marketplace allows ~6 KB of HTML).
  - [ ] Logo `pluginIcon.svg` review — current one is functional, could be more
        distinctive.
- [ ] **P1** Set Marketplace channels in advance: `default`, `beta`, `nightly`
      (created lazily on first upload).
- [ ] **P2** Decide pricing model: free, paid, or "trial". Default: free, MIT-licensed.

### 0.3 Local development verification (per environment)

- [ ] **P0** Confirm `./gradlew runIde` opens a sandbox IDE with the plugin loaded,
      on the user's primary OS.
- [ ] **P0** Confirm `./gradlew check verifyPlugin` passes on the user's machine
      (this sandbox can't reach JetBrains repos; verification has to happen elsewhere).
- [ ] **P1** Manually verify across at least three IDEs: IntelliJ IDEA Community
      2024.3, WebStorm 2024.3, PyCharm Community 2024.3 (per spec §8 acceptance
      criteria).
- [ ] **P1** Capture the README screenshot (live preview side-by-side with source).
- [ ] **P2** Add a `samples/` directory with one example `.md` file for quick demos
      and integration testing.

---

## 1. Reliability defects to fix before v0.1.0 (uncovered in code audit)

> These were surfaced during a self-review of the current implementation. The plugin
> will *appear* to work in the happy path, but each of these will cause a visible bug
> the first time a user hits the relevant edge case. **Ship-blockers.**

### 1.1 Live preview shows stale content while typing **[P0]**

`MarpPreviewPanel` reloads the JCEF browser when the document changes, but
`marp-cli --server` reads the file from **disk**. Until the document is saved, the
preview re-renders the *old* on-disk content. Result: typing into the editor changes
nothing visible until the user hits Save.

**Fix:** before triggering `browser.reload()`, call
`FileDocumentManager.getInstance().saveDocument(document)` on the EDT. Optionally
guard with a "Auto-save before preview refresh" setting for users who keep autosave
off intentionally.

### 1.2 Preview URL is not URL-encoded **[P0]**

`MarpServerManager.getPreviewUrl()` uses `PathUtil.relativePathInProject` which
returns raw paths. A file named `My Slides/intro.md` produces
`http://localhost:N/My Slides/intro.md` — invalid. Files under directories with
non-ASCII characters break silently.

**Fix:** URL-encode each path segment with `java.net.URI(null, null, "/$rel", null)`
or `URLEncoder.encode` per segment with `+` → `%20` substitution.

### 1.3 First preview after server launch fails with `ERR_CONNECTION_REFUSED` **[P0]**

Process is spawned and the URL is assigned immediately, but marp-cli's HTTP server
doesn't bind on the port until ~200-800 ms later. The first preview request fails
silently.

**Fix:** add a readiness wait in `MarpServerManager`:

1. Spawn process.
2. Poll `Socket(localhost, port).use { }` every 100 ms with a 5 s timeout.
3. Only fire `serverStartedListener` once the port accepts connections.

Alternative: parse marp-cli's stdout for the "Server listening at http://..." line.

### 1.4 Frontmatter changes don't refresh detection **[P0]**

`MarpFileDetector` caches on `VirtualFile.modificationStamp`, which only updates on
save. If a user opens a `.md` without frontmatter, adds `marp: true` lines, and saves
— ✓ works. If they open it, *type* `marp: true`, and don't save, the preview tab
won't appear. Worse: if they remove `marp: true` from an already-open Marp file, the
preview tab keeps running on a non-Marp document.

**Fix:** for open documents, check `Document.modificationStamp` from
`FileDocumentManager.getDocument(file)` instead of `VirtualFile.modificationStamp`,
and bust the cache when the document is modified. Re-evaluate provider
`accept()` on document change via a `FileEditorManagerListener`.

### 1.5 Process tree leaks on Windows **[P0]**

`marp.cmd` on Windows is a batch wrapper that spawns a separate `node.exe` child.
`Process.destroy()` on the wrapper does not propagate the signal — orphan `node`
processes are left running after the IDE closes the project.

**Fix:** use `process.descendants().forEach { it.destroy() }` then
`process.destroyForcibly()`. Add an integration smoke check on Windows CI.

### 1.6 `MarpSettingsComponent.isModified` has buggy operator precedence **[P0]**

```kotlin
marpCliVersionField.text.trim() != state.marpCliVersion &&
    !(marpCliVersionField.text.isBlank() && state.marpCliVersion == "latest") ||
```

The `&&` binds tighter than the surrounding `||`, so this is parsed as
`(A && !B) || ...`, which is not what the comment-equivalent suggests. Dirty-state
detection on the version field is wrong in edge cases.

**Fix:** parenthesise explicitly or refactor into named booleans per field. Add a
test for the dirty-state matrix.

### 1.7 JCEF-disabled placeholder is unhelpful **[P1]**

Current message: *"JCEF is not available in this IDE."* Doesn't tell the user how to
enable it (`Help → Find Action → Choose Boot Java Runtime for the IDE → check
"JBR with JCEF"`).

**Fix:** placeholder text references the exact action name and offers a "Open IDE
runtime settings" button that invokes `ChooseRuntimeAction`.

### 1.8 Preview panel rebuilds component tree on every refresh **[P1]**

`loadOrPlaceholder()` calls `removeAll()` and re-adds the browser every time it
runs. This is unnecessary churn that risks flicker.

**Fix:** track the current "mode" (placeholder vs browser) and only swap when the
mode changes.

### 1.9 Server-started listeners accumulate **[P1]**

`MarpServerManager.addServerStartedListener` is called from each panel, but a panel
that's recreated quickly (e.g. file rename) may leak listeners. Removal is best-effort
through panel dispose, but the panel can be disposed before its listener finishes
firing.

**Fix:** use IntelliJ's `MessageBus` with a topic instead of an ad-hoc listener list,
or wrap listeners in `Disposer.register(this) { manager.removeListener(...) }`.

### 1.10 Refresh schedule races with server startup **[P1]**

If the user is typing while the server is still starting up, debounced refreshes will
fire before the URL is loaded for the first time. Result: refreshes are no-ops until
the page loads, then the first edit may be missed.

**Fix:** queue one trailing refresh inside `serverStartedListener` so the most recent
edit is rendered after startup completes.

---

## 2. Test coverage roadmap

Goal: ≥ 80 % line coverage on pure logic packages (`util`, `detector`, `cli`,
`settings`); manual + smoke coverage on `preview` and `service` (JCEF + ProjectService
are awkward to unit test).

### 2.1 Pure-logic coverage (no IntelliJ test framework needed)

- [ ] **P0** `Frontmatter` — already has 9 tests; add tests for: leading BOM, CRLF
      line endings, trailing whitespace on delimiter, UTF-8 BOM in YAML, very long
      values, escaped quotes inside quoted values.
- [ ] **P0** `PathUtil` — add tests for: file outside project, project with no
      basePath, paths containing `..`, paths with non-ASCII characters, drive letters
      on Windows, symlinked paths.
- [ ] **P0** `NodeJsDetector` — refactor to inject `Path` / env resolver so tests can
      mock the filesystem layout for nvm, fnm, Volta, asdf. Use `jimfs` or a
      `Files`-abstraction.
- [ ] **P0** `MarpCliInstaller` cache-root resolution — table-driven test per OS:
      Linux with `XDG_CACHE_HOME` set / unset, macOS, Windows.

### 2.2 IntelliJ test framework coverage

- [ ] **P0** `MarpFileDetector.isMarp(VirtualFile, Project)` — use
      `BasePlatformTestCase` and `LightVirtualFile` to exercise the caching path.
- [ ] **P1** `MarpSettings` round-trip — store, reload, verify all fields persist.
- [ ] **P1** `MarpSettingsComponent.isModified` — instantiate Swing component
      headlessly, drive each field, assert the dirty-state matrix.
- [ ] **P1** `DebouncedRefresher` — verify single trailing call within a sliding
      window using `Alarm`'s deterministic test mode.

### 2.3 Integration coverage

- [ ] **P1** `MarpServerManager` with a **fake `marp` shell script** that opens a
      socket and serves a stub response. Cover: success, port already in use,
      unexpected exit + restart counter, restart-loop limit.
- [ ] **P1** `MarpCliInstaller` with a **fake `npm` shell script** that creates the
      expected directory layout. Cover: success path, version pinning, reinstall on
      version change, cache clear.
- [ ] **P2** End-to-end smoke test using `runIde` headless mode (`--no-splash
      --headless`) that opens a sample Marp file and verifies the preview URL
      responds 200.

### 2.4 CI integration

- [ ] **P0** Surface test reports as workflow artifacts (`build/reports/tests/`).
- [ ] **P1** Add **JaCoCo** with a coverage threshold (60 % overall, 80 % on
      `util`/`detector`/`cli`/`settings`).
- [ ] **P1** Add a **coverage badge** to README pulled from CI (`shields.io` +
      Codecov, or GitHub-native).
- [ ] **P2** Mutation testing with `pitest` on the pure-logic packages once line
      coverage is established.

---

## 3. v0.1.x patch backlog (post-launch polish)

- [ ] **P1** Status-bar widget showing Marp server status (running on port N / not
      started / install pending) with a click-to-restart action.
- [ ] **P1** Settings: "Detect Node.js now" button that runs detection and shows the
      resolved path inline instead of waiting for the next preview.
- [ ] **P1** Settings: display the currently installed Marp CLI version next to the
      version override field.
- [ ] **P1** Right-click context menu on `.md` files: `Open Marp Preview`,
      `Reload Marp Preview`, available even when auto-open is off.
- [ ] **P1** Keyboard shortcuts: `Ctrl+Alt+M` opens preview, `Ctrl+Alt+R` forces
      reload. Configurable through the Keymap settings.
- [ ] **P1** Honor `--allow-local-files` per-project (currently global setting).
- [ ] **P2** Save scroll/slide position when switching tabs and restore on return.
- [ ] **P2** Re-spawn server with new arguments when `allowLocalFiles` is toggled
      without requiring a project reopen.
- [ ] **P2** Persist port choice per project (`workspace.xml`) so links to local
      preview URLs stay stable across IDE restarts when possible.
- [ ] **P2** Throttle file watcher events from marp-cli to avoid double reloads.
- [ ] **P2** Detect `marp.config.js`, `marp.config.cjs`, `package.json#marp` in the
      project root and pass `--config-file` to `marp --server`.
- [ ] **P3** Add an optional "Open preview in tool window" mode instead of editor
      tab — some users prefer a dedicated panel.

---

## 4. v0.2.0 — Export actions (PDF / PPTX / HTML)

- [ ] **P0** `MarpExportAction` registered under `Tools → Marp → Export…` and the
      `.md` context menu. Sub-actions per format.
- [ ] **P0** Background task that invokes `marp <file> --pdf|--pptx|--html
      --output <dest>` via the same `MarpCliInstaller`-resolved binary.
- [ ] **P0** File-chooser dialog with sensible default output path
      (`<file-basename>.<ext>` next to the source).
- [ ] **P0** Success notification with "Reveal in Files" action.
- [ ] **P0** Failure notification with copyable log.
- [ ] **P1** Bulk export action on a directory selection.
- [ ] **P1** Per-project export defaults (output dir, format) persisted in
      `MarpSettings`.
- [ ] **P1** Chrome/Edge detection: marp-cli needs Chromium for PDF/PPTX. Detect
      Chrome via the same heuristic stack as Node.js (PATH, common install
      locations). Notify with action "Install Chrome" if missing.
- [ ] **P2** "Open in default viewer" follow-up action after a successful export.
- [ ] **P2** Progress indicator showing % during PPTX export (parse marp-cli's
      progress lines).

---

## 5. v0.3.0 — Theme picker + Marp directive autocomplete

### 5.1 Theme picker

- [ ] **P0** Inspector-style UI in the preview toolbar listing built-in themes
      (`default`, `gaia`, `uncover`) plus any `themes/*.css` discovered in the
      project.
- [ ] **P0** Selecting a theme inserts/updates the `theme:` frontmatter key in the
      open document.
- [ ] **P1** Live thumbnail per theme (offscreen render of the first slide).
- [ ] **P2** Theme creator: scaffold a CSS file with comments pointing at Marp's
      theming hooks.

### 5.2 Directive autocomplete

- [ ] **P0** Custom `CompletionContributor` registered for Markdown files where
      `MarpFileDetector.isMarp` returns true. Completes inside frontmatter and inline
      `<!-- _class: lead -->` directives.
- [ ] **P0** Complete known global directives (`marp`, `theme`, `paginate`, `header`,
      `footer`, `class`, `size`, `backgroundColor`, `color`) plus local equivalents
      (`_class`, `_paginate`, etc.).
- [ ] **P1** Value completion for `theme:` from discovered themes, `size:` from
      Marp's preset sizes, `paginate:` from `true`/`false`/`hold`.
- [ ] **P1** Documentation popups (`F1`) with the canonical Marp docs link per
      directive.
- [ ] **P2** Inspection: flag unknown directives with a hint.
- [ ] **P2** Quick-fix to convert global `class:` to local `_class:` and vice versa.

---

## 6. v0.4.0 — New-from-template wizard + slide-jump gutter icons

### 6.1 Template wizard

- [ ] **P0** `New → Marp Presentation` action under the standard New Files menu.
- [ ] **P0** Wizard step 1: pick template (Blank, Pitch deck, Tutorial, Conference
      talk).
- [ ] **P0** Wizard step 2: title, author, theme.
- [ ] **P0** Generated file uses the project's detected indentation and includes
      Marp's recommended frontmatter.
- [ ] **P1** Templates live in `resources/templates/*.md` so they can be overridden
      per-user via plugin settings → "Custom template directory".

### 6.2 Slide-jump gutter icons

- [ ] **P0** `LineMarkerProvider` placing a gutter icon at every `---` slide
      delimiter, with a tooltip showing the slide's heading.
- [ ] **P0** Click the icon → preview scrolls to that slide.
- [ ] **P1** Structure view tree of slides for the side panel.
- [ ] **P1** Breadcrumbs at the top of the editor showing "Slide 5 of 23 — Title".

---

## 7. Future / speculative

- [ ] **P2** Reverse-link: clicking a slide in the preview moves the editor caret to
      that slide's source.
- [ ] **P2** Diagram support indicator: detect Mermaid / PlantUML / Math in source,
      show a status if the configured theme doesn't render them.
- [ ] **P2** Multi-cursor / multi-slide editing helpers (e.g. "wrap selection in new
      slide").
- [ ] **P3** Speaker notes panel: separate view that shows `<!-- presenter notes -->`
      content.
- [ ] **P3** Remote presentation mode: marp-cli's `--preview` flag opens a separate
      OS-level window; wrap this with an IDE action.
- [ ] **P3** Export to Google Slides / Reveal.js integration.
- [ ] **P3** Live coediting (Code-With-Me integration).
- [ ] **P3** AI-assisted: "Generate a slide outline from this Markdown" — only if it
      can be implemented without sending content to a third party by default.

---

## 8. Cross-cutting

### 8.1 Build & CI tooling

- [ ] **P1** Add **detekt** with a baseline tuned to the existing code, fail PRs on
      new issues.
- [ ] **P1** Add **ktlint** formatting (or `intellij.platform.gradle.plugin`'s
      built-in formatter task) and a `.editorconfig`-driven CI check.
- [ ] **P1** Gradle build cache enabled in CI (`gradle/actions/setup-gradle` does
      this by default — verify hit rate).
- [ ] **P2** Renovate or Dependabot config that watches `@marp-team/marp-cli` npm
      releases and opens PRs to bump the default version.
- [ ] **P2** Reproducible builds: pin all Gradle plugins, set
      `org.gradle.caching.debug=true` for diagnostics.

### 8.2 Internationalization

- [ ] **P1** Audit all user-facing strings — anything not yet in
      `MarpBundle.properties` must be moved there.
- [ ] **P1** Switch hard-coded English strings in `MarpSettingsComponent`,
      `MarpPreviewPanel` placeholders, and `MarpNotifications` to bundle lookups.
- [ ] **P2** Translations: Japanese (Marp originated in Japan; native-speaker
      community is the largest), German, Spanish.
- [ ] **P2** RTL layout verification for any future Arabic/Hebrew translation.

### 8.3 Security

- [ ] **P1** Validate user-supplied executable paths in settings against directory
      traversal (e.g. reject `..` segments in nodeJsPath).
- [ ] **P1** Add a SECURITY.md with disclosure email and supported versions.
- [ ] **P2** Add a one-line warning + audit log when the plugin spawns a child
      process from a user-overridden Node.js path.
- [ ] **P2** Use IDE `PasswordSafe` (not plain settings storage) for any future
      tokens / credentials.
- [ ] **P3** Sandbox marp-cli further via a project-scoped temp HOME so a compromised
      npm dependency can't reach the user's `.npmrc` credentials.

### 8.4 Documentation

- [ ] **P0** README screenshot (live preview side-by-side).
- [ ] **P1** README section: troubleshooting (Node not detected, npm corp proxy,
      JCEF disabled, port in use).
- [ ] **P1** Animated GIF or short video showing first-run install + preview.
- [ ] **P2** A `docs/` folder with deeper docs (architecture, contributing, release
      process, signing setup).
- [ ] **P2** Tutorial blog post / Dev.to article on the JetBrains plugin SDK with
      this plugin as a worked example.
- [ ] **P2** Auto-generate KDoc HTML via Dokka and publish to GitHub Pages.

### 8.5 Telemetry / privacy

- [ ] **P0** (Negative requirement, spec §2) Confirm there is no telemetry on every
      release. Add a periodic grep CI step that fails the build if `analytics`,
      `telemetry`, `phoneHome`, or any network call to non-Marp-server hosts appears
      outside of `MarpCliInstaller` (npm install) and `MarpNotifications` (browser
      open).
- [ ] **P1** Add an explicit privacy statement to the README.

### 8.6 Observability / debuggability

- [ ] **P1** Add a "Collect diagnostics" action that writes the resolved Node.js
      path, Marp CLI path, installed version, current server port, last 200 lines of
      marp stderr, and a redacted `MarpSettings` dump to a single text file the user
      can attach to bug reports.
- [ ] **P2** Verbose logging toggle in settings that bumps the plugin's Logger
      categories to DEBUG without requiring `Help → Diagnostic Tools → Debug Log
      Settings`.

---

## How to use this file

1. Pick an unchecked **P0** in section 0 or 1 before doing anything else.
2. Promote items between sections as scope changes — don't leave stale `P3` items
   that you've actually started.
3. When closing an item, prefer linking the resolving commit in the checkbox line
   (`- [x] [Fix link-encoding bug](commit-sha)`) before deleting.
4. Before tagging a release, re-read the version's section in this file. If items
   remain, either move them or accept the deferral explicitly in `CHANGELOG.md`.
