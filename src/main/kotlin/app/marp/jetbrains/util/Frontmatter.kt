package app.marp.jetbrains.util

object Frontmatter {

    private val DELIMITER = Regex("^-{3,}\\s*$")

    /**
     * Parse YAML frontmatter from the start of [text].
     * Returns null if no frontmatter present.
     * Only supports flat key: value pairs (no nesting).
     */
    fun parse(text: String): Map<String, String>? {
        if (text.isEmpty()) return null
        // Strip a leading UTF-8 BOM if present — files saved by Notepad or some
        // exporters embed one and would otherwise fail the delimiter match.
        val sanitized = if (text.startsWith('﻿')) text.substring(1) else text
        val lines = sanitized.lineSequence().iterator()
        if (!lines.hasNext()) return null
        val first = lines.next()
        if (!DELIMITER.matches(first)) return null

        val result = LinkedHashMap<String, String>()
        while (lines.hasNext()) {
            val line = lines.next()
            if (DELIMITER.matches(line)) {
                return result
            }
            if (line.isBlank()) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim()
            if (key.isEmpty()) continue
            val value = line.substring(colon + 1).trim().trimQuotes()
            result[key] = value
        }
        return null
    }

    /**
     * Check if [text] starts with frontmatter containing `marp: true`.
     */
    fun isMarp(text: String): Boolean {
        val fm = parse(text) ?: return false
        return fm["marp"].equals("true", ignoreCase = true)
    }

    private fun String.trimQuotes(): String {
        if (length >= 2) {
            val first = this[0]
            val last = this[length - 1]
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return substring(1, length - 1)
            }
        }
        return this
    }
}
