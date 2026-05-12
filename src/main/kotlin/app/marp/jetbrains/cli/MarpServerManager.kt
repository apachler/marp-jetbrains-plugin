package app.marp.jetbrains.cli

import app.marp.jetbrains.notification.MarpNotifications
import app.marp.jetbrains.service.MarpApplicationService
import app.marp.jetbrains.settings.MarpSettings
import app.marp.jetbrains.util.MarpConfig
import app.marp.jetbrains.util.PathUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class MarpServerManager(private val project: Project) : Disposable {

    private val log = Logger.getInstance(MarpServerManager::class.java)
    private val state = AtomicReference<ServerState?>(null)
    private val restartCount = AtomicInteger(0)
    private val restartWindowStart = AtomicReference<Long>(0L)

    private fun publishServerReady() {
        if (project.isDisposed) return
        runCatching {
            project.messageBus.syncPublisher(TOPIC).onServerReady()
        }.onFailure { log.debug("Failed to publish server-ready event", it) }
    }

    /** Idempotent: start once, no-op if already running. */
    fun startIfNeeded(): Result<Unit> {
        val current = state.get()
        if (current != null && current.process.isAlive) {
            return Result.success(Unit)
        }

        val settings = MarpSettings.getInstance().state
        val app = MarpApplicationService.getInstance()
        val marp = app.cachedExecutable() ?: app.ensureInstalled(
            project = project,
            nodeOverride = settings.nodeJsPath,
            marpVersion = settings.marpCliVersion,
            marpCliPathOverride = settings.marpCliPath,
            onReady = {
                ApplicationManager.getApplication().invokeLater {
                    startIfNeeded()
                }
            },
            onFailure = { /* notification already shown */ },
        )
        if (marp == null) {
            return Result.failure(IllegalStateException("Marp CLI not yet available"))
        }

        return launch(marp, settings.allowLocalFiles)
    }

    private fun launch(marp: Path, allowLocalFiles: Boolean): Result<Unit> {
        val projectRoot = PathUtil.projectRoot(project)
            ?: return Result.failure(IllegalStateException("Project has no base path"))

        val port = try {
            ServerSocket(0).use { it.localPort }
        } catch (e: IOException) {
            MarpNotifications.notifyPortConflict(project)
            return Result.failure(e)
        }

        val configFile = MarpConfig.findConfigFile(projectRoot)
        if (configFile != null) {
            log.info("Using Marp config file at $configFile")
        }

        val args = buildList {
            add("--server")
            if (configFile != null) {
                add("--config-file")
                add(configFile.toString())
            }
            if (allowLocalFiles) add("--allow-local-files")
            add(projectRoot.toString())
        }

        val process = try {
            val builder = ProcessBuilder(buildList<String> {
                add(marp.toString())
                addAll(args)
            })
            builder.directory(projectRoot.toFile())
            builder.environment().apply {
                put("PORT", port.toString())
                // marp-cli reads PORT env var (it also accepts --port via -p but PORT is more portable).
            }
            builder.redirectErrorStream(false)
            builder.start()
        } catch (e: IOException) {
            log.warn("Failed to spawn marp server", e)
            return Result.failure(e)
        }

        val newState = ServerState(process, port, projectRoot)
        state.set(newState)
        watchProcess(newState, marp, allowLocalFiles)
        awaitReadiness(newState)
        return Result.success(Unit)
    }

    /**
     * Poll the marp server's TCP port until it accepts connections, then
     * notify listeners on the EDT. If the process exits or the deadline lapses
     * before that happens, log and bail — the panel will keep its placeholder
     * and rely on the next refresh / retry.
     */
    private fun awaitReadiness(s: ServerState) {
        Thread {
            val deadline = System.currentTimeMillis() + READINESS_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (!s.process.isAlive) {
                    log.warn("Marp server exited before becoming ready on port ${s.port}")
                    return@Thread
                }
                if (probePort(s.port)) {
                    if (s.ready.compareAndSet(false, true) && state.get() === s) {
                        ApplicationManager.getApplication().invokeLater { publishServerReady() }
                    }
                    return@Thread
                }
                try {
                    Thread.sleep(READINESS_POLL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@Thread
                }
            }
            log.warn("Marp server did not become ready within ${READINESS_TIMEOUT_MS}ms on port ${s.port}")
        }.apply { name = "marp-server-readiness"; isDaemon = true; start() }
    }

    private fun probePort(port: Int): Boolean {
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress("127.0.0.1", port), READINESS_CONNECT_TIMEOUT_MS)
                true
            }
        } catch (_: IOException) {
            false
        }
    }

    private fun watchProcess(s: ServerState, marp: Path, allowLocalFiles: Boolean) {
        Thread {
            s.process.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    log.warn("[marp server] $line")
                }
            }
        }.apply { name = "marp-server-stderr"; isDaemon = true; start() }

        Thread {
            s.process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    log.debug("[marp server] $line")
                }
            }
        }.apply { name = "marp-server-stdout"; isDaemon = true; start() }

        Thread {
            val exit = runCatching { s.process.waitFor() }.getOrElse { -1 }
            if (state.get() === s) {
                state.set(null)
                if (exit != 0 && !project.isDisposed) {
                    onUnexpectedExit(marp, allowLocalFiles)
                }
            }
        }.apply { name = "marp-server-watcher"; isDaemon = true; start() }
    }

    private fun onUnexpectedExit(marp: Path, allowLocalFiles: Boolean) {
        val now = System.currentTimeMillis()
        val windowStart = restartWindowStart.get()
        if (now - windowStart > 60_000) {
            restartWindowStart.set(now)
            restartCount.set(0)
        }
        if (restartCount.incrementAndGet() > 3) {
            MarpNotifications.notifyServerCrashed(project) {
                restartCount.set(0)
                restartWindowStart.set(System.currentTimeMillis())
                launch(marp, allowLocalFiles)
            }
            return
        }
        launch(marp, allowLocalFiles)
    }

    /** URL for previewing the given .md file (relative to project root). */
    fun getPreviewUrl(file: VirtualFile): String? {
        val s = state.get() ?: return null
        if (!s.process.isAlive || !s.ready.get()) return null
        val rel = PathUtil.relativePathInProject(project, file) ?: return null
        val encoded = PathUtil.encodePathSegments(rel)
        return "http://localhost:${s.port}/$encoded"
    }

    fun isRunning(): Boolean {
        val s = state.get() ?: return false
        return s.process.isAlive && s.ready.get()
    }

    /**
     * Stop the server. Called automatically on dispose.
     *
     * Walks `ProcessHandle.descendants()` because on Windows `marp.cmd` is a
     * batch wrapper around `node.exe`; killing only the parent leaves an orphan
     * Node process holding the port. We collect descendants *before* destroying
     * the parent (afterward the tree is gone), then escalate to destroyForcibly
     * on anything still alive after the grace period.
     */
    fun stop() {
        val s = state.getAndSet(null) ?: return
        try {
            if (!s.process.isAlive) return

            val descendants = runCatching { s.process.descendants().toList() }
                .getOrDefault(emptyList())

            s.process.destroy()
            descendants.forEach { runCatching { it.destroy() } }

            val deadline = System.currentTimeMillis() + STOP_GRACE_MS
            while (System.currentTimeMillis() < deadline) {
                if (!s.process.isAlive && descendants.none { it.isAlive }) break
                try {
                    Thread.sleep(STOP_POLL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }

            if (s.process.isAlive) s.process.destroyForcibly()
            descendants.forEach { handle ->
                if (handle.isAlive) runCatching { handle.destroyForcibly() }
            }
        } catch (e: Exception) {
            log.debug("Error stopping marp server", e)
        }
    }

    override fun dispose() {
        stop()
    }

    private class ServerState(
        val process: Process,
        val port: Int,
        val root: Path,
        val ready: AtomicBoolean = AtomicBoolean(false),
    )

    companion object {
        private const val READINESS_TIMEOUT_MS = 10_000L
        private const val READINESS_POLL_MS = 100L
        private const val READINESS_CONNECT_TIMEOUT_MS = 250
        private const val STOP_GRACE_MS = 2_000L
        private const val STOP_POLL_MS = 50L

        /**
         * Project-bus topic fired when the Marp server has bound its port and
         * is ready to serve preview requests. Subscribers should connect via
         * `project.messageBus.connect(disposable)` so the subscription
         * disposes deterministically with their owning component.
         */
        @JvmField
        val TOPIC: Topic<MarpServerListener> =
            Topic.create("Marp server", MarpServerListener::class.java)

        fun getInstance(project: Project): MarpServerManager = project.service()

        @Suppress("unused")
        fun marpBinary(): String = if (SystemInfo.isWindows) "marp.cmd" else "marp"
    }
}

interface MarpServerListener {
    fun onServerReady()
}
