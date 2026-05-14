# HAPPY-CODING — Engineering Backlog

Persistent backlog of worthwhile improvements that were **deliberately deferred**
during the OSS maturity audit on 2026-05-14. Each item lists priority,
rationale, implementation notes, expected impact, and rough effort.

This file complements `TODO.md` (product/feature backlog) — it focuses on
infrastructure, supply-chain, and engineering-practice work.

**Priority legend**

- **P0** — High value, low risk. Should land in the next maintenance cycle.
- **P1** — Useful, moderate effort or moderate risk.
- **P2** — Nice-to-have polish or speculative.

---

## Code quality & static analysis

### Add detekt for Kotlin static analysis

- **Priority**: P1
- **Rationale**: Catches common Kotlin smells (complexity, naming, exception
  swallowing, performance pitfalls) that CodeQL doesn't surface. Industry
  standard for Kotlin projects.
- **Implementation notes**:
  - Add `io.gitlab.arturbosch.detekt` plugin to `gradle/libs.versions.toml`
    (pin to a 1.23.x release) and apply in `build.gradle.kts`.
  - Generate `detekt.yml` via `./gradlew detektGenerateConfig` and trim it to
    the rules you actually want — avoid wholesale defaults.
  - Wire `detekt` into the `check` task with `ignoreFailures = true` on the
    first run so the baseline report lands without blocking CI.
  - Add a CI step uploading SARIF to GitHub Security (`reports/sarif`).
  - Once warnings are at zero, flip `ignoreFailures = false`.
- **Expected impact**: Earlier feedback on Kotlin-specific issues; SARIF
  results show in the Security tab; small but durable maintainability win.
- **Effort**: 2–4 hours including baseline triage.

### Add ktlint formatting check

- **Priority**: P2
- **Rationale**: Enforces a consistent Kotlin style across contributors so
  formatting never becomes a review topic. Pairs well with detekt.
- **Implementation notes**:
  - Use `org.jlleitschuh.gradle.ktlint` (or the detekt-formatting ruleset).
  - Run only `ktlintCheck` in CI; reserve `ktlintFormat` for local fixup.
- **Expected impact**: Eliminates style nits in PR review.
- **Effort**: 1–2 hours.

---

## Supply-chain hardening

### Pin all third-party actions to commit SHAs

- **Priority**: P1
- **Rationale**: OpenSSF Scorecard's `Pinned-Dependencies` check expects SHA
  pinning. Major-version tags can be silently re-pointed by their maintainers,
  which is the typical supply-chain compromise pattern (`tj-actions/changed-files`
  incident, 2025).
- **Implementation notes**:
  - Replace e.g. `actions/checkout@v4` with `actions/checkout@<sha> # v4.x.y`.
  - Dependabot understands the `# version` trailing comment and will keep both
    SHA and comment in sync.
  - First-party (`actions/*`, `github/*`) actions are lower risk and can stay
    on major tags if you'd rather minimise churn — but Scorecard will still
    flag them.
- **Tradeoff**: Increases Dependabot PR volume. Mitigation: dependabot.yml
  already groups `actions/*` updates into one PR.
- **Expected impact**: +1 to OpenSSF Scorecard score; mitigates a real
  supply-chain vector.
- **Effort**: 1 hour the first time; ongoing maintenance handled by Dependabot.

### Generate SBOM on every release

- **Priority**: P1
- **Rationale**: Required for SLSA Level 1+, in some procurement processes
  (FedRAMP, EU CRA), and good practice generally. The Gradle CycloneDX plugin
  produces a CycloneDX JSON of all resolved dependencies.
- **Implementation notes**:
  - Add `org.cyclonedx.bom` plugin (`org.cyclonedx:cyclonedx-gradle-plugin`).
  - Add a `cyclonedxBom` task invocation to the `draft-github-release` job and
    upload `build/reports/bom.json` as a release asset alongside the `.zip`.
- **Expected impact**: Downstream consumers can audit transitive deps without
  rebuilding; satisfies supply-chain procurement checklists.
- **Effort**: 1–2 hours.

### Sign release artifacts with cosign / Sigstore

- **Priority**: P2
- **Rationale**: The marketplace `.zip` is already JetBrains-signed for
  Marketplace distribution. Cosign keyless signing on the GitHub release
  artifact provides a separate, verifiable provenance chain rooted in OIDC.
- **Implementation notes**:
  - Use `sigstore/cosign-installer@v3` + `cosign sign-blob --yes` in the
    release job, with `id-token: write` permission.
  - Publish `.zip.sig` and `.zip.cert` alongside the `.zip` on the GitHub
    Release.
- **Expected impact**: Independent supply-chain verification path for the
  GitHub Release download.
- **Effort**: 2 hours.

### Add `actions/attest-build-provenance` to releases

- **Priority**: P2
- **Rationale**: Produces a SLSA v1.0 provenance attestation linked to the
  workflow run that built the artifact. Stronger than a plain signature.
- **Implementation notes**:
  - Add the action after `buildPlugin`, before the release upload.
  - Requires `id-token: write` and `attestations: write` permissions.
- **Effort**: 1 hour.

### Tighten OSV-Scanner to fail on high/critical CVEs

- **Priority**: P1
- **Rationale**: The current `osv-scanner.yml` is set to never fail builds so
  the first run doesn't break CI on day one. Once the baseline is clean, the
  workflow should gate PRs on new high/critical vulnerabilities.
- **Implementation notes**:
  - Switch from the reusable workflow to a job invocation of
    `google/osv-scanner-action@<sha>` with explicit `fail-on-vuln: true` and
    a severity filter, or invoke `osv-scanner` directly with
    `--config osv-scanner.toml` to tune ignored CVEs.
- **Effort**: 1 hour once baseline is clean.

---

## CI/CD & build

### Add Windows + macOS to the CI build matrix

- **Priority**: P1
- **Rationale**: The plugin has OS-specific code paths: `NodeJsDetector` does
  Windows-installer lookups, `ProcessUtil` does descendant-process kill on
  Windows, and `MarpServerManager` parses Windows path semantics. Currently
  only Ubuntu is exercised in CI.
- **Implementation notes**:
  - Convert `build.yml`'s `runs-on` to a matrix:
    `os: [ubuntu-latest, windows-latest, macos-latest]`.
  - First run will likely surface path-handling and EOL issues — budget
    triage time. Consider gating macOS behind `fail-fast: false` first.
- **Tradeoff**: 3× CI minutes per push. Acceptable for a low-volume repo.
- **Expected impact**: Catches OS regressions before users do.
- **Effort**: 30 min config + however long triage takes.

### Add JetBrains Plugin Verifier multi-version matrix

- **Priority**: P2
- **Rationale**: `verifyPlugin` currently only checks against
  `pluginVerification { ides { recommended() } }` (the latest stable IDE).
  Verifying against the lowest supported (`pluginSinceBuild = 243`) and an
  EAP build catches breakage earlier.
- **Implementation notes**:
  - In `build.gradle.kts`, expand `intellijPlatform.pluginVerification.ides`
    to include `ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3")`,
    `ide(IntelliJPlatformType.IntellijIdeaCommunity, "LATEST-EAP-SNAPSHOT")`.
  - Run verification only on `push to main` and `schedule`, not every PR —
    it's slow.
- **Effort**: 1–2 hours.

### Adopt release-please for automated changelogs and tagging

- **Priority**: P2
- **Rationale**: Project already uses Conventional Commits and Keep-a-Changelog
  format. `release-please` reads CC commits, updates `CHANGELOG.md`,
  bumps `gradle.properties:pluginVersion`, and opens a PR. Merging that PR
  cuts a tag, which triggers the existing release workflow.
- **Implementation notes**:
  - Add `googleapis/release-please-action@<sha>` workflow with
    `release-type: simple` and an `extra-files` entry for `gradle.properties`.
  - Switch human commit discipline to strict Conventional Commits (already
    largely there).
- **Tradeoff**: Slightly less hand-curated changelog wording in exchange for
  automation.
- **Effort**: 2 hours including dry-run validation.

### Add hardening via step-security/harden-runner

- **Priority**: P2
- **Rationale**: Audits egress from CI runners and detects unexpected
  network calls — defense-in-depth against compromised dependencies.
- **Implementation notes**:
  - Add `step-security/harden-runner@<sha>` as the first step of every job,
    starting with `egress-policy: audit`. Promote to `block` after the
    baseline allowlist is captured from the audit logs.
- **Tradeoff**: Adds a third-party dependency to every job. Worth it given
  the threat model.
- **Effort**: 1 hour for audit setup + ~1 week observation before tightening.

### Cache Gradle wrapper distribution

- **Priority**: P2
- **Rationale**: `gradle/actions/setup-gradle@v4` already caches the daemon
  and dependencies. Adding `actions/setup-java` Gradle cache key reduces
  cold-start time further on PR branches.
- **Effort**: 15 min, marginal ROI.

---

## Testing

### Add UI / integration tests for JCEF preview pane

- **Priority**: P2
- **Rationale**: `MarpPreviewPanel`, `MarpPreviewFileEditor`, and the JCEF
  load path are currently outside the >80% coverage gate by design (CLAUDE.md
  documents why). They are also the most user-visible surface.
- **Implementation notes**:
  - Use `IntelliJ Platform Test Framework` with `BasePlatformTestCase` for
    project/editor fixtures. JCEF cannot run in headless CI on Linux without
    extra setup — consider using `HeavyPlatformTestCase` with a stub browser.
  - Alternative: skip JCEF entirely and assert that the panel emits the
    correct `URL`s and reload signals against a mock server.
- **Effort**: 1–2 days for a useful baseline.

### Add a `samples/` directory with sample decks

- **Priority**: P1
- **Rationale**: TODO §0.3 already calls for this. Doubles as a manual smoke
  test target and as an example for README screenshots.
- **Effort**: 1 hour.

### Track flaky-test runs and quarantine the noisy ones

- **Priority**: P2
- **Rationale**: With JCEF + Project fixtures, intermittent failures are
  likely. A `@FlakyTest` annotation + a separate CI job that runs them with
  retries keeps the main pipeline green.
- **Effort**: Wait until flakiness materialises.

---

## Documentation

### Capture screenshots and embed in README + Marketplace listing

- **Priority**: P1
- **Rationale**: README has no visual; Marketplace listing absolutely needs
  3–5 screenshots before publishing. TODO §0.2 already tracks this.
- **Implementation notes**:
  - 1280×800 PNG, light + dark, of: split preview, settings page, status-bar
    widget, context menu.
  - Store under `docs/screenshots/` and reference with relative paths.
- **Effort**: 1 hour.

### Stand up GitHub Pages documentation site

- **Priority**: P2
- **Rationale**: Once README exceeds ~500 lines or screenshots start to
  dominate, moving usage docs to a Pages site (mkdocs-material, or just
  `/docs` Jekyll) keeps the README scannable.
- **Implementation notes**: Defer until docs actually outgrow README. Avoid
  premature site infrastructure.
- **Effort**: Half a day when triggered.

### Architecture Decision Records (ADRs)

- **Priority**: P2
- **Rationale**: Repo already has substantial architectural rationale
  scattered in commit messages and `CLAUDE.md`. Promoting the load-bearing
  decisions (e.g. "why we don't write HTML files to the project", "why JCEF
  not bundled browser") to `docs/adr/NNN-*.md` keeps them findable.
- **Implementation notes**: Use `adr-tools` format. One ADR per non-obvious
  decision, not one per change.
- **Effort**: 30 min per ADR; start with 3–5 of the load-bearing ones.

### Add ROADMAP.md

- **Priority**: P2
- **Rationale**: TODO.md is comprehensive but ~22 KB — it's a working backlog,
  not a public roadmap. A short ROADMAP.md (one screenful) signalling
  near/mid/long-term direction helps prospective contributors and
  Marketplace visitors.
- **Effort**: 30 min.

---

## Repository ops

### Configure branch protection on `main`

- **Priority**: P0
- **Rationale**: TODO §0.1 lists this as a P0 already and is the load-bearing
  control behind the entire CI / signed-commits posture. Cannot be set via
  files in the repo — must be configured in GitHub Settings.
- **Implementation notes**:
  - Require PR before merging.
  - Require `build / build` status check.
  - Require `CodeQL / Analyze (java-kotlin)` status check.
  - Require signed commits.
  - Block force-pushes and deletions.
  - Optionally: require linear history.
- **Effort**: 5 min in the UI.

### Enable GitHub Discussions

- **Priority**: P1
- **Rationale**: `SUPPORT.md` and the issue template config both link to
  Discussions. The links 404 until the feature is enabled in repo settings.
- **Effort**: 1 click in `Settings → General → Features`.

### Enable Private Vulnerability Reporting

- **Priority**: P1
- **Rationale**: The issue template config now links security reporters to
  `/security/advisories/new`, which requires Private Vulnerability Reporting
  to be enabled (`Settings → Code security and analysis`).
- **Effort**: 1 click.

### Set repository description, homepage URL, topics

- **Priority**: P1
- **Rationale**: Improves discoverability on GitHub search. TODO §0.1 has
  the specific topic suggestions already.
- **Effort**: 5 min in the UI.

### Set up a `.github/FUNDING.yml`

- **Priority**: P2
- **Rationale**: Only valuable if you want to accept sponsorship. Trivial to
  add; trivial to skip.
- **Effort**: 5 min.

### Add `secret_scanning_push_protection` and signed-commit required status

- **Priority**: P1
- **Rationale**: Secret scanning is on by default for public repos; push
  protection blocks accidentally-committed secrets at the push boundary
  rather than after the fact. Pairs with the signed-commits requirement
  already on the TODO.
- **Effort**: 1 click.

---

## Observability & monitoring

### Add error reporting / log aggregation guidance

- **Priority**: P2
- **Rationale**: The plugin explicitly does not phone home (good). For users
  who hit issues, a documented "how to attach IDE logs" flow in the bug
  report template is the right level of observability for an IDE plugin.
  Don't add telemetry — but do streamline log capture.
- **Implementation notes**:
  - Add a `Help → Marp → Collect Diagnostics` action that zips
    `idea.log` lines tagged `[Marp]` plus the Marp CLI cache directory layout
    (paths only, not contents).
- **Effort**: Half a day.

---

## Scalability / production readiness

### Bound the Marp CLI cache disk usage

- **Priority**: P2
- **Rationale**: `MarpCliInstaller` writes to a per-OS plugin cache directory
  and never garbage-collects. Multiple plugin upgrades can accumulate.
- **Implementation notes**:
  - On install, delete sibling version directories older than N days.
  - Settings: max retained versions (default 2).
- **Effort**: 2–3 hours.

### Validate that `marp --server` actually responds before declaring ready

- **Priority**: P1
- **Rationale**: Already partially done in `MarpServerManager` (port-probe
  before notifying listeners). Worth verifying the probe handles slow boot
  (npm postinstall steps on first run) without false-negative.
- **Effort**: 30 min review + integration test if practical.

---

## Compliance

### Add `.well-known/security.txt` equivalent in repo

- **Priority**: P2
- **Rationale**: `SECURITY.md` already covers disclosure. A `security.txt`
  at the repo root or `/docs/security.txt` is purely for automated
  vulnerability disclosure tooling (e.g. `securitytxt.org` scanners). Adds
  zero maintenance burden.
- **Effort**: 5 min.

### Confirm MIT licence compatibility of all transitive deps

- **Priority**: P1
- **Rationale**: `dependency-review.yml` already denies AGPL on PRs, but the
  baseline isn't audited. One-shot scan with `org.cyclonedx.bom` + the
  resulting SBOM piped through `licensee` or `pivotal-cf/LicenseFinder`.
- **Effort**: 1–2 hours, one-shot.

---

## Out-of-scope / explicitly rejected

These were considered and not added — recording them so they aren't
re-evaluated indefinitely.

- **Codecov / Coveralls integration** — JaCoCo XML is already produced and
  uploaded as an artifact. Adding an external service costs a third-party
  dependency for marginal benefit on a private-maintainer project. Revisit
  if there are multiple contributors comparing coverage diffs across PRs.
- **Docker / containerisation** — this is an IDE plugin, not a service.
  Reproducibility comes from the Gradle wrapper, not from a container.
- **Renovate** — Dependabot already covers Gradle + Actions and is now
  grouped. Renovate would add features but also configuration surface; no
  net win for a single-ecosystem repo.
- **Conventional-commits enforcement via commitlint hook** — the maintainer
  already writes conventional commits. Enforcement via a husky/precommit
  hook adds setup friction for external contributors. The release-please
  adoption path (above) makes commit hygiene self-enforcing without a
  client-side hook.
