package app.marp.jetbrains.util

import java.nio.file.Files
import java.nio.file.Path

object MarpConfig {

    /**
     * File names that marp-cli recognises as a project-level configuration
     * file. Order matches marp-cli's own lookup so we honour the highest-priority
     * file when more than one is present.
     */
    private val CONFIG_NAMES = listOf(
        "marp.config.js",
        "marp.config.cjs",
        "marp.config.mjs",
        ".marprc.js",
        ".marprc.cjs",
        ".marprc.json",
        ".marprc.yml",
        ".marprc.yaml",
    )

    /**
     * Return the first marp-cli config file found in [projectRoot], or null
     * if the project doesn't define one. Only checks the immediate root —
     * marp-cli walks the cwd upwards itself when no `--config-file` is
     * supplied, so we deliberately don't recurse.
     */
    fun findConfigFile(projectRoot: Path): Path? {
        if (!Files.isDirectory(projectRoot)) return null
        for (name in CONFIG_NAMES) {
            val candidate = projectRoot.resolve(name)
            if (Files.isRegularFile(candidate)) return candidate
        }
        return null
    }
}
