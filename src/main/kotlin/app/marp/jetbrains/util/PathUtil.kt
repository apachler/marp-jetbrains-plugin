package app.marp.jetbrains.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths

object PathUtil {

    /**
     * Percent-encode each `/`-separated segment of [relative] so the result is
     * safe to drop into the path component of a URL. Spaces become `%20` (not
     * `+`), non-ASCII characters are UTF-8 percent-encoded, and slashes between
     * segments are preserved.
     *
     *     "My Slides/résumé.md"  →  "My%20Slides/r%C3%A9sum%C3%A9.md"
     */
    fun encodePathSegments(relative: String): String {
        if (relative.isEmpty()) return relative
        return relative.split('/').joinToString("/") { segment ->
            if (segment.isEmpty()) segment
            else URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
        }
    }


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
