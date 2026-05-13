package app.marp.jetbrains.cli

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class NodeJsLocation(val path: Path, val version: String)

object NodeJsDetector {

    private val LOG = Logger.getInstance(NodeJsDetector::class.java)
    private const val MIN_MAJOR = 18

    /** Run detection in given order. Returns null if not found. */
    fun detect(override: String? = null): NodeJsLocation? {
        return runCatching { detectInternal(override) }
            .onFailure { LOG.debug("Node.js detection failed", it) }
            .getOrNull()
    }

    private fun detectInternal(override: String?): NodeJsLocation? {
        // 1. Override
        if (!override.isNullOrBlank()) {
            val candidate = Paths.get(override)
            verify(candidate)?.let { return it }
            LOG.debug("Override path is not a usable Node.js: $override")
        }

        // 2. PATH lookup
        pathLookup()?.let { return it }

        // 3. Volta
        userHome()?.resolve(".volta/bin/${nodeExe()}")?.let { verify(it)?.let { v -> return v } }

        // 4. fnm
        sysEnv("FNM_DIR")?.let { dir ->
            Paths.get(dir, "aliases", "default", "bin", nodeExe()).let { verify(it)?.let { v -> return v } }
        }
        sysEnv("FNM_MULTISHELL_PATH")?.let { dir ->
            Paths.get(dir, "bin", nodeExe()).let { verify(it)?.let { v -> return v } }
        }

        // 5. NVM
        nvmHighest()?.let { return it }

        // 6. asdf
        userHome()?.resolve(".asdf/shims/${nodeExe()}")?.let { verify(it)?.let { v -> return v } }

        // 7. macOS Homebrew
        if (SystemInfo.isMac) {
            for (p in listOf("/opt/homebrew/bin/${nodeExe()}", "/usr/local/bin/${nodeExe()}")) {
                verify(Paths.get(p))?.let { return it }
            }
        }

        // 8. Windows: %ProgramFiles%\nodejs\node.exe
        if (SystemInfo.isWindows) {
            sysEnv("ProgramFiles")?.let { pf ->
                verify(Paths.get(pf, "nodejs", "node.exe"))?.let { return it }
            }
        }

        return null
    }

    private fun pathLookup(): NodeJsLocation? {
        val path = sysEnv("PATH") ?: return null
        val separator = if (SystemInfo.isWindows) ";" else ":"
        for (dir in path.split(separator)) {
            if (dir.isBlank()) continue
            val candidate = try {
                Paths.get(dir, nodeExe())
            } catch (_: Exception) {
                continue
            }
            verify(candidate)?.let { return it }
        }
        return null
    }

    private fun nvmHighest(): NodeJsLocation? {
        val home = userHome() ?: return null
        val versionsDir = home.resolve(".nvm/versions/node")
        if (!Files.isDirectory(versionsDir)) return null
        val candidates = try {
            Files.list(versionsDir).use { stream ->
                stream.filter { Files.isDirectory(it) }
                    .map { it.fileName.toString() }
                    .toList()
            }
        } catch (_: Exception) {
            return null
        }
        val sorted = candidates.mapNotNull { v ->
            val parsed = parseSemver(v) ?: return@mapNotNull null
            v to parsed
        }.sortedWith(compareByDescending<Pair<String, IntArray>> { it.second[0] }
            .thenByDescending { it.second[1] }
            .thenByDescending { it.second[2] })

        for ((name, _) in sorted) {
            val exe = versionsDir.resolve(name).resolve("bin").resolve(nodeExe())
            verify(exe)?.let { return it }
        }
        return null
    }

    /**
     * Parse a Node.js-style version string into a (major, minor, patch) array,
     * or null when [input] is not a recognisable three-part semver. Strips a
     * leading `v` and any pre-release / build suffix on the patch component
     * (e.g. `v18.0.0-rc.1` → `[18, 0, 0]`).
     *
     * Exposed `internal` so tests can exercise the parser without invoking
     * the disk / PATH heuristics in [detect].
     */
    internal fun parseSemver(input: String): IntArray? {
        val trimmed = input.removePrefix("v")
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('.', limit = 4)
        if (parts.size < 3) return null
        val nums = IntArray(3)
        for (i in 0..2) {
            val token = parts[i].takeWhile { it.isDigit() }
            if (token.isEmpty()) return null
            nums[i] = token.toIntOrNull() ?: return null
        }
        return nums
    }

    /**
     * True iff [output] looks like `node --version` output for a release
     * matching the minimum-major requirement (currently 18). Pure function
     * extracted from [verify] for test coverage independent of an actual
     * filesystem.
     */
    internal fun isAcceptableVersionOutput(output: String): Boolean {
        val trimmed = output.trim()
        if (!trimmed.startsWith("v")) return false
        val rest = trimmed.removePrefix("v")
        // Require at least major.minor — `node --version` always emits three
        // components, so a dotless string can't be a real Node release.
        if (!rest.contains('.')) return false
        val major = rest.substringBefore('.').toIntOrNull() ?: return false
        return major >= MIN_MAJOR
    }

    /** Verify a candidate path is a Node.js executable with major >= 18. */
    private fun verify(candidate: Path): NodeJsLocation? {
        if (!Files.isRegularFile(candidate) || !Files.isExecutable(candidate)) return null
        val res = ProcessUtil.run(candidate, listOf("--version"), timeoutMs = 5_000) ?: return null
        if (!res.success) return null
        val output = res.stdout.trim()
        if (!isAcceptableVersionOutput(output)) return null
        return NodeJsLocation(candidate, output)
    }

    private fun nodeExe(): String = if (SystemInfo.isWindows) "node.exe" else "node"

    private fun userHome(): Path? = System.getProperty("user.home")?.let { Paths.get(it) }

    private fun sysEnv(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
}
