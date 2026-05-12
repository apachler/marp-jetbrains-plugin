package app.marp.jetbrains.service

import app.marp.jetbrains.cli.MarpCliInstaller
import app.marp.jetbrains.cli.NodeJsDetector
import app.marp.jetbrains.cli.NodeJsLocation
import app.marp.jetbrains.notification.MarpNotifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class MarpApplicationService {

    private val log = Logger.getInstance(MarpApplicationService::class.java)

    private val executableRef = AtomicReference<Path?>(null)
    private val installing = AtomicReference<Boolean>(false)

    /** Synchronously detect Node.js with optional user override. */
    fun detectNodeJs(override: String?): NodeJsLocation? = NodeJsDetector.detect(override)

    /** Currently known marp executable, if any. */
    fun cachedExecutable(): Path? = executableRef.get()

    /**
     * Ensure the Marp CLI is installed. If already installed and version matches, returns it.
     * Otherwise spawns a background task to install it. The first request for a fresh install
     * returns null while installation runs; the caller should retry after [onReady] fires.
     */
    fun ensureInstalled(
        project: Project?,
        nodeOverride: String?,
        marpVersion: String,
        marpCliPathOverride: String?,
        onReady: (Path) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): Path? {
        if (!marpCliPathOverride.isNullOrBlank()) {
            val override = Path.of(marpCliPathOverride)
            if (java.nio.file.Files.isRegularFile(override)) {
                executableRef.set(override)
                return override
            }
        }

        val node = NodeJsDetector.detect(nodeOverride)
        if (node == null) {
            MarpNotifications.notifyNodeJsMissing(project)
            onFailure(IllegalStateException("Node.js not detected"))
            return null
        }

        val installer = MarpCliInstaller(node)
        val existing = installer.getInstalledExecutable()
        val pinned = installer.getInstalledVersion()
        if (existing != null && pinned == marpVersion) {
            executableRef.set(existing)
            return existing
        }

        if (!installing.compareAndSet(false, true)) {
            return executableRef.get()
        }

        ApplicationManager.getApplication().invokeLater {
            object : Task.Backgroundable(project, "Installing Marp CLI", true) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        val result = installer.install(marpVersion, indicator)
                        result.onSuccess { exe ->
                            executableRef.set(exe)
                            ApplicationManager.getApplication().invokeLater { onReady(exe) }
                        }.onFailure { err ->
                            log.warn("Marp CLI install failed", err)
                            ApplicationManager.getApplication().invokeLater {
                                MarpNotifications.notifyInstallFailed(project, err.message.orEmpty())
                                onFailure(err)
                            }
                        }
                    } finally {
                        installing.set(false)
                    }
                }
            }.queue()
        }
        return null
    }

    /** Forget any cached executable so the next ensureInstalled re-checks. */
    fun invalidateCache() {
        executableRef.set(null)
    }

    companion object {
        fun getInstance(): MarpApplicationService = service()
    }
}
