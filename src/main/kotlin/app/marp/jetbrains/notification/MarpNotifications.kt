package app.marp.jetbrains.notification

import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection

object MarpNotifications {

    private const val GROUP_ID = "Marp"
    private const val NODE_INSTALL_URL = "https://nodejs.org"
    private const val SETTINGS_ID = "app.marp.jetbrains.settings"

    fun notifyNodeJsMissing(project: Project?) {
        notify(
            project,
            "Node.js not found",
            "Marp for JetBrains needs Node.js 18 or newer.",
            NotificationType.WARNING,
        ) { n ->
            n.addAction(action("Install Node.js") { BrowserUtil.browse(NODE_INSTALL_URL) })
            n.addAction(openSettingsAction(project))
        }
    }

    fun notifyInstallFailed(project: Project?, log: String) {
        notify(
            project,
            "Marp CLI installation failed",
            "Could not install @marp-team/marp-cli.",
            NotificationType.ERROR,
        ) { n ->
            n.addAction(action("Copy log") {
                CopyPasteManager.getInstance().setContents(StringSelection(log))
            })
        }
    }

    fun notifyServerCrashed(project: Project?, onRetry: () -> Unit) {
        notify(
            project,
            "Marp preview server stopped",
            "The Marp server exited unexpectedly.",
            NotificationType.ERROR,
        ) { n ->
            n.addAction(action("Retry") { onRetry() })
        }
    }

    fun notifyPortConflict(project: Project?) {
        notify(
            project,
            "Could not allocate port",
            "No free port available for the Marp preview server.",
            NotificationType.ERROR,
        ) { n ->
            n.addAction(openSettingsAction(project))
        }
    }

    fun notifyInfo(project: Project?, title: String, content: String) {
        notify(project, title, content, NotificationType.INFORMATION) {}
    }

    private inline fun notify(
        project: Project?,
        title: String,
        content: String,
        type: NotificationType,
        configure: (Notification) -> Unit,
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)
        configure(notification)
        notification.notify(project)
    }

    private fun openSettingsAction(project: Project?): NotificationAction =
        action("Open Settings") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, SETTINGS_ID)
        }

    private fun action(text: String, perform: () -> Unit): NotificationAction =
        object : NotificationAction(text) {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                perform()
            }
        }
}
