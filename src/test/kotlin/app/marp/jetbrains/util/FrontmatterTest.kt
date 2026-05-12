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
}
