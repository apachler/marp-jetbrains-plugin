package app.marp.jetbrains.cli

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit

object ProcessUtil {

    private val LOG = Logger.getInstance(ProcessUtil::class.java)

    data class ExecResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val success: Boolean get() = exitCode == 0
    }

    /** Run [command] with [args] and wait up to [timeoutMs] ms. Returns null on IO / timeout. */
    fun run(
        command: Path,
        args: List<String> = emptyList(),
        workingDir: Path? = null,
        env: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000,
    ): ExecResult? = run(command.toString(), args, workingDir, env, timeoutMs)

    fun run(
        command: String,
        args: List<String> = emptyList(),
        workingDir: Path? = null,
        env: Map<String, String> = emptyMap(),
        timeoutMs: Long = 30_000,
    ): ExecResult? {
        val cmdLine = buildList {
            add(command)
            addAll(args)
        }
        return try {
            val builder = ProcessBuilder(cmdLine)
            if (workingDir != null) builder.directory(workingDir.toFile())
            if (env.isNotEmpty()) builder.environment().putAll(env)
            val process = builder.start()
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                LOG.debug("Command timed out: $cmdLine")
                return null
            }
            val out = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            val err = process.errorStream.readBytes().toString(StandardCharsets.UTF_8)
            ExecResult(process.exitValue(), out, err)
        } catch (e: IOException) {
            LOG.debug("Command failed: $cmdLine", e)
            null
        } catch (e: SecurityException) {
            LOG.debug("Command denied: $cmdLine", e)
            null
        }
    }

    /**
     * Run a long-running command while reporting progress and killing on cancel.
     * Returns the full exit result, or null if the indicator cancelled before completion.
     */
    fun runWithIndicator(
        command: String,
        args: List<String> = emptyList(),
        workingDir: Path? = null,
        env: Map<String, String> = emptyMap(),
        indicator: ProgressIndicator,
        onStderr: ((String) -> Unit)? = null,
    ): ExecResult? {
        val cmdLine = buildList {
            add(command)
            addAll(args)
        }
        val process = try {
            val builder = ProcessBuilder(cmdLine).redirectErrorStream(false)
            if (workingDir != null) builder.directory(workingDir.toFile())
            if (env.isNotEmpty()) builder.environment().putAll(env)
            builder.start()
        } catch (e: IOException) {
            LOG.debug("Failed to start: $cmdLine", e)
            return null
        }

        val stdoutBuf = StringBuilder()
        val stderrBuf = StringBuilder()
        val outThread = Thread {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    stdoutBuf.append(line).append('\n')
                }
            }
        }.apply { isDaemon = true; start() }
        val errThread = Thread {
            process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    stderrBuf.append(line).append('\n')
                    onStderr?.invoke(line)
                }
            }
        }.apply { isDaemon = true; start() }

        while (process.isAlive) {
            if (indicator.isCanceled) {
                process.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                return null
            }
            try {
                process.waitFor(200, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                process.destroyForcibly()
                return null
            }
        }
        outThread.join(1000)
        errThread.join(1000)
        return ExecResult(process.exitValue(), stdoutBuf.toString(), stderrBuf.toString())
    }

    fun isWindows(): Boolean = SystemInfo.isWindows
}
