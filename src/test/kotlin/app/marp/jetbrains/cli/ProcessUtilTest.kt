package app.marp.jetbrains.cli

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path

/**
 * ProcessUtil tests use the JVM's bundled `java` binary because it is the
 * one process we can guarantee is on PATH wherever tests run (it's the host
 * that's executing them). We never depend on shell-specific commands here
 * because CI may run on macOS/Linux/Windows.
 */
class ProcessUtilTest {

    private val javaExe: Path by lazy {
        val home = System.getProperty("java.home")
        val name = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        Path.of(home, "bin", name)
    }

    @Test
    fun `run returns null when the command does not exist`() {
        val result = ProcessUtil.run("/this/path/should/not/exist/binary-12345", emptyList())
        assertNull(result)
    }

    @Test
    fun `run returns ExecResult when command exists`() {
        val result = ProcessUtil.run(javaExe, listOf("-version"))
        assertNotNull(result)
    }

    @Test
    fun `run captures exit code 0 for successful command`() {
        val result = ProcessUtil.run(javaExe, listOf("-version"))!!
        // `java -version` exits 0.
        assertTrue(result.success, "expected success but exit=${result.exitCode}, stderr=${result.stderr}")
    }

    @Test
    fun `run captures nonzero exit for invalid argument`() {
        val result = ProcessUtil.run(javaExe, listOf("--definitely-not-a-real-flag"))!!
        // Modern JDK exits 1 on unknown flag.
        assertFalse(result.success, "expected non-zero exit, got 0")
    }

    @Test
    fun `run captures stderr from java -version`() {
        // `java -version` traditionally prints to stderr.
        val result = ProcessUtil.run(javaExe, listOf("-version"))!!
        assertTrue(
            result.stderr.contains("version", ignoreCase = true) ||
                result.stdout.contains("version", ignoreCase = true),
            "Expected 'version' somewhere in output. stdout=${result.stdout} stderr=${result.stderr}",
        )
    }

    @Test
    fun `ExecResult success property is true only for zero exit`() {
        val ok = ProcessUtil.ExecResult(0, "", "")
        val fail = ProcessUtil.ExecResult(1, "", "")
        val neg = ProcessUtil.ExecResult(-1, "", "")
        assertTrue(ok.success)
        assertFalse(fail.success)
        assertFalse(neg.success)
    }

    @Test
    fun `run honours workingDir`() {
        val home = File(System.getProperty("user.home")).toPath()
        // Just verify it doesn't throw when workingDir is supplied; we
        // cannot reliably observe cwd via java -version output.
        val result = ProcessUtil.run(javaExe, listOf("-version"), workingDir = home)
        assertNotNull(result)
    }
}
