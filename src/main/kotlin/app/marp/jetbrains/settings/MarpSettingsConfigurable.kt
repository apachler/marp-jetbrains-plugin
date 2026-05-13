package app.marp.jetbrains.settings

import app.marp.jetbrains.cli.MarpCliInstaller
import app.marp.jetbrains.cli.NodeJsDetector
import app.marp.jetbrains.service.MarpApplicationService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class MarpSettingsConfigurable : Configurable {

    private var component: MarpSettingsComponent? = null

    override fun getDisplayName(): String = "Marp"

    override fun getPreferredFocusedComponent(): JComponent? = component?.getPreferredFocusedComponent()

    override fun createComponent(): JComponent {
        val c = MarpSettingsComponent()
        c.load(MarpSettings.getInstance().state)
        c.reinstallAction = {
            val settings = MarpSettings.getInstance().state
            val node = NodeJsDetector.detect(settings.nodeJsPath)
            if (node != null) {
                MarpCliInstaller(node).clearCache()
                MarpApplicationService.getInstance().invalidateCache()
            }
        }
        component = c
        return c.panel
    }

    override fun isModified(): Boolean {
        val c = component ?: return false
        return c.isModified(MarpSettings.getInstance().state)
    }

    override fun apply() {
        val c = component ?: return
        val settings = MarpSettings.getInstance()
        val old = settings.state.copy()
        settings.update { state -> c.apply(state) }
        val new = settings.state.copy()
        if (old != new) {
            ApplicationManager.getApplication().messageBus
                .syncPublisher(MarpSettings.TOPIC)
                .onSettingsChanged(old, new)
        }
    }

    override fun reset() {
        component?.load(MarpSettings.getInstance().state)
    }

    override fun disposeUIResources() {
        component = null
    }
}
