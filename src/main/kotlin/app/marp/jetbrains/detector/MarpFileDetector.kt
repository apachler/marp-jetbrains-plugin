package app.marp.jetbrains.detector

import app.marp.jetbrains.util.Frontmatter
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets

object MarpFileDetector {

    private const val HEAD_BYTES = 2048
    private val CACHE_KEY = Key.create<CachedResult>("app.marp.jetbrains.isMarp")

    private data class CachedResult(val stamp: Long, val isMarp: Boolean)

    /** True if the file is a Markdown file with marp:true frontmatter. */
    fun isMarp(file: VirtualFile, @Suppress("UNUSED_PARAMETER") project: Project): Boolean {
        if (!isMarkdown(file)) return false

        val stamp = file.modificationStamp
        val cached = file.getUserData(CACHE_KEY)
        if (cached != null && cached.stamp == stamp) return cached.isMarp

        val result = readIsMarp(file)
        file.putUserData(CACHE_KEY, CachedResult(stamp, result))
        return result
    }

    /** Read at most the first 2 KB of the file and check for `marp: true` frontmatter. */
    fun isMarp(text: String): Boolean = Frontmatter.isMarp(text)

    private fun isMarkdown(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext == "md" || ext == "markdown" || ext == "mdown" || ext == "mkd"
    }

    private fun readIsMarp(file: VirtualFile): Boolean {
        if (!file.isValid || file.isDirectory) return false
        val bytes = try {
            file.inputStream.use { it.readNBytes(HEAD_BYTES) }
        } catch (_: Exception) {
            return false
        }
        val charset = file.charset ?: StandardCharsets.UTF_8
        val text = LoadTextUtil.getTextByBinaryPresentation(bytes, charset).toString()
        return Frontmatter.isMarp(text)
    }
}
