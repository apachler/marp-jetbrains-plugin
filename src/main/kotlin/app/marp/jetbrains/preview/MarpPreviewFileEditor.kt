package app.marp.jetbrains.preview

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class MarpPreviewFileEditor(
    project: Project,
    private val file: VirtualFile,
) : UserDataHolderBase(), FileEditor {

    private val panel = MarpPreviewPanel(project, file)

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = "Marp Preview"
    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) { /* no-op */ }
    override fun getState(level: FileEditorStateLevel): FileEditorState =
        FileEditorState { _, _ -> false }

    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) { /* no-op */ }
    override fun removePropertyChangeListener(listener: PropertyChangeListener) { /* no-op */ }
    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        com.intellij.openapi.util.Disposer.dispose(panel)
    }
}
