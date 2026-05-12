package app.marp.jetbrains.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrontmatterTest {

    @Test
    fun `returns null when there is no frontmatter`() {
        assertNull(Frontmatter.parse("# Just a title\n\nsome body"))
        assertNull(Frontmatter.parse(""))
    }

    @Test
    fun `returns null when frontmatter is not closed`() {
        val text = """
            ---
            marp: true
            theme: default
        """.trimIndent()
        assertNull(Frontmatter.parse(text))
    }

    @Test
    fun `parses flat key value pairs`() {
        val text = """
            ---
            marp: true
            theme: default
            paginate: false
            ---
            # Slide
        """.trimIndent()
        val fm = Frontmatter.parse(text)!!
        assertEquals("true", fm["marp"])
        assertEquals("default", fm["theme"])
        assertEquals("false", fm["paginate"])
    }

    @Test
    fun `strips surrounding quotes from values`() {
        val text = """
            ---
            title: "Hello"
            author: 'Jane'
            ---
        """.trimIndent()
        val fm = Frontmatter.parse(text)!!
        assertEquals("Hello", fm["title"])
        assertEquals("Jane", fm["author"])
    }

    @Test
    fun `ignores malformed lines without colon`() {
        val text = """
            ---
            marp: true
            not a key value line
            theme: default
            ---
        """.trimIndent()
        val fm = Frontmatter.parse(text)!!
        assertEquals("true", fm["marp"])
        assertEquals("default", fm["theme"])
        assertEquals(2, fm.size)
    }

    @Test
    fun `isMarp returns true for marp true`() {
        val text = """
            ---
            marp: true
            ---
            # Slide
        """.trimIndent()
        assertTrue(Frontmatter.isMarp(text))
    }

    @Test
    fun `isMarp returns false for marp false`() {
        val text = """
            ---
            marp: false
            ---
        """.trimIndent()
        assertFalse(Frontmatter.isMarp(text))
    }

    @Test
    fun `isMarp returns false when marp key absent`() {
        val text = """
            ---
            title: Hello
            theme: default
            ---
        """.trimIndent()
        assertFalse(Frontmatter.isMarp(text))
    }

    @Test
    fun `isMarp returns false when no frontmatter`() {
        assertFalse(Frontmatter.isMarp("just text"))
        assertFalse(Frontmatter.isMarp(""))
    }

    @Test
    fun `isMarp is case insensitive on value`() {
        val text = """
            ---
            marp: TRUE
            ---
        """.trimIndent()
        assertTrue(Frontmatter.isMarp(text))
    }

    @Test
    fun `handles UTF-8 BOM at start of file`() {
        val text = "﻿---\nmarp: true\n---\n# Slide\n"
        assertTrue(Frontmatter.isMarp(text))
        val fm = Frontmatter.parse(text)!!
        assertEquals("true", fm["marp"])
    }

    @Test
    fun `handles CRLF line endings`() {
        val text = "---\r\nmarp: true\r\ntheme: default\r\n---\r\n"
        val fm = Frontmatter.parse(text)!!
        assertEquals("true", fm["marp"])
        assertEquals("default", fm["theme"])
    }

    @Test
    fun `accepts trailing whitespace on delimiter line`() {
        val text = "---   \nmarp: true\n---  \n"
        assertTrue(Frontmatter.isMarp(text))
    }

    @Test
    fun `accepts more than three dashes on delimiter`() {
        val text = "----\nmarp: true\n----\n"
        assertTrue(Frontmatter.isMarp(text))
    }

    @Test
    fun `handles very long values`() {
        val longValue = "x".repeat(500)
        val text = "---\nmarp: true\ndescription: $longValue\n---\n"
        val fm = Frontmatter.parse(text)!!
        assertEquals(longValue, fm["description"])
    }

    @Test
    fun `preserves quoted value with escaped inner content as-is`() {
        // Spec is "flat key: value pairs", so we don't unescape, just preserve
        // the textual content between the outer quotes.
        val text = "---\nmarp: true\ntitle: \"Hello \\\"world\\\"\"\n---\n"
        val fm = Frontmatter.parse(text)!!
        assertEquals("Hello \\\"world\\\"", fm["title"])
    }

    @Test
    fun `blank lines between key-value pairs are ignored`() {
        val text = """
            ---
            marp: true

            theme: default

            paginate: true
            ---
        """.trimIndent()
        val fm = Frontmatter.parse(text)!!
        assertEquals("true", fm["marp"])
        assertEquals("default", fm["theme"])
        assertEquals("true", fm["paginate"])
    }

    @Test
    fun `value with embedded colon is preserved after first colon`() {
        val text = "---\nmarp: true\nurl: https://example.com:8080/path\n---\n"
        val fm = Frontmatter.parse(text)!!
        assertEquals("https://example.com:8080/path", fm["url"])
    }

    @Test
    fun `key with leading whitespace is trimmed`() {
        val text = "---\n   marp: true\n---\n"
        val fm = Frontmatter.parse(text)!!
        assertEquals("true", fm["marp"])
    }

    @Test
    fun `returns null when text has only delimiter`() {
        assertNull(Frontmatter.parse("---\n"))
    }

    @Test
    fun `returns null when text is single delimiter without newline`() {
        assertNull(Frontmatter.parse("---"))
    }
}
