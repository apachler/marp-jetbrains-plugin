# Contributing

Thanks for your interest in contributing to **Marp for JetBrains**.

## Development setup

Requirements:

- JDK 17
- Git
- A JetBrains IDE (IntelliJ IDEA Community is enough)
- Node.js 18+ (only needed at runtime, for the plugin to install Marp CLI)

Clone and run the sandbox IDE:

```bash
git clone https://github.com/apachler/marp-jetbrains-plugin
cd marp-jetbrains-plugin
./gradlew runIde
```

`./gradlew runIde` downloads the IntelliJ Platform and launches a sandbox IDE with this
plugin pre-installed.

## Useful tasks

| Task | Description |
|---|---|
| `./gradlew runIde` | Launch a sandbox IDE with the plugin loaded. |
| `./gradlew test` | Run unit tests. |
| `./gradlew check` | Run all checks (tests, plugin metadata validation). |
| `./gradlew verifyPlugin` | Run JetBrains Plugin Verifier across recommended IDE versions. |
| `./gradlew buildPlugin` | Build a distributable `.zip` at `build/distributions/`. |

## Coding style

- Kotlin only. JDK 17 toolchain.
- Keep public class signatures aligned with `SPEC.md` §5.6.
- No telemetry. No phone-home.
- Prefer `Logger.getInstance(X::class.java).debug(...)` over swallowing exceptions silently.

## Reporting issues

Please open an issue with:

- Your IDE and version (e.g. IntelliJ IDEA Community 2024.3.1).
- Your operating system.
- Node.js version (`node --version`).
- Steps to reproduce.
- Any relevant log excerpts from `Help → Show Log in Explorer/Finder`.

## Submitting changes

1. Fork the repository and create a feature branch.
2. Add tests for any new logic where practical.
3. Run `./gradlew check` locally.
4. Open a pull request describing the change and linking any related issues.
