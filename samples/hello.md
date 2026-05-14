---
marp: true
theme: default
paginate: true
---

# Marp for JetBrains

A live-preview sample deck.

Open this file in any JetBrains IDE with the **Marp for JetBrains** plugin
installed and a preview tab will appear next to the source editor.

---

## How it works

- `marp: true` in the frontmatter activates the preview.
- Edits in the source hot-reload the preview after a short debounce.
- No HTML files are written to your project — preview is served by an
  in-process Marp CLI server.

---

## Features

- Live preview of Marp Markdown
- Hot-reload on edit
- Auto-install of `@marp-team/marp-cli` on first use
- Node.js auto-detection (PATH, Volta, fnm, nvm, asdf, Homebrew)

---

## Slide directives

You can use the full Marp directive set:

```yaml
---
marp: true
theme: default
class: invert
size: 16:9
paginate: true
---
```

---

## Try it

1. Edit any heading or bullet on the left.
2. Watch the right pane update.
3. Open `Settings → Tools → Marp` to tweak the debounce delay or Marp CLI
   version.

---

<!-- _class: invert -->

# That's it

See the [README](../README.md) for installation and configuration details.
