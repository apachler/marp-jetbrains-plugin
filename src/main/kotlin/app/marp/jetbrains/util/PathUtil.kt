package app.marp.jetbrains.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.nio.file.Paths

object PathUtil {

    /**
     * Return the project root path, or null if the project has no base path.
     */
    fun projectRoot(project: Project): Path? {
        val base = project.basePath ?: return null
        return Paths.get(base)
    }

    /**
     * Path of [file] relative to the project root, using forward slashes.
     * Returns null if the file is not located under the project root.
     */
    fun relativePathInProject(project: Project, file: VirtualFile): String? {
        val root = projectRoot(project) ?: return null
        val target = runCatching { Paths.get(file.path) }.getOrNull() ?: return null
        if (!target.startsWith(root)) return null
        val rel = root.relativize(target).toString()
        return rel.replace('\\', '/')
    }
}
