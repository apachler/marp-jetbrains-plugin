# Sample decks

Example `.md` files for trying out the **Marp for JetBrains** plugin and for
manual smoke-testing during development.

## Files

| File | Purpose |
|---|---|
| [`hello.md`](./hello.md) | Minimal Marp deck demonstrating frontmatter, slide breaks, and class directives. |

## Using these samples

1. Build and run the plugin in a sandbox IDE:
   ```bash
   ./gradlew runIde
   ```
2. Open this `samples/` directory as a project (or open one of the files
   directly).
3. The Marp preview tab should appear next to the source editor.

## Adding a new sample

- Keep samples small (one screenful of source) so the preview renders quickly.
- Demonstrate one feature per file rather than packing many into one.
- Reference the sample from this README so it stays discoverable.
