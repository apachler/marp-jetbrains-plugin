package app.marp.jetbrains.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class MarpConfigTest {

    @Test
    fun `returns null when no config file present`(@TempDir dir: Path) {
        assertNull(MarpConfig.findConfigFile(dir))
    }

    @Test
    fun `returns null when root is not a directory`(@TempDir dir: Path) {
        val file = Files.createFile(dir.resolve("not-a-dir"))
        assertNull(MarpConfig.findConfigFile(file))
    }

    @Test
    fun `detects marp config js`(@TempDir dir: Path) {
        val cfg = Files.createFile(dir.resolve("marp.config.js"))
        assertEquals(cfg, MarpConfig.findConfigFile(dir))
    }

    @Test
    fun `detects marp config cjs`(@TempDir dir: Path) {
        val cfg = Files.createFile(dir.resolve("marp.config.cjs"))
        assertEquals(cfg, MarpConfig.findConfigFile(dir))
    }

    @Test
    fun `detects marp config mjs`(@TempDir dir: Path) {
        val cfg = Files.createFile(dir.resolve("marp.config.mjs"))
        assertEquals(cfg, MarpConfig.findConfigFile(dir))
    }

    @Test
    fun `detects dotted marprc js`(@TempDir dir: Path) {
        val cfg = Files.createFile(dir.resolve(".marprc.js"))
        assertEquals(cfg, MarpConfig.findConfigFile(dir))
    }

    @Test
    fun `detects dotted marprc yaml`(@TempDir dir: Path) {
        val cfg = Files.createFile(dir.resolve(".marprc.yaml"))
        assertEquals(cfg, MarpConfig.findConfigFile(dir))
    }

    @Test
    fun `marp config js wins over marprc when both present`(@TempDir dir: Path) {
        Files.createFile(dir.resolve(".marprc.json"))
        val winner = Files.createFile(dir.resolve("marp.config.js"))
        assertEquals(winner, MarpConfig.findConfigFile(dir))
    }

    @Test
    fun `ignores files in subdirectories`(@TempDir dir: Path) {
        val sub = Files.createDirectory(dir.resolve("nested"))
        Files.createFile(sub.resolve("marp.config.js"))
        assertNull(MarpConfig.findConfigFile(dir))
    }
}
