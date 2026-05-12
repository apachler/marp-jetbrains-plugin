package app.marp.jetbrains.cli

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteRecursively

class MarpCliInstaller(private val nodeJs: NodeJsLocation) {

    private val log = Logger.getInstance(MarpCliInstaller::class.java)

    /** Plugin cache root for this OS. */
    val cacheRoot: Path = resolveCacheRoot()

    /** Path to the marp executable if installed, null otherwise. */
    fun getInstalledExecutable(): Path? {
        val exe = cacheRoot.resolve("node_modules").resolve(".bin").resolve(marpBinary())
        return if (Files.isRegularFile(exe)) exe else null
    }

    /** Read the pinned installed version, or null. */
    fun getInstalledVersion(): String? {
        val file = cacheRoot.resolve(".installed-version")
        if (!Files.isRegularFile(file)) return null
        return try {
            Files.readString(file).trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Install or upgrade to [version]. Reports progress via [indicator].
     */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun install(
        version: String = "latest",
        indicator: ProgressIndicator,
    ): Result<Path> {
        return try {
            Files.createDirectories(cacheRoot)
            indicator.text = "Installing Marp CLI ($version)…"
            indicator.isIndeterminate = true

            ensurePackageJson()

            val npmCmd = resolveNpmCommand()
                ?: return Result.failure(IllegalStateException(
                    "Could not locate npm next to Node.js at ${nodeJs.path}"
                ))

            val args = mutableListOf(
                "install",
                "--no-audit",
                "--no-fund",
                "--prefix",
                cacheRoot.toString(),
                "@marp-team/marp-cli@$version",
            )

            val errLog = StringBuilder()
            val result = ProcessUtil.runWithIndicator(
                command = npmCmd.toString(),
                args = args,
                workingDir = cacheRoot,
                env = mapOf("PATH" to enrichedPath()),
                indicator = indicator,
                onStderr = { line ->
                    indicator.text2 = line.take(120)
                    errLog.append(line).append('\n')
                },
            )

            if (result == null) {
                return Result.failure(RuntimeException("npm install was cancelled or failed to start"))
            }
            if (!result.success) {
                return Result.failure(RuntimeException(
                    "npm install exited with code ${result.exitCode}\n$errLog"
                ))
            }

            val exe = getInstalledExecutable()
                ?: return Result.failure(IllegalStateException(
                    "Marp CLI executable not found at expected path after install"
                ))

            try {
                Files.writeString(
                    cacheRoot.resolve(".installed-version"),
                    version,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (e: Exception) {
                log.warn("Failed to pin installed Marp CLI version", e)
            }

            Result.success(exe)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete the entire cache directory. */
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun clearCache() {
        if (Files.exists(cacheRoot)) {
            cacheRoot.deleteRecursively()
        }
    }

    private fun ensurePackageJson() {
        val pkg = cacheRoot.resolve("package.json")
        if (!Files.exists(pkg)) {
            Files.writeString(
                pkg,
                """{"name":"marp-jetbrains-cache","private":true,"version":"0.0.0"}""",
            )
        }
    }

    private fun resolveNpmCommand(): Path? {
        val nodeDir = nodeJs.path.parent ?: return null
        val name = if (SystemInfo.isWindows) "npm.cmd" else "npm"
        val candidate = nodeDir.resolve(name)
        if (Files.isRegularFile(candidate)) return candidate
        // Fall back to PATH lookup for npm.
        return findOnPath(name)
    }

    private fun findOnPath(executable: String): Path? {
        val path = System.getenv("PATH") ?: return null
        val sep = if (SystemInfo.isWindows) ";" else ":"
        for (dir in path.split(sep)) {
            if (dir.isBlank()) continue
            val candidate = try {
                Paths.get(dir, executable)
            } catch (_: Exception) {
                continue
            }
            if (Files.isRegularFile(candidate)) return candidate
        }
        return null
    }

    /** Prepend Node.js bin dir to PATH so npm can find node. */
    private fun enrichedPath(): String {
        val current = System.getenv("PATH").orEmpty()
        val sep = if (SystemInfo.isWindows) ";" else ":"
        val nodeBin = nodeJs.path.parent?.toString() ?: return current
        return if (current.split(sep).any { it == nodeBin }) current else "$nodeBin$sep$current"
    }

    private fun marpBinary(): String = if (SystemInfo.isWindows) "marp.cmd" else "marp"

    private fun resolveCacheRoot(): Path {
        val home = System.getProperty("user.home")
        return when {
            SystemInfo.isWindows -> {
                val local = System.getenv("LOCALAPPDATA")
                    ?: "$home${java.io.File.separator}AppData${java.io.File.separator}Local"
                Paths.get(local, "marp-jetbrains")
            }
            SystemInfo.isMac -> Paths.get(home, "Library", "Caches", "marp-jetbrains")
            else -> {
                val xdg = System.getenv("XDG_CACHE_HOME")
                if (!xdg.isNullOrBlank()) Paths.get(xdg, "marp-jetbrains")
                else Paths.get(home, ".cache", "marp-jetbrains")
            }
        }
    }
}
