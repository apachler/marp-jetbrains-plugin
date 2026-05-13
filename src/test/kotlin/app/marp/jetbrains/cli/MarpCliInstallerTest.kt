package app.marp.jetbrains.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
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

    // --- readInstalledVersion coverage ---------------------------------------

    @Test
    fun `readInstalledVersion returns null when cache directory does not exist`(@TempDir tmp: Path) {
        val missing = tmp.resolve("never-created")
        assertNull(MarpCliInstaller.readInstalledVersion(missing))
    }

    @Test
    fun `readInstalledVersion returns null when pin file is absent`(@TempDir cache: Path) {
        assertNull(MarpCliInstaller.readInstalledVersion(cache))
    }

    @Test
    fun `readInstalledVersion reads pin file content`(@TempDir cache: Path) {
        Files.writeString(cache.resolve(".installed-version"), "1.2.3")
        assertEquals("1.2.3", MarpCliInstaller.readInstalledVersion(cache))
    }

    @Test
    fun `readInstalledVersion trims surrounding whitespace`(@TempDir cache: Path) {
        Files.writeString(cache.resolve(".installed-version"), "  4.0.1  \n")
        assertEquals("4.0.1", MarpCliInstaller.readInstalledVersion(cache))
    }

    @Test
    fun `readInstalledVersion returns null for blank file`(@TempDir cache: Path) {
        Files.writeString(cache.resolve(".installed-version"), "   \n  ")
        assertNull(MarpCliInstaller.readInstalledVersion(cache))
    }

    @Test
    fun `readInstalledVersion accepts npm version selectors`(@TempDir cache: Path) {
        // The pin file stores whatever version selector was used at install
        // time — usually "latest" or "1.2.3" but also "^4.0.0" is valid.
        Files.writeString(cache.resolve(".installed-version"), "^4.0.0")
        assertEquals("^4.0.0", MarpCliInstaller.readInstalledVersion(cache))
    }

    @Test
    fun `resolveCacheRootForCurrentHost returns a non-null path`() {
        // Smoke test — the actual host-specific path is exercised by cacheRootFor
        // tests above; here we just confirm the live-system overload is wired
        // and doesn't throw.
        val root = MarpCliInstaller.resolveCacheRootForCurrentHost()
        assertNotNull(root)
        assertTrue(
            root.toString().contains("marp-jetbrains"),
            "expected 'marp-jetbrains' in resolved path, got $root",
        )
    }
}
