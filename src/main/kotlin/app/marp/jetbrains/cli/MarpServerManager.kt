package app.marp.jetbrains.cli

import app.marp.jetbrains.notification.MarpNotifications
import app.marp.jetbrains.service.MarpApplicationService
import app.marp.jetbrains.settings.MarpSettings
import app.marp.jetbrains.util.PathUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import java.io.IOException
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class MarpServerManager(private val project: Project) : Disposable {

    private val log = Logger.getInstance(MarpServerManager::class.java)
    private val state = AtomicReference<ServerState?>(null)
    private val restartCount = AtomicInteger(0)
    private val restartWindowStart = AtomicReference<Long>(0L)
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun addServerStartedListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeServerStartedListener(listener: () -> Unit) {
        listeners.remove(listener)
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
                    listeners.forEach { runCatching { it() } }
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

        val args = buildList {
            add("--server")
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
        listeners.forEach { runCatching { it() } }
        return Result.success(Unit)
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
        if (!s.process.isAlive) return null
        val rel = PathUtil.relativePathInProject(project, file) ?: return null
        val encoded = PathUtil.encodePathSegments(rel)
        return "http://localhost:${s.port}/$encoded"
    }

    fun isRunning(): Boolean = state.get()?.process?.isAlive == true

    /** Stop the server. Called automatically on dispose. */
    fun stop() {
        val s = state.getAndSet(null) ?: return
        try {
            if (s.process.isAlive) {
                s.process.destroy()
                if (!s.process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    s.process.destroyForcibly()
                }
            }
        } catch (e: Exception) {
            log.debug("Error stopping marp server", e)
        }
    }

    override fun dispose() {
        stop()
        listeners.clear()
    }

    private data class ServerState(val process: Process, val port: Int, val root: Path)

    companion object {
        fun getInstance(project: Project): MarpServerManager = project.service()

        @Suppress("unused")
        fun marpBinary(): String = if (SystemInfo.isWindows) "marp.cmd" else "marp"
    }
}
