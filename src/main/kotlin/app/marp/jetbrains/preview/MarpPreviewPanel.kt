package app.marp.jetbrains.preview

import app.marp.jetbrains.cli.MarpServerListener
import app.marp.jetbrains.cli.MarpServerManager
import app.marp.jetbrains.settings.MarpSettings
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

class MarpPreviewPanel(
    private val project: Project,
    private val file: VirtualFile,
) : JBPanel<MarpPreviewPanel>(BorderLayout()), Disposable {

    private enum class Mode { PLACEHOLDER, BROWSER, JCEF_DISABLED }

    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private val refresher: DebouncedRefresher
    private val documentListener: DocumentListener

    private val placeholderHolder = JBPanel<JBPanel<*>>(BorderLayout())
    private val placeholderLabel = JBLabel("", SwingConstants.CENTER)
    private val retryButton = JButton("Retry")
    private val helpButton = JButton("Help")

    private var currentMode: Mode? = null

    init {
        val delay = MarpSettings.getInstance().state.previewRefreshDelayMs
        refresher = DebouncedRefresher(this, delay) { reload() }
        Disposer.register(this, refresher)

        if (browser != null) Disposer.register(this, browser)

        retryButton.addActionListener {
            MarpServerManager.getInstance(project).stop()
            startServerAndLoad()
        }
        helpButton.addActionListener {
            BrowserUtil.browse(JCEF_DOCS_URL)
        }
        buildPlaceholderHolder()

        documentListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = scheduleRefresh()
        }
        FileDocumentManager.getInstance().getDocument(file)?.addDocumentListener(documentListener, this)

        // MessageBus subscription disposes automatically with `this` — no manual
        // removeListener call needed, no risk of leaking on dispose-after-dispatch.
        project.messageBus.connect(this).subscribe(
            MarpServerManager.TOPIC,
            object : MarpServerListener {
                override fun onServerReady() = this@MarpPreviewPanel.onServerStarted()
            },
        )

        startServerAndLoad()
    }

    private fun buildPlaceholderHolder() {
        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)
        placeholderLabel.alignmentX = CENTER_ALIGNMENT
        content.add(placeholderLabel)
        val buttons = JPanel(FlowLayout(FlowLayout.CENTER, 8, 8))
        buttons.add(retryButton)
        buttons.add(helpButton)
        buttons.alignmentX = CENTER_ALIGNMENT
        content.add(buttons)

        placeholderHolder.add(content, BorderLayout.CENTER)
    }

    private fun startServerAndLoad() {
        val serverManager = MarpServerManager.getInstance(project)
        serverManager.startIfNeeded()
        loadOrPlaceholder()
    }

    /** Called from MarpServerManager on the EDT when the server is ready. */
    private fun onServerStarted() {
        ApplicationManager.getApplication().invokeLater {
            loadOrPlaceholder()
            // Trailing refresh: render the most recent edit that arrived while the
            // server was starting up (debounced refreshes during startup were no-ops).
            scheduleRefresh()
        }
    }

    private fun loadOrPlaceholder() {
        if (browser == null) {
            switchMode(
                Mode.JCEF_DISABLED,
                "JCEF runtime is not enabled. Open <b>Find Action → Choose Boot Java Runtime for the IDE</b> " +
                    "and pick the JBR with JCEF, then restart the IDE.",
                showRetry = false,
                showHelp = true,
            )
            return
        }
        val url = MarpServerManager.getInstance(project).getPreviewUrl(file)
        if (url == null) {
            switchMode(
                Mode.PLACEHOLDER,
                "Marp preview is starting…",
                showRetry = true,
                showHelp = false,
            )
        } else {
            switchMode(Mode.BROWSER, "", showRetry = false, showHelp = false)
            browser.loadURL(url)
        }
    }

    private fun switchMode(mode: Mode, message: String, showRetry: Boolean, showHelp: Boolean) {
        if (currentMode == mode) {
            // Same mode: just update placeholder text without rebuilding the tree.
            if (mode != Mode.BROWSER) {
                placeholderLabel.text = "<html><div style='text-align:center;'>$message</div></html>"
                retryButton.isVisible = showRetry
                helpButton.isVisible = showHelp
            }
            return
        }

        currentMode = mode
        removeAll()
        when (mode) {
            Mode.BROWSER -> {
                add(browser!!.component, BorderLayout.CENTER)
            }
            Mode.PLACEHOLDER, Mode.JCEF_DISABLED -> {
                placeholderLabel.text = "<html><div style='text-align:center;'>$message</div></html>"
                retryButton.isVisible = showRetry
                helpButton.isVisible = showHelp
                add(placeholderHolder, BorderLayout.CENTER)
            }
        }
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
        // Marp CLI reads the source file from disk; in-memory edits must be flushed
        // first or the preview re-renders stale content. The Marp server's own
        // WebSocket-based auto-reload will fire when the file changes on disk;
        // the explicit browser.reload() below is a safety net for cases where the
        // WebSocket connection has been dropped (e.g. after suspend/resume).
        saveDocumentIfNeeded()

        val cef = browser.cefBrowser
        if (cef.url.isNullOrBlank()) {
            loadOrPlaceholder()
        } else {
            cef.reload()
        }
    }

    private fun saveDocumentIfNeeded() {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val fdm = FileDocumentManager.getInstance()
        if (fdm.isDocumentUnsaved(document)) {
            fdm.saveDocument(document)
        }
    }

    override fun dispose() {
        FileDocumentManager.getInstance().getDocument(file)?.removeDocumentListener(documentListener)
    }

    companion object {
        private const val JCEF_DOCS_URL =
            "https://www.jetbrains.com/help/idea/managing-plugins.html#jcef"
    }
}
