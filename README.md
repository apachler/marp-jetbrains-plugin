# Marp for JetBrains

[![Build](https://img.shields.io/github/actions/workflow/status/apachler/marp-jetbrains-plugin/build.yml?branch=main&label=build&logo=github)](https://github.com/apachler/marp-jetbrains-plugin/actions/workflows/build.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/apachler/marp-jetbrains-plugin/codeql.yml?branch=main&label=codeql&logo=github)](https://github.com/apachler/marp-jetbrains-plugin/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.securityscorecards.dev/projects/github.com/apachler/marp-jetbrains-plugin/badge)](https://securityscorecards.dev/viewer/?uri=github.com/apachler/marp-jetbrains-plugin)
[![License: MIT](https://img.shields.io/github/license/apachler/marp-jetbrains-plugin?color=blue)](./LICENSE)
[![Marketplace](https://img.shields.io/jetbrains/plugin/v/app.marp.jetbrains?label=marketplace&logo=jetbrains)](https://plugins.jetbrains.com/plugin/app.marp.jetbrains)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/app.marp.jetbrains?label=downloads&logo=jetbrains)](https://plugins.jetbrains.com/plugin/app.marp.jetbrains)
[![Rating](https://img.shields.io/jetbrains/plugin/r/rating/app.marp.jetbrains?label=rating&logo=jetbrains)](https://plugins.jetbrains.com/plugin/app.marp.jetbrains)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2024.3%2B-blueviolet?logo=intellijidea)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)

Live preview of [Marp](https://marp.app) Markdown presentations inside JetBrains IDEs.

Mirrors the core experience of the official "Marp for VS Code" extension: open a `.md`
file that begins with `marp: true` frontmatter and a live preview tab appears to the right
of the source editor, hot-reloading as you type.

## Features

- Live preview of Marp Markdown in a split editor tab
- Hot-reload on edit (debounced)
- Auto-install of Marp CLI on first use (via npm, into a plugin-managed cache)
- Configurable Node.js and Marp CLI paths
- Optional `--allow-local-files` flag for serving images referenced by absolute paths

## Requirements

- IntelliJ Platform **2024.3 or newer** (any JetBrains IDE that bundles the Markdown plugin
  — IntelliJ IDEA Community/Ultimate, WebStorm, PyCharm, GoLand, PhpStorm, RubyMine, CLion,
  Rider, RustRover, Android Studio).
- **Node.js 18 or newer** available on your system. The plugin auto-detects Node from
  `PATH`, Volta, fnm, nvm, asdf, Homebrew, and `%ProgramFiles%\nodejs` on Windows.

## Install

### From JetBrains Marketplace

1. Open your IDE → `Settings` → `Plugins` → `Marketplace`.
2. Search for **Marp for JetBrains** and click **Install**.
3. Restart the IDE.

### From a release `.zip`

1. Download the latest `.zip` from the
   [GitHub Releases](https://github.com/apachler/marp-jetbrains-plugin/releases) page.
2. `Settings` → `Plugins` → ⚙ → `Install Plugin from Disk…` → select the `.zip`.
3. Restart the IDE.

## Usage

Create a Markdown file beginning with Marp frontmatter:

```markdown
---
marp: true
theme: default
---

# Slide 1

Hello, JetBrains!

---

# Slide 2

- Live preview
- Hot reload
```

When you open the file, a **Marp Preview** tab appears next to the source. Edits in the
source update the preview after a short debounce (default 300 ms).

## Settings

`Settings` → `Tools` → `Marp`:

| Setting | Default | Description |
|---|---|---|
| Node.js executable | (auto-detect) | Override the auto-detected Node.js path. |
| Marp CLI executable (override) | (none) | Use a pre-installed Marp CLI instead of the cached one. |
| Marp CLI version | `latest` | npm version selector for `@marp-team/marp-cli`. |
| Preview refresh delay (ms) | `300` | Debounce delay between an edit and a preview refresh. |
| Auto-open preview tab for Marp files | on | Open the preview tab automatically. |
| Allow local file access in Marp preview | off | Pass `--allow-local-files` to the Marp server. |
| Reinstall Marp CLI | — | Delete the cached Marp CLI; next preview reinstalls it. |

## How it works

On the first preview the plugin:

1. Detects Node.js (PATH, Volta, fnm, nvm, asdf, Homebrew, Windows installer location).
2. Runs `npm install --prefix <cache> @marp-team/marp-cli@<version>` into a per-OS cache
   directory.
3. Spawns `marp --server <projectRoot>` once per project on a free port.
4. Loads the per-file URL `http://localhost:<port>/<relative-path>` into a JCEF browser.
5. Reloads the browser (debounced) on every edit.

No intermediate HTML files are written into your project.

## Building from source

```bash
git clone https://github.com/apachler/marp-jetbrains-plugin
cd marp-jetbrains-plugin
./gradlew runIde       # launches a sandbox IDE with the plugin loaded
./gradlew buildPlugin  # produces build/distributions/*.zip
./gradlew check verifyPlugin
```

## Contributing

Contributions are welcome. See [`CONTRIBUTING.md`](./CONTRIBUTING.md) for the
development setup and PR checklist. By participating you agree to the
[Code of Conduct](./CODE_OF_CONDUCT.md).

## Support

Bug reports and feature requests: see [`SUPPORT.md`](./SUPPORT.md).
Security vulnerabilities: see [`SECURITY.md`](./SECURITY.md) — please disclose
privately, not via a public issue.

## License

[MIT](./LICENSE)
