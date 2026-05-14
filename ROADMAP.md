# Roadmap

A short, public view of where **Marp for JetBrains** is heading. For the
day-to-day backlog see [`TODO.md`](./TODO.md); for engineering / infrastructure
work see [`HAPPY-CODING.md`](./HAPPY-CODING.md).

The roadmap is a statement of intent, not a commitment. Priorities can shift in
response to bug reports, Marketplace feedback, or JetBrains Platform changes.

## Now — `0.1.x`

Stabilise the published Marketplace release.

- Live preview with hot-reload (shipped).
- Auto-install of Marp CLI via npm (shipped).
- Node.js auto-detection across PATH / Volta / fnm / nvm / asdf / Homebrew /
  Windows (shipped).
- Cross-platform manual verification: IntelliJ IDEA, WebStorm, PyCharm on
  Linux, macOS, Windows.
- First Marketplace listing with screenshots.

## Next — `0.2.x`

User-visible polish based on early feedback.

- Export actions (PDF, HTML, PPTX) invoked via Marp CLI, surfaced as IDE
  actions.
- Print/PDF view that does not depend on JCEF being available.
- Better diagnostics when Node.js or `marp` is missing, including a one-click
  "open settings" affordance from the notification.
- Sample gallery linked from the welcome screen.

## Later — `0.3.x` and beyond

Larger items that need design work first.

- Theme picker UI that reads `themes/*.css` discovered in the project.
- "Open in browser" action for sharing the preview URL on a LAN.
- Smarter detection of nested Marp config (`marp.config.js`,
  `package.json#marp`).
- Optional WebStorm-specific integration with the bundled JS plugin for
  inline JS evaluation in slide HTML blocks.

## Out of scope

These have been considered and explicitly rejected. They may be revisited
later if user demand changes the calculation.

- **Bundling Node.js with the plugin.** Increases the plugin payload from
  ~200 KB to ~50 MB and adds a per-platform binary release matrix. Users
  install Node.js themselves; the plugin auto-detects it.
- **Telemetry or analytics.** No phone-home, ever. This is a hard product
  line.
- **A custom Markdown renderer.** Marp CLI is the source of truth; building
  an alternative renderer would diverge from upstream and break export
  parity.
- **Standalone IDE distribution.** This is a plugin, not an IDE fork.

## How to influence the roadmap

- Open a [Discussion](https://github.com/apachler/marp-jetbrains-plugin/discussions)
  to argue for or against an item.
- Open a [feature request issue](https://github.com/apachler/marp-jetbrains-plugin/issues/new?template=feature_request.md)
  for a concrete, scoped proposal.
- Send a PR against `TODO.md` to add an item with a clear rationale.
