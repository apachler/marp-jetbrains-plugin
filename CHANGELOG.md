# Changelog

All notable changes to **Marp for JetBrains** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0]

### Added

- Live preview of Marp Markdown in a side editor tab (split view).
- Hot-reload on edit (debounced, default 300 ms).
- Auto-install of `@marp-team/marp-cli` via npm into a per-OS plugin cache.
- Node.js detection across PATH, Volta, fnm, nvm, asdf, Homebrew, and
  `%ProgramFiles%\nodejs` on Windows.
- Settings page under `Tools → Marp` with overrides for Node.js path, Marp CLI executable
  and version, preview refresh delay, auto-open behaviour, `--allow-local-files`, and a
  reinstall button.
- English localisation-ready resource bundle.

[Unreleased]: https://github.com/apachler/marp-jetbrains-plugin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/apachler/marp-jetbrains-plugin/releases/tag/v0.1.0
