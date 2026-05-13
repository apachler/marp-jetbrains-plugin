package app.marp.jetbrains.actions

import app.marp.jetbrains.detector.MarpFileDetector
import app.marp.jetbrains.preview.MarpPreviewFileEditor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager

class ReloadMarpPreviewAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        e.presentation.isEnabledAndVisible =
            project != null && file != null && !file.isDirectory && MarpFileDetector.isMarp(file, project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        FileEditorManager.getInstance(project).getEditors(file)
            .filterIsInstance<MarpPreviewFileEditor>()
            .forEach { it.refresh() }
    }
}
