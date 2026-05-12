package app.marp.jetbrains.preview

import app.marp.jetbrains.detector.MarpFileDetector
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class MarpPreviewFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        MarpFileDetector.isMarp(file, project)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        MarpPreviewFileEditor(project, file)

    override fun getEditorTypeId(): String = "marp-preview"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}
