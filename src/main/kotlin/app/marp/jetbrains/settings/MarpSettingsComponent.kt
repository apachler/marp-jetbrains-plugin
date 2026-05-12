package app.marp.jetbrains.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class MarpSettingsComponent {

    private val nodeJsPathField = TextFieldWithBrowseButton()
    private val marpCliPathField = TextFieldWithBrowseButton()
    private val marpCliVersionField = JBTextField()
    private val refreshDelaySpinner = JSpinner(SpinnerNumberModel(300, 100, 2000, 50))
    private val autoOpenCheckbox = JBCheckBox("Open preview tab automatically for Marp files")
    private val allowLocalFilesCheckbox = JBCheckBox("Allow local file access in Marp preview (less secure)")
    private val reinstallButton = JButton("Reinstall Marp CLI")

    var reinstallAction: (() -> Unit)? = null

    val panel: JPanel

    init {
        nodeJsPathField.addBrowseFolderListener(
            "Select Node.js Executable",
            "Choose the node binary",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )
        marpCliPathField.addBrowseFolderListener(
            "Select Marp CLI Executable",
            "Choose the marp binary",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor(),
        )
        reinstallButton.addActionListener { reinstallAction?.invoke() }

        val warningLabel = JBLabel(
            "<html><i>Enabling local file access lets the preview read files outside the project. Use with care.</i></html>"
        )

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Node.js executable:"), nodeJsPathField, 1, false)
            .addLabeledComponent(JBLabel("Marp CLI executable (override):"), marpCliPathField, 1, false)
            .addLabeledComponent(JBLabel("Marp CLI version:"), marpCliVersionField, 1, false)
            .addLabeledComponent(JBLabel("Preview refresh delay (ms):"), refreshDelaySpinner, 1, false)
            .addComponent(autoOpenCheckbox, 1)
            .addComponent(allowLocalFilesCheckbox, 1)
            .addComponent(warningLabel, 1)
            .addComponent(reinstallButton, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    fun getPreferredFocusedComponent(): JComponent = nodeJsPathField.textField

    fun load(state: MarpSettings.State) {
        nodeJsPathField.text = state.nodeJsPath.orEmpty()
        marpCliPathField.text = state.marpCliPath.orEmpty()
        marpCliVersionField.text = state.marpCliVersion
        refreshDelaySpinner.value = state.previewRefreshDelayMs
        autoOpenCheckbox.isSelected = state.autoOpenPreview
        allowLocalFilesCheckbox.isSelected = state.allowLocalFiles
    }

    fun apply(target: MarpSettings.State) {
        target.nodeJsPath = nodeJsPathField.text.trim().ifBlank { null }
        target.marpCliPath = marpCliPathField.text.trim().ifBlank { null }
        target.marpCliVersion = marpCliVersionField.text.trim().ifBlank { "latest" }
        target.previewRefreshDelayMs = (refreshDelaySpinner.value as Number).toInt()
        target.autoOpenPreview = autoOpenCheckbox.isSelected
        target.allowLocalFiles = allowLocalFilesCheckbox.isSelected
    }

    fun isModified(state: MarpSettings.State): Boolean {
        return nodeJsPathField.text.trim().ifBlank { null } != state.nodeJsPath ||
            marpCliPathField.text.trim().ifBlank { null } != state.marpCliPath ||
            marpCliVersionField.text.trim() != state.marpCliVersion &&
                !(marpCliVersionField.text.isBlank() && state.marpCliVersion == "latest") ||
            (refreshDelaySpinner.value as Number).toInt() != state.previewRefreshDelayMs ||
            autoOpenCheckbox.isSelected != state.autoOpenPreview ||
            allowLocalFilesCheckbox.isSelected != state.allowLocalFiles
    }
}
