# Security Policy

## Reporting a vulnerability

If you discover a security issue in **Marp for JetBrains**, please report it
privately rather than opening a public issue.

**Email:** `apachler@paan-systems.com`
**Subject prefix:** `[security] marp-jetbrains-plugin`

Please include:

- A description of the vulnerability and the impact you believe it has.
- Steps to reproduce, ideally with a minimal proof-of-concept.
- The plugin version (Settings → Plugins → Marp for JetBrains) and the IDE +
  version you observed it on.
- Whether you intend to disclose it publicly, and on what timeline.

I'll acknowledge receipt within **3 business days** and aim to confirm a
remediation plan within **14 days**.

## Supported versions

Security fixes are released against the latest published Marketplace version
only. The plugin is pre-1.0 and breaking changes can occur in any minor; please
keep the plugin updated to receive fixes.

| Version  | Supported       |
|----------|-----------------|
| `0.1.x`  | yes (current)   |
| `< 0.1`  | no              |

## Disclosure

Once a fix is available on the JetBrains Marketplace, a security advisory will
be published at
https://github.com/apachler/marp-jetbrains-plugin/security/advisories with the
CVE (if assigned), the affected versions, and the upgrade path.

## Hardening notes

- This plugin spawns `marp --server` as a child process scoped to the project
  root; it does not bind a public port. `--allow-local-files` is **off by
  default** and the settings UI warns when enabling it.
- Marp CLI is installed via npm into a per-OS plugin cache; the version is
  pinned in `<cache>/.installed-version`. The plugin never executes Node.js
  code directly — only the `marp` binary.
- No telemetry, no opt-in or opt-out. The plugin never makes network requests
  outside of the npm install step at first use.
