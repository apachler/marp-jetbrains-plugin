package app.marp.jetbrains.preview

import app.marp.jetbrains.cli.MarpServerManager
import app.marp.jetbrains.settings.MarpSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.SwingConstants

class MarpPreviewPanel(
    private val project: Project,
    private val file: VirtualFile,
) : JBPanel<MarpPreviewPanel>(BorderLayout()), Disposable {

    private val log = Logger.getInstance(MarpPreviewPanel::class.java)
    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val refresher: DebouncedRefresher
    private val serverListener: () -> Unit = { ApplicationManager.getApplication().invokeLater { loadOrPlaceholder() } }
    private val documentListener: DocumentListener

    init {
        val delay = MarpSettings.getInstance().state.previewRefreshDelayMs
        refresher = DebouncedRefresher(this, delay) { reload() }
        Disposer.register(this, refresher)

        if (browser != null) {
            Disposer.register(this, browser)
            add(browser.component, BorderLayout.CENTER)
        }

        documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                scheduleRefresh()
            }
        }
        FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(documentListener, this)

        val serverManager = MarpServerManager.getInstance(project)
        serverManager.addServerStartedListener(serverListener)

        startServerAndLoad()
    }

    private fun startServerAndLoad() {
        val serverManager = MarpServerManager.getInstance(project)
        serverManager.startIfNeeded()
        loadOrPlaceholder()
    }

    private fun loadOrPlaceholder() {
        if (browser == null) {
            showPlaceholder("JCEF is not available in this IDE. Please enable the JCEF runtime in IDE settings.")
            return
        }
        val url = MarpServerManager.getInstance(project).getPreviewUrl(file)
        if (url == null) {
            showPlaceholder("Marp preview is starting…")
        } else {
            removeAll()
            add(browser.component, BorderLayout.CENTER)
            browser.loadURL(url)
            revalidate()
            repaint()
        }
    }

    private fun showPlaceholder(message: String) {
        removeAll()
        val panel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.CENTER, 12, 12))
        panel.add(JBLabel(message, SwingConstants.CENTER))
        val retry = JButton("Retry")
        retry.addActionListener {
            MarpServerManager.getInstance(project).stop()
            startServerAndLoad()
        }
        panel.add(retry)
        add(panel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    /** Refresh the JCEF browser. Debounced. */
    fun scheduleRefresh() {
        val delay = MarpSettings.getInstance().state.previewRefreshDelayMs
        refresher.schedule(delay)
    }

    private fun reload() {
        if (browser == null) return
        val cef = browser.cefBrowser
        if (cef.url.isNullOrBlank()) {
            loadOrPlaceholder()
        } else {
            cef.reload()
        }
    }

    override fun dispose() {
        MarpServerManager.getInstance(project).removeServerStartedListener(serverListener)
        FileDocumentManager.getInstance().getDocument(file)?.removeDocumentListener(documentListener)
    }
}
