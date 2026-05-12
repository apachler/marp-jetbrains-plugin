package app.marp.jetbrains.service

import app.marp.jetbrains.cli.MarpServerManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class MarpProjectService(private val project: Project) : Disposable {

    /** Lazily-resolved server manager. */
    fun serverManager(): MarpServerManager = MarpServerManager.getInstance(project)

    /** Ensure the Marp CLI is installed and the server is running. */
    fun bootstrap() {
        serverManager().startIfNeeded()
    }

    override fun dispose() {
        // server manager is its own service and will dispose when the project closes
    }

    companion object {
        fun getInstance(project: Project): MarpProjectService = project.service()
    }
}
