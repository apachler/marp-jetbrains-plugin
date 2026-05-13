package app.marp.jetbrains.statusbar

import app.marp.jetbrains.cli.MarpServerListener
import app.marp.jetbrains.cli.MarpServerManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

class MarpStatusBarWidget(private val project: Project) :
    StatusBarWidget, StatusBarWidget.TextPresentation {

    @Volatile private var statusBar: StatusBar? = null

    override fun ID(): String = WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        // Subscribe to server-ready events; on every event repaint the widget.
        project.messageBus.connect(this).subscribe(
            MarpServerManager.TOPIC,
            object : MarpServerListener {
                override fun onServerReady() = repaint()
            },
        )
    }

    private fun repaint() {
        ApplicationManager.getApplication().invokeLater {
            statusBar?.updateWidget(ID())
        }
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String {
        val running = runCatching { MarpServerManager.getInstance(project).isRunning() }
            .getOrDefault(false)
        return if (running) "Marp: ready" else "Marp: idle"
    }

    override fun getTooltipText(): String =
        "Marp preview server status — click to open settings"

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, "app.marp.jetbrains.settings")
    }

    override fun dispose() {
        // The message-bus connection auto-disposes via Disposer.register chain.
        statusBar = null
    }

    companion object {
        const val WIDGET_ID = "Marp"
    }
}
