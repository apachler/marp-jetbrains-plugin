package app.marp.jetbrains.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class MarpCliInstallerTest {

    @Test
    fun `linux without XDG_CACHE_HOME uses ~slash cache`() {
        val root = MarpCliInstaller.cacheRootFor(
            isWindows = false, isMac = false,
            userHome = "/home/jane",
            env = emptyMap(),
        )
        assertEquals(Paths.get("/home/jane/.cache/marp-jetbrains"), root)
    }

    @Test
    fun `linux honours XDG_CACHE_HOME when set`() {
        val root = MarpCliInstaller.cacheRootFor(
            isWindows = false, isMac = false,
            userHome = "/home/jane",
            env = mapOf("XDG_CACHE_HOME" to "/var/cache/jane"),
        )
        assertEquals(Paths.get("/var/cache/jane/marp-jetbrains"), root)
    }

    @Test
    fun `linux falls back when XDG_CACHE_HOME is blank`() {
        val root = MarpCliInstaller.cacheRootFor(
            isWindows = false, isMac = false,
            userHome = "/home/jane",
            env = mapOf("XDG_CACHE_HOME" to "   "),
        )
        assertEquals(Paths.get("/home/jane/.cache/marp-jetbrains"), root)
    }

    @Test
    fun `linux ignores LOCALAPPDATA`() {
        val root = MarpCliInstaller.cacheRootFor(
            isWindows = false, isMac = false,
            userHome = "/home/jane",
            env = mapOf("LOCALAPPDATA" to "C:\\Users\\Jane\\AppData\\Local"),
        )
        assertEquals(Paths.get("/home/jane/.cache/marp-jetbrains"), root)
    }

    @Test
    fun `macOS uses Library Caches regardless of XDG`() {
        val root = MarpCliInstaller.cacheRootFor(
            isWindows = false, isMac = true,
            userHome = "/Users/jane",
            env = mapOf("XDG_CACHE_HOME" to "/should/not/be/used"),
        )
        assertEquals(Paths.get("/Users/jane/Library/Caches/marp-jetbrains"), root)
    }

    @Test
    fun `windows honours LOCALAPPDATA when set`() {
        val root = MarpCliInstaller.cacheRootFor(
            isWindows = true, isMac = false,
            userHome = "C:\\Users\\Jane",
            env = mapOf("LOCALAPPDATA" to "C:\\Users\\Jane\\AppData\\Local"),
        )
        assertEquals(
            Paths.get("C:\\Users\\Jane\\AppData\\Local", "marp-jetbrains"),
            root,
        )
    }

    @Test
    fun `windows falls back to userHome AppData Local when LOCALAPPDATA missing`() {
        val root = MarpCliInstaller.cacheRootFor(
            isWindows = true, isMac = false,
            userHome = "C:\\Users\\Jane",
            env = emptyMap(),
        )
        val path = root.toString()
        // Path separator can vary; assert both segments are present.
        assertTrue(path.contains("Jane"), "expected userHome in $path")
        assertTrue(path.contains("AppData"), "expected AppData in $path")
        assertTrue(path.contains("Local"), "expected Local in $path")
        assertTrue(path.contains("marp-jetbrains"), "expected marp-jetbrains in $path")
    }
}
