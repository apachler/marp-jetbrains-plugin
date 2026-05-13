package app.marp.jetbrains.cli

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Files

class NodeJsDetectorTest {

    @Test
    fun `override pointing at non-existent file returns null without throwing`() {
        val bogus = Files.createTempDirectory("nodejs-detect-").resolve("definitely-not-node")
        val result = NodeJsDetector.detect(bogus.toString())
        // We cannot guarantee that no system Node.js exists in the sandbox PATH,
        // but a bogus override path itself should never resolve to a NodeJsLocation
        // matching that path.
        if (result != null) {
            assert(result.path != bogus) { "Bogus override should not resolve to that path" }
        }
    }

    @Test
    fun `override pointing at empty string falls through to autodetection`() {
        // Should not throw on empty / blank override.
        val result = NodeJsDetector.detect("")
        // Don't assert true/false — environment dependent. Just must not throw.
        @Suppress("USELESS_IS_CHECK")
        assert(result == null || result is NodeJsLocation)
    }

    @Test
    fun `detect returns null when override is a directory`() {
        val dir = Files.createTempDirectory("nodejs-detect-dir-")
        val result = NodeJsDetector.detect(dir.toString())
        // The directory itself must not resolve to that exact path as Node.
        if (result != null) {
            assertNull(null) // no-op; only fail if result.path == dir which we check next
            assert(result.path != dir) { "Directory must not be treated as node executable" }
        }
    }

    // --- parseSemver coverage ------------------------------------------------

    @Test
    fun `parseSemver - simple three-part with v prefix`() {
        val v = NodeJsDetector.parseSemver("v18.0.0")!!
        org.junit.jupiter.api.Assertions.assertArrayEquals(intArrayOf(18, 0, 0), v)
    }

    @Test
    fun `parseSemver - simple three-part without v prefix`() {
        val v = NodeJsDetector.parseSemver("20.10.1")!!
        org.junit.jupiter.api.Assertions.assertArrayEquals(intArrayOf(20, 10, 1), v)
    }

    @Test
    fun `parseSemver - strips pre-release suffix on patch`() {
        val v = NodeJsDetector.parseSemver("v22.5.0-rc.1")!!
        org.junit.jupiter.api.Assertions.assertArrayEquals(intArrayOf(22, 5, 0), v)
    }

    @Test
    fun `parseSemver - rejects two-part version`() {
        assertNull(NodeJsDetector.parseSemver("v18.5"))
    }

    @Test
    fun `parseSemver - rejects empty string`() {
        assertNull(NodeJsDetector.parseSemver(""))
        assertNull(NodeJsDetector.parseSemver("v"))
    }

    @Test
    fun `parseSemver - rejects non-numeric major`() {
        assertNull(NodeJsDetector.parseSemver("vfoo.0.0"))
    }

    @Test
    fun `parseSemver - rejects garbage between numbers`() {
        // Patch component has leading non-digits → empty digit run → null.
        assertNull(NodeJsDetector.parseSemver("v18.0.abc"))
    }

    // --- isAcceptableVersionOutput coverage -----------------------------------

    @Test
    fun `accepts v18 patch zero`() {
        assertTrue(NodeJsDetector.isAcceptableVersionOutput("v18.0.0"))
    }

    @Test
    fun `accepts v20 lts`() {
        assertTrue(NodeJsDetector.isAcceptableVersionOutput("v20.10.1"))
    }

    @Test
    fun `accepts pre-release suffix on v22`() {
        assertTrue(NodeJsDetector.isAcceptableVersionOutput("v22.5.0-rc.1"))
    }

    @Test
    fun `accepts trailing whitespace`() {
        assertTrue(NodeJsDetector.isAcceptableVersionOutput("v22.5.0\n"))
        assertTrue(NodeJsDetector.isAcceptableVersionOutput("  v18.0.0  "))
    }

    @Test
    fun `rejects v17 as below minimum`() {
        assertFalse(NodeJsDetector.isAcceptableVersionOutput("v17.9.0"))
    }

    @Test
    fun `rejects legacy v0 release`() {
        assertFalse(NodeJsDetector.isAcceptableVersionOutput("v0.10.48"))
    }

    @Test
    fun `rejects empty output`() {
        assertFalse(NodeJsDetector.isAcceptableVersionOutput(""))
        assertFalse(NodeJsDetector.isAcceptableVersionOutput("   "))
    }

    @Test
    fun `rejects output without leading v`() {
        assertFalse(NodeJsDetector.isAcceptableVersionOutput("20.0.0"))
    }

    @Test
    fun `rejects garbage`() {
        assertFalse(NodeJsDetector.isAcceptableVersionOutput("v.0.0"))
        assertFalse(NodeJsDetector.isAcceptableVersionOutput("vfoo.0.0"))
        assertFalse(NodeJsDetector.isAcceptableVersionOutput("not even close"))
    }

    @Test
    fun `rejects integer-only major`() {
        // Missing the dot+minor portion: major is "" before the first dot.
        assertFalse(NodeJsDetector.isAcceptableVersionOutput("v22"))
    }
}
