package app.marp.jetbrains.detector

import app.marp.jetbrains.util.Frontmatter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets

object MarpFileDetector {

    private const val HEAD_BYTES = 2048
    private const val HEAD_CHARS = 4096
    private val CACHE_KEY = Key.create<CachedResult>("app.marp.jetbrains.isMarp")

    private data class CachedResult(val key: Long, val isMarp: Boolean)

    /**
     * True if [file] is a Markdown file whose head (first ~2 KB) contains Marp
     * frontmatter. When [file] has an open in-memory document, the document's
     * current text is used so unsaved `marp: true` toggles are detected
     * immediately. The result is cached on the VirtualFile keyed by a composite
     * of the disk and document modification stamps, so the cache is invalidated
     * automatically on either edit or save.
     */
    fun isMarp(file: VirtualFile, @Suppress("UNUSED_PARAMETER") project: Project): Boolean {
        if (!isMarkdown(file)) return false

        val document = FileDocumentManager.getInstance().getDocument(file)
        val cacheKey = composeCacheKey(file.modificationStamp, document?.modificationStamp)

        val cached = file.getUserData(CACHE_KEY)
        if (cached != null && cached.key == cacheKey) return cached.isMarp

        val result = readIsMarp(file, document)
        file.putUserData(CACHE_KEY, CachedResult(cacheKey, result))
        return result
    }

    /** Pure-text check used by tests. */
    fun isMarp(text: String): Boolean = Frontmatter.isMarp(text)

    private fun isMarkdown(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext == "md" || ext == "markdown" || ext == "mdown" || ext == "mkd"
    }

    private fun readIsMarp(file: VirtualFile, document: com.intellij.openapi.editor.Document?): Boolean {
        if (!file.isValid || file.isDirectory) return false

        if (document != null) {
            val end = minOf(HEAD_CHARS, document.textLength)
            val head = document.charsSequence.subSequence(0, end).toString()
            return Frontmatter.isMarp(head)
        }

        val bytes = try {
            file.inputStream.use { it.readNBytes(HEAD_BYTES) }
        } catch (_: Exception) {
            return false
        }
        val charset = file.charset ?: StandardCharsets.UTF_8
        val text = LoadTextUtil.getTextByBinaryPresentation(bytes, charset).toString()
        return Frontmatter.isMarp(text)
    }

    private fun composeCacheKey(fileStamp: Long, docStamp: Long?): Long {
        // Mix file stamp with doc stamp so the cache invalidates on either change.
        // Long.MIN_VALUE marks "no document open"; any subsequent document mod
        // changes the high half of the key.
        val doc = docStamp ?: Long.MIN_VALUE
        return fileStamp xor java.lang.Long.rotateLeft(doc, 32)
    }
}
